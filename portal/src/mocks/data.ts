import type { ClienteGuardado } from "@/lib/api/clientes";
import type { ProductoGuardado } from "@/lib/api/productos";
import type { EntregaWebhook, Webhook } from "@/lib/api/webhooks";
import { hoyLima, inicioDelProximoCiclo, sumarDias } from "@/lib/formato";

function base64url(obj: unknown): string {
  return btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fakeJwt(payload: Record<string, unknown>): string {
  return `${base64url({ alg: "none" })}.${base64url(payload)}.firma-de-prueba`;
}

export type Usuario = { id: string; cuenta_id: string; email: string; rol: string; correo_verificado: boolean };
export type Administrador = { id: string; email: string };
/** El id de la cuenta sembrada número `n`: un UUID, porque la página de detalle (#181) descarta todo lo que no lo sea. */
export function idCuentaMock(n: number): string {
  return `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
}

/** Y para los usuarios del detalle de una cuenta (#183): el BFF descarta todo id de usuario que no sea un UUID. */
export function idUsuarioMock(n: number): string {
  return `00000000-0000-4000-a000-${String(n).padStart(12, "0")}`;
}

/** Y para las API keys del detalle de una empresa (#187): el BFF descarta todo id de key que no sea un UUID. */
export function idApiKeyMock(n: number): string {
  return `00000000-0000-4000-b000-${String(n).padStart(12, "0")}`;
}

/** Lo mismo para las empresas del listado del backoffice (#185): el detalle (#186) descarta todo lo que no sea un UUID. */
export function idEmpresaMock(n: number): string {
  return `00000000-0000-4000-9000-${String(n).padStart(12, "0")}`;
}

/** Cuenta del listado del backoffice (#180) con sus empresas; el endpoint devuelve solo el número de empresas. */
export type CuentaAdminMock = {
  id: string;
  nombre: string;
  email: string;
  telefono?: string;
  creada_en: string;
  ultimo_acceso?: string;
  /** Desde cuándo está suspendida (#182); ausente si está activa. */
  suspendida_en?: string;
  /** Desde cuándo está dada de baja (#201); ausente si está en servicio. Las de baja no salen en los listados salvo que se pida con `bajas`. */
  baja_en?: string;
  empresas: Array<{ ruc: string; razon_social: string }>;
  /** Los cambios de estado que hizo el mock (H15), del más reciente al más antiguo, como `historial_estado` del backend. */
  historial_estado?: Array<{ accion: string; actor: "ADMINISTRADOR"; administrador: string; ocurrido_en: string; detalle?: string }>;
};
/**
 * Una empresa del listado del backoffice (#185). El certificado se siembra como días desde hoy (`null`: sin certificado; `"sin_fecha"`:
 * cargado sin vigencia): el mock calcula las fechas al responder, así «por vencer» no caduca con el calendario.
 */
export type EmpresaAdminMock = {
  id: string;
  ruc: string;
  razon_social: string;
  cuenta?: { id: string; nombre: string };
  entorno: "BETA" | "PRODUCCION";
  certificado: number | "sin_fecha" | null;
  tiene_credenciales_sol: boolean;
  series: number;
  comprobantes_del_mes: number;
  /** Días hacia atrás desde hoy; `null`: nunca emitió. */
  ultima_emision_hace: number | null;
  creada_en: string;
};
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
  /** #107: SUNAT rechazó las credenciales SOL; guardar credenciales nuevas lo levanta, como en el backend. */
  credenciales_sol_rechazadas?: { desde: string; motivo: string } | null;
  /** #318: credenciales API SUNAT cargadas (client_id y client_secret); como en el backend, nunca se devuelven. */
  tiene_credenciales_api_sunat?: boolean;
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
    gratuita?: boolean;
    cargos?: Array<{ tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; motivo?: string | null; codigo: string }> | null;
    isc?: { sistema: string; tasa: number; monto: number; base?: number; base_pvp?: number | null; monto_unitario?: number | null } | null;
  }>;
  estado_documento: string;
  hash: string;
  nombre_archivo: string;
  intentos: number;
  ultimo_error: string | null;
  /** `resumen` (#362 B10): solo en una boleta informada por resumen diario; el identificador del RC cuyo CDR es. */
  cdr: { codigo: string; descripcion: string; observaciones: string[]; resumen?: string } | null;
  fecha_vencimiento?: string | null;
  /** Último día en que SUNAT lo recibe a tiempo (`PlazoEnvio`); solo lo trae el sembrado que lo necesita. */
  fecha_limite_envio?: string;
  totales: {
    gravado: number; exonerado: number; inafecto: number; igv: number; total: number; ivap?: number; exportacion?: number; gratuito?: number; igv_gratuitas?: number; total_precio_venta?: number; total_anticipos?: number;
    total_cargos?: number; redondeo?: number; cargos?: Array<{ tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; motivo?: string | null; codigo: string }>;
  };
  forma_pago: { tipo: "contado" | "credito"; monto_pendiente: number | null; cuotas: Array<{ id: string; monto: number; vencimiento: string }> };
  detraccion?: { codigo_bien_servicio: string; descripcion: string; porcentaje: number; monto: number; cuenta_banco_nacion: string; medio_pago: string } | null;
  observaciones?: string | null;
  tipo_cambio?: number | null;
  total_en_soles?: number | null;
  retencion_igv?: { porcentaje: number; monto: number; neto_cobrar: number } | null;
  percepcion?: { regimen: string; descripcion: string; porcentaje: number; base: number; monto: number; total_con_percepcion: number } | null;
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
  fecha_referencia?: string | null;
  motivo: string;
  condicion?: "ALTA" | "BAJA";
  /** #331: un alta informada pasado el 7.º día (SUNAT puede observarla con 4443). El backend la manda siempre; una baja, en false. */
  fuera_de_plazo?: boolean;
  estado: "GENERADA" | "ENVIADA" | "ERROR_ENVIO" | "ACEPTADA" | "RECHAZADA";
  /** #301: la posición en su resumen diario y cuántas líneas tiene (el backend las manda siempre; el mock no agrupa: 1 de 1). */
  linea?: number;
  lineas?: number;
  ticket: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  intentos: number;
  ultimo_error: string | null;
};

type Sesion = { usuario: Usuario };

/** Un plan del backoffice (#190), con la forma del JSON del backend. `historial`: alguna cuenta lo tuvo alguna vez (no se puede borrar aunque hoy nadie lo tenga). */
export type PlanMock = import("@/lib/api/admin-planes").PlanAdmin & { historial?: boolean };

/** El plan de una cuenta en el mock (#191): la suscripción vigente y, si hay, la bajada que espera el ciclo siguiente. Los ids de plan son los de `db.planesAdmin`. */
export type PlanDeCuentaMock = {
  planId: string;
  iniciaEn: string;
  venceEn?: string;
  diasDeGracia: number;
  programado?: { planId: string; aplicaDesde: string; venceEn?: string; diasDeGracia: number };
};

/** Un pago registrado a mano (#194), con la forma del JSON del backend. */
export type PagoMock = import("@/lib/api/admin-pagos").PagoAdmin;

export function idPagoMock(n: number): string {
  return `00000000-0000-4000-b000-${String(n).padStart(12, "0")}`;
}

export function idPlanMock(n: number): string {
  return `00000000-0000-4000-a000-${String(n).padStart(12, "0")}`;
}

/**
 * Catálogo 03 (#308), copia de `catalogos/03.tsv` del backend: las unidades más usadas, con su descripción y en el mismo orden, y los
 * códigos de toda la rec 20, contra los que `Item` valida la unidad (2936). Si cambia el TSV, regenerar ambos.
 */
export const UNIDADES_MAS_USADAS: Array<{ codigo: string; descripcion: string }> = [
  { codigo: "NIU", descripcion: "Unidad (bienes)" }, { codigo: "ZZ", descripcion: "Unidad (servicios)" }, { codigo: "C62", descripcion: "Unidad genérica" }, { codigo: "KGM", descripcion: "Kilogramo" },
  { codigo: "GRM", descripcion: "Gramo" }, { codigo: "MGM", descripcion: "Miligramo" }, { codigo: "TNE", descripcion: "Tonelada métrica" }, { codigo: "LBR", descripcion: "Libra" },
  { codigo: "LTR", descripcion: "Litro" }, { codigo: "MLT", descripcion: "Mililitro" }, { codigo: "GLL", descripcion: "Galón (EE. UU.)" }, { codigo: "MTR", descripcion: "Metro" },
  { codigo: "CMT", descripcion: "Centímetro" }, { codigo: "MMT", descripcion: "Milímetro" }, { codigo: "KMT", descripcion: "Kilómetro" }, { codigo: "INH", descripcion: "Pulgada" },
  { codigo: "FOT", descripcion: "Pie" }, { codigo: "YRD", descripcion: "Yarda" }, { codigo: "MTK", descripcion: "Metro cuadrado" }, { codigo: "CMK", descripcion: "Centímetro cuadrado" },
  { codigo: "MTQ", descripcion: "Metro cúbico" }, { codigo: "CMQ", descripcion: "Centímetro cúbico" }, { codigo: "HUR", descripcion: "Hora" }, { codigo: "DAY", descripcion: "Día" },
  { codigo: "MON", descripcion: "Mes" }, { codigo: "ANN", descripcion: "Año" }, { codigo: "SET", descripcion: "Juego (set)" }, { codigo: "PR", descripcion: "Par" },
  { codigo: "DZN", descripcion: "Docena" }, { codigo: "CEN", descripcion: "Ciento" }, { codigo: "MIL", descripcion: "Millar" }, { codigo: "BX", descripcion: "Caja" },
  { codigo: "CT", descripcion: "Cartón" }, { codigo: "PK", descripcion: "Paquete" }, { codigo: "BG", descripcion: "Bolsa" }, { codigo: "SA", descripcion: "Saco" },
  { codigo: "BO", descripcion: "Botella" }, { codigo: "CA", descripcion: "Lata" }, { codigo: "DR", descripcion: "Tambor" }, { codigo: "BLL", descripcion: "Barril" },
  { codigo: "BJ", descripcion: "Balde" }, { codigo: "BE", descripcion: "Fardo" }, { codigo: "RO", descripcion: "Rollo" }, { codigo: "TU", descripcion: "Tubo" },
  { codigo: "4A", descripcion: "Bobina" }, { codigo: "KWH", descripcion: "Kilovatio hora" }, { codigo: "CS", descripcion: "Caja (case)" }, { codigo: "CY", descripcion: "Cilindro" },
];

export const CODIGOS_UNIDAD: ReadonlySet<string> = new Set(
  [
    "NIU ZZ C62 KGM GRM MGM TNE LBR LTR MLT GLL MTR CMT MMT KMT INH FOT YRD MTK CMK MTQ CMQ HUR DAY",
    "MON ANN SET PR DZN CEN MIL BX CT PK BG SA BO CA DR BLL BJ BE RO TU 4A KWH CS CY",
    "05 06 08 10 11 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 1G 1H 1I 1J 1K 1L",
    "1M 1X 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2G 2H 2I 2J 2K 2L 2M 2N 2P",
    "2Q 2R 2U 2V 2W 2X 2Y 2Z 30 31 32 33 34 35 36 37 38 3B 3C 3E 3G 3H 3I 40",
    "41 43 44 45 46 47 48 4B 4C 4E 4G 4H 4K 4L 4M 4N 4O 4P 4Q 4R 4T 4U 4W 4X",
    "53 54 56 57 58 59 5A 5B 5C 5E 5F 5G 5H 5I 5J 5K 5P 5Q 60 61 62 63 64 66",
    "69 71 72 73 74 76 77 78 80 81 84 85 87 89 90 91 92 93 94 95 96 97 98 A1",
    "A10 A11 A12 A13 A14 A15 A16 A17 A18 A19 A2 A20 A21 A22 A23 A24 A25 A26 A27 A28 A29 A3 A30 A31",
    "A32 A33 A34 A35 A36 A37 A38 A39 A4 A40 A41 A42 A43 A44 A45 A47 A48 A49 A5 A50 A51 A52 A53 A54",
    "A55 A56 A57 A58 A59 A6 A60 A61 A62 A63 A64 A65 A66 A67 A68 A69 A7 A70 A71 A73 A74 A75 A76 A77",
    "A78 A79 A8 A80 A81 A82 A83 A84 A85 A86 A87 A88 A89 A9 A90 A91 A93 A94 A95 A96 A97 A98 A99 AA",
    "AB ACR ACT AD AE AH AI AJ AK AL AM AMH AMP AP APZ AQ AR ARE AS ASM ASU ATM ATT AV",
    "AW AWG AY AZ B0 B1 B10 B11 B12 B13 B14 B15 B16 B17 B18 B19 B2 B20 B21 B22 B23 B24 B25 B26",
    "B27 B28 B29 B3 B30 B31 B32 B33 B34 B35 B36 B37 B38 B39 B4 B40 B41 B42 B43 B44 B45 B46 B47 B48",
    "B49 B5 B50 B51 B52 B53 B54 B55 B56 B57 B58 B59 B6 B60 B61 B62 B63 B64 B65 B66 B67 B68 B69 B7",
    "B70 B71 B72 B73 B74 B75 B76 B77 B78 B79 B8 B80 B81 B82 B83 B84 B85 B86 B87 B88 B89 B9 B90 B91",
    "B92 B93 B94 B95 B96 B97 B98 B99 BAR BB BD BFT BH BHP BIL BK BL BLD BP BPM BQL BR BT BTU",
    "BUA BUI BW BZ C0 C1 C10 C11 C12 C13 C14 C15 C16 C17 C18 C19 C2 C20 C21 C22 C23 C24 C25 C26",
    "C27 C28 C29 C3 C30 C31 C32 C33 C34 C35 C36 C37 C38 C39 C4 C40 C41 C42 C43 C44 C45 C46 C47 C48",
    "C49 C5 C50 C51 C52 C53 C54 C55 C56 C57 C58 C59 C6 C60 C61 C63 C64 C65 C66 C67 C68 C69 C7 C70",
    "C71 C72 C73 C74 C75 C76 C77 C78 C79 C8 C80 C81 C82 C83 C84 C85 C86 C87 C88 C89 C9 C90 C91 C92",
    "C93 C94 C95 C96 C97 C98 C99 CCT CDL CEL CG CGM CH CJ CK CKG CL CLF CLT CNP CNT CO COU CQ",
    "CR CTG CTM CTN CU CUR CV CWA CWI CZ D03 D04 D1 D10 D11 D12 D13 D14 D15 D16 D17 D18 D19 D2",
    "D20 D21 D22 D23 D24 D25 D26 D27 D28 D29 D30 D31 D32 D33 D34 D35 D36 D37 D38 D39 D40 D41 D42 D43",
    "D44 D45 D46 D47 D48 D49 D5 D50 D51 D52 D53 D54 D55 D56 D57 D58 D59 D6 D60 D61 D62 D63 D64 D65",
    "D66 D67 D68 D69 D7 D70 D71 D72 D73 D74 D75 D76 D77 D78 D79 D8 D80 D81 D82 D83 D85 D86 D87 D88",
    "D89 D9 D90 D91 D92 D93 D94 D95 D96 D97 D98 D99 DAA DAD DB DBM DBW DC DD DE DEC DG DI DJ",
    "DLT DMA DMK DMO DMQ DMT DN DPC DPR DPT DQ DRA DRI DRL DRM DS DT DTN DU DWT DX DY DZP E01",
    "E07 E08 E09 E10 E11 E12 E14 E15 E16 E17 E18 E19 E2 E20 E21 E22 E23 E25 E27 E28 E3 E30 E31 E32",
    "E33 E34 E35 E36 E37 E38 E39 E4 E40 E41 E42 E43 E44 E45 E46 E47 E48 E49 E5 E50 E51 E52 E53 E54",
    "E55 E56 E57 E58 E59 E60 E61 E62 E63 E64 E65 E66 E67 E68 E69 E70 E71 E72 E73 E74 E75 E76 E77 E78",
    "E79 E80 E81 E82 E83 E84 E85 E86 E87 E88 E89 E90 E91 E92 E93 E94 E95 E96 E97 E98 E99 EA EB EC",
    "EP EQ EV F01 F02 F03 F04 F05 F06 F07 F08 F1 F10 F11 F12 F13 F14 F15 F16 F17 F18 F19 F20 F21",
    "F22 F23 F24 F25 F26 F27 F28 F29 F30 F31 F32 F33 F34 F35 F36 F37 F38 F39 F40 F41 F42 F43 F44 F45",
    "F46 F47 F48 F49 F50 F51 F52 F53 F54 F55 F56 F57 F58 F59 F60 F61 F62 F63 F64 F65 F66 F67 F68 F69",
    "F70 F71 F72 F73 F74 F75 F76 F77 F78 F79 F80 F81 F82 F83 F84 F85 F86 F87 F88 F89 F9 F90 F91 F92",
    "F93 F94 F95 F96 F97 F98 F99 FAH FAR FB FBM FC FD FE FF FG FH FIT FL FM FNU FP FR FS",
    "FTK FTQ G01 G04 G05 G06 G08 G09 G10 G11 G12 G13 G14 G15 G16 G17 G18 G19 G2 G20 G21 G23 G24 G25",
    "G26 G27 G28 G29 G3 G30 G31 G32 G33 G34 G35 G36 G37 G38 G39 G40 G41 G42 G43 G44 G45 G46 G47 G48",
    "G49 G50 G51 G52 G53 G54 G55 G56 G57 G58 G59 G60 G61 G62 G63 G64 G65 G66 G67 G68 G69 G7 G70 G71",
    "G72 G73 G74 G75 G76 G77 G78 G79 G80 G81 G82 G83 G84 G85 G86 G87 G88 G89 G90 G91 G92 G93 G94 G95",
    "G96 G97 G98 G99 GB GBQ GC GD GDW GE GF GFI GGR GH GIA GIC GII GIP GJ GK GL GLD GLI GM",
    "GN GO GP GQ GRN GRO GRT GT GV GW GWH GY GZ H03 H04 H05 H06 H07 H08 H09 H1 H10 H11 H12",
    "H13 H14 H15 H16 H18 H19 H2 H20 H21 H22 H23 H24 H25 H26 H27 H28 H29 H30 H31 H32 H33 H34 H35 H36",
    "H37 H38 H39 H40 H41 H42 H43 H44 H45 H46 H47 H48 H49 H50 H51 H52 H53 H54 H55 H56 H57 H58 H59 H60",
    "H61 H62 H63 H64 H65 H66 H67 H68 H69 H70 H71 H72 H73 H74 H75 H76 H77 H78 H79 H80 H81 H82 H83 H84",
    "H85 H87 H88 H89 H90 H91 H92 H93 H94 H95 H96 H98 H99 HA HAD HAR HBA HBX HC HD HDW HE HEA HF",
    "HGM HH HI HIU HJ HK HKM HL HLT HM HMO HMQ HMT HN HO HP HPA HS HT HTZ HWE HY IA IC",
    "IE IF II IL IM INK INQ IP ISD IT IU IUG IV J10 J12 J13 J14 J15 J16 J17 J18 J19 J2 J20",
    "J21 J22 J23 J24 J25 J26 J27 J28 J29 J30 J31 J32 J33 J34 J35 J36 J38 J39 J40 J41 J42 J43 J44 J45",
    "J46 J47 J48 J49 J50 J51 J52 J53 J54 J55 J56 J57 J58 J59 J60 J61 J62 J63 J64 J65 J66 J67 J68 J69",
    "J70 J71 J72 J73 J74 J75 J76 J78 J79 J81 J82 J83 J84 J85 J87 J89 J90 J91 J92 J93 J94 J95 J96 J97",
    "J98 J99 JB JE JG JK JM JNT JO JOU JPS JR JWL K1 K10 K11 K12 K13 K14 K15 K16 K17 K18 K19",
    "K2 K20 K21 K22 K23 K24 K25 K26 K27 K28 K3 K30 K31 K32 K33 K34 K35 K36 K37 K38 K39 K40 K41 K42",
    "K43 K45 K46 K47 K48 K49 K5 K50 K51 K52 K53 K54 K55 K58 K59 K6 K60 K61 K62 K63 K64 K65 K66 K67",
    "K68 K69 K70 K71 K73 K74 K75 K76 K77 K78 K79 K80 K81 K82 K83 K84 K85 K86 K87 K88 K89 K90 K91 K92",
    "K93 K94 K95 K96 K97 K98 K99 KA KAT KB KBA KCC KD KDW KEL KF KG KGS KHY KHZ KI KIC KIP KJ",
    "KJO KL KLK KLX KMA KMH KMK KMQ KNI KNM KNS KNT KO KPA KPH KPO KPP KR KS KSD KSH KT KTM KTN",
    "KUR KVA KVR KVT KW KWN KWO KWS KWT KWY KX L10 L11 L12 L13 L14 L15 L16 L17 L18 L19 L2 L20 L21",
    "L23 L24 L25 L26 L27 L28 L29 L30 L31 L32 L33 L34 L35 L36 L37 L38 L39 L40 L41 L42 L43 L44 L45 L46",
    "L47 L48 L49 L50 L51 L52 L53 L54 L55 L56 L57 L58 L59 L60 L61 L62 L63 L64 L65 L66 L67 L68 L69 L70",
    "L71 L72 L73 L74 L75 L76 L77 L78 L79 L80 L81 L82 L83 L84 L85 L86 L87 L88 L89 L90 L91 L92 L93 L94",
    "L95 L96 L98 L99 LA LAC LBT LC LD LE LEF LF LH LI LJ LK LM LN LO LP LPA LR LS LTN",
    "LUB LUM LUX LX LY M0 M1 M10 M11 M12 M13 M14 M15 M16 M17 M18 M19 M20 M21 M22 M23 M24 M25 M26",
    "M27 M29 M30 M31 M32 M33 M34 M35 M36 M37 M38 M39 M4 M40 M41 M42 M43 M44 M45 M46 M47 M48 M49 M5",
    "M50 M51 M52 M53 M55 M56 M57 M58 M59 M60 M61 M62 M63 M64 M65 M66 M67 M68 M69 M7 M70 M71 M72 M73",
    "M74 M75 M76 M77 M78 M79 M80 M81 M82 M83 M84 M85 M86 M87 M88 M89 M9 M90 M91 M92 M93 M94 M95 M96",
    "M97 M98 M99 MA MAH MAL MAM MAR MAW MBE MBF MBR MC MCU MD MF MHZ MIK MIN MIO MIU MK MKD MKM",
    "MKW MLD MMK MMQ MND MNJ MPA MQ MQD MQH MQM MQS MQW MRD MRM MRW MSK MT MTS MTZ MV MVA MWH N1",
    "N10 N11 N12 N13 N14 N15 N16 N17 N18 N19 N2 N20 N21 N22 N23 N24 N25 N26 N27 N28 N29 N3 N30 N31",
    "N32 N33 N34 N35 N36 N37 N38 N39 N40 N41 N42 N43 N44 N45 N46 N47 N48 N49 N50 N51 N52 N53 N54 N55",
    "N56 N57 N58 N59 N60 N61 N62 N63 N64 N65 N66 N67 N68 N69 N70 N71 N72 N73 N74 N75 N76 N77 N78 N79",
    "N80 N81 N82 N83 N84 N85 N86 N87 N88 N89 N90 N91 N92 N93 N94 N95 N96 N97 N98 N99 NA NAR NB NBB",
    "NC NCL ND NE NEW NF NG NH NI NIL NJ NL NM3 NMI NMP NN NPL NPR NPT NQ NR NRL NT NTT",
    "NTU NU NV NX NY OA ODE ODG ODK ODM OHM ON ONZ OP OPM OT OZ OZA OZI P0 P1 P10 P11 P12",
    "P13 P14 P15 P16 P17 P18 P19 P2 P20 P21 P22 P23 P24 P25 P26 P27 P28 P29 P3 P30 P31 P32 P33 P34",
    "P35 P36 P37 P38 P39 P4 P40 P41 P42 P43 P44 P45 P46 P47 P48 P49 P5 P50 P51 P52 P53 P54 P55 P56",
    "P57 P58 P59 P6 P60 P61 P62 P63 P64 P65 P66 P67 P68 P69 P7 P70 P71 P72 P73 P74 P75 P76 P77 P78",
    "P79 P8 P80 P81 P82 P83 P84 P85 P86 P87 P88 P89 P9 P90 P91 P92 P93 P94 P95 P96 P97 P98 P99 PA",
    "PAL PB PD PE PF PFL PG PGL PI PL PLA PM PN PO PQ PS PT PTD PTI PTL PTN PU PV PW",
    "PY PZ Q10 Q11 Q12 Q13 Q14 Q15 Q16 Q17 Q18 Q19 Q20 Q21 Q22 Q23 Q24 Q25 Q26 Q27 Q28 Q29 Q3 Q30",
    "Q31 Q32 Q33 Q34 Q35 Q36 Q37 Q38 Q39 Q40 Q41 Q42 QA QAN QB QD QH QK QR QT QTD QTI QTL QTR",
    "R1 R4 R9 RA RD RG RH RK RL RM RN ROM RP RPM RPS RS RT RU S3 S4 S5 S6 S7 S8",
    "SAN SCO SCR SD SE SEC SG SHT SIE SK SL SM3 SMI SN SO SP SQ SQR SR SS SST ST STC STI",
    "STK STL STN STW SV SW SX SYR T0 T1 T3 T4 T5 T6 T7 T8 TA TAH TAN TC TD TE TF TI",
    "TIC TIP TJ TK TKM TL TMS TN TP TPI TPR TQ TQD TR TRL TS TSD TSH TST TT TTS TV TW TY",
    "U1 U2 UA UB UC UD UE UF UH UM VA VI VLT VP VQ VS W2 W4 WA WB WCD WE WEB WEE",
    "WG WH WHR WI WM WR WSD WTT WW X1 YDK YDQ YL YT Z1 Z11 Z2 Z3 Z4 Z5 Z6 Z8 Z9 ZP",
  ].join(" ").split(" "),
);

export const db = {
  usuariosPorEmail: new Map<string, { usuario: Usuario; password: string }>(),
  empresasPorCuenta: new Map<string, Empresa[]>(),
  seriesPorEmpresa: new Map<string, Serie[]>(),
  establecimientosPorEmpresa: new Map<string, Establecimiento[]>(),
  apiKeysPorEmpresa: new Map<string, ApiKey[]>(),
  /** Los clientes guardados de cada empresa (#319). */
  clientesPorEmpresa: new Map<string, ClienteGuardado[]>(),
  /** Los productos y servicios guardados de cada empresa (#320). */
  productosPorEmpresa: new Map<string, ProductoGuardado[]>(),
  /** Los endpoints de webhooks de cada empresa (#19) y las entregas de cada uno, por id de endpoint. */
  webhooksPorEmpresa: new Map<string, Webhook[]>(),
  entregasPorWebhook: new Map<string, EntregaWebhook[]>(),
  facturasPorEmpresa: new Map<string, Comprobante[]>(),
  bajas: new Map<string, Baja>(),
  correos: [] as Array<{ comprobante: string; email: string; mensaje: string | null }>,
  sesionesPorToken: new Map<string, Sesion>(),
  /** `segundoFactor`: si ya configuró la app de autenticación (#177). El mock no guarda estado del 2FA: ver los handlers. */
  administradoresPorEmail: new Map<string, { administrador: Administrador; password: string; segundoFactor: boolean }>(),
  cuentasAdmin: [] as CuentaAdminMock[],
  /** El nombre de cada cuenta de CLIENTE (C7), por id de cuenta: lo que `GET /v1/cuenta` devuelve como `nombre`. */
  nombresDeCuenta: new Map<string, string>(),
  /** Cuentas de CLIENTE suspendidas (#182), por id de cuenta: su login y su refresh responden 403 `CUENTA_SUSPENDIDA`. */
  cuentasSuspendidas: new Set<string>(),
  /** Las empresas del listado del backoffice (#185); aparte de `cuentasAdmin` para sembrar todos los estados del certificado. */
  empresasAdmin: [] as EmpresaAdminMock[],
  /** Los planes del backoffice (#190). Las specs de la corrida comparten este mock en paralelo: cada una crea y borra los suyos y no toca los sembrados. */
  planesAdmin: [] as PlanMock[],
  /** El plan de cada cuenta del backoffice (#191), por id de cuenta; la que no figura está en el plan por defecto desde que se creó. */
  planesDeCuenta: new Map<string, PlanDeCuentaMock>(),
  /** Los pagos registrados a mano de cada cuenta (#194), por id de cuenta; sin entrada, ninguno. */
  pagosPorCuenta: new Map<string, PagoMock[]>(),
  /** Idempotency-Key de la emisión (#115): `empresa|clave` → huella del pedido y factura emitida. */
  clavesEmision: new Map<string, { huella: string; id: string }>(),
  /** Idempotency-Key de las notas (#355): `empresa|clave` → huella del pedido y nota emitida. Aparte de las de las facturas, como en el backend. */
  clavesNota: new Map<string, { huella: string; id: string }>(),
  /** Idempotency-Key del alta asistida (#219): clave → huella del pedido y respuesta, con la API key. */
  clavesAlta: new Map<string, { huella: string; respuesta: { cuenta_id: string; tenant_id: string; ruc: string; api_key: string; serie: { tipo: string; serie: string }; invitacion_enviada: boolean } }>(),
  /**
   * Enlaces de verificación del correo (#22): token → correo, si ya se usó y cuántos se mandaron (el tope de reenvíos; el mock no los
   * vence). El e2e arma el enlace con `verif-<correo>`.
   */
  verificaciones: new Map<string, { email: string; usado: boolean; enviados: number }>(),
};

export function resetDb() {
  db.usuariosPorEmail.clear();
  db.empresasPorCuenta.clear();
  db.seriesPorEmpresa.clear();
  db.establecimientosPorEmpresa.clear();
  db.apiKeysPorEmpresa.clear();
  db.clientesPorEmpresa.clear();
  db.productosPorEmpresa.clear();
  db.webhooksPorEmpresa.clear();
  db.entregasPorWebhook.clear();
  db.facturasPorEmpresa.clear();
  db.bajas.clear();
  db.correos.length = 0;
  db.sesionesPorToken.clear();
  db.administradoresPorEmail.clear();
  db.clavesEmision.clear();
  db.clavesNota.clear();
  db.clavesAlta.clear();
  db.cuentasSuspendidas.clear();
  db.nombresDeCuenta.clear();
  db.verificaciones.clear();
  db.empresasAdmin = [];
  db.planesAdmin = [];
  db.planesDeCuenta.clear();
  db.pagosPorCuenta.clear();

  const administrador: Administrador = { id: "admin-demo", email: "admin@khipu.pe" };
  db.administradoresPorEmail.set(administrador.email, { administrador, password: "AdminPass1", segundoFactor: true });
  // Un administrador que todavía no configuró el segundo factor: su login pasa por el QR (#177).
  const nuevo: Administrador = { id: "admin-nuevo", email: "nuevo@khipu.pe" };
  db.administradoresPorEmail.set(nuevo.email, { administrador: nuevo, password: "AdminPass1", segundoFactor: false });

  // 12 cuentas (10 por página + 2): Luna y Ana son las más antiguas, así que caen en la segunda página sin filtros.
  // La más reciente nunca inició sesión y no tiene teléfono: el backend omite esos campos.
  db.cuentasAdmin = [
    {
      id: idCuentaMock(1),
      nombre: "Panadería Sol",
      email: "ana@sol.pe",
      telefono: "987654321",
      creada_en: "2026-09-01T15:00:00Z",
      ultimo_acceso: "2026-10-01T14:30:00Z",
      empresas: [{ ruc: "20100047226", razon_social: "PANADERIA SOL SAC" }],
    },
    {
      id: idCuentaMock(2),
      nombre: "Ferretería Luna",
      email: "luis@luna.pe",
      telefono: "912345678",
      creada_en: "2026-09-02T15:00:00Z",
      ultimo_acceso: "2026-09-30T20:00:00Z",
      empresas: [{ ruc: "20100055121", razon_social: "FERRETERIA LUNA SAC" }],
    },
    ...Array.from({ length: 9 }, (_, i): CuentaAdminMock => {
      const n = i + 3;
      const dos = String(n).padStart(2, "0");
      return {
        id: idCuentaMock(n),
        nombre: `Cliente ${dos}`,
        email: `cliente${dos}@demo.pe`,
        telefono: `9000000${dos}`,
        creada_en: `2026-09-${dos}T15:00:00Z`,
        ultimo_acceso: `2026-10-01T1${i}:00:00Z`,
        // «Cliente 06» está suspendida de siembra (nadie la muta); «Cliente 09» es la que suspende y reactiva el e2e de la acción.
        ...(n === 6 ? { suspendida_en: "2026-10-02T15:00:00Z" } : {}),
        empresas: i % 3 === 0 ? [] : [{ ruc: `2010000${dos}00`, razon_social: `CLIENTE ${dos} SAC` }],
      };
    }),
    { id: idCuentaMock(12), nombre: "Cuenta Nueva", email: "nueva@demo.pe", creada_en: "2026-09-20T15:00:00Z", empresas: [] },
    // De baja de siembra (#201), con una empresa: no sale en los listados por defecto (así los 12 de arriba siguen siendo 12) y nadie
    // la muta, porque dar de baja o reponer una cuenta cambia lo que cuentan las demás specs que corren a la vez.
    {
      id: idCuentaMock(13),
      nombre: "Cliente 13",
      email: "cliente13@demo.pe",
      telefono: "900000013",
      creada_en: "2026-09-13T15:00:00Z",
      ultimo_acceso: "2026-09-20T10:00:00Z",
      baja_en: "2026-10-03T09:00:00Z",
      empresas: [{ ruc: "20100001300", razon_social: "CLIENTE 13 SAC" }],
    },
  ];

  // 12 empresas (10 por página + 2), con todos los estados del certificado. «Panadería Sol» y «Ferretería Luna» son las más antiguas: caen en
  // la segunda página sin filtros, y su cuenta es la de las cuentas sembradas de arriba (el enlace lleva a un detalle que existe). Las tres
  // últimas son de integración: no tienen cuenta.
  const inactiva = { certificado: null, tiene_credenciales_sol: false, series: 0, comprobantes_del_mes: 0, ultima_emision_hace: null, entorno: "BETA" as const };
  db.empresasAdmin = [
    { id: idEmpresaMock(1), ruc: "20100047226", razon_social: "PANADERIA SOL SAC", cuenta: { id: idCuentaMock(1), nombre: "Panadería Sol" }, entorno: "BETA", certificado: 10, tiene_credenciales_sol: true, series: 2, comprobantes_del_mes: 14, ultima_emision_hace: 1, creada_en: "2026-09-01T15:00:00Z" },
    { id: idEmpresaMock(2), ruc: "20100055121", razon_social: "FERRETERIA LUNA SAC", cuenta: { id: idCuentaMock(2), nombre: "Ferretería Luna" }, entorno: "PRODUCCION", certificado: -5, tiene_credenciales_sol: true, series: 1, comprobantes_del_mes: 0, ultima_emision_hace: 40, creada_en: "2026-09-02T15:00:00Z" },
    { id: idEmpresaMock(4), ruc: "20100000400", razon_social: "CLIENTE 04 SAC", cuenta: { id: idCuentaMock(4), nombre: "Cliente 04" }, entorno: "PRODUCCION", certificado: 200, tiene_credenciales_sol: true, series: 1, comprobantes_del_mes: 30, ultima_emision_hace: 0, creada_en: "2026-09-04T15:00:00Z" },
    { id: idEmpresaMock(5), ruc: "20100000500", razon_social: "CLIENTE 05 SAC", cuenta: { id: idCuentaMock(5), nombre: "Cliente 05" }, entorno: "BETA", certificado: "sin_fecha", tiene_credenciales_sol: false, series: 1, comprobantes_del_mes: 2, ultima_emision_hace: 3, creada_en: "2026-09-05T15:00:00Z" },
    { id: idEmpresaMock(7), ruc: "20100000700", razon_social: "CLIENTE 07 SAC", cuenta: { id: idCuentaMock(7), nombre: "Cliente 07" }, entorno: "PRODUCCION", certificado: 29, tiene_credenciales_sol: true, series: 1, comprobantes_del_mes: 3, ultima_emision_hace: 2, creada_en: "2026-09-07T15:00:00Z" },
    { id: idEmpresaMock(8), ruc: "20100000800", razon_social: "CLIENTE 08 SAC", cuenta: { id: idCuentaMock(8), nombre: "Cliente 08" }, entorno: "BETA", certificado: 30, tiene_credenciales_sol: true, series: 0, comprobantes_del_mes: 0, ultima_emision_hace: null, creada_en: "2026-09-08T15:00:00Z" },
    { id: idEmpresaMock(10), ruc: "20100001000", razon_social: "CLIENTE 10 SAC", cuenta: { id: idCuentaMock(10), nombre: "Cliente 10" }, entorno: "BETA", certificado: -1, tiene_credenciales_sol: false, series: 1, comprobantes_del_mes: 0, ultima_emision_hace: 90, creada_en: "2026-09-10T15:00:00Z" },
    { id: idEmpresaMock(11), ruc: "20100001100", razon_social: "CLIENTE 11 SAC", cuenta: { id: idCuentaMock(11), nombre: "Cliente 11" }, ...inactiva, creada_en: "2026-09-11T15:00:00Z" },
    { id: idEmpresaMock(13), ruc: "20100001300", razon_social: "CLIENTE 13 SAC", cuenta: { id: idCuentaMock(13), nombre: "Cliente 13" }, ...inactiva, creada_en: "2026-09-13T15:00:00Z" },
    { id: idEmpresaMock(101), ruc: "20100066611", razon_social: "INTEGRADOR SAC", ...inactiva, creada_en: "2026-09-12T15:00:00Z" },
    { id: idEmpresaMock(102), ruc: "20100066620", razon_social: "INTEGRADOR NORTE SAC", ...inactiva, creada_en: "2026-09-13T15:00:00Z" },
    { id: idEmpresaMock(103), ruc: "20100066638", razon_social: "INTEGRADOR SUR SAC", ...inactiva, creada_en: "2026-09-14T15:00:00Z" },
    { id: idEmpresaMock(104), ruc: "20100066646", razon_social: "INTEGRADOR ESTE SAC", ...inactiva, creada_en: "2026-09-15T15:00:00Z" },
  ];

  // Los cuatro planes de la página de precios (#190). Gratis es el de las cuentas nuevas; Negocio trae un cambio de límites ya programado para el ciclo
  // siguiente; Pro nunca tuvo cuentas hoy pero sí las tuvo (historial), así que no se puede borrar aunque figure con 0 cuentas.
  const limites = (docs: number | null, rucs: number, usuarios: number | null, keys: number | null, retencion: number) => ({
    documentos_al_mes: docs === null ? { ilimitado: true } : { maximo: docs, ilimitado: false },
    rucs,
    usuarios: usuarios === null ? { ilimitado: true } : { maximo: usuarios, ilimitado: false },
    api_keys: keys === null ? { ilimitado: true } : { maximo: keys, ilimitado: false },
    retencion_anios: retencion,
  });
  db.planesAdmin = [
    { id: idPlanMock(1), nombre: "Gratis", precio_mensual: 0, limites: limites(30, 1, 1, 1, 1), estado: "ACTIVO", por_defecto: true, visible_en_publicidad: true, cuentas: 12 },
    { id: idPlanMock(2), nombre: "Emprende", precio_mensual: 29, limites: limites(300, 1, 1, 2, 5), estado: "ACTIVO", por_defecto: false, visible_en_publicidad: true, cuentas: 3 },
    {
      id: idPlanMock(3),
      nombre: "Negocio",
      precio_mensual: 69,
      limites: limites(1500, 3, 3, 5, 5),
      limites_programados: { limites: limites(2000, 3, 3, 5, 5), aplica_desde: inicioDelProximoCiclo(new Date()) },
      estado: "ACTIVO",
      por_defecto: false, visible_en_publicidad: true,
      cuentas: 0,
    },
    { id: idPlanMock(4), nombre: "Pro", precio_mensual: 129, limites: limites(null, 10, null, null, 5), estado: "ACTIVO", por_defecto: false, visible_en_publicidad: true, cuentas: 0, historial: true },
  ];

  // Planes de algunas cuentas (#191), con las fechas relativas a hoy para que el estado de pago sea siempre el que dice cada una:
  // «Panadería Sol» está al día en Emprende; «Ferretería Luna» venció hace dos días y está en gracia; la cuenta 3 venció hace tiempo; la 4 está en Negocio
  // con una bajada a Emprende esperando el ciclo siguiente. El resto está en Gratis, sin vencimiento.
  const ahoraMs = Date.now();
  const enDias = (n: number) => new Date(ahoraMs + n * 86_400_000).toISOString();
  db.planesDeCuenta.set(idCuentaMock(1), { planId: idPlanMock(2), iniciaEn: enDias(-20), venceEn: enDias(40), diasDeGracia: 5 });
  db.planesDeCuenta.set(idCuentaMock(2), { planId: idPlanMock(2), iniciaEn: enDias(-60), venceEn: enDias(-2), diasDeGracia: 5 });
  db.planesDeCuenta.set(idCuentaMock(3), { planId: idPlanMock(2), iniciaEn: enDias(-90), venceEn: enDias(-30), diasDeGracia: 3 });
  db.planesDeCuenta.set(idCuentaMock(4), {
    planId: idPlanMock(3),
    iniciaEn: enDias(-10),
    venceEn: enDias(50),
    diasDeGracia: 0,
    programado: { planId: idPlanMock(2), aplicaDesde: inicioDelProximoCiclo(new Date()), venceEn: enDias(80), diasDeGracia: 3 },
  });

  // Pagos de algunas cuentas (#194): «Panadería Sol» tiene dos que movieron el vencimiento; «Ferretería Luna», uno solo anotado; «Cliente 03» tiene doce, para ver que la
  // ficha muestra los diez más recientes y dice cuántos hay. Las demás no tienen ninguno.
  const pago = (n: number, cuenta: string, desde: string, hasta: string, monto: number, medio: PagoMock["medio"], fecha: string, referencia?: string, extendio?: string): PagoMock => ({
    id: idPagoMock(n),
    cuenta_id: cuenta,
    periodo_desde: desde,
    periodo_hasta: hasta,
    monto,
    medio,
    fecha_de_pago: fecha,
    ...(referencia ? { referencia } : {}),
    registrado_en: `${fecha}T15:00:00Z`,
    ...(extendio ? { extendio_hasta: extendio } : {}),
  });
  db.pagosPorCuenta.set(idCuentaMock(1), [
    pago(2, idCuentaMock(1), "2026-09-21", "2026-10-20", 29, "YAPE", "2026-09-18", "YP-4411", "2026-10-21T05:00:00Z"),
    pago(1, idCuentaMock(1), "2026-08-21", "2026-09-20", 29, "TRANSFERENCIA", "2026-08-20", "BCP-90210", "2026-09-21T05:00:00Z"),
  ]);
  db.pagosPorCuenta.set(idCuentaMock(2), [pago(3, idCuentaMock(2), "2026-07-01", "2026-07-31", 29, "EFECTIVO", "2026-07-02")]);
  db.pagosPorCuenta.set(
    idCuentaMock(3),
    Array.from({ length: 12 }, (_, i) => pago(100 + i, idCuentaMock(3), `2025-${String(i + 1).padStart(2, "0")}-01`, `2025-${String(i + 1).padStart(2, "0")}-28`, 29, "TRANSFERENCIA", `2025-${String(i + 1).padStart(2, "0")}-02`, `T-${i + 1}`)),
  );

  const usuario: Usuario = {
    id: "u-demo",
    cuenta_id: "c-demo",
    email: "demo@example.com",
    rol: "ADMIN",
    correo_verificado: true,
  };
  db.usuariosPorEmail.set(usuario.email, { usuario, password: "Passw0rd1" });
  db.nombresDeCuenta.set(usuario.cuenta_id, "Negocio Demo");
  // Un cliente cuya cuenta está suspendida (#182): sus credenciales son correctas, pero el backend no lo deja entrar.
  const suspendido: Usuario = { id: "u-suspendida", cuenta_id: "c-suspendida", email: "suspendida@example.com", rol: "ADMIN", correo_verificado: true };
  db.usuariosPorEmail.set(suspendido.email, { usuario: suspendido, password: "Passw0rd1" });
  db.cuentasSuspendidas.add(suspendido.cuenta_id);
  // Un cliente al que SUNAT le rechazó las credenciales SOL (#107): cuenta propia, para que corregirlas no le cambie el estado a los demás specs.
  const conSolRechazada: Usuario = { id: "u-sol-rechazada", cuenta_id: "c-sol-rechazada", email: "sol-rechazada@example.com", rol: "ADMIN", correo_verificado: true };
  db.usuariosPorEmail.set(conSolRechazada.email, { usuario: conSolRechazada, password: "Passw0rd1" });
  db.nombresDeCuenta.set(conSolRechazada.cuenta_id, "Bodega Rechazada");
  db.empresasPorCuenta.set(conSolRechazada.cuenta_id, [
    {
      id: "e-sol-rechazada",
      ruc: "20600000001",
      razon_social: "Bodega Rechazada SAC",
      entorno: "BETA",
      tiene_certificado: true,
      tiene_credenciales_sol: true,
      certificado_vigencia_hasta: "2036-01-01",
      credenciales_sol_rechazadas: { desde: "2026-10-09T15:00:00Z", motivo: "0102 - Usuario o contrasena incorrectos" },
    },
  ]);
  db.seriesPorEmpresa.set("e-sol-rechazada", [{ tipo: "01", serie: "F001", ultimo_numero: 0, activa: true, establecimiento: "0000" }]);
  // Un cliente que ya usó los 30 documentos de octubre de su plan Gratis (#18): no emite más hasta el mes siguiente. Cuenta propia: el consumo de la demo no cambia.
  const enElTope: Usuario = { id: "u-en-el-tope", cuenta_id: "c-en-el-tope", email: "tope@example.com", rol: "ADMIN", correo_verificado: true };
  db.usuariosPorEmail.set(enElTope.email, { usuario: enElTope, password: "Passw0rd1" });
  db.nombresDeCuenta.set(enElTope.cuenta_id, "Bodega en el Tope");
  db.empresasPorCuenta.set(enElTope.cuenta_id, [
    { id: "e-en-el-tope", ruc: "20600000028", razon_social: "Bodega en el Tope SAC", entorno: "BETA", tiene_certificado: true, tiene_credenciales_sol: true, certificado_vigencia_hasta: "2036-01-01" },
  ]);
  db.seriesPorEmpresa.set("e-en-el-tope", [{ tipo: "01", serie: "F001", ultimo_numero: 30, activa: true, establecimiento: "0000" }]);
  // Un cliente con el plan Emprende vencido hace 10 días y 3 de gracia ya pasados (#285): no emite hasta renovarlo, aunque le sobre tope. Cuenta propia.
  const conPlanVencido: Usuario = { id: "u-plan-vencido", cuenta_id: "c-plan-vencido", email: "vencido@example.com", rol: "ADMIN", correo_verificado: true };
  db.usuariosPorEmail.set(conPlanVencido.email, { usuario: conPlanVencido, password: "Passw0rd1" });
  db.nombresDeCuenta.set(conPlanVencido.cuenta_id, "Bodega Vencida");
  db.empresasPorCuenta.set(conPlanVencido.cuenta_id, [
    { id: "e-plan-vencido", ruc: "20600000036", razon_social: "Bodega Vencida SAC", entorno: "BETA", tiene_certificado: true, tiene_credenciales_sol: true, certificado_vigencia_hasta: "2036-01-01" },
  ]);
  db.seriesPorEmpresa.set("e-plan-vencido", [{ tipo: "01", serie: "F001", ultimo_numero: 4, activa: true, establecimiento: "0000" }]);
  db.planesDeCuenta.set(conPlanVencido.cuenta_id, { planId: idPlanMock(2), iniciaEn: enDias(-40), venceEn: enDias(-10), diasDeGracia: 3 });

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
    // #20: la boleta se emite desde el mismo diálogo, con su propia serie.
    { tipo: "03", serie: "B001", ultimo_numero: 0, activa: true, establecimiento: "0000" },
    // #294: las notas sobre boletas van en series B.
    { tipo: "07", serie: "BC01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
    { tipo: "08", serie: "BD01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
  ]);
  db.establecimientosPorEmpresa.set(empresa.id, [
    { codigo: "0002", nombre: "Tienda Miraflores", domicilio: { ubigeo: "150122", direccion: "Av. Larco 345", urbanizacion: null, distrito: "MIRAFLORES", provincia: "LIMA", departamento: "LIMA", codigo_establecimiento: "0002" }, activo: true },
  ]);
  db.apiKeysPorEmpresa.set(empresa.id, [
    { id: "k-activa", prefijo: "fk_demo001", activa: true, creada_en: "2026-09-01T15:00:00Z" },
    { id: "k-revocada", prefijo: "fk_demo000", activa: false, creada_en: "2026-08-01T15:00:00Z", revocada_en: "2026-08-20T12:00:00Z" },
  ]);
  // #319: tres clientes guardados (una empresa con RUC, un DNI y un pasaporte) para el autocompletado y la lista.
  db.clientesPorEmpresa.set(empresa.id, [
    { id: "c-pacifico", tipo_doc: "6", num_doc: "20601234565", razon_social: "PESQUERA PACÍFICO S.A.C.", direccion: "Av. Argentina 2450, Callao", email: "facturas@pacifico.pe", creado_en: "2026-09-02T15:00:00Z", actualizado_en: "2026-09-02T15:00:00Z" },
    { id: "c-juan", tipo_doc: "1", num_doc: "12345678", razon_social: "JUAN PÉREZ GÓMEZ", creado_en: "2026-09-03T15:00:00Z", actualizado_en: "2026-09-03T15:00:00Z" },
    { id: "c-anna", tipo_doc: "7", num_doc: "AB1234567", razon_social: "ANNA MÜLLER", direccion: "Berlín", pais: "DE", creado_en: "2026-09-04T15:00:00Z", actualizado_en: "2026-09-04T15:00:00Z" },
  ]);
  // #320: tres productos guardados (un servicio gravado, un bien exonerado y una exportación) para el autocompletado de los ítems y la lista.
  db.productosPorEmpresa.set(empresa.id, [
    { id: "p-consultoria", codigo: "SRV-001", descripcion: "Consultoría tributaria", unidad: "ZZ", tipo_afectacion_igv: "10", precio_unitario: 118, codigo_sunat: "80101500", creado_en: "2026-09-05T15:00:00Z", actualizado_en: "2026-09-05T15:00:00Z" },
    { id: "p-libro", codigo: "LIB-010", descripcion: "Libro técnico de contabilidad", unidad: "NIU", tipo_afectacion_igv: "20", precio_unitario: 50, creado_en: "2026-09-06T15:00:00Z", actualizado_en: "2026-09-06T15:00:00Z" },
    { id: "p-exportacion", descripcion: "Licencia de software para el exterior", unidad: "ZZ", tipo_afectacion_igv: "40", precio_unitario: 1200, creado_en: "2026-09-07T15:00:00Z", actualizado_en: "2026-09-07T15:00:00Z" },
  ]);
  // #19: un endpoint activo con entregas en los tres estados y uno que khipu pausó por entregas fallidas seguidas.
  db.webhooksPorEmpresa.set(empresa.id, [
    { id: "w-erp", url: "https://erp.demo.pe/khipu/webhook", descripcion: "ERP producción", eventos: ["comprobante.aceptado", "comprobante.rechazado", "comprobante.aceptado_con_obs", "comprobante.error_envio", "comprobante.fuera_de_plazo", "comprobante.anulado", "comprobante.descartado"], activo: true, fallos_consecutivos: 0, creado_en: "2026-09-10T15:00:00Z" },
    { id: "w-viejo", url: "https://viejo.demo.pe/hook", descripcion: null, eventos: ["comprobante.aceptado"], activo: false, pausado_motivo: "Se apagó solo tras 10 entregas fallidas seguidas; la última: El servidor respondió HTTP 502", fallos_consecutivos: 10, creado_en: "2026-08-15T15:00:00Z" },
    // El de la prueba que lo reactiva: ninguna otra lo toca, porque las specs comparten este mock en paralelo.
    { id: "w-viejo-2", url: "https://viejo2.demo.pe/hook", descripcion: "Sistema anterior", eventos: ["comprobante.aceptado"], activo: false, pausado_motivo: "Se apagó solo tras 10 entregas fallidas seguidas; la última: No se pudo conectar: Connection refused", fallos_consecutivos: 10, creado_en: "2026-08-16T15:00:00Z" },
  ]);
  db.entregasPorWebhook.set("w-erp", [
    { id: "e-1", evento: "comprobante.aceptado", comprobante_id: "f-aceptada", estado: "EXITOSA", intentos: 1, ultimo_http: 200, ultimo_intento_en: "2026-09-12T16:00:05Z", entregada_en: "2026-09-12T16:00:05Z", creada_en: "2026-09-12T16:00:03Z" },
    { id: "e-2", evento: "comprobante.rechazado", comprobante_id: "f-rechazada", estado: "FALLIDA", intentos: 8, ultimo_http: 503, ultimo_error: "El servidor respondió HTTP 503", ultimo_intento_en: "2026-09-13T14:00:00Z", creada_en: "2026-09-12T16:10:00Z" },
    { id: "e-3", evento: "comprobante.aceptado", comprobante_id: "f-aceptada", estado: "PENDIENTE", intentos: 2, siguiente_intento: "2026-09-14T10:15:00Z", ultimo_http: 500, ultimo_error: "El servidor respondió HTTP 500", ultimo_intento_en: "2026-09-14T10:00:00Z", creada_en: "2026-09-14T09:58:00Z" },
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
      // En el backend un ANULADO siempre tiene su RA aceptada; sin esto el mock enseñaba una ficha imposible.
      baja: { id: "b-anulada", identificador: "RA-20260821-1", comprobante: "F001-6", tipo_comprobante: "01", fecha_generacion: "2026-08-21", fecha_referencia: "2026-08-20", motivo: "Error en el RUC del cliente", estado: "ACEPTADA", ticket: "1758100000001", cdr: { codigo: "0", descripcion: "La Comunicacion de baja RA-20260821-1, ha sido aceptada", observaciones: [] }, intentos: 1, ultimo_error: null },
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
      // Reenvío con la red cortada. Fixture propia: el e2e de «reenvía y queda aceptado» deja f-error en ACEPTADO,
      // y con `fullyParallel` el que corriera después no encontraba el botón «Reenviar».
      id: "f-error-red",
      tipo: "01",
      serie: "F001",
      numero: 19,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [
        { codigo: null, descripcion: "Consultoría técnica", unidad: "ZZ", cantidad: 2, precio_unitario: 25, tipo_afectacion_igv: "10" },
      ],
      estado_documento: "ERROR_ENVIO",
      hash: "f-error-red==",
      nombre_archivo: "20123456786-01-F001-00000019",
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
      enlaces: { xml: "/v1/facturas/f-error-red/xml", pdf: "/v1/facturas/f-error-red/pdf" },
    },
    {
      // Reenvío que el backend rechaza. Misma razón que f-error-red: no compartir el estado con otro test.
      id: "f-error-rechazo",
      tipo: "01",
      serie: "F001",
      numero: 20,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [
        { codigo: null, descripcion: "Consultoría técnica", unidad: "ZZ", cantidad: 2, precio_unitario: 25, tipo_afectacion_igv: "10" },
      ],
      estado_documento: "ERROR_ENVIO",
      hash: "f-error-rechazo==",
      nombre_archivo: "20123456786-01-F001-00000020",
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
      enlaces: { xml: "/v1/facturas/f-error-rechazo/xml", pdf: "/v1/facturas/f-error-rechazo/pdf" },
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
      items: [
        { codigo: null, descripcion: "Libro técnico", unidad: "NIU", cantidad: 4, precio_unitario: 50, tipo_afectacion_igv: "20", valor_venta: 200, igv: 0, precio_venta: 200 },
        // Bonificación (11): no se cobra (`precio_venta` 0, valor referencial 30). La recert #10 midió que con una línea así
        // la NC parcial entera quedaba bloqueada; el backend la emite y SUNAT exige justamente valor unitario 0 (2640).
        { codigo: null, descripcion: "Muestra sin costo", unidad: "NIU", cantidad: 3, precio_unitario: 10, tipo_afectacion_igv: "21", valor_venta: 30, igv: 0, precio_venta: 0, gratuita: true },
      ],
      estado_documento: "ACEPTADO",
      hash: "exo==",
      nombre_archivo: "20123456786-01-F001-00000010",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-10, ha sido aceptada", observaciones: [] },
      totales: { gravado: 0, exonerado: 200, inafecto: 0, igv: 0, gratuito: 30, igv_gratuitas: 0, total: 200 },
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
    {
      // Solo para el e2e de la NC 10 «Otros conceptos», que emite por encima del total: exenta del 3286/3503, pero entra en el
      // acumulado, así que gastaría el tope de cualquier factura compartida (la recert #10 lo midió con 2 workers).
      id: "f-otros-conceptos",
      tipo: "01",
      serie: "F001",
      numero: 17,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio de consultoría", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", valor_venta: 100, igv: 18, precio_venta: 118 }],
      estado_documento: "ACEPTADO",
      hash: "otros==",
      nombre_archivo: "20123456786-01-F001-00000017",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-17, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-otros-conceptos/xml" },
    },
    {
      // Solo para el e2e de paridad del 3503 de gratuitas (f117/f118) y del 2062: nadie más la acredita, así que los mensajes
      // del mock son exactamente los del cubo gratuito. Una línea exonerada onerosa + una bonificación exonerada (21).
      id: "f-gratuitas",
      tipo: "01",
      serie: "F001",
      numero: 18,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [
        { codigo: null, descripcion: "Libro técnico", unidad: "NIU", cantidad: 4, precio_unitario: 50, tipo_afectacion_igv: "20", valor_venta: 200, igv: 0, precio_venta: 200 },
        { codigo: null, descripcion: "Muestra sin costo", unidad: "NIU", cantidad: 3, precio_unitario: 10, tipo_afectacion_igv: "21", valor_venta: 30, igv: 0, precio_venta: 0, gratuita: true },
      ],
      estado_documento: "ACEPTADO",
      hash: "grat==",
      nombre_archivo: "20123456786-01-F001-00000018",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-18, ha sido aceptada", observaciones: [] },
      totales: { gravado: 0, exonerado: 200, inafecto: 0, igv: 0, gratuito: 30, igv_gratuitas: 0, total: 200 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-gratuitas/xml" },
    },
    {
      // Facturas de hoy, dentro del plazo de baja (2957), una por e2e del diálogo: corte de red y respuesta HTML.
      id: "f-baja-red",
      tipo: "01",
      serie: "F001",
      numero: 13,
      fecha_emision: hoyLima(),
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", valor_venta: 100, igv: 18, precio_venta: 118 }],
      estado_documento: "ACEPTADO",
      hash: "f-baja-red==",
      nombre_archivo: "20123456786-01-F001-00000013",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-13, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-baja-red/xml" },
    },
    {
      // Facturas de hoy, dentro del plazo de baja (2957), una por e2e del diálogo: SUNAT rechaza.
      id: "f-baja-rechazada",
      tipo: "01",
      serie: "F001",
      numero: 14,
      fecha_emision: hoyLima(),
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", valor_venta: 100, igv: 18, precio_venta: 118 }],
      estado_documento: "ACEPTADO",
      hash: "f-baja-rechazada==",
      nombre_archivo: "20123456786-01-F001-00000014",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-14, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-baja-rechazada/xml" },
    },
    {
      // Facturas de hoy, dentro del plazo de baja (2957), una por e2e del diálogo: SUNAT en proceso y reconsulta desde la ficha.
      id: "f-baja-enviada",
      tipo: "01",
      serie: "F001",
      numero: 15,
      fecha_emision: hoyLima(),
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", valor_venta: 100, igv: 18, precio_venta: 118 }],
      estado_documento: "ACEPTADO",
      hash: "f-baja-enviada==",
      nombre_archivo: "20123456786-01-F001-00000015",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-15, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-baja-enviada/xml" },
    },
    {
      // Facturas de hoy, dentro del plazo de baja (2957), una por e2e del diálogo: doble clic.
      id: "f-baja-doble",
      tipo: "01",
      serie: "F001",
      numero: 16,
      fecha_emision: hoyLima(),
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", valor_venta: 100, igv: 18, precio_venta: 118 }],
      estado_documento: "ACEPTADO",
      hash: "f-baja-doble==",
      nombre_archivo: "20123456786-01-F001-00000016",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-16, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/f-baja-doble/xml" },
    },
    ...[
      // #315: enviados sin respuesta (la conexión se cortó). Uno que SUNAT sí aceptó (la constancia se recupera) y otro que todavía no tiene; fixtures propias para que
      // ningún e2e dependa del estado que deja otro.
      { id: "f-enviada-recuperable", tipo: "01", serie: "F001", numero: 80 },
      { id: "f-enviada-sin-cdr", tipo: "01", serie: "F001", numero: 81 },
      // Una boleta enviada sin respuesta: SUNAT no expone `getStatusCdr` para boletas, así que la ficha no ofrece recuperar la constancia.
      { id: "b-enviada", tipo: "03", serie: "B001", numero: 82 },
    ].map(({ id, tipo, serie, numero }) => ({
      id,
      tipo,
      serie,
      numero,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: null },
      items: [{ codigo: null, descripcion: "Soporte mensual", unidad: "ZZ", cantidad: 1, precio_unitario: 59, tipo_afectacion_igv: "10" }],
      estado_documento: "ENVIADO" as const,
      hash: `hash-${id}`,
      nombre_archivo: `20123456786-${tipo}-${serie}-${String(numero).padStart(8, "0")}`,
      intentos: 1,
      ultimo_error: null,
      cdr: null,
      totales: { gravado: 50, exonerado: 0, inafecto: 0, igv: 9, total: 59 },
      forma_pago: { tipo: "contado" as const, monto_pendiente: null, cuotas: [] },
      enlaces: { xml: `/v1/facturas/${id}/xml`, pdf: `/v1/facturas/${id}/pdf` },
    })),
    {
      // #294: boleta aceptada con DNI, para la NC total sobre boleta y la baja de esa nota por resumen diario (RC). Fecha que
      // ningún e2e del resumen usa (comprobantes-resumen.spec afirma lo exacto sobre el 20 y el 31 de agosto).
      id: "b-aceptada",
      tipo: "03",
      serie: "B001",
      numero: 21,
      fecha_emision: "2026-09-03",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [{ codigo: null, descripcion: "Zapatillas urbanas", unidad: "NIU", cantidad: 2, precio_unitario: 59, tipo_afectacion_igv: "10", valor_venta: 100, igv: 18, precio_venta: 118 }],
      estado_documento: "ACEPTADO",
      hash: "b-aceptada==",
      nombre_archivo: "20123456786-03-B001-00000021",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Boleta numero B001-21, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/b-aceptada/xml", pdf: "/v1/facturas/b-aceptada/pdf", cdr: "/v1/facturas/b-aceptada/cdr" },
    },
    {
      // #294: boleta «sin documento» (hasta S/ 700): la nota toma ese mismo comprador, sin pedir otro.
      id: "b-sin-documento",
      tipo: "03",
      serie: "B001",
      numero: 22,
      fecha_emision: "2026-09-03",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS", direccion: null },
      items: [{ codigo: null, descripcion: "Pan francés", unidad: "NIU", cantidad: 50, precio_unitario: 0.59, tipo_afectacion_igv: "10", valor_venta: 25, igv: 4.5, precio_venta: 29.5 }],
      estado_documento: "ACEPTADO",
      hash: "b-sin-documento==",
      nombre_archivo: "20123456786-03-B001-00000022",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Boleta numero B001-22, ha sido aceptada", observaciones: [] },
      totales: { gravado: 25, exonerado: 0, inafecto: 0, igv: 4.5, total: 29.5 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/b-sin-documento/xml", pdf: "/v1/facturas/b-sin-documento/pdf", cdr: "/v1/facturas/b-sin-documento/cdr" },
    },
    {
      // #331: boleta con el envío fallido y el 7.º día ya pasado. No vence: al reenviarla va en un resumen diario fuera de plazo. El mock simula que SUNAT la
      // observa con la 4443 (lo que la norma prevé y producción puede dar; e-beta la aceptó sin observaciones) para probar cómo la ficha muestra una aceptada con
      // observaciones. Solo para ese e2e (lo deja ACEPTADO_CON_OBS).
      id: "b-fuera-de-plazo",
      tipo: "03",
      serie: "B001",
      numero: 23,
      fecha_emision: "2026-09-03",
      fecha_limite_envio: "2026-09-10",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [{ codigo: null, descripcion: "Zapatillas urbanas", unidad: "NIU", cantidad: 1, precio_unitario: 59, tipo_afectacion_igv: "10", valor_venta: 50, igv: 9, precio_venta: 59 }],
      estado_documento: "ERROR_ENVIO",
      hash: "b-fuera-de-plazo==",
      nombre_archivo: "20123456786-03-B001-00000023",
      intentos: 3,
      ultimo_error: "SUNAT no respondió a tiempo",
      cdr: null,
      totales: { gravado: 50, exonerado: 0, inafecto: 0, igv: 9, total: 59 },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      enlaces: { xml: "/v1/facturas/b-fuera-de-plazo/xml", pdf: "/v1/facturas/b-fuera-de-plazo/pdf" },
    },
    // #356: boletas que el cliente dejó FIRMADAS sin enviar (`enviar_automatico: false`), con la fecha relativa a hoy porque el aviso depende del día: a los 4 días todavía no hay aviso,
    // a los 5 es el último del envío individual, a los 6 ya solo va en el resumen diario.
    ...[4, 5, 6].map((dias, i) => ({
      id: `b-sin-enviar-${dias}`,
      tipo: "03",
      serie: "B777",
      numero: 70 + i,
      fecha_emision: sumarDias(hoyLima(), -dias),
      fecha_limite_envio: sumarDias(hoyLima(), 7 - dias),
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [{ codigo: null, descripcion: "Zapatillas urbanas", unidad: "NIU", cantidad: 1, precio_unitario: 59, tipo_afectacion_igv: "10", valor_venta: 50, igv: 9, precio_venta: 59 }],
      estado_documento: "FIRMADO" as const,
      hash: `b-sin-enviar-${dias}==`,
      nombre_archivo: `20123456786-03-B777-000000${70 + i}`,
      intentos: 0,
      ultimo_error: null,
      cdr: null,
      totales: { gravado: 50, exonerado: 0, inafecto: 0, igv: 9, total: 59 },
      forma_pago: { tipo: "contado" as const, monto_pendiente: null, cuotas: [] },
      enlaces: { xml: `/v1/facturas/b-sin-enviar-${dias}/xml`, pdf: `/v1/facturas/b-sin-enviar-${dias}/pdf` },
    })),
  ]);
}

resetDb();
