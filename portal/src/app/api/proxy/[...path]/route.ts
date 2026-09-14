import { NextRequest, NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/api/client";
import { refrescar } from "@/lib/api/auth";
import { clearSession, readSession, writeTokens } from "@/lib/session";

type RouteContext = { params: Promise<{ path: string[] }> };

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
  const { access, refresh, empresa } = readSession(req);
  const body = await readBody(req);

  let backendRes = await forward(req, path, body, access, empresa);

  if (backendRes.status === 401 && refresh) {
    try {
      const tokens = await refrescar(refresh);
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
