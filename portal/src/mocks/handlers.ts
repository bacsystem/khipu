import { http, HttpResponse } from "msw";
import { db, fakeJwt, type Empresa, type Usuario } from "./data";

// Debe coincidir con la URL que usa el server del portal (client.ts); si no, MSW no intercepta y las peticiones van al backend real.
const BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

function ok<T>(datos: T, status = 200) {
  return HttpResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { status });
}

function fail(status: number, codigo: string, mensaje: string) {
  return HttpResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });
}

function claims(req: Request): { sub: string; cuenta: string } | null {
  const auth = req.headers.get("authorization");
  if (!auth?.startsWith("Bearer ")) return null;
  const token = auth.slice("Bearer ".length);
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return { sub: payload.sub, cuenta: payload.cuenta };
  } catch {
    return null;
  }
}

function emitirTokens(usuario: Usuario) {
  const access = fakeJwt({ sub: usuario.id, cuenta: usuario.cuenta_id, rol: usuario.rol, exp: Math.floor(Date.now() / 1000) + 900 });
  const refresh = `refresh-${usuario.id}-${Date.now()}`;
  db.sesionesPorToken.set(refresh, { usuario });
  return { access, refresh, usuario };
}

let contador = 0;
function nuevoId(prefijo: string) {
  contador += 1;
  return `${prefijo}-${contador}`;
}

