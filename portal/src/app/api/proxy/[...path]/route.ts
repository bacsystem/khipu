import { NextRequest, NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/api/client";
import { refrescar, type Tokens } from "@/lib/api/auth";
import { clearSession, readSession, writeTokens } from "@/lib/session";

type RouteContext = { params: Promise<{ path: string[] }> };

/**
 * El refresh rota el token en el backend: dos peticiones concurrentes con el mismo
 * refresh vencido no pueden refrescar cada una por su cuenta (la segunda encontraría
 * la sesión ya revocada por la primera). Comparten una única promesa en curso.
 *
 * La clave del mapa es el refresh token, y eso no es un detalle: este módulo es único
 * para todo el proceso, así que dos usuarios distintos pueden estar refrescando a la
 * vez. Con una sola promesa compartida, al segundo se le escribirían las cookies del
 * primero — es decir, terminaría dentro de la sesión ajena.
 */
const refrescosEnCurso = new Map<string, Promise<Tokens>>();

function refrescarUnaVez(refresh: string): Promise<Tokens> {
  const enCurso = refrescosEnCurso.get(refresh);
  if (enCurso) return enCurso;

  const promesa = refrescar(refresh).finally(() => {
    refrescosEnCurso.delete(refresh);
  });
  refrescosEnCurso.set(refresh, promesa);
  return promesa;
}

/**
 * `path` viene de un segmento catch-all: sin validar, un segmento ".." permitiría que
 * `new URL(...)`/`fetch` normalizara la ruta y escapara del prefijo /v1 (p. ej. hacia
 * /v1/admin/**, que no exige JWT). Solo se permiten segmentos "de un solo nivel".
 */
function segmentoValido(segmento: string): boolean {
  return segmento !== "" && segmento !== "." && segmento !== ".." && !segmento.includes("/") && !segmento.includes("\\");
}

/**
 * `/v1/auth/**` no se reenvía nunca desde acá (issue #174).
 *
 * Este proxy no exige sesión: `middleware.ts` solo protege las páginas privadas, no `/api/proxy/**`, así que
 * cualquier visitante sin cuenta puede llamarlo. Antes de esto, un `POST /api/proxy/auth/registro` llegaba
 * directo a `/v1/auth/registro` del backend sin pasar por `/api/auth/registro`, que es donde vive el chequeo de
 * «registro cerrado» del portal — el registro cerrado de la página no protegía nada si alguien llamaba a la API.
 *
 * Los endpoints de auth ya tienen su propia ruta dedicada (`/api/auth/login`, `/registro`, `/recuperar`,
 * `/restablecer`; el refresh no tiene una porque nada del navegador lo llama directo, solo el propio proxy y el
 * middleware), y ninguna parte del portal usa `/api/proxy/auth/*` — comprobado, no hay una sola llamada así en
 * el código. Bloquear el prefijo entero cierra la clase de bypass, no solo el caso del registro.
 */
function esRutaDeAuth(path: string[]): boolean {
  return path[0] === "auth";
}

async function readBody(req: NextRequest): Promise<ArrayBuffer | undefined> {
  if (req.method === "GET" || req.method === "HEAD") return undefined;
  return req.arrayBuffer();
}

async function forward(
  req: NextRequest,
  path: string[],
  body: ArrayBuffer | undefined,
  access: string | undefined,
  empresa: string | undefined,
): Promise<Response> {
  const url = `${apiBaseUrl()}/v1/${path.join("/")}${req.nextUrl.search}`;
  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  if (access) headers.set("Authorization", `Bearer ${access}`);
  if (empresa) headers.set("X-Empresa", empresa);
  return fetch(url, { method: req.method, headers, body, cache: "no-store" });
}

async function handle(req: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  if (path.length === 0 || !path.every(segmentoValido)) {
    return new NextResponse(null, { status: 400 });
  }
  if (esRutaDeAuth(path)) {
    return new NextResponse(null, { status: 404 });
  }

  const { access, refresh, empresa } = readSession(req);
  const body = await readBody(req);

  let backendRes = await forward(req, path, body, access, empresa);

  if (backendRes.status === 401 && refresh) {
    try {
      const tokens = await refrescarUnaVez(refresh);
      backendRes = await forward(req, path, body, tokens.access, empresa);
      const res = new NextResponse(backendRes.body, { status: backendRes.status, headers: backendRes.headers });
      writeTokens(res, tokens);
      return res;
    } catch {
      const res = new NextResponse(backendRes.body, { status: backendRes.status, headers: backendRes.headers });
      clearSession(res);
      return res;
    }
  }

  return new NextResponse(backendRes.body, { status: backendRes.status, headers: backendRes.headers });
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE };
