import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS, COOKIE_ADMIN_DESAFIO } from "@/lib/admin-session";

vi.mock("@/lib/api/auth", () => ({ login: vi.fn(), recuperar: vi.fn(), registrar: vi.fn(), restablecer: vi.fn(), verificarCorreo: vi.fn() }));
vi.mock("@/lib/api/empresas", () => ({ listarEmpresas: vi.fn() }));
vi.mock("@/lib/api/equipo", () => ({ aceptarInvitacion: vi.fn() }));
vi.mock("@/lib/api/admin-auth", () => ({ loginAdministrador: vi.fn(), confirmarSegundoFactor: vi.fn(), verificarSegundoFactor: vi.fn() }));
vi.mock("@/lib/api/admin-alta", () => ({ altaAsistida: vi.fn() }));
vi.mock("@/lib/api/admin-baja", () => ({ darDeBajaCuenta: vi.fn() }));
vi.mock("@/lib/api/admin-suspension", () => ({ suspenderCuenta: vi.fn() }));
vi.mock("@/lib/api/admin-acciones-empresa", async (original) => ({ ...(await original<object>()), cambiarEntornoEmpresa: vi.fn() }));

import * as auth from "@/lib/api/auth";
import * as empresas from "@/lib/api/empresas";
import * as equipo from "@/lib/api/equipo";
import * as adminAuth from "@/lib/api/admin-auth";
import * as adminAlta from "@/lib/api/admin-alta";
import * as adminBaja from "@/lib/api/admin-baja";
import * as adminSuspension from "@/lib/api/admin-suspension";
import * as adminAccionesEmpresa from "@/lib/api/admin-acciones-empresa";

import { POST as adminAuthLogin } from "./admin/auth/login/route";
import { POST as confirmar } from "./admin/auth/segundo-factor/confirmar/route";
import { POST as verificar2fa } from "./admin/auth/segundo-factor/verificar/route";
import { POST as adminCuentas } from "./admin/cuentas/route";
import { POST as baja } from "./admin/cuentas/[id]/baja/route";
import { POST as suspender } from "./admin/cuentas/[id]/suspender/route";
import { POST as entorno } from "./admin/empresas/[id]/entorno/route";
import { POST as aceptarInvitacion } from "./auth/invitacion/aceptar/route";
import { POST as login } from "./auth/login/route";
import { POST as recuperar } from "./auth/recuperar/route";
import { POST as registro } from "./auth/registro/route";
import { POST as restablecer } from "./auth/restablecer/route";
import { POST as verificarCorreo } from "./auth/verificar/route";
import { POST as sesionEmpresa } from "./session/empresa/route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";

type Ruta = {
  nombre: string;
  POST: (req: NextRequest, ctx: { params: Promise<{ id: string }> }) => Promise<Response>;
  /** Pide la sesión del administrador (cookie `httpOnly`). */
  admin?: boolean;
  /** Pide el desafío del login del administrador (paso 2). */
  desafio?: boolean;
  /** El cuerpo es opcional: vacío es válido, lo demás no. */
  vacioValido?: boolean;
};

const RUTAS: Ruta[] = [
  { nombre: "auth/login", POST: login },
  { nombre: "auth/recuperar", POST: recuperar },
  { nombre: "auth/registro", POST: registro },
  { nombre: "auth/restablecer", POST: restablecer },
  { nombre: "auth/verificar", POST: verificarCorreo },
  { nombre: "auth/invitacion/aceptar", POST: aceptarInvitacion },
  { nombre: "admin/auth/login", POST: adminAuthLogin },
  { nombre: "admin/auth/segundo-factor/confirmar", POST: confirmar, desafio: true },
  { nombre: "admin/auth/segundo-factor/verificar", POST: verificar2fa, desafio: true },
  { nombre: "session/empresa", POST: sesionEmpresa },
  { nombre: "admin/cuentas", POST: adminCuentas, admin: true },
  { nombre: "admin/cuentas/[id]/baja", POST: baja, admin: true, vacioValido: true },
  { nombre: "admin/cuentas/[id]/suspender", POST: suspender, admin: true, vacioValido: true },
  { nombre: "admin/empresas/[id]/entorno", POST: entorno, admin: true },
];

/** Cada cuerpo que no es un objeto JSON, con cómo se escribe en el cuerpo de la petición. */
const CUERPOS_INVALIDOS = [
  { nombre: "no es JSON", texto: "esto-no-es-json" },
  { nombre: "vacío", texto: "" },
  { nombre: "null", texto: "null" },
  { nombre: "un arreglo", texto: "[]" },
  { nombre: "un texto", texto: '"x"' },
  { nombre: "un número", texto: "5" },
];