export const handlers = [
  http.post(`${BASE}/v1/auth/registro`, async ({ request }) => {
    const body = (await request.json()) as { nombre: string; email: string; password: string };
    if (db.usuariosPorEmail.has(body.email)) return fail(409, "DUPLICADO", "Ya existe una cuenta con ese correo");
    const usuario: Usuario = { id: nuevoId("u"), cuenta_id: nuevoId("c"), email: body.email, rol: "ADMIN" };
    db.usuariosPorEmail.set(body.email, { usuario, password: body.password });
    db.empresasPorCuenta.set(usuario.cuenta_id, []);
    return ok(emitirTokens(usuario), 201);
  }),

  http.post(`${BASE}/v1/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    const registro = db.usuariosPorEmail.get(body.email);
    if (!registro || registro.password !== body.password) {
      return fail(401, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos");
    }
    return ok(emitirTokens(registro.usuario));
  }),

  http.post(`${BASE}/v1/auth/refresh`, async ({ request }) => {
    const body = (await request.json()) as { refresh: string };
    const sesion = db.sesionesPorToken.get(body.refresh);
    if (!sesion) return fail(401, "SESION_INVALIDA", "Sesión expirada o inválida");
    db.sesionesPorToken.delete(body.refresh);
    return ok(emitirTokens(sesion.usuario));
  }),

  http.post(`${BASE}/v1/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  http.get(`${BASE}/v1/auth/me`, ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const registro = [...db.usuariosPorEmail.values()].find((r) => r.usuario.id === c.sub);
    if (!registro) return fail(404, "NO_ENCONTRADO", "Usuario no encontrado");
    return ok(registro.usuario);
  }),

  http.get(`${BASE}/v1/empresas`, ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    return ok(db.empresasPorCuenta.get(c.cuenta) ?? []);
  }),

  http.post(`${BASE}/v1/empresas`, async ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const body = (await request.json()) as { ruc: string; razon_social: string; entorno: "BETA" | "PRODUCCION" };
    const empresa: Empresa = {
      id: nuevoId("e"),
      ruc: body.ruc,
      razon_social: body.razon_social,
      entorno: body.entorno,
      tiene_certificado: false,
      tiene_credenciales_sol: false,
      certificado_vigencia_hasta: null,
    };
    const lista = db.empresasPorCuenta.get(c.cuenta) ?? [];
    lista.push(empresa);
    db.empresasPorCuenta.set(c.cuenta, lista);
    db.seriesPorEmpresa.set(empresa.id, []);
    db.facturasPorEmpresa.set(empresa.id, []);
    return ok(empresa, 201);
  }),

  http.get(`${BASE}/v1/empresa`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa");
    const empresa = [...db.empresasPorCuenta.values()].flat().find((e) => e.id === empresaId);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    return ok(empresa);
  }),

  http.post(`${BASE}/v1/empresa/certificado`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa");
    const empresa = [...db.empresasPorCuenta.values()].flat().find((e) => e.id === empresaId);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    empresa.tiene_certificado = true;
    empresa.certificado_vigencia_hasta = "2036-01-01";
    return new HttpResponse(null, { status: 204 });
  }),

  http.put(`${BASE}/v1/empresa/credenciales-sol`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa");
    const empresa = [...db.empresasPorCuenta.values()].flat().find((e) => e.id === empresaId);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    empresa.tiene_credenciales_sol = true;
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${BASE}/v1/empresa/api-keys`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    return ok(db.apiKeysPorEmpresa.get(empresaId) ?? []);
  }),

  http.post(`${BASE}/v1/empresa/api-keys`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const apiKey = `fk_${nuevoId("mock")}`;
    const lista = db.apiKeysPorEmpresa.get(empresaId) ?? [];
    lista.unshift({ id: nuevoId("k"), prefijo: apiKey.slice(0, 10), activa: true, creada_en: new Date().toISOString() });
    db.apiKeysPorEmpresa.set(empresaId, lista);
    return ok({ api_key: apiKey }, 201);
  }),

  http.delete(`${BASE}/v1/empresa/api-keys/:id`, ({ request, params }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const key = (db.apiKeysPorEmpresa.get(empresaId) ?? []).find((k) => k.id === params.id);
    if (!key) return fail(404, "NO_ENCONTRADO", "API key no encontrada");
    key.activa = false;
    key.revocada_en = new Date().toISOString();
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${BASE}/v1/series`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    return ok(db.seriesPorEmpresa.get(empresaId) ?? []);
  }),

  http.post(`${BASE}/v1/series`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const body = (await request.json()) as { tipo: string; serie: string; correlativo_inicial?: number };
    const lista = db.seriesPorEmpresa.get(empresaId) ?? [];
    lista.push({ tipo: body.tipo, serie: body.serie, ultimo_numero: body.correlativo_inicial ?? 0, activa: true });
    db.seriesPorEmpresa.set(empresaId, lista);
    return new HttpResponse(null, { status: 201 });
  }),

  http.get(`${BASE}/v1/facturas`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const url = new URL(request.url);
    const estado = url.searchParams.get("estado");
    let lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    if (estado) lista = lista.filter((f) => f.estado_documento === estado);
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1));
    const porPagina = Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20));
    const total = lista.length;
    const datos = lista.slice((pagina - 1) * porPagina, pagina * porPagina);
    return HttpResponse.json(
      { estado: "exito", datos, mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(total) } },
    );
  }),

  http.get(`${BASE}/v1/facturas/:id`, ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    return ok(factura);
  }),

  http.post(`${BASE}/v1/facturas/:id/enviar`, ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    factura.estado_documento = "ACEPTADO";
    factura.cdr = { codigo: "0", descripcion: "Aceptado", observaciones: [] };
    factura.enlaces = { ...factura.enlaces, cdr: `/v1/facturas/${factura.id}/cdr` };
    return ok(factura);
  }),

  http.get(
    `${BASE}/v1/facturas/:id/xml`,
    () =>
      new HttpResponse(
        `<?xml version="1.0" encoding="UTF-8"?><Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"><cbc:ID>F001-1</cbc:ID><cac:AccountingSupplierParty><cbc:CustomerAssignedAccountID>20123456789</cbc:CustomerAssignedAccountID></cac:AccountingSupplierParty></Invoice>`,
        { headers: { "content-type": "application/xml" } },
      ),
  ),
  http.get(`${BASE}/v1/facturas/:id/cdr`, ({ request }) => {
    if (new URL(request.url).searchParams.get("formato") === "xml") {
      return new HttpResponse(
        `<?xml version="1.0" encoding="UTF-8"?><ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"><cbc:ID>0</cbc:ID><cac:DocumentResponse><cac:Response><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Factura numero F001-1, ha sido aceptada</cbc:Description></cac:Response></cac:DocumentResponse></ar:ApplicationResponse>`,
        { headers: { "content-type": "application/xml" } },
      );
    }
    return new HttpResponse(new Uint8Array([80, 75]), { headers: { "content-type": "application/zip" } });
  }),

  http.get(`${BASE}/openapi.json`, () =>
    HttpResponse.json({
      openapi: "3.0.1",
      info: { title: "factura (mock)", version: "v0" },
      servers: [{ url: BASE }],
      paths: {
        "/v1/facturas": {
          get: { tags: ["factura-controller"], operationId: "listar", responses: { "200": { description: "OK" } } },
        },
        "/v1/empresa": {
          get: { tags: ["empresa-controller"], operationId: "ver", responses: { "200": { description: "OK" } } },
        },
      },
      components: { securitySchemes: { ApiKey: { type: "apiKey", in: "header", name: "X-Api-Key" } } },
    }),
  ),
];
