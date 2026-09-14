import { NextRequest, NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/api/client";
import { refrescar, type Tokens } from "@/lib/api/auth";
import { clearSession, readSession, writeTokens } from "@/lib/session";

type RouteContext = { params: Promise<{ path: string[] }> };

/**
 * El refresh rota el token en el backend: dos peticiones concurrentes con el mismo
 * refresh vencido no pueden refrescar cada una por su cuenta (la segunda encontraría
 * la sesión ya revocada por la primera). Comparten una única promesa en curso.
 */
let refrescoEnCurso: Promise<Tokens> | null = null;

function refrescarUnaVez(refresh: string): Promise<Tokens> {
  if (!refrescoEnCurso) {
    refrescoEnCurso = refrescar(refresh).finally(() => {
      refrescoEnCurso = null;
    });
  }
  return refrescoEnCurso;
}

/**
 * `path` viene de un segmento catch-all: sin validar, un segmento ".." permitiría que
 * `new URL(...)`/`fetch` normalizara la ruta y escapara del prefijo /v1 (p. ej. hacia
 * /v1/admin/**, que no exige JWT). Solo se permiten segmentos "de un solo nivel".
 */
function segmentoValido(segmento: string): boolean {
  return segmento !== "" && segmento !== "." && segmento !== ".." && !segmento.includes("/") && !segmento.includes("\\");
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
