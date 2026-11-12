import { NextRequest, NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/api/client";
import { refrescar, type Tokens } from "@/lib/api/auth";
import { esCuentaSuspendida } from "@/lib/api/cuenta-suspendida";
import { CABECERA_IDEMPOTENCIA } from "@/lib/idempotencia";
import { cabecerasDeOrigen } from "@/lib/origen";
import { clearSession, readSession, writeTokens } from "@/lib/session";
import { CABECERA_SESION, SESION_TERMINADA } from "@/lib/sesion/sesion-terminada";

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
 *
 * Este mapa solo ve su propio proceso: el middleware (edge) y las otras instancias del
 * portal no lo comparten. Para eso el backend acepta un refresh recién rotado durante
 * 30 segundos (S6), así que una carrera entre procesos no cierra la sesión. Esto evita
 * la mayoría de las carreras; aquello cubre las que quedan.
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
  // Next entrega los segmentos ya decodificados: un `#`, un `?` o un `;` de un número de documento (`/clientes/documento/7/AB#12`) cortaría la ruta si no se vuelven a codificar.
  const url = `${apiBaseUrl()}/v1/${path.map(encodeURIComponent).join("/")}${req.nextUrl.search}`;
  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  if (access) headers.set("Authorization", `Bearer ${access}`);
  if (empresa) headers.set("X-Empresa", empresa);
  // La clave de idempotencia de la emisión (#115) es del navegador y viaja tal cual; también en el reintento tras renovar el token,
  // que es el mismo pedido.
  const clave = req.headers.get(CABECERA_IDEMPOTENCIA);
  if (clave) headers.set(CABECERA_IDEMPOTENCIA, clave);
  // Solo la IP de confianza ya resuelta (#208): este proxy no copia las cabeceras del navegador, y esta tampoco.
  for (const [nombre, valor] of Object.entries(cabecerasDeOrigen(req.headers))) headers.set(nombre, valor);
  return fetch(url, { method: req.method, headers, body, cache: "no-store" });
}

const HTTP_NO_AUTORIZADO = 401;
const HTTP_PROHIBIDO = 403;

function pasar(backendRes: Response): NextResponse {
  return new NextResponse(backendRes.body, { status: backendRes.status, headers: backendRes.headers });
}

/** El 401 del backend, marcado para que el navegador sepa que ya no tiene arreglo (#654): ahí abre «Tu sesión terminó». Solo este proxy pone la marca. */
function sesionTerminada(backendRes: Response): NextResponse {
  const res = pasar(backendRes);
  res.headers.set(CABECERA_SESION, SESION_TERMINADA);
  return res;
}

/** El sobre de siempre, armado acá porque el cuerpo y las cabeceras del 401 original no corresponden a un 403. */
function cuentaSuspendida(): NextResponse {
  return NextResponse.json({ estado: "error", datos: null, mensaje: null, codigo: "CUENTA_SUSPENDIDA", errores: null }, { status: HTTP_PROHIBIDO });
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

  const backendRes = await forward(req, path, body, access, empresa);
  if (backendRes.status !== HTTP_NO_AUTORIZADO) return pasar(backendRes);
  // Sin refresh no hay nada que renovar (una limpieza anterior, un cierre de sesión en otra pestaña, una sesión de soporte vencida): la sesión ya terminó.
  if (!refresh) return sesionTerminada(backendRes);

  // El `try` cubre solo el refresh: si el reintento falla por la red no se limpia nada, porque el refresh viejo sigue valiendo 30 s (S6) y el siguiente intento lo recupera.
  let tokens: Tokens;
  try {
    tokens = await refrescarUnaVez(refresh);
  } catch (error) {
    // Una cuenta suspendida (#182) no es una sesión muerta: se conserva la sesión y el 403 llega tal cual, como en `middleware.ts`.
    if (esCuentaSuspendida(error)) return cuentaSuspendida();
    const res = sesionTerminada(backendRes);
    clearSession(res);
    return res;
  }

  const reintento = await forward(req, path, body, tokens.access, empresa);
  const res = pasar(reintento);
  writeTokens(res, tokens);
  return res;
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE };
