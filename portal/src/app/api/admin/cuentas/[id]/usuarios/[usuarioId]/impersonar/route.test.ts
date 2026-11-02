import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/admin-impersonacion", () => ({ impersonarUsuario: vi.fn() }));
vi.mock("@/lib/api/empresas", () => ({ listarEmpresas: vi.fn() }));

import { impersonarUsuario } from "@/lib/api/admin-impersonacion";
import { listarEmpresas } from "@/lib/api/empresas";
import { POST } from "./route";

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const USUARIO = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
const TOKEN = "jwt-de-soporte.firmado.por-el-backend";

function sesion(minutos = 15) {
  return { access_token: TOKEN, expira_en: new Date(Date.now() + minutos * 60_000).toISOString(), usuario: { id: USUARIO, cuenta_id: CUENTA, email: "ana@negocio.pe", rol: "ADMIN", correo_verificado: true } };
}

function peticion(init: { sesion?: boolean; xff?: string; cookies?: string } = {}) {
  const headers: Record<string, string> = {};
  const cookies = [init.sesion === false ? "" : `${COOKIE_ADMIN_ACCESS}=jwt-admin`, init.cookies ?? ""].filter(Boolean).join("; ");
  if (cookies) headers.cookie = cookies;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${CUENTA}/usuarios/${USUARIO}/impersonar`, { method: "POST", headers });
}

const contexto = (id = CUENTA, usuarioId = USUARIO) => ({ params: Promise.resolve({ id, usuarioId }) });

const EMPRESA_1 = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d";
const EMPRESA_2 = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6e";

beforeEach(() => {
  vi.mocked(listarEmpresas).mockResolvedValue([{ id: EMPRESA_1 }, { id: EMPRESA_2 }] as never);
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(impersonarUsuario).mockReset();
  vi.mocked(listarEmpresas).mockReset();
});

/** BFF de «entrar como un usuario» (#184): el token de soporte nunca llega al navegador como dato, solo como cookie `httpOnly`. */
describe("POST /api/admin/cuentas/[id]/usuarios/[usuarioId]/impersonar", () => {
  it("sin sesión de administrador responde 401, sin llamar al backend ni dejar ninguna cookie", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(impersonarUsuario).not.toHaveBeenCalled();
    expect(res.cookies.get(COOKIE_ACCESS)).toBeUndefined();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const [cuenta, usuario] of [["../auth/me", USUARIO], [CUENTA, "no-es-un-uuid"], [`${CUENTA}/x`, USUARIO], [CUENTA, ""]]) {
      const res = await POST(peticion(), contexto(cuenta, usuario));
      expect(res.status, `${cuenta} ${usuario}`).toBe(400);
    }
    expect(impersonarUsuario).not.toHaveBeenCalled();
  });

  it("pide la sesión de soporte con el JWT del administrador y los dos ids", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion());

    await POST(peticion(), contexto());

    expect(impersonarUsuario).toHaveBeenCalledWith("jwt-admin", CUENTA, USUARIO, {});
  });

  /** El token es una credencial: va a una cookie `httpOnly`, nunca al cuerpo de la respuesta que lee el JS del administrador. */
  it("deja el token solo en una cookie httpOnly, nunca en el cuerpo", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion());

    const res = await POST(peticion(), contexto());
    const texto = await res.text();

    expect(res.status).toBe(200);
    expect(texto).not.toContain(TOKEN);
    expect(texto).not.toContain("access_token");
    const cookie = res.cookies.get(COOKIE_ACCESS);
    expect(cookie?.value).toBe(TOKEN);
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.sameSite).toBe("lax");
    expect(cookie?.path).toBe("/");
  });

  it("la cookie vive lo que le queda al token, ni un segundo más", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion(5));

    const res = await POST(peticion(), contexto());

    const maxAge = res.cookies.get(COOKIE_ACCESS)?.maxAge ?? 0;
    expect(maxAge).toBeGreaterThan(290);
    expect(maxAge).toBeLessThanOrEqual(300);
  });

  it("aunque el backend dijera más de 15 minutos, la cookie no pasa de 15", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion(120));

    const res = await POST(peticion(), contexto());

    expect(res.cookies.get(COOKIE_ACCESS)?.maxAge).toBe(15 * 60);
  });

  it("un token que ya venció deja una cookie de un segundo, no una inmortal ni una negativa", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion(-10));

    const res = await POST(peticion(), contexto());

    expect(res.cookies.get(COOKIE_ACCESS)?.maxAge).toBe(1);
  });

  /** Sin refresh la sesión de soporte no se renueva; y lo de cualquier sesión de cliente que hubiera en el navegador se borra para no mezclarlas. */
  it("borra el refresh de cualquier sesión de cliente previa, y reemplaza su empresa activa por la de la cuenta que se mira", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion());

    const res = await POST(peticion({ cookies: `${COOKIE_REFRESH}=refresh-viejo; ${COOKIE_EMPRESA}=empresa-vieja` }), contexto());

    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("");
    expect(res.cookies.get(COOKIE_REFRESH)?.maxAge).toBe(0);
    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe(EMPRESA_1);
  });

  /**
   * Las páginas del portal leen la empresa activa de su cookie (el login la fija): sin ella, «Empresa» lleva al onboarding. Se lista con el propio token de soporte
   * (lectura) y se fija la primera, con la misma vida que la sesión, no los 30 días de una sesión normal.
   */
  it("fija como empresa activa la primera de la cuenta, con la vida de la sesión de soporte", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion(5));

    const res = await POST(peticion(), contexto());

    expect(listarEmpresas).toHaveBeenCalledWith(TOKEN);
    const empresa = res.cookies.get(COOKIE_EMPRESA);
    expect(empresa?.value).toBe(EMPRESA_1);
    expect(empresa?.httpOnly).toBe(true);
    expect(empresa?.maxAge).toBeGreaterThan(290);
    expect(empresa?.maxAge).toBeLessThanOrEqual(300);
  });

  it("si la cuenta todavía no tiene empresas no deja ninguna empresa activa, y la sesión se abre igual", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion());
    vi.mocked(listarEmpresas).mockResolvedValue([]);

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(200);
    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe("");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe(TOKEN);
  });

  it("si no se pueden listar las empresas la sesión se abre igual, sin empresa activa", async () => {
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion());
    vi.mocked(listarEmpresas).mockRejectedValue(new ApiError(500, "INTERNO", "Error interno"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(200);
    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe("");
  });

  it("la respuesta dice a quién se mira y hasta cuándo, y no se guarda en ninguna caché", async () => {
    const abierta = sesion();
    vi.mocked(impersonarUsuario).mockResolvedValue(abierta);

    const res = await POST(peticion(), contexto());
    const json = await res.json();

    expect(json.datos).toEqual({ expira_en: abierta.expira_en, usuario: { email: "ana@negocio.pe" } });
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(impersonarUsuario).mockResolvedValue(sesion());

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(impersonarUsuario).toHaveBeenCalledWith("jwt-admin", CUENTA, USUARIO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("si el backend se niega no se deja ninguna cookie de soporte y se propaga el código", async () => {
    vi.mocked(impersonarUsuario).mockRejectedValue(new ApiError(409, "USUARIO_INACTIVO", "El usuario está desactivado"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("USUARIO_INACTIVO");
    expect(res.cookies.get(COOKIE_ACCESS)).toBeUndefined();
  });
});
