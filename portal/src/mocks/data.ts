import { hoyLima } from "@/lib/formato";

function base64url(obj: unknown): string {
  return btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fakeJwt(payload: Record<string, unknown>): string {
  return `${base64url({ alg: "none" })}.${base64url(payload)}.firma-de-prueba`;
}

export type Usuario = { id: string; cuenta_id: string; email: string; rol: string };
export type Empresa = {
  id: string;
  ruc: string;
  razon_social: string;
  entorno: "BETA" | "PRODUCCION";
  tiene_certificado: boolean;
  tiene_credenciales_sol: boolean;
  certificado_vigencia_hasta: string | null;
  tiene_domicilio?: boolean;
  domicilio?: { ubigeo: string; direccion: string; urbanizacion: string | null; distrito: string | null; provincia: string | null; departamento: string | null; codigo_establecimiento: string } | null;
  cuenta_detracciones?: string | null;
  nombre_comercial?: string | null;
  /** Padrón de tasa especial del IGV (#84): 10.5 % en vez de 18 %. Lo lee el diálogo de emisión para previsualizar. */
  padron_tasa_especial_igv?: boolean;
  personalizacion_pdf?: PersonalizacionPdf;
};

export type PersonalizacionPdf = {
  plantilla: "clasico" | "moderno" | "sutil" | "corporativo" | "gris";
  color_primario: string;
  tiene_logo: boolean;
  pie_de_pagina: string | null;
  observaciones_por_defecto: string | null;
};

export const PERSONALIZACION_POR_DEFECTO: PersonalizacionPdf = { plantilla: "clasico", color_primario: "#1E1E24", tiene_logo: false, pie_de_pagina: null, observaciones_por_defecto: null };
export type Serie = { tipo: string; serie: string; ultimo_numero: number; activa: boolean; establecimiento: string };
export type Establecimiento = { codigo: string; nombre: string; domicilio: NonNullable<Empresa["domicilio"]>; activo: boolean };
export type ApiKey = { id: string; prefijo: string; activa: boolean; creada_en: string; revocada_en?: string };
export type Comprobante = {
  id: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  moneda: string;
  tipo_operacion: string;
  receptor: { tipo_doc: string; num_doc: string; razon_social: string; direccion: string | null };
  items: Array<{
    codigo: string | null;
    descripcion: string;
    unidad: string;
    cantidad: number;
    precio_unitario: number;
    tipo_afectacion_igv: string;
    // Lo que el backend calcula por línea y una nota parcial tiene que leer (cargos, ISC) o mostrar (precio_venta).
    valor_venta?: number;
    igv?: number;
    precio_venta?: number;
    cargos?: Array<{ tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; motivo?: string | null; codigo: string }> | null;
    isc?: { sistema: string; tasa: number; monto: number; base?: number; base_pvp?: number | null; monto_unitario?: number | null } | null;
  }>;
  estado_documento: string;
  hash: string;
  nombre_archivo: string;
  intentos: number;
  ultimo_error: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  fecha_vencimiento?: string | null;
  totales: {
    gravado: number; exonerado: number; inafecto: number; igv: number; total: number; ivap?: number; exportacion?: number; total_precio_venta?: number; total_anticipos?: number;
    total_cargos?: number; redondeo?: number; cargos?: Array<{ tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; motivo?: string | null; codigo: string }>;
  };
  forma_pago: { tipo: "contado" | "credito"; monto_pendiente: number | null; cuotas: Array<{ id: string; monto: number; vencimiento: string }> };
  detraccion?: { codigo_bien_servicio: string; descripcion: string; porcentaje: number; monto: number; cuenta_banco_nacion: string; medio_pago: string } | null;
  anticipos?: Array<{ comprobante: string; serie: string; numero: number; monto: number; importe_pagado: number; afectacion: string; codigo_sunat: string; fecha_pago: string | null }>;
  referencias?: { orden_compra?: string | null; guias?: Array<{ tipo: string; numero: string }> | null; documentos_relacionados?: Array<{ tipo: string; numero: string }> | null } | null;
  nota?: { tipo_afectado: string; documento_afectado: string; motivo: string; motivo_descripcion: string; descripcion: string } | null;
  notas?: Array<{ id: string; tipo: string; comprobante: string; fecha_emision: string; motivo: string; motivo_descripcion: string; estado_documento: string; total: number }> | null;
  /** Historial de intentos (#7): solo lo devuelve GET por id. */
  eventos?: Array<{ fecha: string; estado_anterior: string | null; estado_resultante: string; mensaje: string | null }> | null;
  baja?: Baja | null;
  enlaces: { xml: string; pdf?: string; cdr?: string };
};

export type Baja = {
  id: string;
  identificador: string;
  comprobante: string;
  tipo_comprobante: string;
  fecha_generacion: string;
  motivo: string;
  estado: "GENERADA" | "ENVIADA" | "ERROR_ENVIO" | "ACEPTADA" | "RECHAZADA";
  ticket: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  intentos: number;
  ultimo_error: string | null;
};

type Sesion = { usuario: Usuario };

export const db = {
  usuariosPorEmail: new Map<string, { usuario: Usuario; password: string }>(),
  empresasPorCuenta: new Map<string, Empresa[]>(),
  seriesPorEmpresa: new Map<string, Serie[]>(),
  establecimientosPorEmpresa: new Map<string, Establecimiento[]>(),
  apiKeysPorEmpresa: new Map<string, ApiKey[]>(),
  facturasPorEmpresa: new Map<string, Comprobante[]>(),
  bajas: new Map<string, Baja>(),
  correos: [] as Array<{ comprobante: string; email: string; mensaje: string | null }>,
  sesionesPorToken: new Map<string, Sesion>(),
};

export function resetDb() {
  db.usuariosPorEmail.clear();
  db.empresasPorCuenta.clear();
  db.seriesPorEmpresa.clear();
  db.establecimientosPorEmpresa.clear();
  db.apiKeysPorEmpresa.clear();
  db.facturasPorEmpresa.clear();
  db.bajas.clear();
  db.correos.length = 0;
  db.sesionesPorToken.clear();

  const usuario: Usuario = {
    id: "u-demo",
    cuenta_id: "c-demo",
    email: "demo@example.com",
    rol: "ADMIN",
  };
  db.usuariosPorEmail.set(usuario.email, { usuario, password: "Passw0rd1" });

  const empresa: Empresa = {
    id: "e-demo",
    ruc: "20123456786",
    razon_social: "Demo SAC",
    entorno: "BETA",
    tiene_certificado: true,
    tiene_credenciales_sol: true,
    certificado_vigencia_hasta: "2036-01-01",
  };
  db.empresasPorCuenta.set(usuario.cuenta_id, [empresa]);
  db.seriesPorEmpresa.set(empresa.id, [
    { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
    // Serie de uso exclusivo del e2e del correlativo: los specs corren en paralelo contra este mismo mock, y sobre
    // F001 el número avanza por debajo de los pies; sobre F002 solo emite ese test, así que puede afirmar `===`.
    { tipo: "01", serie: "F002", ultimo_numero: 0, activa: true, establecimiento: "0000" },
    { tipo: "07", serie: "FC01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
    { tipo: "08", serie: "FD01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
  ]);
  db.establecimientosPorEmpresa.set(empresa.id, [
    { codigo: "0002", nombre: "Tienda Miraflores", domicilio: { ubigeo: "150122", direccion: "Av. Larco 345", urbanizacion: null, distrito: "MIRAFLORES", provincia: "LIMA", departamento: "LIMA", codigo_establecimiento: "0002" }, activo: true },
  ]);
  db.apiKeysPorEmpresa.set(empresa.id, [
    { id: "k-activa", prefijo: "fk_demo001", activa: true, creada_en: "2026-09-01T15:00:00Z" },
    { id: "k-revocada", prefijo: "fk_demo000", activa: false, creada_en: "2026-08-01T15:00:00Z", revocada_en: "2026-08-20T12:00:00Z" },
  ]);
  db.facturasPorEmpresa.set(empresa.id, [
    {
      id: "f-aceptada",
      tipo: "01",
      serie: "F001",
      numero: 1,
      fecha_emision: "2026-09-01",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [
        { codigo: "SRV-001", descripcion: "Servicio de desarrollo de software", unidad: "ZZ", cantidad: 1, precio_unitario: 141.6, tipo_afectacion_igv: "10" },
      ],
      estado_documento: "ACEPTADO",
      hash: "y4M8+jW8Xp278K1aM02q19KjvO3k=",
      nombre_archivo: "20123456786-01-F001-00000001",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-1, ha sido aceptada", observaciones: [] },
      // Operación de 120 + IGV con un anticipo de 20 (pagó 23.60) y recargo al consumo 5 % (46, sin IGV): base neta 100, IGV 18,
      // a pagar 141.60 + 5.00 − 23.60 = 123.
      totales: {
        gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 123, total_precio_venta: 141.6, total_anticipos: 23.6,
        total_cargos: 5, cargos: [{ tipo: "PORCENTAJE", valor: 5, monto: 5, afecta_base_igv: false, motivo: "recargo_consumo", codigo: "46" }],
      },
      anticipos: [{ comprobante: "F001-90", serie: "F001", numero: 90, monto: 20, importe_pagado: 23.6, afectacion: "gravado", codigo_sunat: "04", fecha_pago: "2026-08-20" }],
      referencias: { orden_compra: "OC-2026-0457", guias: [{ tipo: "09", numero: "T001-123" }], documentos_relacionados: null },
      fecha_vencimiento: "2026-11-01",
      forma_pago: {
        tipo: "credito",
        monto_pendiente: 123,
        cuotas: [
          { id: "Cuota001", monto: 61.5, vencimiento: "2026-10-01" },
          { id: "Cuota002", monto: 61.5, vencimiento: "2026-11-01" },
        ],
      },
      detraccion: { codigo_bien_servicio: "022", descripcion: "Otros servicios empresariales", porcentaje: 12, monto: 15, cuenta_banco_nacion: "00-000-123456", medio_pago: "001" },
      enlaces: { xml: "/v1/facturas/f-aceptada/xml", pdf: "/v1/facturas/f-aceptada/pdf", cdr: "/v1/facturas/f-aceptada/cdr" },
    },
    {
      // Exportación de servicios (0201 sería sin RUC; 0200 admite RUC): para probar que las notas siguen la afectación 40
      // de la factura (2642) y que el motivo 11 solo se ofrece acá.
      id: "f-export",
      tipo: "01",
      serie: "F001",
      // Número y fecha propios: F001-4 ya es f-firmada, y el e2e de filtros cuenta qué cae el 1 y el 2 de setiembre.
      numero: 5,
      fecha_emision: "2026-08-25",
      moneda: "USD",
      tipo_operacion: "0200",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio de diseño para el exterior", unidad: "ZZ", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "40" }],
      estado_documento: "ACEPTADO",
      hash: "exp==",
      nombre_archivo: "20123456786-01-F001-00000005",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-5, ha sido aceptada", observaciones: [] },
      totales: { gravado: 0, exonerado: 0, inafecto: 0, igv: 0, total: 100 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-export/xml" },
    },
    {
      // Factura con cargo de línea 47 e ISC en los sistemas 02 y 03: la recertificación de notas midió que la NC parcial
      // descartaba los cargos (acreditaba de menos) y mandaba el ISC como {sistema, tasa}, que el dominio rechaza en
      // 02 y 03. Fecha fuera de setiembre para no alterar el e2e de filtros por fecha.
      id: "f-cargos",
      tipo: "01",
      serie: "F001",
      numero: 7,
      fecha_emision: "2026-08-26",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [
        // 10 × 118 con flete del 10 % que paga IGV (47): valor 1000 + 100, IGV 198, paga 1298.
        { codigo: "MESA-01", descripcion: "Mesa de trabajo", unidad: "NIU", cantidad: 10, precio_unitario: 118, tipo_afectacion_igv: "10", valor_venta: 1100, igv: 198, precio_venta: 1298, cargos: [{ tipo: "PORCENTAJE", valor: 10, monto: 100, afecta_base_igv: true, codigo: "47" }] },
        // ISC de monto fijo (02): 2.25 por unidad; la tasa que devuelve el backend es derivada.
        { codigo: null, descripcion: "Cerveza artesanal 330 ml", unidad: "NIU", cantidad: 2, precio_unitario: 20, tipo_afectacion_igv: "10", valor_venta: 29.4, igv: 6.1, precio_venta: 40, isc: { sistema: "02", tasa: 15.31, monto: 4.5, base: 29.4, monto_unitario: 2.25 } },
        // ISC al valor según PVP (03): tasa sobre el PVP sugerido unitario.
        { codigo: null, descripcion: "Gaseosa 500 ml", unidad: "NIU", cantidad: 3, precio_unitario: 5, tipo_afectacion_igv: "10", valor_venta: 10.92, igv: 2.29, precio_venta: 15, isc: { sistema: "03", tasa: 17, monto: 1.79, base: 10.5, base_pvp: 3.5 } },
      ],
      estado_documento: "ACEPTADO",
      hash: "cargos==",
      nombre_archivo: "20123456786-01-F001-00000007",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-7, ha sido aceptada", observaciones: [] },
      totales: { gravado: 1140.32, exonerado: 0, inafecto: 0, igv: 206.39, total: 1353 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-cargos/xml" },
    },
    {
      // Venta de arroz pilado afecta al IVAP (afectación 17, 4 %): la rama IVAP del formulario de notas no tenía
      // ninguna factura en el mock y una ND 01/02 sobre ella salía con 17 y motivo ≠ 12 (SUNAT 3230).
      id: "f-ivap",
      tipo: "01",
      serie: "F001",
      numero: 8,
      fecha_emision: "2026-08-27",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [{ codigo: null, descripcion: "Arroz pilado, saco de 50 kg", unidad: "NIU", cantidad: 10, precio_unitario: 10.4, tipo_afectacion_igv: "17", valor_venta: 100, igv: 4, precio_venta: 104 }],
      estado_documento: "ACEPTADO",
      hash: "ivap==",
      nombre_archivo: "20123456786-01-F001-00000008",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-8, ha sido aceptada", observaciones: [] },
      // Como `Totales` del backend: en una factura IVAP la base 1016 va en `gravado` y el impuesto en `ivap`, con `igv` 0.
      // La recert #8 midió que con {gravado: 0, igv: 4} el mock rechazaba con 3503 toda NC sobre esta factura.
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 0, ivap: 4, total: 104 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-ivap/xml" },
    },
    {
      // Anulada por comunicación de baja aceptada: el estado más peligroso para las notas (2120) no estaba en el mock.
      id: "f-anulada",
      tipo: "01",
      serie: "F001",
      numero: 6,
      fecha_emision: "2026-08-20",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio anulado", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10" }],
      estado_documento: "ANULADO",
      hash: "anul==",
      nombre_archivo: "20123456786-01-F001-00000006",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-6, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-anulada/xml" },
    },
    {
      // NC RECHAZADA por SUNAT sobre f-export: no acreditó nada, así que el tope 3286 de esa factura NO debe
      // descontarla (igual que `acreditadoPorNotas` en el backend). Sin este fixture ese filtro no tenía test.
      id: "n-rechazada",
      tipo: "07",
      serie: "FC01",
      numero: 9,
      fecha_emision: "2026-08-26",
      moneda: "USD",
      tipo_operacion: "0200",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio de diseño para el exterior", unidad: "ZZ", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "40" }],
      estado_documento: "RECHAZADO",
      hash: "rech==",
      nombre_archivo: "20123456786-07-FC01-00000009",
      intentos: 1,
      ultimo_error: "3286 - El monto total de la nota de credito debe ser menor o igual al monto del documento que modifica",
      cdr: { codigo: "3286", descripcion: "El monto total de la nota de credito debe ser menor o igual al monto del documento que modifica", observaciones: [] },
      totales: { gravado: 0, exonerado: 0, inafecto: 0, igv: 0, total: 100 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      nota: { tipo_afectado: "01", documento_afectado: "F001-5", motivo: "07", motivo_descripcion: "Devolución por ítem", descripcion: "Intento rechazado" },
      enlaces: { xml: "/v1/facturas/n-rechazada/xml" },
    },
    {
      // NC ANULADA (baja aceptada) sobre f-cargos: ya no acredita nada, así que el tope 3286 de la factura no debe
      // descontarla. El filtro del formulario existía sin test y su mutación sobrevivía a la suite.
      id: "n-anulada",
      tipo: "07",
      serie: "FC01",
      numero: 10,
      fecha_emision: "2026-08-27",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [{ codigo: null, descripcion: "Cerveza artesanal 330 ml", unidad: "NIU", cantidad: 5, precio_unitario: 20, tipo_afectacion_igv: "10" }],
      estado_documento: "ANULADO",
      hash: "ncanul==",
      nombre_archivo: "20123456786-07-FC01-00000010",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Nota de Credito numero FC01-10, ha sido aceptada", observaciones: [] },
      totales: { gravado: 84.75, exonerado: 0, inafecto: 0, igv: 15.25, total: 100 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      nota: { tipo_afectado: "01", documento_afectado: "F001-7", motivo: "07", motivo_descripcion: "Devolución por ítem", descripcion: "Nota emitida por error y dada de baja" },
      enlaces: { xml: "/v1/facturas/n-anulada/xml" },
    },
    {
      id: "f-obs",
      tipo: "01",
      serie: "F001",
      numero: 3,
      fecha_emision: hoyLima(),
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [{ codigo: null, descripcion: "Consultoría", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10" }],
      estado_documento: "ACEPTADO_CON_OBS",
      hash: "obs8+jW8Xp278K1aM02q19KjvO3k=",
      nombre_archivo: "20123456786-01-F001-00000003",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-3, ha sido aceptada", observaciones: ["4252 - El dato ingresado como atributo @listName es incorrecto."] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-obs/xml", pdf: "/v1/facturas/f-obs/pdf", cdr: "/v1/facturas/f-obs/cdr" },
    },
    {
      id: "f-firmada",
      tipo: "01",
      serie: "F001",
      numero: 4,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Soporte mensual", unidad: "ZZ", cantidad: 1, precio_unitario: 236, tipo_afectacion_igv: "10" }],
      estado_documento: "FIRMADO",
      hash: "firm8+jW8Xp278K1aM02q19KjvO3k=",
      nombre_archivo: "20123456786-01-F001-00000004",
      intentos: 0,
      ultimo_error: null,
      cdr: null,
      totales: { gravado: 200, exonerado: 0, inafecto: 0, igv: 36, total: 236 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-firmada/xml", pdf: "/v1/facturas/f-firmada/pdf" },
    },
    {
      id: "f-error",
      tipo: "01",
      serie: "F001",
      numero: 2,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [
        { codigo: null, descripcion: "Consultoría técnica", unidad: "ZZ", cantidad: 2, precio_unitario: 25, tipo_afectacion_igv: "10" },
      ],
      estado_documento: "ERROR_ENVIO",
      hash: "hash-2",
      nombre_archivo: "20123456786-01-F001-00000002",
      intentos: 2,
      ultimo_error: "SUNAT no respondió a tiempo",
      eventos: [
        { fecha: "2026-09-02T15:00:01Z", estado_anterior: "RECIBIDO", estado_resultante: "FIRMADO", mensaje: "Firmado; resumen k9Qx…" },
        { fecha: "2026-09-02T15:00:05Z", estado_anterior: "FIRMADO", estado_resultante: "ERROR_ENVIO", mensaje: "SUNAT no disponible (timeout)" },
        { fecha: "2026-09-02T15:02:10Z", estado_anterior: "ERROR_ENVIO", estado_resultante: "ENVIADO", mensaje: "Enviado a SUNAT (intento 2)" },
        { fecha: "2026-09-02T15:02:40Z", estado_anterior: "ENVIADO", estado_resultante: "ERROR_ENVIO", mensaje: "SUNAT no respondió a tiempo" },
      ],
      cdr: null,
      totales: { gravado: 50, exonerado: 0, inafecto: 0, igv: 9, total: 59 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-error/xml", pdf: "/v1/facturas/f-error/pdf" },
    },
    {
      // Al final del arreglo a propósito: la lista pagina de a 10 y los e2e cuentan con f-error en la primera página.
      // Factura con redondeo del importe total (−0.44, para cobrar sin céntimos): la nota total copia los ítems pero no
      // el redondeo, así que sale 0.44 por encima del total de la factura (3286, sin tolerancia). Antes nadie avisaba.
      id: "f-redondeo",
      tipo: "01",
      serie: "F001",
      numero: 9,
      fecha_emision: "2026-08-28",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [{ codigo: null, descripcion: "Servicio de mantenimiento", unidad: "ZZ", cantidad: 1, precio_unitario: 118.44, tipo_afectacion_igv: "10", valor_venta: 100.37, igv: 18.07, precio_venta: 118.44 }],
      estado_documento: "ACEPTADO",
      hash: "redondeo==",
      nombre_archivo: "20123456786-01-F001-00000009",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-9, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100.37, exonerado: 0, inafecto: 0, igv: 18.07, total: 118, total_precio_venta: 118.44, redondeo: -0.44 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-redondeo/xml" },
    },
    {
      // Factura íntegramente exonerada (libros, afectación 20): la NC por importe debe salir exonerada, no gravada.
      id: "f-exonerada",
      tipo: "01",
      serie: "F001",
      numero: 10,
      fecha_emision: "2026-08-29",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [{ codigo: null, descripcion: "Libro técnico", unidad: "NIU", cantidad: 4, precio_unitario: 50, tipo_afectacion_igv: "20", valor_venta: 200, igv: 0, precio_venta: 200 }],
      estado_documento: "ACEPTADO",
      hash: "exo==",
      nombre_archivo: "20123456786-01-F001-00000010",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-10, ha sido aceptada", observaciones: [] },
      totales: { gravado: 0, exonerado: 200, inafecto: 0, igv: 0, total: 200 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-exonerada/xml" },
    },
    {
      // Solo para el e2e del tope por tributo (nadie más la acredita, así que el tope mostrado es exacto): una línea con ISC de
      // monto fijo, donde el total (200) supera al gravado + IGV (177.50) y el 3503 es el límite real.
      id: "f-isc",
      tipo: "01",
      serie: "F001",
      numero: 11,
      fecha_emision: "2026-08-30",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [{ codigo: null, descripcion: "Cerveza artesanal 330 ml", unidad: "NIU", cantidad: 10, precio_unitario: 20, tipo_afectacion_igv: "10", valor_venta: 146.99, igv: 30.51, precio_venta: 200, isc: { sistema: "02", tasa: 15.31, monto: 22.5, base: 146.99, monto_unitario: 2.25 } }],
      estado_documento: "ACEPTADO",
      hash: "isc==",
      nombre_archivo: "20123456786-01-F001-00000011",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-11, ha sido aceptada", observaciones: [] },
      totales: { gravado: 146.99, exonerado: 0, inafecto: 0, igv: 30.51, total: 200 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-isc/xml" },
    },
    {
      // Exportación con un cargo global sin IGV (50): el total (110) supera la base 9995 (100), así que el tope real de una
      // NC por importe es la base (3503, fila 114), no el total. Solo para ese e2e.
      id: "f-export-cargo",
      tipo: "01",
      serie: "F001",
      numero: 12,
      fecha_emision: "2026-08-31",
      moneda: "USD",
      tipo_operacion: "0200",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio de diseño para el exterior", unidad: "ZZ", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "40", valor_venta: 100, igv: 0, precio_venta: 100 }],
      estado_documento: "ACEPTADO",
      hash: "expcargo==",
      nombre_archivo: "20123456786-01-F001-00000012",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-12, ha sido aceptada", observaciones: [] },
      totales: { gravado: 0, exonerado: 0, inafecto: 0, igv: 0, exportacion: 100, total: 110, total_cargos: 10, cargos: [{ tipo: "MONTO", valor: 10, monto: 10, afecta_base_igv: false, codigo: "50" }] },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-export-cargo/xml" },
    },
  ]);
}

resetDb();
