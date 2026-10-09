import { hoyLima, inicioDelProximoCiclo } from "@/lib/formato";

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
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  fecha_vencimiento?: string | null;
  totales: {
    gravado: number; exonerado: number; inafecto: number; igv: number; total: number; ivap?: number; exportacion?: number; gratuito?: number; igv_gratuitas?: number; total_precio_venta?: number; total_anticipos?: number;
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
  fecha_referencia?: string | null;
  motivo: string;
  condicion?: "ALTA" | "BAJA";
  estado: "GENERADA" | "ENVIADA" | "ERROR_ENVIO" | "ACEPTADA" | "RECHAZADA";
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
  db.facturasPorEmpresa.clear();
  db.bajas.clear();
  db.correos.length = 0;
  db.sesionesPorToken.clear();
  db.administradoresPorEmail.clear();
  db.clavesEmision.clear();
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
  ]);
}

resetDb();
