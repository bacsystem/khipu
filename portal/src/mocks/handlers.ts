import { http, HttpResponse } from "msw";
import { hoyLima } from "@/lib/formato";
import { db, fakeJwt, PERSONALIZACION_POR_DEFECTO, type Baja, type Comprobante, type Empresa, type Establecimiento, type PersonalizacionPdf, type Usuario } from "./data";

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
function empresaDe(request: Request): Empresa | undefined {
  const empresaId = request.headers.get("x-empresa");
  return [...db.empresasPorCuenta.values()].flat().find((e) => e.id === empresaId);
}

async function guardarEstablecimiento(request: Request, codigoRuta: string | null) {
  const empresa = empresaDe(request);
  if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
  const body = (await request.json()) as { codigo: string; nombre: string; domicilio: { ubigeo: string; direccion: string; urbanizacion?: string | null } };
  if (codigoRuta && codigoRuta !== body.codigo) return fail(422, "ESTABLECIMIENTO_INVALIDO", `El código de la ruta (${codigoRuta}) y del cuerpo (${body.codigo}) no coinciden`);
  if (!/^\d{4}$/.test(body.codigo)) return fail(422, "ESTABLECIMIENTO_INVALIDO", "3030 - El código del establecimiento anexo son 4 dígitos, tal como figura en la ficha RUC");
  if (body.codigo === "0000") return fail(422, "ESTABLECIMIENTO_INVALIDO", "El 0000 es el domicilio fiscal: se configura en los datos fiscales de la empresa, no como anexo");
  const u = UBIGEOS.find((x) => x.codigo === body.domicilio?.ubigeo);
  if (!u) return fail(422, "DOMICILIO_INVALIDO", "4093 - El ubigeo debe ser un código de 6 dígitos del catálogo 13 (INEI)");
  const lista = db.establecimientosPorEmpresa.get(empresa.id) ?? [];
  const existente = lista.find((x) => x.codigo === body.codigo);
  const e: Establecimiento = {
    codigo: body.codigo, nombre: body.nombre, activo: existente?.activo ?? true,
    domicilio: { ubigeo: u.codigo, direccion: body.domicilio.direccion, urbanizacion: body.domicilio.urbanizacion ?? null, distrito: u.extra.Distrito, provincia: u.extra.Provincia, departamento: u.extra.Departamento, codigo_establecimiento: body.codigo },
  };
  if (existente) Object.assign(existente, e); else lista.push(e);
  db.establecimientosPorEmpresa.set(empresa.id, lista);
  return ok({ ...e, principal: false }, existente ? 200 : 201);
}

/** Mismo módulo 11 que el backend (pesos 5-4-3-2-7-6-5-4-3-2). */
function rucValido(ruc: string): boolean {
  if (!/^(10|15|16|17|20)\d{9}$/.test(ruc)) return false;
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const suma = pesos.reduce((acc, p, i) => acc + Number(ruc[i]) * p, 0);
  const resto = 11 - (suma % 11);
  const digito = resto === 10 ? 0 : resto === 11 ? 1 : resto;
  return Number(ruc[10]) === digito;
}

function nuevoId(prefijo: string) {
  contador += 1;
  return `${prefijo}-${contador}`;
}

/** Subconjunto del catálogo 13 (ubigeo INEI) para el formulario de domicilio fiscal. */
const UBIGEOS = [
  { codigo: "150101", descripcion: "LIMA / LIMA / LIMA", extra: { Departamento: "LIMA", Provincia: "LIMA", Distrito: "LIMA" } },
  { codigo: "150122", descripcion: "LIMA / LIMA / MIRAFLORES", extra: { Departamento: "LIMA", Provincia: "LIMA", Distrito: "MIRAFLORES" } },
  { codigo: "150131", descripcion: "LIMA / LIMA / SAN ISIDRO", extra: { Departamento: "LIMA", Provincia: "LIMA", Distrito: "SAN ISIDRO" } },
  { codigo: "070101", descripcion: "CALLAO / CALLAO / CALLAO", extra: { Departamento: "CALLAO", Provincia: "CALLAO", Distrito: "CALLAO" } },
  { codigo: "040101", descripcion: "AREQUIPA / AREQUIPA / AREQUIPA", extra: { Departamento: "AREQUIPA", Provincia: "AREQUIPA", Distrito: "AREQUIPA" } },
];

/** Subconjunto de los catálogos SUNAT, suficiente para la página /developers/catalogos y el formulario de domicilio. */
const CATALOGOS = [
  { id: "06", nombre: "Código de tipo de documento de identidad", columnas: ["Código", "Descripción"],
    entradas: [{ codigo: "1", descripcion: "DNI", extra: {} }, { codigo: "6", descripcion: "RUC", extra: {} }] },
  { id: "09", nombre: "Códigos de tipo de nota de crédito electrónica", columnas: ["Código", "Descripción"],
    entradas: [
      { codigo: "01", descripcion: "Anulación de la operación", extra: {} },
      { codigo: "07", descripcion: "Devolución por ítem", extra: {} },
      { codigo: "13", descripcion: "Corrección o modificación del monto neto pendiente de pago y/o la(s) fechas(s) de vencimiento", extra: {} },
    ] },
  { id: "10", nombre: "Códigos de tipo de nota de débito electrónica", columnas: ["Código", "Descripción"],
    entradas: [{ codigo: "01", descripcion: "Intereses por mora", extra: {} }, { codigo: "13", descripcion: "Penalidades", extra: {} }] },
  { id: "07", nombre: "Código de tipo de afectación del IGV", columnas: ["Código", "Descripción", "Codigo de tributo"],
    entradas: [
      { codigo: "10", descripcion: "Gravado - Operación Onerosa", extra: { "Codigo de tributo": "1000" } },
      { codigo: "17", descripcion: "Gravado - IVAP", extra: { "Codigo de tributo": "1016 o 9996" } },
      { codigo: "20", descripcion: "Exonerado - Operación Onerosa", extra: { "Codigo de tributo": "9997" } },
      { codigo: "30", descripcion: "Inafecto - Operación Onerosa", extra: { "Codigo de tributo": "9998" } },
      { codigo: "40", descripcion: "Exportación de Bienes o Servicios", extra: { "Codigo de tributo": "9995" } },
    ] },
  { id: "13", nombre: "Código de ubicación geográfica (UBIGEO, INEI)", columnas: ["Código", "Descripción", "Departamento", "Provincia", "Distrito"], entradas: UBIGEOS },
  { id: "25", nombre: "Código de producto SUNAT (UNSPSC; listados 25.1–25.3)", columnas: ["Código", "Descripción", "Listado", "Partidas arancelarias"],
    entradas: [{ codigo: "15101505", descripcion: "Combustible diésel", extra: { Listado: "25.1 Padrón obligado: Combustible" } }] },
];

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
    if (!rucValido(body.ruc)) return fail(422, "RUC_INVALIDO", `Empresa: el dígito verificador del RUC ${body.ruc} no es válido; revise el número`);
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

  // Personalización del PDF: se guarda en la empresa; la vista previa devuelve un PDF mínimo con los parámetros en un comentario.
  http.get(`${BASE}/v1/empresa/personalizacion-pdf`, ({ request }) => {
    const empresa = empresaDe(request);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    return ok(empresa.personalizacion_pdf ?? PERSONALIZACION_POR_DEFECTO);
  }),
  http.put(`${BASE}/v1/empresa/personalizacion-pdf`, async ({ request }) => {
    const empresa = empresaDe(request);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    const body = (await request.json()) as Partial<PersonalizacionPdf>;
    if (body.color_primario && !/^#[0-9a-fA-F]{6}$/.test(body.color_primario)) {
      return HttpResponse.json({ estado: "error", datos: null, mensaje: "Validación fallida", codigo: "VALIDACION", errores: { colorPrimario: ["color_primario: hexadecimal de 6 dígitos"] } }, { status: 422 });
    }
    const actual = empresa.personalizacion_pdf ?? PERSONALIZACION_POR_DEFECTO;
    empresa.personalizacion_pdf = { ...actual, plantilla: body.plantilla ?? "clasico", color_primario: (body.color_primario ?? "#1E1E24").toUpperCase(), pie_de_pagina: body.pie_de_pagina || null, observaciones_por_defecto: body.observaciones_por_defecto || null };
    return ok(empresa.personalizacion_pdf);
  }),
  http.get(`${BASE}/v1/empresa/personalizacion-pdf/vista-previa`, ({ request }) => {
    const q = new URL(request.url).searchParams;
    return new HttpResponse(`%PDF-1.4\n%plantilla=${q.get("plantilla") ?? ""} color=${q.get("color_primario") ?? ""}\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF`, {
      headers: { "content-type": "application/pdf", "cache-control": "no-store" },
    });
  }),
  http.put(`${BASE}/v1/empresa/logo`, async ({ request }) => {
    const empresa = empresaDe(request);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    const archivo = (await request.formData()).get("archivo");
    if (!(archivo instanceof File) || !["image/png", "image/jpeg"].includes(archivo.type)) return fail(422, "LOGO_INVALIDO", "El logo debe ser PNG o JPEG");
    empresa.personalizacion_pdf = { ...(empresa.personalizacion_pdf ?? PERSONALIZACION_POR_DEFECTO), tiene_logo: true };
    return ok(empresa.personalizacion_pdf);
  }),
  http.get(`${BASE}/v1/empresa/logo`, ({ request }) => {
    const empresa = empresaDe(request);
    if (!empresa?.personalizacion_pdf?.tiene_logo) return fail(404, "NO_ENCONTRADO", "La empresa no tiene logo");
    return new HttpResponse(Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), { headers: { "content-type": "image/png" } });
  }),
  http.delete(`${BASE}/v1/empresa/logo`, ({ request }) => {
    const empresa = empresaDe(request);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    empresa.personalizacion_pdf = { ...(empresa.personalizacion_pdf ?? PERSONALIZACION_POR_DEFECTO), tiene_logo: false };
    return ok(empresa.personalizacion_pdf);
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

  http.put(`${BASE}/v1/empresa/datos-fiscales`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa");
    const empresa = [...db.empresasPorCuenta.values()].flat().find((e) => e.id === empresaId);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    const body = (await request.json()) as { domicilio?: { ubigeo: string; direccion: string; urbanizacion?: string; codigo_establecimiento?: string } | null; cuenta_detracciones?: string | null; nombre_comercial?: string | null };
    if (body.domicilio) {
      const u = UBIGEOS.find((x) => x.codigo === body.domicilio?.ubigeo);
      if (!u) return fail(422, "DOMICILIO_INVALIDO", "4093 - El ubigeo debe ser un código de 6 dígitos del catálogo 13 (INEI)");
      empresa.domicilio = { ubigeo: u.codigo, direccion: body.domicilio.direccion, urbanizacion: body.domicilio.urbanizacion ?? null,
        distrito: u.extra.Distrito, provincia: u.extra.Provincia, departamento: u.extra.Departamento, codigo_establecimiento: body.domicilio.codigo_establecimiento || "0000" };
    } else empresa.domicilio = null;
    empresa.tiene_domicilio = Boolean(empresa.domicilio);
    empresa.cuenta_detracciones = body.cuenta_detracciones ?? null;
    empresa.nombre_comercial = body.nombre_comercial ?? null;
    return ok(empresa);
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
    const body = (await request.json()) as { tipo: string; serie: string; correlativo_inicial?: number; establecimiento?: string | null };
    const establecimiento = body.establecimiento || "0000";
    if (establecimiento !== "0000") {
      const e = (db.establecimientosPorEmpresa.get(empresaId) ?? []).find((x) => x.codigo === establecimiento);
      if (!e) return fail(422, "ESTABLECIMIENTO_INVALIDO", `El establecimiento ${establecimiento} no existe en la empresa: regístrelo antes de asignarle una serie`);
      if (!e.activo) return fail(422, "ESTABLECIMIENTO_INVALIDO", `El establecimiento ${establecimiento} (${e.nombre}) está dado de baja`);
    }
    const lista = db.seriesPorEmpresa.get(empresaId) ?? [];
    lista.push({ tipo: body.tipo, serie: body.serie, ultimo_numero: body.correlativo_inicial ?? 0, activa: true, establecimiento });
    db.seriesPorEmpresa.set(empresaId, lista);
    return new HttpResponse(null, { status: 201 });
  }),

  // Establecimientos anexos (#80): el 0000 es el domicilio fiscal de la empresa y se lista como principal.
  http.get(`${BASE}/v1/empresa/establecimientos`, ({ request }) => {
    const empresa = empresaDe(request);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    const anexos = (db.establecimientosPorEmpresa.get(empresa.id) ?? []).map((e) => ({ ...e, principal: false }));
    const principal = empresa.domicilio ? [{ codigo: "0000", nombre: "Domicilio fiscal", domicilio: empresa.domicilio, activo: true, principal: true }] : [];
    return ok([...principal, ...anexos]);
  }),
  http.post(`${BASE}/v1/empresa/establecimientos`, async ({ request }) => guardarEstablecimiento(request, null)),
  http.put(`${BASE}/v1/empresa/establecimientos/:codigo`, async ({ request, params }) => guardarEstablecimiento(request, String(params.codigo))),
  http.delete(`${BASE}/v1/empresa/establecimientos/:codigo`, ({ request, params }) => {
    const empresa = empresaDe(request);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    const codigo = String(params.codigo);
    if (codigo === "0000") return fail(422, "ESTABLECIMIENTO_INVALIDO", "El 0000 es el domicilio fiscal: no se da de baja, se edita en datos fiscales");
    const e = (db.establecimientosPorEmpresa.get(empresa.id) ?? []).find((x) => x.codigo === codigo);
    if (!e) return fail(404, "NO_ENCONTRADO", `Establecimiento ${codigo} no encontrado`);
    const enUso = (db.seriesPorEmpresa.get(empresa.id) ?? []).filter((s) => s.activa && s.establecimiento === codigo).map((s) => s.serie);
    if (enUso.length) return fail(409, "ESTABLECIMIENTO_EN_USO", `El establecimiento ${codigo} tiene series activas (${enUso.join(", ")}): reasígnelas antes de darlo de baja`);
    e.activo = false;
    return new HttpResponse(null, { status: 204 });
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
    const lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    const factura = lista.find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    // Como el backend: una factura lista las notas emitidas sobre ella.
    const notas = factura.tipo === "01"
      ? lista.filter((n) => n.nota?.documento_afectado === `${factura.serie}-${factura.numero}`).map((n) => ({
          id: n.id, tipo: n.tipo, comprobante: `${n.serie}-${n.numero}`, fecha_emision: n.fecha_emision, motivo: n.nota!.motivo,
          motivo_descripcion: n.nota!.motivo_descripcion, estado_documento: n.estado_documento, total: n.totales.total,
        }))
      : [];
    return ok({ ...factura, notas: notas.length ? notas : null });
  }),

  // Notas de crédito/débito: la factura debe existir y estar aceptada; la nota copia cliente y moneda y se acepta al instante.
  http.post(`${BASE}/v1/notas`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const body = (await request.json()) as { tipo: "07" | "08"; serie: string; fecha_emision: string; documento_afectado: { serie: string; numero: number }; motivo: string; descripcion: string; items?: Array<{ descripcion: string; unidad: string; cantidad: number; precio_unitario: number; tipo_afectacion_igv: string }> };
    const lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    const factura = lista.find((f) => f.tipo === "01" && f.serie === body.documento_afectado.serie && f.numero === body.documento_afectado.numero);
    if (!factura) return fail(422, "NOTA_INVALIDA", `2119 - La factura ${body.documento_afectado.serie}-${body.documento_afectado.numero} no existe en esta empresa`);
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(422, "NOTA_INVALIDA", `2119 - La factura no está aceptada por SUNAT (estado ${factura.estado_documento})`);
    const serie = (db.seriesPorEmpresa.get(empresaId) ?? []).find((s) => s.tipo === body.tipo && s.serie === body.serie);
    if (!serie) return fail(422, "SERIE_NO_CONFIGURADA", `Serie no configurada: ${body.serie}`);
    serie.ultimo_numero += 1;
    const motivos: Record<string, string> = { "01": body.tipo === "07" ? "Anulación de la operación" : "Intereses por mora", "07": "Devolución por ítem", "13": body.tipo === "07" ? "Corrección o modificación del monto neto pendiente de pago" : "Penalidades" };
    const items = body.motivo === "13" && body.tipo === "07"
      ? [{ codigo: null, descripcion: body.descripcion, unidad: "ZZ", cantidad: 1, precio_unitario: 0, tipo_afectacion_igv: "10" }]
      : body.items?.length ? body.items.map((i) => ({ codigo: null, ...i })) : factura.items;
    const total = items.reduce((acc, i) => acc + i.cantidad * i.precio_unitario, 0);
    const id = nuevoId("n");
    const nota: Comprobante = {
      id, tipo: body.tipo, serie: body.serie, numero: serie.ultimo_numero, fecha_emision: body.fecha_emision, moneda: factura.moneda,
      tipo_operacion: factura.tipo_operacion, receptor: factura.receptor, items, estado_documento: "ACEPTADO", hash: "hashnota==",
      nombre_archivo: `20123456786-${body.tipo}-${body.serie}-${String(serie.ultimo_numero).padStart(8, "0")}`, intentos: 1, ultimo_error: null,
      cdr: { codigo: "0", descripcion: `La Nota de ${body.tipo === "07" ? "Credito" : "Debito"} numero ${body.serie}-${serie.ultimo_numero}, ha sido aceptada`, observaciones: [] },
      totales: { gravado: Number((total / 1.18).toFixed(2)), exonerado: 0, inafecto: 0, igv: Number((total - total / 1.18).toFixed(2)), total: Number(total.toFixed(2)) },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      nota: { tipo_afectado: "01", documento_afectado: `${factura.serie}-${factura.numero}`, motivo: body.motivo, motivo_descripcion: motivos[body.motivo] ?? "Otros", descripcion: body.descripcion },
      enlaces: { xml: `/v1/facturas/${id}/xml`, pdf: `/v1/facturas/${id}/pdf`, cdr: `/v1/facturas/${id}/cdr` },
    };
    lista.unshift(nota);
    return ok(nota, 201);
  }),

  // Comunicación de baja: se acepta al instante (en el backend real pasa por ticket y el worker la reconsulta) y anula el comprobante.
  http.post(`${BASE}/v1/facturas/:id/baja`, async ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    const body = (await request.json()) as { motivo: string };
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(422, "BAJA_INVALIDA", `El comprobante no está aceptado por SUNAT (estado ${factura.estado_documento})`);
    if (factura.baja && factura.baja.estado !== "RECHAZADA") return fail(422, "BAJA_INVALIDA", `Ya hay una comunicación de baja en curso para ${factura.serie}-${factura.numero}`);
    const hoy = hoyLima().replace(/-/g, "");
    const baja: Baja = {
      id: nuevoId("b"), identificador: `RA-${hoy}-1`, comprobante: `${factura.serie}-${factura.numero}`, tipo_comprobante: factura.tipo, fecha_generacion: hoyLima(),
      motivo: body.motivo, estado: "ACEPTADA", ticket: "1758200000123", cdr: { codigo: "0", descripcion: `La Comunicacion de baja RA-${hoy}-1, ha sido aceptada`, observaciones: [] },
      intentos: 1, ultimo_error: null,
    };
    factura.baja = baja;
    factura.estado_documento = "ANULADO";
    db.bajas.set(baja.id, baja);
    return ok(baja, 201);
  }),
  http.get(`${BASE}/v1/bajas/:id`, ({ params }) => {
    const baja = db.bajas.get(String(params.id));
    return baja ? ok(baja) : fail(404, "NO_ENCONTRADO", "Comunicación de baja no encontrada");
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

  // Representación impresa: un PDF mínimo válido (lo que importa en el portal es el enlace y el tipo de contenido).
  http.get(`${BASE}/v1/facturas/:id/pdf`, () =>
    new HttpResponse("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF", {
      headers: { "content-type": "application/pdf", "content-disposition": 'inline; filename="20123456786-01-F001-00000001.pdf"' },
    }),
  ),
  // Envío por correo al adquirente: solo comprobantes aceptados; el backend valida el email (422 VALIDACION).
  http.post(`${BASE}/v1/facturas/:id/correo`, async ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    const body = (await request.json()) as { email: string; mensaje?: string | null };
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email ?? "")) {
      return HttpResponse.json({ estado: "error", datos: null, mensaje: "Validación fallida", codigo: "VALIDACION", errores: { email: ["debe ser una dirección de correo electrónico con formato correcto"] } }, { status: 422 });
    }
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(409, "NO_ACEPTADO", `Solo se envían comprobantes aceptados por SUNAT; ${factura.serie}-${factura.numero} está ${factura.estado_documento}`);
    db.correos.push({ comprobante: factura.id, email: body.email, mensaje: body.mensaje ?? null });
    return ok(null, 202);
  }),

  http.get(
    `${BASE}/v1/facturas/:id/xml`,
    () =>
      new HttpResponse(
        `<?xml version="1.0" encoding="UTF-8"?><Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"><cbc:ID>F001-1</cbc:ID><cac:AccountingSupplierParty><cbc:CustomerAssignedAccountID>20123456786</cbc:CustomerAssignedAccountID></cac:AccountingSupplierParty></Invoice>`,
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

  // Catálogos SUNAT públicos: un subconjunto suficiente para la página /developers/catalogos.
  http.get(`${BASE}/v1/catalogos`, ({ request }) => {
    const completo = new URL(request.url).searchParams.get("completo") === "true";
    return ok(completo ? CATALOGOS : CATALOGOS.map((c) => ({ id: c.id, nombre: c.nombre, entradas: c.entradas.length })));
  }),
  http.get(`${BASE}/v1/catalogos/:id`, ({ params }) => {
    const c = CATALOGOS.find((x) => x.id === params.id);
    return c ? ok(c) : fail(404, "NO_ENCONTRADO", "No existe el catálogo SUNAT " + params.id);
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
