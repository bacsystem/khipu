import { http, HttpResponse } from "msw";
import { hoyLima } from "@/lib/formato";
import { ESTADOS_DE_GUIA, type CuerpoDeGuia, type Guia, type PuntoDeGuia } from "@/lib/api/guias";
import { db } from "./data";

/**
 * Las guías de remisión del mock (#39), con la forma y los códigos de error de `GuiaRemisionController` y de las reglas de SUNAT que el dominio valida antes de numerar.
 * El servidor real viene con el envío apagado (`SUNAT_API_GRE_HABILITADA=false`): emitir con `enviar_automatico: true` o reenviar responde `422 GRE_NO_DISPONIBLE`. Solo la
 * fixture `g-firmada-habilitada` hace de la empresa a la que ya le encendieron el envío y avanza a `ENVIADO_PENDIENTE_TICKET`; su ticket la lleva a `ACEPTADO`.
 */
const BASE = process.env.API_BASE_URL ?? "http://localhost:8001";

const ok = <T>(datos: T, status = 200) => HttpResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { status });
const fail = (status: number, codigo: string, mensaje: string) => HttpResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

const SERIE_DE_GUIA = /^[TV][A-Z0-9]{3}$/;
const UBIGEO = /^\d{6}$/;
const FECHA = /^\d{4}-\d{2}-\d{2}$/;
const HABILITADA = new Set(["g-firmada-habilitada"]);

/** Los documentos relacionados que el backend trata como comprobantes: serie-número y RUC de quien los emitió (`DocumentoDeTraslado.COMPROBANTES`). */
const COMPROBANTES_RELACIONADOS = new Set(["01", "03", "04", "09", "12", "31", "48"]);

/** Lo que ya se emitió con cada `Idempotency-Key`: la misma clave y el mismo cuerpo devuelven la misma guía; con otro cuerpo, `422 IDEMPOTENCIA_INVALIDA`. */
const emitidasPorClave = new Map<string, { huella: string; id: string }>();
let secuencia = 0;

function guiasDe(request: Request): Guia[] {
  const empresaId = request.headers.get("x-empresa") ?? "";
  let lista = db.guiasPorEmpresa.get(empresaId);
  if (!lista) {
    lista = [];
    db.guiasPorEmpresa.set(empresaId, lista);
  }
  return lista;
}

const horaDeLima = () => new Date().toLocaleTimeString("en-GB", { timeZone: "America/Lima", hour12: false });

/** Resuelve un punto: un establecimiento de la empresa se completa con su ubigeo y dirección (3367/3371); otro RUC o una dirección propia llevan los suyos. */
function resolverPunto(empresaId: string, p: PuntoDeGuia | undefined, nombre: string, remitente: boolean): PuntoDeGuia | Response {
  if (!p) return fail(422, "TRASLADO_INVALIDO", `El ${nombre} es obligatorio`);
  if (p.establecimiento) {
    if (!remitente) return fail(422, "TRASLADO_INVALIDO", `El ${nombre}: el código de establecimiento solo se admite en una guía remitente; indique el ubigeo y la dirección`);
    if (p.establecimiento === "0000") {
      // Como el backend (3366): el 0000 existe solo si la empresa cargó su domicilio fiscal; el seed del mock no lo tiene, igual que toda empresa nueva.
      const fiscal = (db.establecimientosPorEmpresa.get(empresaId) ?? []).find((x) => x.codigo === "0000" && x.activo);
      if (!fiscal) return fail(422, "TRASLADO_INVALIDO", `3366 - El ${nombre} es el domicilio fiscal (0000), pero la empresa no lo tiene configurado: cárguelo en PUT /v1/empresa/datos-fiscales o indique el ubigeo y la dirección`);
      return { establecimiento: "0000", ubigeo: fiscal.domicilio.ubigeo, direccion: fiscal.domicilio.direccion };
    }
    const e = (db.establecimientosPorEmpresa.get(empresaId) ?? []).find((x) => x.codigo === p.establecimiento);
    if (!e) return fail(422, "TRASLADO_INVALIDO", `3367 - El ${nombre}: el establecimiento ${p.establecimiento} no existe en la empresa`);
    if (!e.activo) return fail(422, "TRASLADO_INVALIDO", `3371 - El ${nombre}: el establecimiento ${p.establecimiento} está dado de baja`);
    return { establecimiento: e.codigo, ubigeo: e.domicilio.ubigeo, direccion: e.domicilio.direccion };
  }
  if (!p.ubigeo || !UBIGEO.test(p.ubigeo)) return fail(422, "TRASLADO_INVALIDO", `El ${nombre}: el ubigeo tiene 6 dígitos (catálogo 13)`);
  if (!p.direccion || p.direccion.trim().length < 3) return fail(422, "TRASLADO_INVALIDO", `El ${nombre}: la dirección tiene de 3 a 500 caracteres`);
  return { ubigeo: p.ubigeo, direccion: p.direccion.trim(), ...(p.ruc ? { ruc: p.ruc } : {}) };
}

