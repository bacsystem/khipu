import { http, HttpResponse } from "msw";
import { diasEntre, hoyLima } from "@/lib/formato";
import { telefonoSchema } from "@/lib/validacion";
import { calcularTotales, esGratuita, redondear } from "@/lib/comprobantes/totales";
import { db, fakeJwt, PERSONALIZACION_POR_DEFECTO, resetDb, type Baja, type Comprobante, type Empresa, type Establecimiento, type PersonalizacionPdf, type Usuario } from "./data";

// Debe coincidir con la URL que usa el server del portal (client.ts); si no, MSW no intercepta y las peticiones van al backend real.
const BASE = process.env.API_BASE_URL ?? "http://localhost:8001";

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
      { codigo: "02", descripcion: "Anulación por error en el RUC", extra: {} },
      { codigo: "03", descripcion: "Corrección por error en la descripción", extra: {} },
      { codigo: "04", descripcion: "Descuento global", extra: {} },
      { codigo: "05", descripcion: "Descuento por ítem", extra: {} },
      { codigo: "06", descripcion: "Devolución total", extra: {} },
      { codigo: "07", descripcion: "Devolución por ítem", extra: {} },
      { codigo: "08", descripcion: "Bonificación", extra: {} },
      { codigo: "09", descripcion: "Disminución en el valor", extra: {} },
      { codigo: "10", descripcion: "Otros Conceptos", extra: {} },
      // 11 y 12 están para probar que el formulario los oculta cuando la factura no es de exportación / IVAP.
      { codigo: "11", descripcion: "Ajustes de operaciones de exportación", extra: {} },
      { codigo: "12", descripcion: "Ajustes afectos al IVAP", extra: {} },
      { codigo: "13", descripcion: "Corrección o modificación del monto neto pendiente de pago y/o la(s) fechas(s) de vencimiento", extra: {} },
    ] },
  { id: "10", nombre: "Códigos de tipo de nota de débito electrónica", columnas: ["Código", "Descripción"],
    entradas: [
      { codigo: "01", descripcion: "Intereses por mora", extra: {} },
      { codigo: "02", descripcion: "Aumento en el valor", extra: {} },
      { codigo: "03", descripcion: "Penalidades/ otros conceptos", extra: {} },
      { codigo: "11", descripcion: "Ajustes de operaciones de exportación", extra: {} },
      { codigo: "12", descripcion: "Ajustes afectos al IVAP", extra: {} },
      { codigo: "13", descripcion: "Penalidades", extra: {} },
    ] },
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
  // Solo bajo API_MOCKING: `globalSetup` de Playwright lo llama al empezar cada corrida. Sin esto la suite no era
  // idempotente contra un dev server reutilizado (`reuseExistingServer` en local): cada corrida gastaba el tope 3286
  // de f-aceptada y dejaba f-obs anulada para siempre —corrida 2: 6 rojos; corrida 3: 10—, y un rojo que «a veces
  // pasa» enseña a ignorar el rojo.
  http.post(`${BASE}/v1/__test/reset`, () => {
    resetDb();
    return ok({ reiniciado: true });
  }),

  http.post(`${BASE}/v1/auth/registro`, async ({ request }) => {
    const body = (await request.json()) as { nombre: string; email: string; password: string; telefono?: string };
    if (!telefonoSchema.safeParse(body.telefono ?? "").success) return fail(422, "TELEFONO_INVALIDO", "El celular debe tener 9 dígitos y empezar con 9 (Perú)");
    // A paridad con `Cuenta`/`Usuario`: nombre obligatorio, correo con formato y en minúsculas (`normalizarEmail`) y
    // contraseña de 8 con letra y dígito. El mock aceptaba todo eso y creaba dos cuentas con el mismo correo en distinta caja.
    if (!body.nombre?.trim()) return fail(422, "NOMBRE_REQUERIDO", "El nombre de la cuenta es obligatorio");
    const email = (body.email ?? "").trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return fail(422, "EMAIL_INVALIDO", `Correo inválido: ${body.email}`);
    if (!body.password || body.password.length < 8 || !/[A-Za-z]/.test(body.password) || !/\d/.test(body.password))
      return fail(422, "PASSWORD_DEBIL", "La contraseña debe tener al menos 8 caracteres, una letra y un dígito");
    if (db.usuariosPorEmail.has(email)) return fail(409, "DUPLICADO", "Ya existe una cuenta con ese correo");
    const usuario: Usuario = { id: nuevoId("u"), cuenta_id: nuevoId("c"), email, rol: "ADMIN" };
    db.usuariosPorEmail.set(email, { usuario, password: body.password });
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

  // Recuperación de contraseña: 202 siempre (el backend no revela si el correo existe). No tenía mock: el e2e de
  // «Cambiar contraseña» llegaba al backend real en :8001 y, si estaba levantado, mandaba un correo de verdad.
  http.post(`${BASE}/v1/auth/recuperar`, () => ok(null, 202)),

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
    // 4338: la razón social del emisor va cruda al XML, sin tabuladores ni saltos de línea, hasta 1500.
    const razonSocial = (body.razon_social ?? "").trim();
    if (!razonSocial) return fail(422, "RAZON_SOCIAL_REQUERIDA", "Razón social requerida");
    if (razonSocial.length > 1500 || /[\x00-\x1F\x7F]/.test(razonSocial))
      return fail(422, "RAZON_SOCIAL_INVALIDA", "4338 - La razón social admite hasta 1500 caracteres, sin saltos de línea ni tabuladores");
    // El RUC es único en TODA la plataforma, no por cuenta (`GestionarEmpresasService`): el mock dejaba a dos cuentas
    // registrar el mismo y el e2e daba por bueno un camino que en producción es un 409.
    if ([...db.empresasPorCuenta.values()].flat().some((e) => e.ruc === body.ruc))
      return fail(409, "DUPLICADO", `Ya existe una empresa con RUC ${body.ruc}`);
    const empresa: Empresa = {
      id: nuevoId("e"),
      ruc: body.ruc,
      razon_social: razonSocial,
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
    // 1001: `F###` para facturas y notas sobre factura, `B###` para boletas. El mock aceptaba «1234» y el onboarding
    // terminaba en verde contra un backend que responde 422.
    const inicial = body.tipo === "03" ? "B" : "F";
    if (!new RegExp(`^${inicial}[A-Z0-9]{3}$`).test(body.serie ?? ""))
      return fail(422, "SERIE_INVALIDA", `1001 - La serie de un comprobante tipo ${body.tipo} es ${inicial}### (p. ej. ${inicial}001): ${body.serie}`);
    const lista = db.seriesPorEmpresa.get(empresaId) ?? [];
    if (lista.some((s) => s.tipo === body.tipo && s.serie === body.serie))
      return fail(409, "DUPLICADO", `Ya existe la serie ${body.serie} para el tipo ${body.tipo}`);
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

  // Emisión manual desde el portal (#17). Los totales salen del mismo helper que previsualiza el formulario, así el
  // mock no puede "confirmar" un cálculo distinto del que ve el usuario.
  http.post(`${BASE}/v1/facturas`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const body = (await request.json()) as {
      serie: string;
      fecha_emision: string;
      moneda: string;
      cliente: { tipo_doc: string; num_doc: string; razon_social: string; direccion?: string };
      items: Array<{ descripcion: string; unidad: string; cantidad: number; precio_unitario: number; tipo_afectacion_igv: string }>;
    };

    const series = db.seriesPorEmpresa.get(empresaId) ?? [];
    const serie = series.find((s) => s.serie === body.serie && s.tipo === "01" && s.activa);
    if (!serie) return fail(422, "SERIE_NO_CONFIGURADA", `La serie ${body.serie} no está registrada como serie de factura activa`);
    if (!/^\d{11}$/.test(body.cliente?.num_doc ?? "")) return fail(422, "RECEPTOR_INVALIDO", "2017 - El RUC del adquirente debe tener 11 dígitos");
    if (!body.items?.length) return fail(422, "ITEMS_REQUERIDOS", "Un comprobante necesita al menos un ítem");

    serie.ultimo_numero += 1;
    // La tasa sale de la empresa, igual que en el diálogo: si el fixture entra al padrón de tasa especial, mock y
    // formulario siguen de acuerdo en vez de romper el e2e con un descuadre que parecería un bug del helper.
    const tasaIgv = empresaDe(request)?.padron_tasa_especial_igv ? 10.5 : 18;
    const t = calcularTotales(
      body.items.map((i) => ({ cantidad: i.cantidad, precioUnitario: i.precio_unitario, tipoAfectacionIgv: i.tipo_afectacion_igv })),
      tasaIgv,
    );
    const id = nuevoId("f");
    const comprobante: Comprobante = {
      id,
      tipo: "01",
      serie: body.serie,
      numero: serie.ultimo_numero,
      fecha_emision: body.fecha_emision,
      moneda: body.moneda,
      tipo_operacion: "0101",
      receptor: { ...body.cliente, direccion: body.cliente.direccion ?? null },
      items: body.items.map((i) => ({ codigo: null, ...i })),
      estado_documento: "ACEPTADO",
      hash: `hash-${id}`,
      nombre_archivo: `20123456786-01-${body.serie}-${String(serie.ultimo_numero).padStart(8, "0")}`,
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: `La Factura numero ${body.serie}-${serie.ultimo_numero}, ha sido aceptada`, observaciones: [] },
      totales: { gravado: t.gravado, exonerado: t.exonerado, inafecto: t.inafecto, igv: t.igv, total: t.total },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: `/v1/facturas/${id}/xml`, pdf: `/v1/facturas/${id}/pdf`, cdr: `/v1/facturas/${id}/cdr` },
    };
    db.facturasPorEmpresa.set(empresaId, [comprobante, ...(db.facturasPorEmpresa.get(empresaId) ?? [])]);
    return ok(comprobante, 201);
  }),

  http.get(`${BASE}/v1/facturas`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const url = new URL(request.url);
    const estado = url.searchParams.get("estado");
    const desde = url.searchParams.get("desde");
    const hasta = url.searchParams.get("hasta");
    const serie = url.searchParams.get("serie")?.toUpperCase();
    if (desde && hasta && desde > hasta) return fail(400, "RANGO_INVALIDO", `desde (${desde}) no puede ser posterior a hasta (${hasta})`);
    let lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    if (estado) lista = lista.filter((f) => f.estado_documento === estado);
    if (desde) lista = lista.filter((f) => f.fecha_emision >= desde);
    if (hasta) lista = lista.filter((f) => f.fecha_emision <= hasta);
    if (serie) lista = lista.filter((f) => f.serie === serie);
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1));
    const porPagina = Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20));
    const total = lista.length;
    // Como el backend: el historial de intentos solo viaja al consultar por id, nunca en el listado.
    const datos = lista.slice((pagina - 1) * porPagina, pagina * porPagina).map((f) => {
      const copia: Partial<typeof f> = { ...f };
      delete copia.eventos;
      return copia;
    });
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
    // Como el backend (#4): el historial viaja solo al consultar por id; un comprobante sin eventos devuelve [].
    return ok({ ...factura, notas: notas.length ? notas : null, eventos: factura.eventos ?? [] });
  }),

  // Notas de crédito/débito: la factura debe existir y estar aceptada; la nota copia cliente y moneda y se acepta al instante.
  http.post(`${BASE}/v1/notas`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    // Con guarda: un cuerpo vacío o truncado (un POST abortado en el teardown de un test, reenviado por el BFF sin
    // cuerpo) hacía que `request.json()` lanzara y MSW respondiera 500 con el stack —el «SyntaxError: Unexpected
    // end of JSON input» de los logs—. El backend real responde 400 JSON_INVALIDO; el mock ahora también.
    type Ajuste = { porcentaje?: number; monto?: number; afecta_base_igv?: boolean };
    type ItemNota = { descripcion: string; unidad: string; cantidad: number; precio_unitario: number; tipo_afectacion_igv: string; descuento?: Ajuste; cargos?: Ajuste[]; isc?: { sistema: string; tasa?: number; monto_unitario?: number; base_pvp?: number } };
    type CuerpoNota = { tipo: "07" | "08"; serie: string; fecha_emision: string; documento_afectado: { serie: string; numero: number }; motivo: string; descripcion: string; items?: ItemNota[] };
    let body: CuerpoNota;
    try {
      body = (await request.json()) as CuerpoNota;
    } catch {
      return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
    }
    const lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    const factura = lista.find((f) => f.tipo === "01" && f.serie === body.documento_afectado.serie && f.numero === body.documento_afectado.numero);
    if (!factura) return fail(422, "NOTA_INVALIDA", `2119 - La factura ${body.documento_afectado.serie}-${body.documento_afectado.numero} no existe en esta empresa`);
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(422, "NOTA_INVALIDA", `2119 - La factura no está aceptada por SUNAT (estado ${factura.estado_documento})`);
    const serie = (db.seriesPorEmpresa.get(empresaId) ?? []).find((s) => s.tipo === body.tipo && s.serie === body.serie);
    if (!serie) return fail(422, "SERIE_NO_CONFIGURADA", `Serie no configurada: ${body.serie}`);

    // Lo que el backend real valida ANTES de numerar, con los códigos de la tabla oficial. Sin esto los e2e afirmaban
    // sobre lo que el mock inventaba: una nota con fecha 2020, sin forma de pago en la NC 13 o por 10× la factura
    // pasaba igual, y ocho mutaciones del formulario sobrevivían a la suite.
    if (!body.serie.startsWith("F")) return fail(422, "SERIE_INVALIDA", `1001 - La serie de una nota sobre ${factura.serie}-${factura.numero} debe ser F###: ${body.serie}`);
    if (body.fecha_emision < factura.fecha_emision) return fail(422, "NOTA_INVALIDA", `2885 - La fecha de la nota no puede ser anterior a la de la factura que modifica (${factura.fecha_emision})`);
    if (body.fecha_emision > hoyLima()) return fail(422, "FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
    const catalogo = CATALOGOS.find((c) => c.id === (body.tipo === "07" ? "09" : "10"))!;
    const entradaMotivo = catalogo.entradas.find((e) => e.codigo === body.motivo);
    if (!entradaMotivo) return fail(422, "NOTA_INVALIDA", `2172 - El motivo ${body.motivo} no existe en el catálogo ${catalogo.id}`);
    const desc = body.descripcion ?? "";
    // Incluye el tabulador (\x09): el backend usa `Character::isISOControl` y SUNAT 2135 lo prohíbe explícitamente.
    // La primera versión lo dejaba pasar y una celda pegada de Excel salía en verde acá y en 422 en producción.
    if (desc.trim() === "" || desc.length > 500 || /[\x00-\x1F\x7F]/.test(desc)) return fail(422, "NOTA_INVALIDA", "2135 - El sustento de la nota tiene de 1 a 500 caracteres, sin saltos de línea ni tabuladores");
    // Contrato de los ítems, a paridad con `Isc`, `DescuentoDto.aDominio` y `CargoDto.aDominio`: sin esto el mock daba
    // 201 a un ISC {sistema: "02", tasa} que el dominio rechaza, y una NC parcial sobre una línea con ISC salía verde en
    // e2e y 422 en producción.
    for (const i of body.items ?? []) {
      for (const a of [i.descuento, ...(i.cargos ?? [])]) {
        if (a && (a.porcentaje == null) === (a.monto == null)) return fail(422, "CARGO_INVALIDO", "Indique porcentaje o monto, no ambos");
      }
      const isc = i.isc;
      if (!isc) continue;
      if (!/^0[123]$/.test(isc.sistema)) return fail(422, "ISC_INVALIDO", "2041 - El sistema de cálculo del ISC debe ser 01, 02 o 03 (catálogo 08)");
      if (isc.sistema === "02" && (!(isc.monto_unitario! > 0) || isc.tasa != null)) return fail(422, "ISC_INVALIDO", "El sistema 02 (monto fijo) exige monto_unitario positivo y no lleva tasa");
      if (isc.sistema !== "02" && (!(isc.tasa! > 0) || isc.monto_unitario != null)) return fail(422, "ISC_INVALIDO", `3104 - El sistema ${isc.sistema} exige una tasa de ISC positiva y no lleva monto_unitario`);
      if (isc.sistema === "03" && !(isc.base_pvp! > 0)) return fail(422, "ISC_INVALIDO", "El sistema 03 (precio de venta al público) exige base_pvp");
    }
    const nc13 = body.tipo === "07" && body.motivo === "13";
    const formaPago = (body as { forma_pago?: { tipo: string; monto_pendiente: number; cuotas: Array<{ monto: number; vencimiento: string }> } }).forma_pago;
    if (nc13 && (!formaPago || formaPago.tipo !== "credito" || !formaPago.cuotas?.length)) return fail(422, "NOTA_INVALIDA", "3257 - Una nota de crédito con motivo 13 debe indicar la forma de pago al crédito con las cuotas corregidas");
    if (!nc13 && formaPago) return fail(422, "NOTA_INVALIDA", "forma_pago solo se admite en una nota de crédito con motivo 13 (corrección de cuotas)");
    // Ítems contra la operación y el dominio, como `Comprobante.exigirAfectacionSegunOperacion` (2642/3107, salvo
    // NC 13) e `Item` (2025: cantidad positiva, hasta 10 decimales). El recertificador midió que el mock daba 201 a
    // una ND 13 con 30 sobre una exportación y a una cantidad con 11 decimales, ambas 422 en el backend.
    const decimales = (n: number) => (String(n).split(".")[1] ?? "").length;
    const exportacion = /^020[0-8]$/.test(factura.tipo_operacion ?? "");
    for (const i of nc13 ? [] : (body.items ?? [])) {
      if (exportacion && i.tipo_afectacion_igv !== "40") return fail(422, "AFECTACION_INVALIDA", `2642 - En una exportación (${factura.tipo_operacion}) todos los ítems llevan tipo_afectacion_igv 40`);
      if (!exportacion && i.tipo_afectacion_igv === "40") return fail(422, "AFECTACION_INVALIDA", `3107 - La afectación 40 (exportación) exige un tipo de operación 0200–0208; recibido ${factura.tipo_operacion}`);
      if (!(i.cantidad > 0) || decimales(i.cantidad) > 10) return fail(422, "ITEM_INVALIDO", "2025 - La cantidad debe ser positiva, con hasta 12 enteros y 10 decimales");
      // `Item.exigirFormatoNumerico`: 12 enteros y 10 decimales también en el precio. El importe de la ND llegaba con 13 enteros.
      if (!(i.precio_unitario >= 0) || decimales(i.precio_unitario) > 10 || Math.trunc(i.precio_unitario) >= 1e12) return fail(422, "ITEM_INVALIDO", "2025 - El precio unitario admite hasta 12 enteros y 10 decimales");
      // `Cargo.montoSobre` (2955): un cargo en porcentaje que redondea a 0.00 sobre la base de la línea. El descuento no
      // (`Descuento.montoSobre` solo rechaza que alcance la base). Base aproximada: /1.18 en gravadas, /1.04 en IVAP.
      const base = (i.cantidad * i.precio_unitario) / (i.tipo_afectacion_igv === "10" ? 1.18 : i.tipo_afectacion_igv === "17" ? 1.04 : 1);
      for (const a of i.cargos ?? []) {
        if (a?.porcentaje != null && Math.round((base * a.porcentaje) / 100 * 100) === 0) return fail(422, "CARGO_INVALIDO", `2955 - El cargo en porcentaje resulta en 0.00 sobre la base ${base.toFixed(2)}`);
      }
      // `Item`: descripción obligatoria y hasta 500 (2026/2027). El «Concepto» de la ND llegaba con 501 y el mock daba 201.
      if (!i.descripcion?.trim()) return fail(422, "ITEM_INVALIDO", "2026 - Cada ítem necesita una descripción");
      if (i.descripcion.length > 500) return fail(422, "ITEM_INVALIDO", "2027 - La descripción del ítem admite hasta 500 caracteres");
    }
    if (nc13) {
      // A paridad con `FormaPago.validarComoCorreccionDe`: escala ≤ 2 en pendiente (3250) y cuotas (3253), y la
      // suma de cuotas igual al pendiente (3319). El recertificador midió que el mock aceptaba las tres cosas que el
      // backend rechaza, así que un e2e verde no garantizaba nada.
      if (factura.forma_pago.tipo !== "credito") return fail(422, "NOTA_INVALIDA", `3260 - El motivo 13 solo aplica a facturas al crédito y ${factura.serie}-${factura.numero} es al contado`);
      if (!(formaPago!.monto_pendiente > 0) || decimales(formaPago!.monto_pendiente) > 2) return fail(422, "FORMA_PAGO_INVALIDA", "3250 - El monto neto pendiente de pago debe ser positivo con hasta 2 decimales");
      if (formaPago!.cuotas.some((q) => !(q.monto > 0) || decimales(q.monto) > 2)) return fail(422, "FORMA_PAGO_INVALIDA", "3253 - El monto de cada cuota debe ser positivo con hasta 2 decimales");
      if (formaPago!.cuotas.some((q) => !(q.vencimiento > factura.fecha_emision))) return fail(422, "FORMA_PAGO_INVALIDA", `3321 - La fecha de la cuota debe ser posterior a la emisión de la factura (${factura.fecha_emision})`);
      if (formaPago!.monto_pendiente > factura.totales.total) return fail(422, "FORMA_PAGO_INVALIDA", `3320 - El monto neto pendiente (${formaPago!.monto_pendiente}) supera el total de la factura (${factura.totales.total})`);
      const suma = Number(formaPago!.cuotas.reduce((acc, q) => acc + q.monto, 0).toFixed(2));
      if (suma !== Number(formaPago!.monto_pendiente.toFixed(2))) return fail(422, "FORMA_PAGO_INVALIDA", `3319 - La suma de las cuotas (${suma}) debe ser igual al monto neto pendiente (${formaPago!.monto_pendiente})`);
    }

    const motivos: Record<string, string> = Object.fromEntries(catalogo.entradas.map((e) => [e.codigo, e.descripcion]));
    const items = nc13
      ? [{ codigo: null, descripcion: body.descripcion, unidad: "ZZ", cantidad: 1, precio_unitario: 0, tipo_afectacion_igv: "10" }]
      : body.items?.length ? body.items.map((i) => ({ codigo: null, ...i })) : factura.items;
    // 3230 en NC (NotaCredito2_0 fila 223) y ND (NotaDebito2_0 fila 206): una línea IVAP (17) exige el motivo 12. Sobre
    // las líneas resueltas, porque la nota total no manda ítems y copia los de la factura. La NC 13 escapa (línea 10).
    // 3507 (hoja NotaDebito2_0, filas 194/283/325): las penalidades (ND motivo 13) son operaciones inafectas — con
    // IGV/IVAP, 9995/9997 o tributo 1000/1016 SUNAT rechaza. El backend real todavía no lo cruza (#123). Sobre los
    // ítems resueltos: sin `items` la nota copia los de la factura (gravados) y antes pasaba.
    if (body.tipo === "08" && body.motivo === "13" && items.some((i) => i.tipo_afectacion_igv !== "30"))
      return fail(422, "NOTA_INVALIDA", "3507 - Las penalidades son operaciones inafectas del IGV: la línea debe llevar afectación 30");
    if (!nc13 && body.motivo !== "12" && items.some((i) => i.tipo_afectacion_igv === "17"))
      return fail(422, "NOTA_INVALIDA", "3230 - Tipo de nota debe ser 'Ajustes afectos al IVAP' (12) cuando la línea lleva afectación 17");
    // Lo que paga el cliente por línea: precio × cantidad más los cargos de línea que viajan en el request (un porcentaje
    // sobre el valor sin IGV; el que afecta la base paga IGV). Aproximación del mock —el backend real es la autoridad—,
    // suficiente para que el 3286 no salte con una NC parcial legítima sobre una línea con cargo 47.
    // Totales por tributo, como `Totales` del dominio (aproximados: el backend real es la autoridad): la ficha de la
    // nota y el 3503 se prueban contra cifras reales, no contra un «gravado = total / 1.18» inventado.
    const t = { gravado: 0, igv: 0, ivap: 0, exonerado: 0, inafecto: 0, exportacion: 0, gratuito: 0, igvGratuitas: 0, total: 0 };
    for (const i of items) {
      // Una gratuita no se cobra (precioVenta 0), pero su valor referencial va al cubo 9996 que el 3503 limita (f117/f118).
      if (esGratuita(i.tipo_afectacion_igv)) {
        const referencial = redondear(i.cantidad * i.precio_unitario, 2);
        t.gratuito += referencial;
        if (/^1[1-6]$/.test(i.tipo_afectacion_igv)) t.igvGratuitas += redondear(referencial * 0.18, 2);
        continue;
      }
      const precio = i.cantidad * i.precio_unitario;
      const factor = i.tipo_afectacion_igv === "10" ? 1.18 : i.tipo_afectacion_igv === "17" ? 1.04 : 1;
      const cargos = ("cargos" in i && i.cargos ? (i.cargos as Ajuste[]) : []).reduce((s, c) => s + (c.monto ?? (precio / factor) * (c.porcentaje ?? 0) / 100) * (c.afecta_base_igv === false ? 1 : factor), 0);
      const conImpuesto = precio + cargos;
      // Como `ItemCalculado`: impuesto = base exacta × tasa, HALF_UP a 2 (0.13 → base 0.125 → 0.01). Restar la base ya
      // redondeada daba 0.00 y el mock rechazaba con 3111 el 0.13 que el formulario recomienda y el dominio acepta.
      const baseExacta = conImpuesto / factor;
      const impuesto = redondear(baseExacta * (factor - 1), 2);
      const base = redondear(conImpuesto - impuesto, 2);
      // 3111 (NC f211 / ND f192): base > 0.06 con impuesto 0.00 (solo pasa con el IVAP al 4 %). El dominio lo rechaza antes de numerar.
      if ((i.tipo_afectacion_igv === "10" || i.tipo_afectacion_igv === "17") && base > 0.06 && impuesto === 0)
        return fail(422, "ITEM_INVALIDO", `3111 - Con base imponible mayor a 0.06 el ${i.tipo_afectacion_igv === "17" ? "IVAP" : "IGV"} de la línea «${i.descripcion}» no puede redondear a 0.00: suba el importe`);
      // Como `Totales`: la base IVAP (1016) va en `gravado` y su impuesto en `ivap`, no en `igv`.
      if (i.tipo_afectacion_igv === "17") { t.gravado += base; t.ivap += impuesto; }
      else if (i.tipo_afectacion_igv === "10") { t.gravado += base; t.igv += impuesto; }
      else if (i.tipo_afectacion_igv === "20") t.exonerado += base;
      else if (i.tipo_afectacion_igv === "40") t.exportacion += base;
      else t.inafecto += base;
      t.total += conImpuesto;
    }
    for (const k of Object.keys(t) as Array<keyof typeof t>) t[k] = Number(t[k].toFixed(2));
    const total = t.total;
    // 2062 (NC f401): el importe total de la nota no puede ser 0 —una NC solo de líneas gratuitas no acredita nada—.
    if (!nc13 && body.items?.length && t.total < 0.005) return fail(422, "NOTA_INVALIDA", "2062 - El importe total de la nota debe ser mayor que cero: las líneas gratuitas no se cobran");
    // La nota total copia también el redondeo de la factura (#123): sale por su PayableAmount exacto.
    const copiaLaFactura = !nc13 && !body.items?.length;
    const redondeo = copiaLaFactura ? (factura.totales.redondeo ?? 0) : 0;
    const totalNota = Number((total + redondeo).toFixed(2));
    // 3286 con el acumulado de NC vigentes sobre la misma factura, como `EmitirComprobanteService.acreditadoPorNotas`.
    if (body.tipo === "07") {
      const acreditado = lista
        .filter((n) => n.tipo === "07" && n.nota?.documento_afectado === `${factura.serie}-${factura.numero}` && n.estado_documento !== "RECHAZADO" && n.estado_documento !== "INVALIDO" && n.estado_documento !== "ANULADO")
        .reduce((acc, n) => acc + n.totales.total, 0);
      // Sin tolerancia sobre facturas (NotaCredito2_0 fila 111; la +1 de la fila 113 es solo boletas) y exento en el motivo 10,
      // como el backend desde #123. Con margen de flotante (0.005) para que 118.44 − 118.44 no dé 1e-14.
      if (body.motivo !== "10" && totalNota + acreditado - factura.totales.total > 0.005) return fail(422, "NOTA_INVALIDA", `3286 - El importe total de la nota (${totalNota}) supera el de la factura ${factura.serie}-${factura.numero} (${factura.totales.total})${acreditado > 0 ? `: ya acreditado ${acreditado} en otras notas de crédito` : ""}`);
      // 3503 (filas 114–122, +1 por concepto, exento el motivo 10) con el acumulado por tributo de las NC vigentes, como
      // `exigirQueNoSupereALaFactura`. La NC por importe sobre f-cargos por el total (1353) lo alcanza de frente: gravado
      // 1146.61 vs 1140.32. El mock daba 201 y el backend 422.
      if (body.motivo !== "10") {
        const vigentes = lista.filter((n) => n.tipo === "07" && n.nota?.documento_afectado === `${factura.serie}-${factura.numero}` && n.estado_documento !== "RECHAZADO" && n.estado_documento !== "INVALIDO" && n.estado_documento !== "ANULADO");
        const suma = (k: "gravado" | "igv" | "ivap" | "exonerado" | "inafecto" | "exportacion" | "gratuito" | "igv_gratuitas") => vigentes.reduce((acc, n) => acc + (n.totales[k] ?? 0), 0);
        const limites: Array<[string, number, number, number]> = [
          ["valor de venta gravado", t.gravado, factura.totales.gravado, suma("gravado")],
          ["IGV", t.igv, factura.totales.igv, suma("igv")],
          ["IVAP", t.ivap, factura.totales.ivap ?? 0, suma("ivap")],
          ["valor de venta exonerado", t.exonerado, factura.totales.exonerado, suma("exonerado")],
          ["valor de venta inafecto", t.inafecto, factura.totales.inafecto, suma("inafecto")],
          ["valor de venta de exportación", t.exportacion, factura.totales.exportacion ?? (exportacion ? factura.totales.total : 0), suma("exportacion")],
          ["valor de las operaciones gratuitas", t.gratuito, factura.totales.gratuito ?? 0, suma("gratuito")],
          ["IGV de las operaciones gratuitas", t.igvGratuitas, factura.totales.igv_gratuitas ?? 0, suma("igv_gratuitas")],
        ];
        for (const [concepto, nota, fact, previo] of limites) {
          if (nota + previo - fact > 1) return fail(422, "NOTA_INVALIDA", `3503 - El ${concepto} de la nota (${nota.toFixed(2)}) supera el de la factura ${factura.serie}-${factura.numero} (${fact.toFixed(2)})${previo > 0 ? `: ya acreditado ${previo.toFixed(2)} en otras notas de crédito` : ""}`);
        }
      }
    }
    serie.ultimo_numero += 1;
    const id = nuevoId("n");
    // La nota guarda las líneas con la forma de la respuesta (sin los ajustes en forma de request).
    const itemsNota = items.map((i) => ({ codigo: i.codigo ?? null, descripcion: i.descripcion, unidad: i.unidad, cantidad: i.cantidad, precio_unitario: i.precio_unitario, tipo_afectacion_igv: i.tipo_afectacion_igv }));
    const nota: Comprobante = {
      id, tipo: body.tipo, serie: body.serie, numero: serie.ultimo_numero, fecha_emision: body.fecha_emision, moneda: factura.moneda,
      tipo_operacion: factura.tipo_operacion, receptor: factura.receptor, items: itemsNota, estado_documento: "ACEPTADO", hash: "hashnota==",
      nombre_archivo: `20123456786-${body.tipo}-${body.serie}-${String(serie.ultimo_numero).padStart(8, "0")}`, intentos: 1, ultimo_error: null,
      cdr: { codigo: "0", descripcion: `La Nota de ${body.tipo === "07" ? "Credito" : "Debito"} numero ${body.serie}-${serie.ultimo_numero}, ha sido aceptada`, observaciones: [] },
      totales: { gravado: t.gravado, exonerado: t.exonerado, inafecto: t.inafecto, igv: t.igv, ivap: t.ivap || undefined, exportacion: t.exportacion || undefined, gratuito: t.gratuito || undefined, igv_gratuitas: t.igvGratuitas || undefined, redondeo: redondeo || undefined, total: totalNota },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      nota: { tipo_afectado: "01", documento_afectado: `${factura.serie}-${factura.numero}`, motivo: body.motivo, motivo_descripcion: motivos[body.motivo] ?? "Otros", descripcion: body.descripcion },
      enlaces: { xml: `/v1/facturas/${id}/xml`, pdf: `/v1/facturas/${id}/pdf`, cdr: `/v1/facturas/${id}/cdr` },
    };
    lista.unshift(nota);
    return ok(nota, 201);
  }),

  // Comunicación de baja, a paridad con `ComunicacionBaja.crear` y `DarDeBajaService` (la auditoría midió que el mock daba 201
  // fuera de plazo, con motivo vacío/tab/101 caracteres y con cuerpo vacío). Por defecto SUNAT la acepta en el acto; con el
  // motivo empezando por «[ENVIADA]» queda en proceso (ticket) hasta que se consulta, y con «[RECHAZADA]» SUNAT la rechaza.
  http.post(`${BASE}/v1/facturas/:id/baja`, async ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    let body: { motivo?: string };
    try {
      body = (await request.json()) as { motivo?: string };
    } catch {
      return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
    }
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(422, "BAJA_INVALIDA", `2105/2398 - Solo se puede dar de baja un comprobante aceptado por SUNAT; ${factura.serie}-${factura.numero} está ${factura.estado_documento}`);
    if (factura.tipo === "03") return fail(422, "BAJA_INVALIDA", "2308 - Las boletas se dan de baja en el resumen diario, no con una comunicación de baja");
    if (diasEntre(factura.fecha_emision, hoyLima()) > 7) return fail(422, "BAJA_INVALIDA", `2957 - El plazo para dar de baja ${factura.serie}-${factura.numero} venció: se emitió el ${factura.fecha_emision} y la comunicación debe presentarse dentro de los 7 días calendario`);
    const motivo = (body.motivo ?? "").trim();
    if (motivo.length < 3 || motivo.length > 100 || /[\x00-\x1F\x7F]/.test(motivo)) return fail(422, "BAJA_INVALIDA", "2315 - El motivo de la baja debe tener de 3 a 100 caracteres, sin saltos de línea");
    if (factura.baja && factura.baja.estado !== "RECHAZADA") return fail(422, "BAJA_INVALIDA", `Ya hay una comunicación de baja en curso para ${factura.serie}-${factura.numero}`);
    const hoy = hoyLima().replace(/-/g, "");
    const simulada = motivo.startsWith("[ENVIADA]") ? "ENVIADA" : motivo.startsWith("[RECHAZADA]") ? "RECHAZADA" : "ACEPTADA";
    const baja: Baja = {
      id: nuevoId("b"), identificador: `RA-${hoy}-1`, comprobante: `${factura.serie}-${factura.numero}`, tipo_comprobante: factura.tipo, fecha_generacion: hoyLima(), fecha_referencia: factura.fecha_emision,
      motivo, estado: simulada, ticket: "1758200000123",
      cdr: simulada === "ACEPTADA" ? { codigo: "0", descripcion: `La Comunicacion de baja RA-${hoy}-1, ha sido aceptada`, observaciones: [] }
        : simulada === "RECHAZADA" ? { codigo: "2323", descripcion: "Existe documento ya informado anteriormente en una comunicacion de baja", observaciones: [] } : null,
      intentos: 1, ultimo_error: simulada === "ENVIADA" ? "98 - SUNAT sigue procesando el ticket 1758200000123" : null,
    };
    factura.baja = baja;
    if (simulada === "ACEPTADA") factura.estado_documento = "ANULADO";
    db.bajas.set(baja.id, baja);
    return ok(baja, 201);
  }),
  // Como `BajaController.obtener`: si está ENVIADA consulta el ticket en el acto. En el mock SUNAT ya terminó: se acepta y anula.
  http.get(`${BASE}/v1/bajas/:id`, ({ params }) => {
    const baja = db.bajas.get(String(params.id));
    if (!baja) return fail(404, "NO_ENCONTRADO", "Comunicación de baja no encontrada");
    if (baja.estado === "ENVIADA") {
      baja.estado = "ACEPTADA";
      baja.cdr = { codigo: "0", descripcion: `La Comunicacion de baja ${baja.identificador}, ha sido aceptada`, observaciones: [] };
      baja.ultimo_error = null;
      baja.intentos += 1;
      for (const lista of db.facturasPorEmpresa.values()) for (const f of lista) if (f.baja?.id === baja.id) f.estado_documento = "ANULADO";
    }
    return ok(baja);
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