const SOBRE_JSON_INVALIDO = { estado: "error", datos: null, mensaje: null, codigo: "JSON_INVALIDO", errores: null };

const LLAMADAS_AL_BACKEND = [
  auth.login, auth.recuperar, auth.registrar, auth.restablecer, auth.verificarCorreo,
  empresas.listarEmpresas, equipo.aceptarInvitacion,
  adminAuth.loginAdministrador, adminAuth.confirmarSegundoFactor, adminAuth.verificarSegundoFactor,
  adminAlta.altaAsistida, adminBaja.darDeBajaCuenta, adminSuspension.suspenderCuenta, adminAccionesEmpresa.cambiarEntornoEmpresa,
];

function peticion(ruta: Ruta, texto: string, opciones: { sesion?: boolean; desafio?: boolean } = {}) {
  const cookies: string[] = [];
  if (ruta.admin && opciones.sesion !== false) cookies.push(`${COOKIE_ADMIN_ACCESS}=jwt-admin`);
  if (ruta.desafio && opciones.desafio !== false) cookies.push(`${COOKIE_ADMIN_DESAFIO}=desafio`);
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (cookies.length > 0) headers.cookie = cookies.join("; ");
  return new NextRequest(`http://localhost/api/${ruta.nombre}`, { method: "POST", headers, body: texto });
}

const llamar = (ruta: Ruta, req: NextRequest) => ruta.POST(req, { params: Promise.resolve({ id: ID }) });

beforeEach(() => {
  for (const f of LLAMADAS_AL_BACKEND) vi.mocked(f).mockReset();
});

afterEach(() => vi.unstubAllEnvs());

/** #608: un cuerpo que no es un objeto JSON nunca llega al backend ni acaba en 500: 400 JSON_INVALIDO, sin texto (el navegador lo traduce por el código). */
describe("las rutas del BFF con un cuerpo que no es un objeto JSON", () => {
  const casos = RUTAS.flatMap((ruta) =>
    CUERPOS_INVALIDOS.filter((c) => !(ruta.vacioValido && c.texto === "")).map((cuerpo) => ({ ruta, cuerpo, titulo: `${ruta.nombre} con un cuerpo ${cuerpo.nombre}` })),
  );

  it.each(casos)("$titulo responde 400 JSON_INVALIDO y no llama al backend", async ({ ruta, cuerpo }) => {
    const res = await llamar(ruta, peticion(ruta, cuerpo.texto));

    expect(res.status).toBe(400);
    expect(await res.json()).toEqual(SOBRE_JSON_INVALIDO);
    for (const f of LLAMADAS_AL_BACKEND) expect(f).not.toHaveBeenCalled();
  });

  it.each(RUTAS.filter((r) => r.vacioValido))("$nombre acepta un cuerpo vacío (es opcional) y llega al backend", async (ruta) => {
    vi.mocked(adminBaja.darDeBajaCuenta).mockResolvedValue({} as never);
    vi.mocked(adminSuspension.suspenderCuenta).mockResolvedValue({} as never);

    const res = await llamar(ruta, peticion(ruta, ""));

    expect(res.status).toBe(200);
  });
});

/** Lo que se mira antes que el cuerpo sigue mandando: la sesión, el desafío y el registro cerrado. */
describe("la precedencia frente al cuerpo", () => {
  it.each(RUTAS.filter((r) => r.admin))("$nombre sin sesión de administrador responde 401 aunque el cuerpo sea inválido", async (ruta) => {
    const res = await llamar(ruta, peticion(ruta, "esto-no-es-json", { sesion: false }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
  });

  it.each(RUTAS.filter((r) => r.desafio))("$nombre sin el desafío del login responde 401 SESION_INVALIDA aunque el cuerpo sea inválido", async (ruta) => {
    const res = await llamar(ruta, peticion(ruta, "esto-no-es-json", { desafio: false }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("SESION_INVALIDA");
  });

  it("el registro cerrado responde 403 aunque el cuerpo sea inválido", async () => {
    vi.stubEnv("REGISTRO_ABIERTO", "false");
    const ruta = RUTAS.find((r) => r.nombre === "auth/registro")!;

    const res = await llamar(ruta, peticion(ruta, "esto-no-es-json"));

    expect(res.status).toBe(403);
    expect((await res.json()).codigo).toBe("REGISTRO_CERRADO");
  });
});