/** Las reglas que el backend aplica antes de numerar. Devuelve la guía que se emitiría (sin id definitivo ni número) o el `422`/`400` que respondería. */
function validar(empresaId: string, c: CuerpoDeGuia): { guia: Omit<Guia, "id" | "numero" | "estado" | "nombre_archivo" | "intentos" | "consultas_ticket"> } | Response {
  const serie = c.serie;
  if (typeof serie !== "string" || !SERIE_DE_GUIA.test(serie)) return fail(422, "VALIDACION", "serie de guía remitente (T###) o transportista (V###) inválida");
  const hoy = hoyLima();
  const fechaEmision = c.fecha_emision ?? hoy;
  if (!FECHA.test(fechaEmision)) return fail(422, "VALIDACION", "fecha_emision debe ser YYYY-MM-DD");
  if (fechaEmision > hoy) return fail(422, "GUIA_INVALIDA", "La fecha de emisión no puede ser futura");
  const d = c.destinatario;
  if (!d || !d.tipo_doc || !d.num_doc || !d.nombre) return fail(422, "VALIDACION", "destinatario: tipo_doc, num_doc y nombre son obligatorios");
  if (!c.traslado) return fail(422, "VALIDACION", "traslado es obligatorio");
  if (!Array.isArray(c.items) || c.items.length === 0) return fail(422, "VALIDACION", "items: la guía lleva al menos un bien");

  const esRemitente = serie.startsWith("T");
  if (esRemitente && (c.remitente || c.registro_mtc)) return fail(422, "GUIA_INVALIDA", "Una guía remitente (serie T###) no lleva remitente ni registro_mtc: esos son datos de la guía transportista");
  if (!esRemitente && (c.motivo || c.descripcion_motivo || c.modalidad || c.transportista)) {
    return fail(422, "GUIA_INVALIDA", "Una guía transportista (serie V###) no lleva motivo, descripcion_motivo, modalidad ni transportista: esos son datos de la guía remitente");
  }
  const registrada = (db.seriesPorEmpresa.get(empresaId) ?? []).find((s) => s.serie === serie && (s.tipo === "09" || s.tipo === "31"));
  if (!registrada) return fail(422, "SERIE_NO_CONFIGURADA", `La serie ${serie} no está registrada para la empresa: créala con POST /v1/series (tipo ${esRemitente ? "09" : "31"})`);

  const t = c.traslado;
  const indicadores = t.indicadores ?? [];
  const vehiculos = t.vehiculos ?? [];
  const conductores = t.conductores ?? [];
  if (!t.fecha_inicio || !FECHA.test(t.fecha_inicio)) return fail(422, "TRASLADO_INVALIDO", "traslado.fecha_inicio es obligatoria (YYYY-MM-DD)");
  if (t.fecha_inicio < fechaEmision) return fail(422, "TRASLADO_INVALIDO", "3343 - La fecha de inicio del traslado no puede ser anterior a la de emisión");
  if (!(Number(t.peso_bruto) > 0) || !/^\d{1,12}(\.\d{1,3})?$/.test(String(t.peso_bruto))) {
    return fail(422, "TRASLADO_INVALIDO", "2880 - El peso bruto total es mayor que cero, con hasta 12 enteros y 3 decimales");
  }
  if (t.unidad_peso && t.unidad_peso !== "KGM" && t.unidad_peso !== "TNE") return fail(422, "TRASLADO_INVALIDO", "unidad_peso es KGM o TNE");

  if (esRemitente) {
    if (!c.motivo) return fail(422, "TRASLADO_INVALIDO", "El motivo del traslado es obligatorio (catálogo 20)");
    if (c.motivo === "13" && !c.descripcion_motivo?.trim()) return fail(422, "TRASLADO_INVALIDO", "Con el motivo 13 (otros) la descripción del motivo es obligatoria");
    if (c.modalidad !== "01" && c.modalidad !== "02") return fail(422, "TRASLADO_INVALIDO", "La modalidad es 01 (transporte público) o 02 (privado)");
    if (c.modalidad === "01") {
      if (!c.transportista?.ruc || !c.transportista.razon_social) return fail(422, "TRANSPORTE_INVALIDO", "En transporte público va el transportista (RUC y razón social)");
      // Solo con el indicador de vehículos y conductores del transportista (reglas 3450 y 3451) el remitente los registra en transporte público.
      if ((vehiculos.length > 0 || conductores.length > 0) && !indicadores.includes("vehiculos_conductores_transportista")) {
        return fail(422, "TRANSPORTE_INVALIDO", "En transporte público no se registran vehículos ni conductores del remitente");
      }
    } else if (!indicadores.includes("vehiculo_m1_o_l") && (vehiculos.length === 0 || conductores.length === 0)) {
      return fail(422, "TRANSPORTE_INVALIDO", "En transporte privado van el vehículo y el conductor principales (o el indicador vehiculo_m1_o_l)");
    }
  } else {
    if (!c.remitente?.num_doc) return fail(422, "REMITENTE_INVALIDO", "La guía transportista lleva al remitente (quien envía los bienes)");
    if (!c.registro_mtc?.trim()) return fail(422, "TRANSPORTE_INVALIDO", "4391 - El transportista tiene que indicar su registro MTC");
    if (vehiculos.length === 0 || conductores.length === 0) return fail(422, "TRANSPORTE_INVALIDO", "La guía transportista lleva siempre el vehículo y el conductor principales");
  }
  if (vehiculos.length > 3 || conductores.length > 3) return fail(422, "TRANSPORTE_INVALIDO", "Hasta un vehículo y un conductor principales y dos secundarios");

  const partida = resolverPunto(empresaId, t.partida, "punto de partida", esRemitente);
  if (partida instanceof Response) return partida;
  const llegada = resolverPunto(empresaId, t.llegada, "punto de llegada", esRemitente);
  if (llegada instanceof Response) return llegada;

  for (const [n, i] of c.items.entries()) {
    if (!i.descripcion?.trim() || !i.unidad) return fail(422, "ITEM_INVALIDO", `Bien ${n + 1}: la descripción y la unidad son obligatorias`);
    if (!(Number(i.cantidad) > 0)) return fail(422, "ITEM_INVALIDO", `Bien ${n + 1}: la cantidad tiene que ser mayor que cero`);
  }
  for (const doc of c.documentos_relacionados ?? []) {
    if (!doc.tipo || !doc.numero) return fail(422, "DOCUMENTO_RELACIONADO_INVALIDO", "Cada documento relacionado lleva su tipo y su número");
    if (COMPROBANTES_RELACIONADOS.has(doc.tipo)) {
      if (!/^[A-Z0-9]{4}-\d{1,8}$/.test(doc.numero)) return fail(422, "DOCUMENTO_RELACIONADO_INVALIDO", `3441 - El número del documento relacionado ${doc.tipo} no cumple el formato serie-número (F001-123, T001-45); recibido ${doc.numero}`);
      if (!doc.ruc_emisor) return fail(422, "DOCUMENTO_RELACIONADO_INVALIDO", `Indique el RUC de quien emitió el documento relacionado ${doc.tipo} ${doc.numero}`);
    }
  }

  return {
    guia: {
      tipo: esRemitente ? "09" : "31",
      serie,
      fecha_emision: fechaEmision,
      destinatario: { tipo_doc: d.tipo_doc, num_doc: d.num_doc, nombre: d.nombre },
      ...(esRemitente
        ? {
            motivo: c.motivo,
            descripcion_motivo: c.descripcion_motivo?.trim() || (c.motivo === "01" ? "Venta" : undefined),
            modalidad: c.modalidad,
            ...(c.transportista ? { transportista: c.transportista } : {}),
          }
        : { remitente: c.remitente, registro_mtc: c.registro_mtc }),
      traslado: {
        fecha_inicio: t.fecha_inicio,
        peso_bruto: Number(t.peso_bruto),
        unidad_peso: t.unidad_peso ?? "KGM",
        partida,
        llegada,
        vehiculos: vehiculos.map((v, n) => ({ placa: v.placa.replace(/-/g, "").toUpperCase(), principal: n === 0 })),
        conductores: conductores.map((x, n) => ({ ...x, principal: n === 0 })),
        indicadores,
      },
      items: c.items,
      documentos_relacionados: c.documentos_relacionados ?? [],
      ...(c.observaciones ? { observaciones: c.observaciones } : {}),
    } as Guia,
  };
}

export const handlersDeGuias = [
  http.get(`${BASE}/v1/guias-remision`, ({ request }) => {
    const url = new URL(request.url);
    const estado = url.searchParams.get("estado");
    const tipo = url.searchParams.get("tipo");
    const serie = url.searchParams.get("serie");
    const desde = url.searchParams.get("desde");
    const hasta = url.searchParams.get("hasta");
    if (estado && !(ESTADOS_DE_GUIA as readonly string[]).includes(estado)) return fail(400, "PARAMETRO_INVALIDO", `estado desconocido: ${estado}`);
    if (tipo && tipo !== "09" && tipo !== "31") return fail(400, "PARAMETRO_INVALIDO", "tipo es 09 (remitente) o 31 (transportista)");
    if (serie && !SERIE_DE_GUIA.test(serie)) return fail(400, "PARAMETRO_INVALIDO", "serie de guía con formato inválido (T### o V###)");
    if (desde && hasta && desde > hasta) return fail(400, "RANGO_INVALIDO", `desde (${desde}) es posterior a hasta (${hasta})`);
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1));
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20)));
    const lista = guiasDe(request)
      .filter((g) => (!estado || g.estado === estado) && (!tipo || g.tipo === tipo) && (!serie || g.serie === serie) && (!desde || g.fecha_emision >= desde) && (!hasta || g.fecha_emision <= hasta))
      // De la más reciente a la más antigua: por fecha de emisión y, dentro del día, por número.
      .sort((a, b) => (a.fecha_emision === b.fecha_emision ? b.numero - a.numero : b.fecha_emision.localeCompare(a.fecha_emision)));
    return HttpResponse.json(
      { estado: "exito", datos: lista.slice((pagina - 1) * porPagina, pagina * porPagina), mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(lista.length) } },
    );
  }),

  http.post(`${BASE}/v1/guias-remision/validar`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const r = validar(empresaId, (await request.json()) as CuerpoDeGuia);
    if (r instanceof Response) return r;
    // Como `/validar`: la guía que se emitiría, sin id definitivo, sin número y sin descargas.
    const { guia } = r;
    return ok({ ...guia, id: "provisional", estado: "RECIBIDO", nombre_archivo: "provisional", intentos: 0, consultas_ticket: 0 });
  }),

  http.post(`${BASE}/v1/guias-remision`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const cuerpo = (await request.json()) as CuerpoDeGuia;
    const clave = request.headers.get("idempotency-key");
    if (clave !== null && !/^[A-Za-z0-9_-]{8,100}$/.test(clave)) {
      return fail(422, "IDEMPOTENCIA_INVALIDA", "Idempotency-Key debe tener de 8 a 100 letras, dígitos, guiones o guiones bajos (se recomienda un UUID)");
    }
    const huella = JSON.stringify(cuerpo);
    const lista = guiasDe(request);
    if (clave !== null) {
      const previa = emitidasPorClave.get(`${empresaId}:${clave}`);
      if (previa) {
        if (previa.huella !== huella) return fail(422, "IDEMPOTENCIA_INVALIDA", `La clave de idempotencia ${clave} ya se usó con otro contenido`);
        const guia = lista.find((g) => g.id === previa.id);
        if (guia) return ok(guia, 200);
      }
    }
    const r = validar(empresaId, cuerpo);
    if (r instanceof Response) return r;
    // El envío viene apagado: se rechaza antes de numerar, como el servidor, y no gasta el correlativo.
    if (cuerpo.enviar_automatico === true) {
      return fail(422, "GRE_NO_DISPONIBLE", "El envío de guías de remisión a SUNAT no está habilitado en este servidor. Emita con enviar_automatico=false para revisar el XML y el PDF.");
    }
    const registrada = (db.seriesPorEmpresa.get(empresaId) ?? []).find((s) => s.serie === cuerpo.serie)!;
    registrada.ultimo_numero += 1;
    const numero = registrada.ultimo_numero;
    const id = `g-nueva-${++secuencia}-${numero}`;
    const guia: Guia = {
      ...r.guia,
      id,
      numero,
      estado: "FIRMADO",
      nombre_archivo: `20100047226-${r.guia.tipo}-${cuerpo.serie}-${numero}`,
      hora_emision: horaDeLima(),
      hash: "bW9jay1kaWdlc3Q=",
      intentos: 0,
      consultas_ticket: 0,
      enlaces: { xml: `/v1/guias-remision/${id}/xml`, pdf: `/v1/guias-remision/${id}/pdf` },
    };
    lista.push(guia);
    if (clave !== null) emitidasPorClave.set(`${empresaId}:${clave}`, { huella, id });
    return ok(guia, 201);
  }),

  http.get(`${BASE}/v1/guias-remision/:id`, ({ request, params }) => {
    const g = guiasDe(request).find((x) => x.id === params.id);
    return g ? ok(g) : fail(404, "NO_ENCONTRADO", "No existe la guía de remisión");
  }),

  http.post(`${BASE}/v1/guias-remision/:id/enviar`, ({ request, params }) => {
    const g = guiasDe(request).find((x) => x.id === params.id);
    if (!g) return fail(404, "NO_ENCONTRADO", "No existe la guía de remisión");
    const forzar = new URL(request.url).searchParams.get("forzar") === "true";
    if (g.estado === "ENVIANDO" && !forzar) return fail(409, "ENVIO_EN_CURSO", "La guía tiene un envío en curso: espere su respuesta o, pasados 10 minutos y revisado en SOL que no llegó, reenvíe con forzar=true");
    if (g.estado !== "FIRMADO" && g.estado !== "ERROR_ENVIO" && g.estado !== "ENVIANDO") return fail(409, "ESTADO_NO_ENVIABLE", `La guía está ${g.estado}: solo se envía una FIRMADO o una en ERROR_ENVIO`);
    if (!HABILITADA.has(g.id) && g.estado !== "ENVIANDO") {
      return fail(422, "GRE_NO_DISPONIBLE", "El envío de guías de remisión a SUNAT no está habilitado en este servidor");
    }
    g.estado = "ENVIADO_PENDIENTE_TICKET";
    g.ticket = "11111111-2222-4333-8444-555555555555";
    g.fecha_recepcion = new Date().toISOString().slice(0, 19);
    g.intentos += 1;
    g.reintento_automatico = true;
    delete g.ultimo_error;
    return ok(g);
  }),

  http.post(`${BASE}/v1/guias-remision/:id/ticket`, ({ request, params }) => {
    const g = guiasDe(request).find((x) => x.id === params.id);
    if (!g) return fail(404, "NO_ENCONTRADO", "No existe la guía de remisión");
    if (g.estado !== "ENVIADO_PENDIENTE_TICKET") return fail(409, "TICKET_NO_PENDIENTE", `La guía está ${g.estado}: no espera ningún ticket`);
    g.estado = "ACEPTADO";
    g.consultas_ticket += 1;
    g.reintento_automatico = false;
    g.cdr = { codigo: "0", descripcion: `La Guia de Remision numero ${g.serie}-${g.numero}, ha sido aceptada` };
    g.enlaces = { xml: `/v1/guias-remision/${g.id}/xml`, pdf: `/v1/guias-remision/${g.id}/pdf`, cdr: `/v1/guias-remision/${g.id}/cdr` };
    return ok(g);
  }),

  http.get(`${BASE}/v1/guias-remision/:id/xml`, ({ request, params }) => {
    const g = guiasDe(request).find((x) => x.id === params.id);
    if (!g) return fail(404, "NO_ENCONTRADO", "No existe la guía de remisión");
    return new HttpResponse(`<?xml version="1.0" encoding="UTF-8"?><DespatchAdvice><ID>${g.serie}-${g.numero}</ID></DespatchAdvice>`, {
      headers: { "content-type": "application/xml", "content-disposition": `attachment; filename="${g.nombre_archivo}.xml"` },
    });
  }),

  http.get(`${BASE}/v1/guias-remision/:id/pdf`, ({ request, params }) => {
    const g = guiasDe(request).find((x) => x.id === params.id);
    if (!g) return fail(404, "NO_ENCONTRADO", "No existe la guía de remisión");
    return new HttpResponse("%PDF-1.4\n%mock\n", { headers: { "content-type": "application/pdf", "content-disposition": `inline; filename="${g.nombre_archivo}.pdf"` } });
  }),

  http.get(`${BASE}/v1/guias-remision/:id/cdr`, ({ request, params }) => {
    const g = guiasDe(request).find((x) => x.id === params.id);
    if (!g) return fail(404, "NO_ENCONTRADO", "No existe la guía de remisión");
    if (!g.cdr) return fail(404, "SIN_CDR", "La guía todavía no tiene constancia de SUNAT");
    return new HttpResponse("PK\u0003\u0004mock", { headers: { "content-type": "application/zip", "content-disposition": `attachment; filename="R-${g.nombre_archivo}.zip"` } });
  }),
];
