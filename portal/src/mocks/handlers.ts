import { http, HttpResponse } from "msw";
import { diasEntre, hoyLima, inicioDelProximoCiclo, sumarDias, ultimoDiaCubierto, venceDesdeFechaDeLima } from "@/lib/formato";
import { esUuid } from "@/lib/uuid";
import { messages } from "@/lib/messages";
import { serieCoincideConTipo, telefonoSchema } from "@/lib/validacion";
import { avisoDePlazo, fechaMinimaDeEmision, PLAZO_ENVIO } from "@/lib/comprobantes/plazo";
import { calcularTotales, esGratuita, redondear } from "@/lib/comprobantes/totales";
import { rechazoPorLineaMock, rechazoPorNrusMock, rechazoPorTopeDeLineasMock, rechazoPorValorDeVentaMock, type LineaMock } from "./reglas-linea";
import { CODIGOS_UNIDAD, UNIDADES_MAS_USADAS, db, fakeJwt, idCuentaMock, idApiKeyMock, idEmpresaMock, idPagoMock, idUsuarioMock, PERSONALIZACION_POR_DEFECTO, resetDb, type Administrador, type Baja, type Comprobante, type Empresa, type Establecimiento, type PersonalizacionPdf, type PagoMock, type PlanDeCuentaMock, type PlanMock, type Usuario } from "./data";

/** Distinto de `claims()`: exige el claim `tipo=plataforma` (ver JwtAdministradorTokenEmisor), así que un token de
 * cliente nunca pasa como administrador en el mock — igual que en el backend real. */
/** Como la bitácora del backend (H15): el cambio de estado queda primero en el historial, con su motivo si lo tiene. */
function anotarCambioDeEstado(cuenta: (typeof db.cuentasAdmin)[number], accion: string, ocurrido_en: string, motivo?: string) {
  cuenta.historial_estado = [
    { accion, actor: "ADMINISTRADOR", administrador: "admin@khipu.pe", ocurrido_en, ...(motivo ? { detalle: `motivo=${motivo}` } : {}) },
    ...(cuenta.historial_estado ?? []),
  ];
}

function claimsAdmin(req: Request): { sub: string } | null {
  const auth = req.headers.get("authorization");
  if (!auth?.startsWith("Bearer ")) return null;
  const token = auth.slice("Bearer ".length);
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    if (payload.tipo !== "plataforma") return null;
    return { sub: payload.sub };
  } catch {
    return null;
  }
}

type UsuarioDeCuentaMock = { id: string; email: string; rol: string; activo: boolean; correo_verificado_en?: string; ultimo_acceso?: string };

/**
 * Los usuarios del detalle de una cuenta (#181/#183), con ids UUID. Todas tienen a su administrador, activo y con el correo verificado; la
 * primera («Panadería Sol») suma tres casos para las acciones de acceso: un colega activo sin verificar (`beto`), uno desactivado (`carla`:
 * no se le manda nada) y uno cuyo correo no sale (`sin-correo`: el servidor no tiene SMTP).
 */
function usuariosDeCuenta(cuenta: { id: string; email: string; ultimo_acceso?: string }): UsuarioDeCuentaMock[] {
  const principal: UsuarioDeCuentaMock = {
    id: idUsuarioMock(Number(cuenta.id.slice(-12))),
    email: cuenta.email,
    rol: "ADMIN",
    activo: true,
    correo_verificado_en: "2026-09-01T15:05:00Z",
    ...(cuenta.ultimo_acceso ? { ultimo_acceso: cuenta.ultimo_acceso } : {}),
  };
  if (cuenta.id !== idCuentaMock(1)) return [principal];
  return [
    principal,
    // Roles del dominio (`Rol`: ADMIN, EMISOR, LECTURA), como el backend (revisión de #229).
    { id: idUsuarioMock(9001), email: "beto@sol.pe", rol: "EMISOR", activo: true },
    { id: idUsuarioMock(9002), email: "carla@sol.pe", rol: "LECTURA", activo: false },
    { id: idUsuarioMock(9003), email: "sin-correo@sol.pe", rol: "EMISOR", activo: true },
  ];
}

/** Como el backend (#201): qué hacer con las cuentas dadas de baja en un listado; por defecto se ocultan y un valor desconocido es 400, no se ignora. */
function visibilidadDeBajas(url: URL): "OCULTAS" | "INCLUIDAS" | "SOLO" | null {
  const v = url.searchParams.get("bajas");
  if (v === null || v === "") return "OCULTAS";
  return v === "OCULTAS" || v === "INCLUIDAS" || v === "SOLO" ? v : null;
}

const estadoDeCuenta = (c: { suspendida_en?: string; baja_en?: string }) => (c.baja_en ? "BAJA" : c.suspendida_en ? "SUSPENDIDA" : "ACTIVA");

/** Como el `JwtFilter` del backend (#22): sin verificar el correo no se escribe. */
function correoVerificado(usuarioId: string) {
  return [...db.usuariosPorEmail.values()].some((r) => r.usuario.id === usuarioId && r.usuario.correo_verificado);
}

/** El desafío del login (#177): solo vale con `tipo=plataforma-desafio`, nunca un token de sesión ni de cliente. */
function delDesafio(desafio: string) {
  try {
    const payload = JSON.parse(atob(desafio.split(".")[1]));
    if (payload.tipo !== "plataforma-desafio") return null;
    return db.administradoresPorEmail.get(payload.email) ?? null;
  } catch {
    return null;
  }
}

function sesionAdmin(administrador: Administrador) {
  const access_token = fakeJwt({ sub: administrador.id, tipo: "plataforma", email: administrador.email, exp: Math.floor(Date.now() / 1000) + 1800 });
  return { access_token, expira_en: 1800, administrador };
}

const SECRETO_TOTP_MOCK = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
const PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
const CODIGOS_RECUPERACION_MOCK = [
  "ABCDE-FGHJK", "LMNPQ-RSTUV", "WXYZ2-34567", "89ABC-DEFGH", "JKLMN-PQRST",
  "UVWXY-Z2345", "6789A-BCDEF", "GHJKL-MNPQR", "STUVW-XYZ23", "45678-9ABCD",
];

// Debe coincidir con la URL que usa el server del portal (client.ts); si no, MSW no intercepta y las peticiones van al backend real.
const BASE = process.env.API_BASE_URL ?? "http://localhost:8001";

/**
 * La cola de errores del mock (#196). Los comprobantes que solo se leen (empresas 4, 7 y 101, y los terminales de las empresas 1 y 2) nunca cambian; los que se reintentan o se
 * descartan tienen su propio comportamiento, uno por prueba: las pruebas corren en paralelo contra esta misma memoria, y ninguna toca lo que cuentan las demás.
 */
type FilaDeErrorMock = {
  comprobante_id: string;
  empresa_id: string;
  ruc: string;
  razon_social: string;
  cuenta_id?: string;
  cuenta_nombre?: string;
  nombre_archivo: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  estado: string;
  clase: "ERROR_DE_ENVIO" | "ERROR_DE_FORMATO" | "FUERA_DE_PLAZO" | "BOLETA_SIN_ENVIAR";
  intentos: number;
  fault?: { codigo?: string; mensaje?: string };
  proximo_intento?: string;
  actualizado_en: string;
  accionable: boolean;
};

/** Qué pasa al reintentar un comprobante: falla otra vez, SUNAT lo acepta, lo rechaza con un fault de formato, se pasó el plazo, o ya lo había resuelto otro administrador. */
type ReintentoMock = "FALLA" | "ACEPTA" | "RECHAZA" | "VENCE" | "OBSOLETO";

const idComprobanteErrorMock = (n: number) => `00000000-0000-4000-d000-${String(n).padStart(12, "0")}`;

function fila(n: number, empresa: number, ruc: string, razon: string, cuenta: { id: string; nombre: string } | null, clase: FilaDeErrorMock["clase"], fecha: string, p: Partial<FilaDeErrorMock> = {}): FilaDeErrorMock {
  const envio = clase === "ERROR_DE_ENVIO";
  return {
    comprobante_id: idComprobanteErrorMock(n),
    empresa_id: idEmpresaMock(empresa),
    ruc,
    razon_social: razon,
    ...(cuenta ? { cuenta_id: cuenta.id, cuenta_nombre: cuenta.nombre } : {}),
    nombre_archivo: `${ruc}-01-F001-${n}`,
    tipo: "01",
    serie: "F001",
    numero: n,
    fecha_emision: fecha,
    estado: envio ? "ERROR_ENVIO" : clase === "ERROR_DE_FORMATO" ? "RECHAZADO" : clase === "BOLETA_SIN_ENVIAR" ? "FIRMADO" : "FUERA_DE_PLAZO",
    clase,
    intentos: envio ? 2 : clase === "BOLETA_SIN_ENVIAR" ? 0 : 1,
    fault: envio ? { codigo: "0109", mensaje: "El sistema no puede responder su solicitud" } : undefined,
    ...(envio ? { proximo_intento: "2026-10-04T18:30:00Z" } : {}),
    actualizado_en: "2026-10-04T15:00:00Z",
    accionable: envio,
    ...p,
  };
}

const PANADERIA = { ruc: "20100047226", razon: "PANADERIA SOL SAC", cuenta: { id: idCuentaMock(1), nombre: "Panadería Sol" } };
const FERRETERIA = { ruc: "20100055121", razon: "FERRETERIA LUNA SAC", cuenta: { id: idCuentaMock(2), nombre: "Ferretería Luna" } };
const CLIENTE_4 = { ruc: "20100000400", razon: "CLIENTE 04 SAC", cuenta: { id: idCuentaMock(4), nombre: "Cliente 04" } };
const CLIENTE_7 = { ruc: "20100000700", razon: "CLIENTE 07 SAC", cuenta: { id: idCuentaMock(7), nombre: "Cliente 07" } };
const INTEGRADOR = { ruc: "20100066611", razon: "INTEGRADOR SAC", cuenta: null };
const BODEGA = { ruc: "20100000800", razon: "BODEGA SAN JUAN SAC", cuenta: { id: idCuentaMock(8), nombre: "Bodega San Juan" } };

function erroresIniciales(): FilaDeErrorMock[] {
  const d = (e: { ruc: string; razon: string; cuenta: { id: string; nombre: string } | null }) => [e.ruc, e.razon, e.cuenta] as const;
  return [
    // Con acciones: una por prueba.
    fila(101, 1, ...d(PANADERIA), "ERROR_DE_ENVIO", "2026-10-01", { intentos: 3 }),
    fila(102, 1, ...d(PANADERIA), "ERROR_DE_ENVIO", "2026-10-01"),
    fila(103, 1, ...d(PANADERIA), "ERROR_DE_ENVIO", "2026-10-01"),
    fila(104, 2, ...d(FERRETERIA), "ERROR_DE_ENVIO", "2026-10-01"),
    fila(105, 1, ...d(PANADERIA), "ERROR_DE_ENVIO", "2026-10-01"),
    fila(106, 2, ...d(FERRETERIA), "ERROR_DE_ENVIO", "2026-10-01"),
    fila(107, 1, ...d(PANADERIA), "ERROR_DE_ENVIO", "2026-10-01"),
    // Solo lectura: la empresa 4 tiene una de cada clase y un fallo propio sin código de SUNAT.
    fila(201, 4, ...d(CLIENTE_4), "ERROR_DE_ENVIO", "2026-10-02", { intentos: 2 }),
    fila(202, 4, ...d(CLIENTE_4), "ERROR_DE_ENVIO", "2026-10-03", { intentos: 5, fault: { mensaje: "INFRA - storage no disponible" }, proximo_intento: undefined }),
    fila(203, 4, ...d(CLIENTE_4), "ERROR_DE_FORMATO", "2026-10-02", { fault: { codigo: "1033", mensaje: "El comprobante fue registrado previamente con otros datos" } }),
    fila(204, 4, ...d(CLIENTE_4), "FUERA_DE_PLAZO", "2026-10-01", { fault: { codigo: "2108", mensaje: "Presentación fuera de fecha: el plazo venció el 2026-10-04" } }),
    // Solo lectura: doce de la empresa 7, para paginar.
    ...Array.from({ length: 12 }, (_, i) => fila(301 + i, 7, ...d(CLIENTE_7), "ERROR_DE_ENVIO", `2026-09-${String(10 + i).padStart(2, "0")}`)),
    // Solo lectura: una empresa de integración (sin cuenta), un formato y un fuera de plazo de las empresas 1 y 2.
    fila(401, 101, ...d(INTEGRADOR), "ERROR_DE_ENVIO", "2026-10-02", { intentos: 1 }),
    fila(501, 2, ...d(FERRETERIA), "ERROR_DE_FORMATO", "2026-10-01", { fault: { codigo: "1001", mensaje: "Serie inválida" } }),
    fila(502, 1, ...d(PANADERIA), "FUERA_DE_PLAZO", "2026-10-01", { fault: { codigo: "2108", mensaje: "Presentación fuera de fecha: el plazo venció el 2026-10-01" } }),
    // #356: una boleta que el cliente dejó FIRMADA sin enviar, ya en el 5.º día: no falló nada (sin fault ni reintento) y no hay nada que reintentar.
    fila(601, 8, ...d(BODEGA), "BOLETA_SIN_ENVIAR", "2026-10-03", { tipo: "03", serie: "B001", nombre_archivo: "20100000800-03-B001-601" }),
  ];
}

const REINTENTOS_MOCK: Record<string, ReintentoMock> = {
  [idComprobanteErrorMock(101)]: "FALLA",
  [idComprobanteErrorMock(102)]: "ACEPTA",
  [idComprobanteErrorMock(103)]: "RECHAZA",
  [idComprobanteErrorMock(104)]: "VENCE",
  [idComprobanteErrorMock(107)]: "OBSOLETO",
};

let erroresMock: FilaDeErrorMock[] = erroresIniciales();

/**
 * Las bajas y resúmenes diarios (#362 H9) de las fichas de integridad, como las arma el backend: de la más reciente a la más antigua, con lo que SUNAT no dio (ticket, CDR,
 * último error) ausente. Solo lectura. La boleta B001-7 (2) tiene su alta fuera de plazo aceptada; la factura F001-3 (3), una baja rechazada por SUNAT; la F001-1 de la
 * empresa 2 (4), una baja que SUNAT sigue procesando; la F001-14 (1) no tiene.
 */
const BAJAS_DE_INTEGRIDAD: Record<number, object[]> = {
  2: [{
    id: "00000000-0000-4000-d000-000000000002", identificador: "RC-20260921-1", condicion: "ALTA", estado: "ACEPTADA", ticket: "1789768174685",
    motivo: "Pasó el 7.º día: se informa en el resumen diario fuera de plazo", fecha_generacion: "2026-09-21", fecha_referencia: "2026-09-10", fuera_de_plazo: true,
    cdr: { codigo: "0", descripcion: "El Resumen diario RC-20260921-1, ha sido aceptado" }, intentos: 0,
  }],
  3: [{
    id: "00000000-0000-4000-d000-000000000003", identificador: "RA-20260912-1", condicion: "BAJA", estado: "RECHAZADA", ticket: "1789768174001", motivo: "Error en el RUC del cliente",
    fecha_generacion: "2026-09-12", fecha_referencia: "2026-09-10", fuera_de_plazo: false, cdr: { codigo: "2323", descripcion: "El comprobante indicado no existe o no está aceptado" }, intentos: 0,
  }],
  4: [{
    id: "00000000-0000-4000-d000-000000000004", identificador: "RA-20260913-1", condicion: "BAJA", estado: "ENVIADA", ticket: "1789768174002", motivo: "Venta duplicada",
    fecha_generacion: "2026-09-13", fecha_referencia: "2026-09-10", fuera_de_plazo: false, intentos: 2, ultimo_error: "SUNAT respondió HTTP 503",
  }],
};

/** Las fichas (#251) de los comprobantes que reporta la verificación de integridad del mock: aceptados, con el archivo que les falta o está dañado. */
const FICHAS_DE_INTEGRIDAD: Record<string, object> = Object.fromEntries(
  (
    [
      [1, 1, PANADERIA, "01", "F001", 14, { tiene_xml: true, tiene_cdr: true }],
      [2, 1, PANADERIA, "03", "B001", 7, { tiene_xml: true, tiene_cdr: false }],
      [3, 2, FERRETERIA, "01", "F001", 3, { tiene_xml: false, tiene_cdr: true }],
      [4, 2, FERRETERIA, "01", "F001", 1, { tiene_xml: true, tiene_cdr: true }],
    ] as const
  ).map(([n, empresa, e, tipo, serie, numero, archivos]) => {
    const id = `00000000-0000-4000-c000-${String(n).padStart(12, "0")}`;
    return [
      id,
      {
        id, empresa_id: idEmpresaMock(empresa), ruc: e.ruc, razon_social: e.razon, cuenta_id: e.cuenta?.id, nombre_archivo: `${e.ruc}-${tipo}-${serie}-${numero}`,
        tipo, serie, numero, fecha_emision: "2026-09-10", estado: "ACEPTADO", intentos: 0,
        respuesta_sunat: { codigo: "0", descripcion: `El comprobante ${serie}-${numero} ha sido aceptado` }, ...archivos, bajas: BAJAS_DE_INTEGRIDAD[n] ?? [],
      },
    ];
  }),
);

/** Sin tildes y en minúsculas: la búsqueda del backend no distingue ni mayúsculas ni tildes. */
const sinTildes = (s: string) => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

/**
 * Los avisos a clientes del mock (#197). Como la cola de errores, las pruebas corren en paralelo contra esta memoria: los avisos que se mandan tienen su propia empresa (una por
 * prueba) y el resto solo se lee. Certificados (hoy + días): empresa 1 por vencer en 10 (se le avisa), 2 vencido hace 5 (se le avisa), 5 por vencer en 20 (otro administrador ya
 * avisó: 409), 4 por vencer en 3 con un aviso de hace 2 días (bloqueada), 7 por vencer en 29, 10 vencido hace 1 (el correo falla: 502) y la de integración 101, vencida hace 2 y sin
 * cuenta. Credenciales SOL: empresa 8 con 5 comprobantes atascados (se le avisa), 4 con 2, 7 con 1 y un aviso de ayer (bloqueada) y la de integración 101 con 3.
 */
type CuentaDeAvisoMock = { id: string; nombre: string; email: string };
type UltimoAvisoMock = { enviado_en: string; destinatario: string };
type FilaDeAvisoMock = {
  empresa_id: string;
  ruc: string;
  razon_social: string;
  cuenta?: CuentaDeAvisoMock;
  ultimo_aviso?: UltimoAvisoMock;
  avisar_desde?: string;
  puede_avisar: boolean;
};
type CertificadoDeAvisoMock = FilaDeAvisoMock & { motivo: "CERTIFICADO_POR_VENCER" | "CERTIFICADO_VENCIDO"; dias_restantes: number };
type SolDeAvisoMock = FilaDeAvisoMock & { comprobantes_afectados: number; ultimo_fallo: string; ultimo_error: string };

const DIA_MS = 86_400_000;
const fechaDeLimaMock = (dias: number) => new Date(Date.now() + dias * DIA_MS).toLocaleDateString("en-CA", { timeZone: "America/Lima" });

const cuentaAvisoMock = (n: number, nombre: string, email: string): CuentaDeAvisoMock => ({ id: idCuentaMock(n), nombre, email });

function empresaAvisoMock(n: number, ruc: string, razon: string, cuenta?: CuentaDeAvisoMock) {
  return { empresa_id: idEmpresaMock(n), ruc, razon_social: razon, ...(cuenta ? { cuenta } : {}), puede_avisar: cuenta !== undefined };
}

function certificadoAvisoMock(n: number, ruc: string, razon: string, dias: number, cuenta?: CuentaDeAvisoMock, p: Partial<CertificadoDeAvisoMock> = {}): CertificadoDeAvisoMock {
  return { ...empresaAvisoMock(n, ruc, razon, cuenta), motivo: dias < 0 ? "CERTIFICADO_VENCIDO" : "CERTIFICADO_POR_VENCER", dias_restantes: dias, ...p };
}

function solAvisoMock(n: number, ruc: string, razon: string, afectados: number, error: string, cuenta?: CuentaDeAvisoMock, p: Partial<SolDeAvisoMock> = {}): SolDeAvisoMock {
  return { ...empresaAvisoMock(n, ruc, razon, cuenta), comprobantes_afectados: afectados, ultimo_fallo: new Date(Date.now() - 600_000).toISOString(), ultimo_error: error, ...p };
}

function avisosIniciales() {
  const ayer = new Date(Date.now() - DIA_MS).toISOString();
  const haceDos = new Date(Date.now() - 2 * DIA_MS).toISOString();
  const panaderia = cuentaAvisoMock(1, "Panadería Sol", "panaderia@sol.pe");
  const ferreteria = cuentaAvisoMock(2, "Ferretería Luna", "ferreteria@luna.pe");
  const c4 = cuentaAvisoMock(4, "Cliente 04", "cliente04@negocio.pe");
  const c5 = cuentaAvisoMock(5, "Cliente 05", "cliente05@negocio.pe");
  const c7 = cuentaAvisoMock(7, "Cliente 07", "cliente07@negocio.pe");
  const c8 = cuentaAvisoMock(8, "Cliente 08", "cliente08@negocio.pe");
  const c10 = cuentaAvisoMock(10, "Cliente 10", "cliente10@negocio.pe");
  return {
    certificados: [
      certificadoAvisoMock(1, "20100047226", "PANADERIA SOL SAC", 10, panaderia),
      certificadoAvisoMock(2, "20100055121", "FERRETERIA LUNA SAC", -5, ferreteria),
      certificadoAvisoMock(5, "20100000500", "CLIENTE 05 SAC", 20, c5),
      certificadoAvisoMock(4, "20100000400", "CLIENTE 04 SAC", 3, c4, {
        ultimo_aviso: { enviado_en: haceDos, destinatario: c4.email },
        avisar_desde: new Date(Date.now() + 5 * DIA_MS).toISOString(),
        puede_avisar: false,
      }),
      certificadoAvisoMock(7, "20100000700", "CLIENTE 07 SAC", 29, c7),
      certificadoAvisoMock(10, "20100001000", "CLIENTE 10 SAC", -1, c10),
      certificadoAvisoMock(101, "20100066611", "INTEGRADOR SAC", -2),
    ],
    sol: [
      solAvisoMock(8, "20100000800", "CLIENTE 08 SAC", 5, "0000 - SUNAT respondió HTTP 401 en 2 intentos (revisar credenciales SOL/URL)", c8),
      solAvisoMock(4, "20100000400", "CLIENTE 04 SAC", 2, "0102 - Usuario o contraseña incorrectos", c4),
      solAvisoMock(7, "20100000700", "CLIENTE 07 SAC", 1, "0104 - La clave ingresada es incorrecta", c7, {
        ultimo_aviso: { enviado_en: ayer, destinatario: c7.email },
        avisar_desde: new Date(Date.now() + 6 * DIA_MS).toISOString(),
        puede_avisar: false,
      }),
      solAvisoMock(101, "20100066611", "INTEGRADOR SAC", 3, "0103 - El usuario ingresado no existe"),
    ],
  };
}

let avisosMock = avisosIniciales();

/**
 * La configuración de la plataforma del mock (#199). Es **global**: el remitente y el aviso son uno solo para todo el mock, así que las pruebas que los cambian van juntas, en una
 * sola prueba o en un bloque en serie; los correos son seis independientes y cada prueba usa el suyo. Sin nada guardado, el remitente es el del servidor y cada correo sale de fábrica.
 * Los textos, variables y mensajes repiten los del dominio (`PlantillaDeCorreo`, `RemitenteDeCorreo`, `BannerDeMantenimiento`).
 */
type TextoMock = { asunto: string; cuerpo: string };
type VariableMock = { nombre: string; descripcion: string; ejemplo: string; indispensable: boolean };
type PlantillaMock = { tipo: string; etiqueta: string; cuando_se_manda: string; defecto: TextoMock; variables: VariableMock[] };

const v = (nombre: string, descripcion: string, ejemplo: string, indispensable = false): VariableMock => ({ nombre, descripcion, ejemplo, indispensable });
const VARIABLES_AVISO_CERTIFICADO = [
  v("razon_social", "La razón social de la empresa.", "PANADERIA SOL SAC"),
  v("ruc", "El RUC de la empresa.", "20100047226"),
  v("fecha", "El día en que vence o venció el certificado (dd/mm/aaaa).", "25/10/2026"),
  v("cuando", "Cuándo vence o venció: «hoy», «mañana», «ayer», «en N días» o «hace N días».", "en 10 días"),
  v("enlace", "El portal, donde el cliente carga su certificado.", "https://app.khipu.pe"),
];

const PLANTILLAS_MOCK: PlantillaMock[] = [
  {
    tipo: "VERIFICACION_CORREO",
    etiqueta: "Verificación de correo",
    cuando_se_manda: "Se manda al registrarse, y cuando alguien pide o un administrador reenvía el enlace para verificar el correo.",
    defecto: { asunto: "Verifica tu correo en khipu", cuerpo: "Para terminar de crear tu cuenta, verifica tu correo abriendo este enlace (válido {validez}, de un solo uso):\n{enlace}" },
    variables: [v("enlace", "El enlace de un solo uso para verificar el correo.", "https://app.khipu.pe/verificar/0a1b2c3d", true), v("validez", "Cuánto dura el enlace.", "24 horas")],
  },
  {
    tipo: "RECUPERACION_CLAVE",
    etiqueta: "Restablecer la contraseña",
    cuando_se_manda: "Se manda cuando alguien olvidó su contraseña, o un administrador le envía el enlace para restablecerla.",
    defecto: { asunto: "Restablecer contraseña", cuerpo: "Para restablecer tu contraseña abre este enlace (válido {validez}):\n{enlace}" },
    variables: [v("enlace", "El enlace de un solo uso para elegir una contraseña nueva.", "https://app.khipu.pe/restablecer/0a1b2c3d", true), v("validez", "Cuánto dura el enlace.", "1 hora")],
  },
  {
    tipo: "BIENVENIDA",
    etiqueta: "Bienvenida de un cliente dado de alta",
    cuando_se_manda: "Se manda a quien un administrador dio de alta, para que cree su contraseña.",
    defecto: { asunto: "Te damos la bienvenida a khipu", cuerpo: "Dimos de alta a {razon_social} (RUC {ruc}) en khipu.\nPara entrar, crea tu contraseña en este enlace (válido {validez}, de un solo uso):\n{enlace}" },
    variables: [
      v("enlace", "El enlace de un solo uso para crear la contraseña.", "https://app.khipu.pe/restablecer/0a1b2c3d?invitacion=1", true),
      v("razon_social", "La razón social de la empresa dada de alta.", "PANADERIA SOL SAC"),
      v("ruc", "El RUC de la empresa.", "20100047226"),
      v("validez", "Cuánto dura el enlace.", "7 días"),
    ],
  },
  {
    tipo: "AVISO_CERTIFICADO_POR_VENCER",
    etiqueta: "Aviso: certificado por vencer",
    cuando_se_manda: "Se manda desde «Avisos» cuando el certificado digital de una empresa vence en menos de 30 días.",
    defecto: {
      asunto: "Tu certificado digital de {razon_social} vence {cuando}",
      cuerpo: "Hola,\n\nEl certificado digital de {razon_social} (RUC {ruc}) vence el {fecha} ({cuando}). Cuando venza, khipu ya no podrá firmar los comprobantes de esta empresa y no podrás emitir.\n\nPara renovarlo, consigue un certificado nuevo y cárgalo en el portal:\n{enlace}\n\nSi ya lo renovaste, ignora este mensaje.\n\nkhipu",
    },
    variables: VARIABLES_AVISO_CERTIFICADO,
  },
  {
    tipo: "AVISO_CERTIFICADO_VENCIDO",
    etiqueta: "Aviso: certificado vencido",
    cuando_se_manda: "Se manda desde «Avisos» cuando el certificado digital de una empresa ya venció.",
    defecto: {
      asunto: "El certificado digital de {razon_social} venció",
      cuerpo: "Hola,\n\nEl certificado digital de {razon_social} (RUC {ruc}) venció el {fecha}. Mientras no cargues uno vigente, khipu no puede firmar los comprobantes de esta empresa y no podrás emitir.\n\nConsigue un certificado nuevo y cárgalo en el portal:\n{enlace}\n\nkhipu",
    },
    variables: VARIABLES_AVISO_CERTIFICADO,
  },
  {
    tipo: "AVISO_CREDENCIALES_SOL",
    etiqueta: "Aviso: credenciales SOL",
    cuando_se_manda: "Se manda desde «Avisos» cuando SUNAT no acepta el usuario o la clave SOL de una empresa.",
    defecto: {
      asunto: "SUNAT no acepta las credenciales SOL de {razon_social}",
      cuerpo: "Hola,\n\nLos envíos de {razon_social} (RUC {ruc}) a SUNAT están fallando porque SUNAT no acepta el usuario o la clave SOL cargados. Los comprobantes quedan pendientes hasta que se corrijan.\n\nRevísalos y vuelve a guardarlos en el portal:\n{enlace}\n\nSi ya las corregiste, ignora este mensaje.\n\nkhipu",
    },
    variables: [v("razon_social", "La razón social de la empresa.", "PANADERIA SOL SAC"), v("ruc", "El RUC de la empresa.", "20100047226"), v("enlace", "El portal, donde el cliente corrige sus credenciales.", "https://app.khipu.pe")],
  },
];

type ConfiguracionMock = {
  remitente?: { nombre?: string; email: string; responder_a?: string; actualizado_en: string };
  plantillas: Record<string, TextoMock & { actualizada_en: string }>;
  banner?: { texto: string; desde: string; hasta: string; actualizado_en: string };
};

const configuracionInicial = (): ConfiguracionMock => ({ plantillas: {} });
let configuracionMock = configuracionInicial();
const REMITENTE_DEL_SERVIDOR = { email: "no-responder@khipu.pe" };
const MARCA_DE_VARIABLE = /\{([a-z_]+)}/g;

function remitenteConfiguradoMock() {
  const r = configuracionMock.remitente;
  const { actualizado_en, ...vigente } = r ?? { ...REMITENTE_DEL_SERVIDOR, actualizado_en: "" };
  return { vigente, personalizado: r !== undefined, ...(r ? { actualizado_en, actualizado_por: "admin@khipu.pe" } : {}), predeterminado: REMITENTE_DEL_SERVIDOR };
}

function plantillaConfiguradaMock(p: PlantillaMock) {
  const guardada = configuracionMock.plantillas[p.tipo];
  const { actualizada_en, ...texto } = guardada ?? { ...p.defecto, actualizada_en: "" };
  return {
    tipo: p.tipo,
    etiqueta: p.etiqueta,
    cuando_se_manda: p.cuando_se_manda,
    vigente: texto,
    defecto: p.defecto,
    personalizada: guardada !== undefined,
    ...(guardada ? { actualizada_en, actualizada_por: "admin@khipu.pe" } : {}),
    variables: p.variables,
  };
}

/** El mismo orden de reglas y los mismos mensajes que `PlantillaDeCorreo.validar`; devuelve el mensaje, o `null` si el texto sirve. */
function mensajeDePlantillaInvalidaMock(p: PlantillaMock, asuntoCrudo: unknown, cuerpoCrudo: unknown): string | null {
  const asunto = typeof asuntoCrudo === "string" ? asuntoCrudo.trim() : "";
  const cuerpo = typeof cuerpoCrudo === "string" ? cuerpoCrudo.replace(/\r\n?/g, "\n").trimEnd() : "";
  if (asunto === "") return "El asunto no puede estar vacío";
  if (asunto.length > 150) return "El asunto admite hasta 150 caracteres";
  if (/[\u0000-\u001f\u007f]/.test(asunto)) return "El asunto va en una sola línea";
  if (cuerpo.trim() === "") return "El cuerpo no puede estar vacío";
  if (cuerpo.length > 5000) return "El cuerpo admite hasta 5000 caracteres";
  const usadas = [...new Set([...asunto.matchAll(MARCA_DE_VARIABLE), ...cuerpo.matchAll(MARCA_DE_VARIABLE)].map((m) => m[1]))];
  const desconocidas = usadas.filter((u) => !p.variables.some((x) => x.nombre === u)).map((u) => `{${u}}`);
  if (desconocidas.length > 0) return `Este correo no tiene la variable ${desconocidas.join(", ")}. Las que admite son: ${p.variables.map((x) => `{${x.nombre}}`).join(", ")}`;
  const falta = p.variables.find((x) => x.indispensable && !cuerpo.includes(`{${x.nombre}}`));
  if (falta) return `El cuerpo tiene que incluir {${falta.nombre}}: sin eso el correo no sirve (${falta.descripcion})`;
  return null;
}

const renderizarConEjemplosMock = (p: PlantillaMock, t: string) => t.replace(MARCA_DE_VARIABLE, (m, nombre: string) => p.variables.find((x) => x.nombre === nombre)?.ejemplo ?? m);

/** Las mismas reglas y mensajes que `BannerDeMantenimiento.de`. */
function mensajeDeBannerInvalidoMock(cuerpo: { texto?: unknown; desde?: unknown; hasta?: unknown }): string | null {
  const texto = typeof cuerpo.texto === "string" ? cuerpo.texto.trim() : "";
  if (texto === "") return "El texto del aviso no puede estar vacío";
  if (texto.length > 300) return "El texto del aviso admite hasta 300 caracteres";
  if (/[\u0000-\u001f\u007f]/.test(texto)) return "El texto del aviso va en una sola línea";
  const desde = typeof cuerpo.desde === "string" ? Date.parse(cuerpo.desde) : NaN;
  const hasta = typeof cuerpo.hasta === "string" ? Date.parse(cuerpo.hasta) : NaN;
  if (Number.isNaN(desde) || Number.isNaN(hasta)) return "Indica desde cuándo y hasta cuándo se muestra";
  if (hasta <= desde) return "El aviso tiene que terminar después de empezar";
  if (hasta <= Date.now()) return "El aviso ya venció: elige un fin que todavía no haya pasado";
  if (hasta - desde > 90 * DIA_MS) return "Un aviso puede durar hasta 90 días";
  return null;
}

const bannerVigenteMock = () => {
  const b = configuracionMock.banner;
  return b && Date.parse(b.desde) <= Date.now() && Date.now() < Date.parse(b.hasta) ? b : null;
};

/** Cuántas veces se leyó el monitor (#195): la cola de envíos crece una por lectura, y así las pruebas ven que el panel se actualizó. */
let lecturasDelMonitorMock = 0;

/** La forma de pago de un pedido, con las reglas de dominio del crédito (RS 193-2020, #309): cuotas que suman el neto pendiente, vencimientos posteriores a la emisión y crecientes. */
type FormaPagoPedido = { tipo: "contado" | "credito"; monto_pendiente?: number; cuotas?: Array<{ monto: number; vencimiento: string }> };
function formaPagoMock(forma: FormaPagoPedido | undefined, emision: string, total: number) {
  if (!forma || forma.tipo === "contado") return { formaPago: { tipo: "contado" as const, monto_pendiente: null, cuotas: [] } };
  const cuotas = forma.cuotas ?? [];
  const pendiente = forma.monto_pendiente ?? 0;
  if (cuotas.length === 0) return { error: fail(422, "FORMA_PAGO_INVALIDA", "3259 - Una factura al crédito lleva al menos una cuota") };
  if (pendiente > total + 0.005) return { error: fail(422, "FORMA_PAGO_INVALIDA", `3319 - El neto pendiente (${pendiente.toFixed(2)}) no puede superar el total (${total.toFixed(2)})`) };
  const suma = Math.round(cuotas.reduce((s, q) => s + Math.round(q.monto * 100), 0)) / 100;
  if (Math.abs(suma - pendiente) > 0.005) return { error: fail(422, "FORMA_PAGO_INVALIDA", `3250 - Las cuotas suman ${suma.toFixed(2)} y el neto pendiente es ${pendiente.toFixed(2)}`) };
  for (const [i, q] of cuotas.entries()) {
    if (q.vencimiento <= emision) return { error: fail(422, "FORMA_PAGO_INVALIDA", `3256 - El vencimiento de la cuota ${i + 1} debe ser posterior a la fecha de emisión`) };
    if (i > 0 && q.vencimiento <= cuotas[i - 1].vencimiento) return { error: fail(422, "FORMA_PAGO_INVALIDA", `3267 - El vencimiento de la cuota ${i + 1} debe ser posterior al de la cuota ${i}`) };
  }
  return { formaPago: { tipo: "credito" as const, monto_pendiente: pendiente, cuotas: cuotas.map((q, i) => ({ id: `Cuota${String(i + 1).padStart(3, "0")}`, monto: q.monto, vencimiento: q.vencimiento })) } };
}

/** Las reglas de dominio de la exportación (#311): afectación 40 en todas las líneas (2642) y fuera de ella ninguna (3107), país del cliente en la factura, país de uso en 0201 y 0208 (3098, 3099) y sin RUC donde lo prohíbe el 2800. */
function rechazoPorExportacionMock(body: { tipo_operacion?: string; items: Array<{ tipo_afectacion_igv: string }>; cliente: { tipo_doc: string; pais?: string }; exportacion?: { pais_uso?: string } }, boleta: boolean) {
  const op = body.tipo_operacion ?? "0101";
  const exporta = /^020[0-8]$/.test(op);
  if (exporta && body.items.some((i) => i.tipo_afectacion_igv !== "40")) return fail(422, "AFECTACION_INVALIDA", `2642 - En una exportación (${op}) todos los ítems llevan tipo_afectacion_igv 40`);
  if (!exporta && body.items.some((i) => i.tipo_afectacion_igv === "40")) return fail(422, "AFECTACION_INVALIDA", `3107 - La afectación 40 (exportación) exige un tipo de operación 0200–0208; recibido ${op}`);
  if (!exporta) return null;
  if (body.cliente.tipo_doc === "6" && (boleta || ["0200", "0201", "0204"].includes(op))) return fail(422, "RECEPTOR_INVALIDO", `2800 - La exportación ${op} no admite un receptor con RUC (tipo_doc 6)`);
  if (!boleta && !body.cliente.pais) return fail(422, "RECEPTOR_INVALIDO", "En una exportación el receptor lleva el país (cliente.pais, código ISO 3166-1 del catálogo 04)");
  if (["0201", "0208"].includes(op) && !body.exportacion?.pais_uso) return fail(422, "EXPORTACION_INVALIDA", `3098 - La exportación de servicios ${op} exige exportacion.pais_uso`);
  return null;
}

/** Las reglas de dominio de la detracción (#288, 3127–3129, 3208): obligatoria con 1001–1004, prohibida fuera de ellas, y el monto en soles (total × % redondeado al sol). */
type DetraccionPedido = { codigo_bien_servicio: string; porcentaje: number; medio_pago?: string; cuenta_banco_nacion?: string; monto?: number };
function detraccionMock(operacion: string | undefined, detraccion: DetraccionPedido | undefined, moneda: string, total: number) {
  const op = operacion ?? "0101";
  const exige = ["1001", "1002", "1003", "1004"].includes(op);
  if (exige && !detraccion) return { error: fail(422, "DETRACCION_INVALIDA", `3127 - El tipo de operación ${op} exige los datos de la detracción (bien/servicio, porcentaje, monto y cuenta)`) };
  if (!exige && detraccion) return { error: fail(422, "DETRACCION_INVALIDA", "3128 - Los datos de la detracción solo aplican a los tipos de operación 1001–1004") };
  if (!detraccion) return {};
  if (op === "1003" && detraccion.codigo_bien_servicio !== "028") return { error: fail(422, "DETRACCION_INVALIDA", "3129 - Con el tipo de operación 1003 el código de bien o servicio debe ser 028") };
  if (!detraccion.cuenta_banco_nacion) return { error: fail(422, "DETRACCION_INVALIDA", "3034 - Falta la cuenta de detracciones del Banco de la Nación") };
  if (moneda !== "PEN" && detraccion.monto == null) return { error: fail(422, "DETRACCION_INVALIDA", `3208 - En facturas en ${moneda} debe indicar el monto de la detracción en soles`) };
  const monto = moneda === "PEN" ? Math.round((total * detraccion.porcentaje) / 100) : (detraccion.monto ?? 0);
  return { detraccion: { codigo_bien_servicio: detraccion.codigo_bien_servicio, descripcion: `Bien o servicio ${detraccion.codigo_bien_servicio}`, porcentaje: detraccion.porcentaje, monto, cuenta_banco_nacion: detraccion.cuenta_banco_nacion, medio_pago: detraccion.medio_pago ?? "001" } };
}

/** Las reglas de dominio de la percepción (#297): régimen 51–53 con su tasa (3071), solo con 2001 (3308), al contado (3330), en soles (2788), base ≤ total (2797) y 2001 sin percepción al contado (3093). */
const REGIMENES_MOCK: Record<string, { tasa: number; descripcion: string }> = {
  "51": { tasa: 2, descripcion: "Percepción venta interna - Tasa 2%" },
  "52": { tasa: 1, descripcion: "Percepción a la adquisición de combustible - Tasa 1%" },
  "53": { tasa: 0.5, descripcion: "Percepción realizada al agente de percepción con tasa especial - Tasa 0.5%" },
};
type PercepcionPedido = { regimen: string; base?: number; monto?: number };
function percepcionMock(operacion: string | undefined, percepcion: PercepcionPedido | undefined, moneda: string, alCredito: boolean, total: number) {
  const op = operacion ?? "0101";
  if (!percepcion) return op === "2001" && !alCredito ? { error: fail(422, "PERCEPCION_INVALIDA", "3093 - Una operación sujeta a percepción (2001) al contado debe informar la percepción") } : {};
  const r = REGIMENES_MOCK[percepcion.regimen];
  if (!r) return { error: fail(422, "PERCEPCION_INVALIDA", "3071 - El régimen de percepción debe ser 51, 52 o 53 (catálogo 53)") };
  if (op !== "2001") return { error: fail(422, "PERCEPCION_INVALIDA", `3308 - Solo se informa percepción con tipo de operación 2001, no ${op}`) };
  if (alCredito) return { error: fail(422, "PERCEPCION_INVALIDA", "3330 - La percepción solo se informa en facturas al contado") };
  if (moneda !== "PEN") return { error: fail(422, "PERCEPCION_INVALIDA", "2788 - La percepción solo se informa en facturas en soles") };
  const base = percepcion.base ?? total;
  if (base <= 0) return { error: fail(422, "PERCEPCION_INVALIDA", "3233 - La base de la percepción debe ser mayor a 0.00") };
  if (base > total + 0.005) return { error: fail(422, "PERCEPCION_INVALIDA", `2797 - La base de la percepción (${base.toFixed(2)}) no puede superar el importe total (${total.toFixed(2)})`) };
  const monto = Math.round(base * r.tasa) / 100;
  return { percepcion: { regimen: percepcion.regimen, descripcion: r.descripcion, porcentaje: r.tasa, base, monto, total_con_percepcion: Math.round((total + monto) * 100) / 100 } };
}

/** Las reglas de dominio de las referencias (#300): guía 4005/4006/2364, documento 4009/4010/2365, orden de compra 4233 (solo factura) y observaciones de hasta 1000. */
type ReferenciasPedido = { orden_compra?: string; guias?: Array<{ tipo: string; numero: string }>; documentos_relacionados?: Array<{ tipo: string; numero: string }>; observaciones?: string };
const NUMERO_GUIA_MOCK = /^(T[A-Z0-9]{3}|[0-9]{4}|EG0[1-4]|EG07|G[0-9]{3}|V[A-Z0-9]{3})-[0-9]{1,8}$/;
function referenciasMock(body: ReferenciasPedido, boleta: boolean): { error?: Response; referencias?: NonNullable<Comprobante["referencias"]> | null; observaciones?: string | null } {
  const invalida = (mensaje: string) => ({ error: fail(422, "DOCUMENTO_RELACIONADO_INVALIDO", mensaje) });
  const oc = body.orden_compra;
  if (oc !== undefined && (oc.length < 1 || oc.length > 20 || /[\u0000-\u001f]/.test(oc))) return invalida("4233 - La orden de compra debe tener de 1 a 20 caracteres, sin saltos de línea ni tabuladores");
  const guias = body.guias ?? [];
  for (const g of guias) {
    if (!["09", "31"].includes(g.tipo)) return invalida("4005 - El tipo de guía relacionada debe ser 09 (remitente) o 31 (transportista)");
    if (!NUMERO_GUIA_MOCK.test(g.numero)) return invalida(`4006 - El número de guía ${g.numero} no tiene el formato serie-número esperado (p. ej. T001-123, EG01-45)`);
  }
  if (new Set(guias.map((g) => `${g.tipo}|${g.numero}`)).size < guias.length) return invalida("2364 - La misma guía de remisión aparece más de una vez");
  const docs = body.documentos_relacionados ?? [];
  for (const d of docs) {
    if (!["04", "05", "06", "07", "08", "09", "99"].includes(d.tipo)) return invalida("4009 - El tipo de documento relacionado debe ser 04–09 o 99 (catálogo 12); los anticipos van en `anticipos`");
    if (d.numero.length < 1 || d.numero.length > 30 || /\s/.test(d.numero)) return invalida("4010 - El número del documento relacionado debe tener de 1 a 30 caracteres sin espacios");
    if (boleta && ["06", "07"].includes(d.tipo)) return invalida(`4009 - El documento relacionado de tipo ${d.tipo} no se admite en una boleta (solo 04, 05, 08, 09 o 99, catálogo 12): SUNAT la observaría; es válido solo en factura`);
  }
  if (new Set(docs.map((d) => `${d.tipo}|${d.numero}`)).size < docs.length) return invalida("2365 - El mismo documento relacionado aparece más de una vez");
  if ((body.observaciones ?? "").length > 1000) return { error: fail(422, "VALIDACION", "observaciones: tamaño debe estar entre 0 y 1000") };
  const hay = oc !== undefined || guias.length > 0 || docs.length > 0;
  return { referencias: hay ? { orden_compra: oc ?? null, guias: guias.length ? guias : null, documentos_relacionados: docs.length ? docs : null } : null, observaciones: body.observaciones ?? null };
}

/**
 * Los anticipos (#312), como `Totales.aplicarAnticipos` y `EmitirComprobanteService.validarFacturaDeAnticipo`: cada uno resta su valor de la base de su afectación (y el IGV
 * sobre la base neta); no puede superar lo pendiente de esta factura ni, en la emisión, lo que el anticipo facturó menos lo ya regularizado. `guardados` solo se pasa al emitir.
 */
type AnticipoPedido = { serie: string; numero: number; monto: number; afectacion?: "gravado" | "exonerado" | "inafecto" };
type TotalesMock = { gravado: number; exonerado: number; inafecto: number; igv: number; total: number };
type ResultadoAnticipos = { error: Response; totales?: undefined; anticipos?: undefined } | { error?: undefined; totales: TotalesMock & { total_anticipos?: number }; anticipos: Comprobante["anticipos"] | null };
function anticiposMock(pedidos: AnticipoPedido[] | undefined, t: TotalesMock, tasaIgv: number, moneda: string, numDocCliente: string, guardados?: Comprobante[]): ResultadoAnticipos {
  const invalido = (mensaje: string): ResultadoAnticipos => ({ error: fail(422, "ANTICIPO_INVALIDO", mensaje) });
  if (!pedidos?.length) return { totales: t, anticipos: null };
  const claves = pedidos.map((a) => `${a.serie}-${a.numero}`);
  if (new Set(claves).size < claves.length) return invalido("3215 - La misma factura de anticipo aparece más de una vez");
  const totales = { ...t, total_anticipos: 0 };
  const aplicados: NonNullable<Comprobante["anticipos"]> = [];
  for (const a of pedidos) {
    const comprobante = `${a.serie}-${a.numero}`;
    const afectacion = a.afectacion ?? "gravado";
    if (!/^[FB][A-Z0-9]{3}$/.test(a.serie)) return invalido("2521 - Indique serie y número del comprobante de anticipo: factura (F###-correlativo) o boleta (B###-correlativo)");
    if (!(a.monto > 0) || Math.round(a.monto * 100) / 100 !== a.monto) return invalido("2503 - El monto del anticipo debe ser positivo con hasta 2 decimales");
    const pendiente = totales[afectacion];
    if (a.monto > pendiente + 0.005) return invalido(`El anticipo ${comprobante} (${a.monto.toFixed(2)}) supera el valor de venta ${afectacion} pendiente de esta factura (${pendiente.toFixed(2)})`);
    if (guardados) {
      const que = a.serie.startsWith("B") ? "La boleta" : "La factura";
      const origen = guardados.find((f) => f.serie === a.serie && f.numero === a.numero && (f.tipo === "01" || f.tipo === "03"));
      if (!origen) return invalido(`3218 - ${que} de anticipo ${comprobante} no existe en esta empresa`);
      if (origen.estado_documento !== "ACEPTADO" && origen.estado_documento !== "ACEPTADO_CON_OBS") return invalido(`3218 - ${que} de anticipo ${comprobante} no está aceptada por SUNAT (estado ${origen.estado_documento})`);
      if (origen.moneda !== moneda) return invalido(`2071 - ${que} de anticipo ${comprobante} es en ${origen.moneda} y este comprobante en ${moneda}`);
      if (origen.receptor?.num_doc !== numDocCliente) return invalido(`${que} de anticipo ${comprobante} fue emitida a otro cliente (documento ${origen.receptor?.num_doc})`);
      const facturado = origen.totales[afectacion];
      // `montoRegularizado`: lo regularizado de todas las afectaciones, sin las finales rechazadas o inválidas.
      const ya = guardados.filter((f) => !["RECHAZADO", "INVALIDO"].includes(f.estado_documento))
        .flatMap((f) => f.anticipos ?? []).filter((x) => x.serie === a.serie && x.numero === a.numero).reduce((s, x) => s + x.monto, 0);
      if (ya + a.monto > facturado + 0.005)
        return invalido(`El anticipo ${comprobante} (${a.monto.toFixed(2)}) supera el valor de venta ${afectacion} de esa factura (${facturado.toFixed(2)})${ya > 0 ? `: ya se regularizaron ${ya.toFixed(2)} en otras facturas` : ""}`);
    }
    const igvDelAnticipo = afectacion === "gravado" ? Math.round(a.monto * tasaIgv) / 100 : 0;
    totales[afectacion] = Math.round((pendiente - a.monto) * 100) / 100;
    if (afectacion === "gravado") totales.igv = Math.round((totales.igv - igvDelAnticipo) * 100) / 100;
    totales.total = Math.round((totales.total - a.monto - igvDelAnticipo) * 100) / 100;
    totales.total_anticipos = Math.round((totales.total_anticipos + a.monto + igvDelAnticipo) * 100) / 100;
    aplicados.push({ comprobante, serie: a.serie, numero: a.numero, monto: a.monto, importe_pagado: Math.round((a.monto + igvDelAnticipo) * 100) / 100, afectacion, codigo_sunat: { gravado: "04", exonerado: "05", inafecto: "06" }[afectacion], fecha_pago: null });
  }
  return { totales, anticipos: aplicados };
}

/** La retención del IGV (#313), como `RetencionIgv`: informativa, solo en factura, porcentaje entre 0 y 100 y monto = total × % a 2 decimales. */
type RetencionPedido = { porcentaje?: number; monto?: number };
function retencionMock(retencion: RetencionPedido | undefined, boleta: boolean, total: number) {
  if (!retencion) return {};
  if (boleta) return { error: fail(422, "RETENCION_INVALIDA", "La retención del IGV la hace un agente de retención con RUC: va en factura, no en boleta") };
  const porcentaje = retencion.porcentaje ?? 3;
  if (!(porcentaje > 0) || porcentaje >= 100) return { error: fail(422, "RETENCION_INVALIDA", "El porcentaje de retención debe ser mayor que 0 y menor que 100 (tasa legal 3 %)") };
  const monto = Math.round((total * porcentaje) / 100 * 100) / 100;
  return { retencion: { porcentaje, monto, neto_cobrar: Math.round((total - monto) * 100) / 100 } };
}

/** La moneda y el tipo de cambio (#316), como `Comprobante.exigirMonedaYTipoCambio`: las 17 del catálogo 02 que admite el dominio (3088) y soles por unidad, solo fuera de PEN. */
const MONEDAS_ADMITIDAS_MOCK = new Set(["ARS", "AUD", "BOB", "BRL", "CAD", "CHF", "CLP", "CNY", "COP", "EUR", "GBP", "JPY", "MXN", "PEN", "PYG", "USD", "UYU"]);
function monedaMock(moneda: string, tipoCambio: number | undefined, total: number) {
  if (!MONEDAS_ADMITIDAS_MOCK.has(moneda)) return { error: fail(422, "MONEDA_INVALIDA", `3088 - Moneda no soportada: ${moneda} (catálogo 02; admitidas: ${[...MONEDAS_ADMITIDAS_MOCK].sort().join(", ")})`) };
  if (tipoCambio === undefined || tipoCambio === null) return { totalEnSoles: null };
  if (moneda === "PEN") return { error: fail(422, "TIPO_CAMBIO_INVALIDO", "Un comprobante en soles no lleva tipo de cambio: tipo_cambio solo va fuera de PEN") };
  const [enteros, decimales = ""] = String(tipoCambio).split(".");
  if (!(tipoCambio > 0) || decimales.length > 3 || enteros.length > 3)
    return { error: fail(422, "TIPO_CAMBIO_INVALIDO", `El tipo de cambio es positivo, de hasta 3 enteros y 3 decimales (soles por ${moneda}); recibido ${tipoCambio}`) };
  return { totalEnSoles: Math.round(total * tipoCambio * 100) / 100 };
}

function ok<T>(datos: T, status = 200) {
  return HttpResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { status });
}

/** `ok` con los avisos de SUNAT (#378): al nivel de `datos`, y ausentes —no `[]`— si no hay nada que avisar, como `ApiResponse.ok(datos, avisos)`. */
function okConAvisos<T>(datos: T, avisos: Array<{ codigo: string; mensaje: string }>, status = 200) {
  return HttpResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null, ...(avisos.length ? { avisos } : {}) }, { status });
}

function fail(status: number, codigo: string, mensaje: string) {
  return HttpResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });
}

/** Un trozo del padrón reducido de SUNAT para la consulta de RUC (#379): razón social, estado y condición tal como los publica SUNAT, y el domicilio si lo trae. */
const padronMock: Record<string, { razon_social: string; estado: string; condicion: string; direccion: string | null }> = {
  "20100070970": { razon_social: "TIENDAS DEL SUR S.A.C.", estado: "ACTIVO", condicion: "HABIDO", direccion: "AV. REPUBLICA DE PANAMA NRO. 2457" },
  "20160109441": { razon_social: "QUISPE MAMANI JOSE", estado: "BAJA DE OFICIO", condicion: "NO HABIDO", direccion: null },
  "20602160280": { razon_social: "COMERCIAL VERDE E.I.R.L.", estado: "ACTIVO", condicion: "NO HABIDO", direccion: null },
};

/**
 * Lo que SUNAT observa del comprador aunque acepte el comprobante, como `ValidarCompradorEnPadron.avisosDe` (#378): 4013 si el RUC no está activo y 4014 solo con la
 * condición «NO HABIDO». Un RUC que el padrón no tiene no avisa nada (el mock no rechaza con 1083: el padrón de ejemplo es un trozo).
 */
function avisosDelComprador(numDoc: string | undefined): Array<{ codigo: string; mensaje: string }> {
  const c = numDoc ? padronMock[numDoc] : undefined;
  if (!c) return [];
  const avisos: Array<{ codigo: string; mensaje: string }> = [];
  if (c.estado !== "ACTIVO") avisos.push({ codigo: "4013", mensaje: `El RUC ${numDoc} del comprador no está activo en SUNAT (${c.estado}): SUNAT aceptará el comprobante con una observación` });
  if (c.condicion === "NO HABIDO") avisos.push({ codigo: "4014", mensaje: `El RUC ${numDoc} del comprador figura como no habido en SUNAT: SUNAT aceptará el comprobante con una observación` });
  return avisos;
}

/**
 * Los contadores de intentos del backend (#261, `JdbcIntentosDeAccesoRepository`): se reserva antes de comprobar, el que llega al
 * tope todavía se concede y bloquea, y un acierto pone el correo en cero. Sin el límite por IP: los e2e corren todos desde la misma.
 */
const intentosMock = new Map<string, { intentos: number; desde: number; hasta?: number }>();

function reservarIntento(clave: string, max: number, ventanaMs: number, bloqueoMs: number = ventanaMs): boolean {
  const ahora = Date.now();
  const fila = intentosMock.get(clave);
  if (fila?.hasta !== undefined && fila.hasta > ahora) return false;
  const nueva = !fila || fila.hasta !== undefined || fila.desde + ventanaMs <= ahora;
  const intentos = nueva ? 1 : fila.intentos + 1;
  intentosMock.set(clave, { intentos, desde: nueva ? ahora : fila.desde, hasta: intentos >= max ? ahora + bloqueoMs : undefined });
  return true;
}

/** 5 contraseñas en 15 minutos bloquean ese correo 15 minutos; `null` si ya está bloqueado. */
function reservarIntentoDeLogin(clave: string): { acerto: () => void } | null {
  if (!reservarIntento(`login:${clave}`, 5, 15 * 60_000)) return null;
  return { acerto: () => intentosMock.delete(`login:${clave}`) };
}

/**
 * El 400 del backend cuando un parámetro de ruta o de consulta no convierte a su tipo (un UUID, un enum): `GlobalExceptionHandler`, ante una
 * `MethodArgumentTypeMismatchException`, nombra el parámetro. Un solo lugar para el código y el mensaje, así ningún handler vuelve a inventar otro.
 */
function parametroInvalido(nombre: string) {
  return fail(400, "PARAMETRO_INVALIDO", `El parámetro '${nombre}' no tiene un formato válido`);
}

/** El 400 del backend cuando el cuerpo no se puede leer, p. ej. un enum con un valor que no existe (`HttpMessageNotReadableException`). */
function jsonInvalido() {
  return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
}

function claims(req: Request): { sub: string; cuenta: string; imp?: string; exp?: number; ue?: string; cx?: string } | null {
  const auth = req.headers.get("authorization");
  if (!auth?.startsWith("Bearer ")) return null;
  const token = auth.slice("Bearer ".length);
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return { sub: payload.sub, cuenta: payload.cuenta, imp: payload.imp, exp: payload.exp, ue: payload.ue, cx: payload.cx };
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

/** Un UUID de verdad: el BFF valida los ids de ruta con `esUuid` antes de llamar al backend, y un plan creado en la sesión también tiene que pasar. */
function nuevoUuid() {
  return crypto.randomUUID();
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
  // #288: bienes y servicios sujetos a detracción y medios de pago, lo que lee el formulario de la factura.
  { id: "54", nombre: "Códigos de bienes y servicios sujetos a detracciones", columnas: ["Código", "Descripción"],
    entradas: [
      { codigo: "004", descripcion: "Recursos hidrobiológicos", extra: {} },
      { codigo: "019", descripcion: "Arrendamiento de bienes muebles", extra: {} },
      { codigo: "022", descripcion: "Otros servicios empresariales", extra: {} },
      { codigo: "027", descripcion: "Servicio de transporte de carga", extra: {} },
      { codigo: "028", descripcion: "Servicio de transporte de pasajeros", extra: {} },
      { codigo: "037", descripcion: "Demás servicios gravados con el IGV", extra: {} },
    ] },
  { id: "59", nombre: "Medios de pago", columnas: ["Código", "Descripción"],
    entradas: [{ codigo: "001", descripcion: "Depósito en cuenta", extra: {} }, { codigo: "002", descripcion: "Giro", extra: {} }] },
  // #308: las más usadas, marcadas como en el backend, y una de las otras 2000 de la rec 20 para que el selector tenga algo que descartar.
  { id: "03", nombre: "Código de tipo de unidad de medida comercial (UN/ECE rec 20 completa)", columnas: ["Código", "Descripción", "Más usada"],
    entradas: [...UNIDADES_MAS_USADAS.map((u) => ({ ...u, extra: { "Más usada": "Sí" } })), { codigo: "E48", descripcion: "service unit", extra: {} }] },
];

/** En blanco para el `@NotBlank` de Bean Validation: lo que `String.trim()` de Java deja vacío (hasta U+0020; el espacio de no separación no). */
function enBlancoParaJava(s: string | null | undefined): boolean {
  return !(s ?? "").replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, "");
}

/**
 * #348-H2: el `@NotBlank` de `ItemDto.unidad` corre antes que el servicio y que el dominio, en la factura y en la nota (`@Valid`), y
 * `GlobalExceptionHandler` junta los campos en un 422 VALIDACION ordenado por nombre (TreeMap).
 */
function erroresDeUnidadEnBlanco(items: Array<{ unidad?: string }> | undefined): Record<string, string[]> {
  const errores: Record<string, string[]> = {};
  (items ?? []).forEach((i, n) => {
    if (enBlancoParaJava(i.unidad)) errores[`items[${n}].unidad`] = ["no debe estar vacío"];
  });
  return errores;
}

function validacionFallida(errores: Record<string, string[]>): Response {
  const ordenados = Object.fromEntries(Object.entries(errores).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
  return HttpResponse.json({ estado: "error", datos: null, mensaje: "Validación fallida", codigo: "VALIDACION", errores: ordenados }, { status: 422 });
}

/** `Item.exigirValidoParaFactura` (#308): la unidad de cada ítem existe en el catálogo 03 (2936), con el mensaje del backend. */
function rechazoPorUnidadMock(items: Array<{ unidad: string }>): Response | null {
  const rara = items.find((i) => !CODIGOS_UNIDAD.has(i.unidad));
  return rara
    ? fail(422, "ITEM_INVALIDO", `2936 - La unidad de medida ${rara.unidad} no existe en el catálogo 03 (UN/ECE rec 20; GET /v1/catalogos/03): NIU unidad (bienes), ZZ unidad (servicios), KGM, HUR…`)
    : null;
}

/**
 * #354, como `Comprobante.crear` e `Item.exigirValidoParaFactura`: el tope de líneas (2023), el ICBPER (2892, 4320), el precio en cero (2369/2367), el NRUS con ICBPER
 * (3240, solo en boleta) y el valor de venta en 0.00 (2370), todos 422 ITEM_INVALIDO antes de numerar. Los ítems llegan con la forma del request.
 */
function rechazoPorLineasMock(items: LineaMock[], documento: "factura" | "boleta", tipoOperacion: string | undefined, tasaIgv: number, fechaEmision: string): Response | null {
  const mensaje = rechazoPorTopeDeLineasMock(items, documento)
    ?? items.map((i) => rechazoPorLineaMock(i)).find((m) => m !== null)
    ?? (documento === "boleta" ? rechazoPorNrusMock(tipoOperacion, items) : null)
    ?? items.map((i) => rechazoPorValorDeVentaMock(i, tasaIgv, fechaEmision)).find((m) => m !== null);
  return mensaje ? fail(422, "ITEM_INVALIDO", mensaje) : null;
}

/**
 * Valida el cuerpo de crear o editar un plan con las reglas del backend (#190): nombre obligatorio, de hasta 40 caracteres y único sin importar mayúsculas;
 * precio de cero o más con hasta dos decimales; límites mayores que cero o ilimitados (nunca omitidos ni las dos cosas a la vez); RUC y retención mayores que cero.
 */
function validarPlanMock(body: unknown, exceptoId?: string): { error: Response } | { nombre: string; precio: number; limites: PlanMock["limites"] } {
  const b = (body ?? {}) as { nombre?: unknown; precio_mensual?: unknown; limites?: Record<string, unknown> };
  const nombre = typeof b.nombre === "string" ? b.nombre.trim() : "";
  if (!nombre) return { error: fail(422, "NOMBRE_REQUERIDO", "El nombre del plan es obligatorio") };
  if (nombre.length > 40) return { error: fail(422, "NOMBRE_INVALIDO", "El nombre del plan admite hasta 40 caracteres") };
  const precio = b.precio_mensual;
  if (typeof precio !== "number" || precio < 0 || Math.round(precio * 100) / 100 !== precio)
    return { error: fail(422, "PRECIO_INVALIDO", "El precio mensual debe ser cero o más, con hasta dos decimales") };
  if (precio > 99_999_999.99) return { error: fail(422, "PRECIO_INVALIDO", "El precio mensual admite hasta 99999999.99") };
  const l = b.limites;
  if (!l) return { error: fail(422, "LIMITE_INVALIDO", "Faltan los límites del plan") };
  const limite = (campo: string): { maximo?: number; ilimitado: boolean } | Response => {
    const v = l[campo] as { maximo?: number; ilimitado?: boolean } | undefined;
    if (!v) return fail(422, "LIMITE_INVALIDO", `Falta el límite «${campo}»`);
    if (v.ilimitado) return v.maximo === undefined ? { ilimitado: true } : fail(422, "LIMITE_INVALIDO", `El límite «${campo}» no puede ser ilimitado y tener un máximo a la vez`);
    if (v.maximo === undefined) return fail(422, "LIMITE_INVALIDO", `Falta el máximo del límite «${campo}»`);
    return Number.isInteger(v.maximo) && v.maximo > 0 ? { maximo: v.maximo, ilimitado: false } : fail(422, "LIMITE_INVALIDO", "Un límite debe ser mayor que cero (o ilimitado)");
  };
  const documentos = limite("documentos_al_mes");
  if (documentos instanceof Response) return { error: documentos };
  const usuarios = limite("usuarios");
  if (usuarios instanceof Response) return { error: usuarios };
  const apiKeys = limite("api_keys");
  if (apiKeys instanceof Response) return { error: apiKeys };
  if (!Number.isInteger(l.rucs) || (l.rucs as number) <= 0) return { error: fail(422, "LIMITE_INVALIDO", "Un plan debe permitir al menos un RUC") };
  if (!Number.isInteger(l.retencion_anios) || (l.retencion_anios as number) <= 0) return { error: fail(422, "RETENCION_INVALIDA", "La retención debe ser de al menos un año") };
  if (db.planesAdmin.some((p) => p.id !== exceptoId && p.nombre.toLowerCase() === nombre.toLowerCase()))
    return { error: fail(409, "NOMBRE_DUPLICADO", `Ya existe un plan llamado «${nombre}»`) };
  return {
    nombre,
    precio,
    limites: { documentos_al_mes: documentos, rucs: l.rucs as number, usuarios, api_keys: apiKeys, retencion_anios: l.retencion_anios as number },
  };
}

function planesOrdenados(): PlanMock[] {
  return [...db.planesAdmin].sort((a, b) => a.precio_mensual - b.precio_mensual || a.nombre.localeCompare(b.nombre));
}

/** Lo que el backend deja ver de un plan: sin lo que solo sabe el mock (`historial`). */
function planVisible(p: PlanMock) {
  const visible: Partial<PlanMock> = { ...p };
  delete visible.historial;
  return visible;
}


/** Lo que el mock entiende como el plan de una cuenta (#191): el guardado, o el plan por defecto desde que la cuenta se creó. */
function planDeCuentaMock(cuentaId: string): PlanDeCuentaMock {
  const guardado = db.planesDeCuenta.get(cuentaId);
  if (guardado) return guardado;
  const cuenta = db.cuentasAdmin.find((c) => c.id === cuentaId);
  const porDefecto = db.planesAdmin.find((p) => p.por_defecto);
  return { planId: porDefecto?.id ?? "", iniciaEn: cuenta?.creada_en ?? new Date().toISOString(), diasDeGracia: 0 };
}

function resumenDePlan(p: PlanMock) {
  return { id: p.id, nombre: p.nombre, precio_mensual: p.precio_mensual, limites: p.limites };
}

/** Vigente hasta el vencimiento, en gracia los días siguientes, vencida después; sin vencimiento siempre vigente (igual que `Suscripcion.estadoEn`). */
function estadoDeSuscripcion(s: PlanDeCuentaMock): "VIGENTE" | "EN_GRACIA" | "VENCIDA" {
  if (!s.venceEn) return "VIGENTE";
  const ahora = Date.now();
  if (ahora < Date.parse(s.venceEn)) return "VIGENTE";
  return ahora < Date.parse(s.venceEn) + s.diasDeGracia * 86_400_000 ? "EN_GRACIA" : "VENCIDA";
}

function vistaDePlanDeCuenta(cuentaId: string) {
  const s = planDeCuentaMock(cuentaId);
  const plan = db.planesAdmin.find((p) => p.id === s.planId);
  if (!plan) throw new Error(`El mock no tiene el plan ${s.planId}`);
  const programado = s.programado ? db.planesAdmin.find((p) => p.id === s.programado!.planId) : undefined;
  return {
    cuenta_id: cuentaId,
    plan: resumenDePlan(plan),
    estado: estadoDeSuscripcion(s),
    inicia_en: s.iniciaEn,
    ...(s.venceEn ? { vence_en: s.venceEn, hasta_cuando_cubre: new Date(Date.parse(s.venceEn) + s.diasDeGracia * 86_400_000).toISOString() } : {}),
    dias_de_gracia: s.diasDeGracia,
    ...(s.programado && programado
      ? { programado: { plan: resumenDePlan(programado), aplica_desde: s.programado.aplicaDesde, ...(s.programado.venceEn ? { vence_en: s.programado.venceEn } : {}), dias_de_gracia: s.programado.diasDeGracia } }
      : {}),
  };
}

/** Cuánto consumió cada cuenta este mes en el mock: «Cliente 7» ya pasó el tope de Emprende, para ver la advertencia. */
function consumoDelMesMock(cuentaId: string): number {
  if (cuentaId === idCuentaMock(1)) return 312;
  if (cuentaId === idCuentaMock(7)) return 400;
  if (cuentaId === "c-en-el-tope") return 30;
  return 20;
}

/** El consumo de una cuenta en un mes en el mock (#193): el del mes en curso es el que ya usa la previsualización; «Cliente 05» lleva 25 de 30 para ver «Cerca del límite»; otro mes, 5. */
function consumoEnMesMock(cuentaId: string, mes: string): number {
  if (mes !== new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }).slice(0, 7)) return 5;
  return cuentaId === idCuentaMock(5) ? 25 : consumoDelMesMock(cuentaId);
}

/**
 * #18: como el backend, la cuenta en el tope de documentos del mes de su plan no emite (429 LIMITE_PLAN) ni gasta número. #285: con el plan vencido (pasada la
 * gracia) tampoco (402 PLAN_VENCIDO), quede o no lugar en el tope. `null` si puede emitir: las empresas sin sesión de cuenta (API key de integración) no se
 * controlan, y los planes sin tope solo por el vencimiento.
 */
function rechazoPorTopeDelPlanMock(request: Request) {
  const c = claims(request);
  if (!c) return null;
  const suscripcion = planDeCuentaMock(c.cuenta);
  const plan = db.planesAdmin.find((p) => p.id === suscripcion.planId);
  if (estadoDeSuscripcion(suscripcion) === "VENCIDA") {
    // Como el backend: el último día servido es el anterior a «hasta cuándo cubre» (exclusivo), en hora de Lima.
    const cubre = Date.parse(suscripcion.venceEn!) + suscripcion.diasDeGracia * 86_400_000;
    const ultimoDia = new Date(cubre - 1).toLocaleDateString("es-PE", { timeZone: "America/Lima", day: "numeric", month: "long" });
    return fail(402, "PLAN_VENCIDO", `Tu plan ${plan?.nombre ?? ""} venció y su gracia terminó el ${ultimoDia}: renuévalo para volver a emitir. Lo ya emitido se puede seguir consultando y dar de baja`);
  }
  const tope = plan ? topeDeHoyMock(plan) : undefined;
  if (tope === undefined || consumoDelMesMock(c.cuenta) < tope) return null;
  return fail(429, "LIMITE_PLAN", `Llegaste al tope de ${tope} documentos del mes de tu plan ${plan!.nombre}. Se renueva el 1 del mes que viene; para emitir antes, pide un cambio de plan`);
}

/** El tope de documentos que manda hoy: el del plan, o el de su cambio programado si ya llegó a su fecha (como el backend). */
function topeDeHoyMock(plan: PlanMock): number | undefined {
  const limites = plan.limites_programados && Date.parse(plan.limites_programados.aplica_desde) <= Date.now() ? plan.limites_programados.limites : plan.limites;
  return limites.documentos_al_mes.ilimitado ? undefined : limites.documentos_al_mes.maximo;
}

const UMBRAL_DE_ALERTA_MOCK = 80;

type FilaDeConsumoMock = {
  cuenta_id: string; nombre: string; email: string; plan_id: string; plan: string; documentos: number; limite?: number; porcentaje?: number; en_alerta: boolean;
  estado_del_plan: "VIGENTE" | "EN_GRACIA" | "VENCIDA"; pagado_hasta?: string; se_sirve_hasta?: string;
};

/** Una fila por cuenta que no está de baja, con el plan de hoy y las mismas reglas que el backend: porcentaje hacia abajo, alerta desde el umbral inclusive, plan vencido = en gracia o vencida. */
function filasDeConsumoMock(mes: string, filtro: string, orden: string): FilaDeConsumoMock[] {
  const filas = db.cuentasAdmin
    .filter((c) => !c.baja_en)
    .map((c): FilaDeConsumoMock => {
      const s = planDeCuentaMock(c.id);
      const plan = db.planesAdmin.find((p) => p.id === s.planId)!;
      const documentos = consumoEnMesMock(c.id, mes);
      const limite = topeDeHoyMock(plan);
      const porcentaje = limite === undefined ? undefined : Math.floor((documentos * 100) / limite);
      return {
        cuenta_id: c.id, nombre: c.nombre, email: c.email, plan_id: plan.id, plan: plan.nombre, documentos,
        ...(limite === undefined ? {} : { limite, porcentaje }),
        en_alerta: porcentaje !== undefined && porcentaje >= UMBRAL_DE_ALERTA_MOCK,
        estado_del_plan: estadoDeSuscripcion(s),
        ...(s.venceEn ? { pagado_hasta: s.venceEn, se_sirve_hasta: new Date(Date.parse(s.venceEn) + s.diasDeGracia * 86_400_000).toISOString() } : {}),
      };
    })
    .filter((f) => (filtro === "CERCA_DEL_LIMITE" ? f.en_alerta : filtro === "PLAN_VENCIDO" ? f.estado_del_plan !== "VIGENTE" : true));
  const desempate = (a: FilaDeConsumoMock, b: FilaDeConsumoMock) => a.nombre.localeCompare(b.nombre) || a.cuenta_id.localeCompare(b.cuenta_id);
  return filas.sort((a, b) =>
    orden === "DOCUMENTOS"
      ? b.documentos - a.documentos || desempate(a, b)
      : Number(a.porcentaje === undefined) - Number(b.porcentaje === undefined) || (b.porcentaje ?? 0) - (a.porcentaje ?? 0) || b.documentos - a.documentos || desempate(a, b),
  );
}

/** El texto de un cliente no se ejecuta como fórmula en una hoja de cálculo (comilla delante) y, si trae coma, comillas o saltos, va entre comillas. */
function celdaCsvMock(valor: string): string {
  const t = valor && "=+-@\t\r".includes(valor[0]) ? `'${valor}` : valor;
  return /[",\r\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
}

/** Como el backend: BOM UTF-8, registros con CRLF (también el último), `ilimitado` sin tope y celdas vacías sin fecha. */
function csvDeConsumoMock(filas: FilaDeConsumoMock[]): string {
  const cabecera = "cuenta_id,cuenta,correo,plan,documentos,limite,porcentaje,en_alerta,estado_del_plan,pagado_hasta,se_sirve_hasta";
  const lineas = filas.map((f) =>
    [f.cuenta_id, celdaCsvMock(f.nombre), celdaCsvMock(f.email), celdaCsvMock(f.plan), f.documentos, f.limite ?? "ilimitado", f.porcentaje ?? "", f.en_alerta ? "si" : "no", f.estado_del_plan, f.pagado_hasta ? ultimoDiaCubierto(f.pagado_hasta) : "", f.se_sirve_hasta ? ultimoDiaCubierto(f.se_sirve_hasta) : ""].join(","),
  );
  return "\uFEFF" + [cabecera, ...lineas].join("\r\n") + "\r\n";
}

/** Los parámetros de las dos rutas de consumo: un mes, filtro u orden mal escritos son 400 (no se ignoran), igual que el backend. */
function parametrosDeConsumoMock(url: URL): { mes: string; filtro: string; orden: string } | null {
  const mes = url.searchParams.get("mes") ?? new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }).slice(0, 7);
  const filtro = url.searchParams.get("filtro") ?? "TODAS";
  const orden = url.searchParams.get("orden") ?? "PORCENTAJE";
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(mes) || !["TODAS", "CERCA_DEL_LIMITE", "PLAN_VENCIDO"].includes(filtro) || !["PORCENTAJE", "DOCUMENTOS"].includes(orden)) return null;
  return { mes, filtro, orden };
}

const MEDIOS_DE_PAGO_MOCK = ["TRANSFERENCIA", "DEPOSITO", "YAPE", "PLIN", "TARJETA", "EFECTIVO", "OTRO"];

/** `YYYY-MM-DD` de un día que existe, como el backend lo lee (`2026-02-30` no). */
function fechaIsoValidaMock(texto: unknown): texto is string {
  if (typeof texto !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(texto)) return false;
  const [a, m, d] = texto.split("-").map(Number);
  return new Date(Date.UTC(a, m - 1, d)).getUTCMonth() === m - 1;
}

/** El último día que admite un periodo que empieza en `desde`: un año después menos un día (el 29 de febrero se ajusta al último día del mes). */
function ultimoDiaDeUnPeriodoMock(desde: string): string {
  const [a, m, d] = desde.split("-").map(Number);
  const f = new Date(Date.UTC(a + 1, m - 1, d));
  if (f.getUTCMonth() !== m - 1) f.setTime(Date.UTC(a + 1, m, 0));
  f.setUTCDate(f.getUTCDate() - 1);
  return f.toISOString().slice(0, 10);
}

/**
 * Las mismas reglas que el backend al registrar un pago (#194), en su mismo orden: el periodo, el monto, el medio y las fechas son datos (422); luego la fecha futura; luego
 * la extensión del vencimiento (409 si el plan no vence o el pago no lo adelanta); luego el pago repetido (409). Con `extender_vencimiento` el vencimiento pasa a la
 * medianoche de Lima del día siguiente al fin del periodo. Devuelve el pago guardado o el error.
 */
function registrarPagoMock(cuentaId: string, c: Record<string, unknown>): { pago: PagoMock } | { error: ReturnType<typeof fail> } {
  const desde = c.periodo_desde;
  const hasta = c.periodo_hasta;
  if (!fechaIsoValidaMock(desde) || !fechaIsoValidaMock(hasta)) return { error: fail(422, "PERIODO_INVALIDO", "El pago necesita el periodo que cubre: desde y hasta") };
  if (hasta < desde) return { error: fail(422, "PERIODO_INVALIDO", "El periodo no puede terminar antes de empezar") };
  if (hasta > ultimoDiaDeUnPeriodoMock(desde)) return { error: fail(422, "PERIODO_INVALIDO", "El periodo no puede pasar de un año") };
  const monto = c.monto;
  if (typeof monto !== "number" || !(monto > 0) || Math.round(monto * 100) / 100 !== monto || monto > 9_999_999.99) return { error: fail(422, "MONTO_INVALIDO", "El monto debe ser mayor que cero, con hasta dos decimales") };
  // Como el backend: sin medio lo rechaza el dominio (422); un medio que no existe ni siquiera se convierte del JSON (400).
  if (c.medio === undefined || c.medio === null) return { error: fail(422, "MEDIO_INVALIDO", "El pago necesita el medio por el que se hizo") };
  if (typeof c.medio !== "string" || !MEDIOS_DE_PAGO_MOCK.includes(c.medio)) return { error: jsonInvalido() };
  if (!fechaIsoValidaMock(c.fecha_de_pago)) return { error: fail(422, "FECHA_DE_PAGO_INVALIDA", "El pago necesita la fecha en que se hizo") };
  const referencia = typeof c.referencia === "string" && c.referencia.trim() ? c.referencia.trim() : undefined;
  const nota = typeof c.nota === "string" && c.nota.trim() ? c.nota.trim() : undefined;
  if (referencia && referencia.length > 100) return { error: fail(422, "REFERENCIA_INVALIDA", "La referencia no puede pasar de 100 caracteres") };
  if (nota && nota.length > 200) return { error: fail(422, "NOTA_INVALIDA", "La nota no puede pasar de 200 caracteres") };
  if (c.fecha_de_pago > hoyLima()) return { error: fail(422, "FECHA_DE_PAGO_FUTURA", `La fecha de pago no puede ser futura: ${c.fecha_de_pago}`) };

  const actual = planDeCuentaMock(cuentaId);
  let extendioHasta: string | undefined;
  if (c.extender_vencimiento === true) {
    if (!actual.venceEn) return { error: fail(409, "PLAN_SIN_VENCIMIENTO", "El plan de la cuenta no vence: no hay vencimiento que extender") };
    extendioHasta = venceDesdeFechaDeLima(hasta);
    if (Date.parse(extendioHasta) <= Date.parse(actual.venceEn))
      return { error: fail(409, "EXTENSION_SIN_EFECTO", "La cuenta ya está pagada hasta esa fecha o más: el pago no extiende nada. Regístralo sin extender el vencimiento") };
  }
  const existentes = db.pagosPorCuenta.get(cuentaId) ?? [];
  if (referencia && existentes.some((p) => p.medio === c.medio && p.referencia?.toLowerCase() === referencia.toLowerCase()))
    return { error: fail(409, "PAGO_DUPLICADO", `Esa cuenta ya tiene un pago por ${messages.admin.pagos.medios[c.medio as keyof typeof messages.admin.pagos.medios] ?? c.medio} con la referencia «${referencia}»`) };

  if (extendioHasta) db.planesDeCuenta.set(cuentaId, { ...actual, venceEn: extendioHasta });
  const pago: PagoMock = {
    id: idPagoMock(1000 + [...db.pagosPorCuenta.values()].reduce((n, l) => n + l.length, 0) + Math.floor(Math.random() * 1_000_000)),
    cuenta_id: cuentaId,
    periodo_desde: desde,
    periodo_hasta: hasta,
    monto,
    medio: c.medio as PagoMock["medio"],
    fecha_de_pago: c.fecha_de_pago,
    ...(referencia ? { referencia } : {}),
    ...(nota ? { nota } : {}),
    registrado_en: new Date().toISOString(),
    ...(extendioHasta ? { extendio_hasta: extendioHasta } : {}),
  };
  db.pagosPorCuenta.set(cuentaId, [...existentes, pago]);
  return { pago };
}

/** Lo de boleta (la boleta y sus notas, `Comprobante.esDeBoleta()`): se informa y se anula en el resumen diario (RC), no en una comunicación de baja (RA). */
function deBoletaMock(f: Comprobante): boolean {
  return f.tipo === "03" || f.nota?.tipo_afectado === "03";
}

/**
 * Como `Comprobante.anular()`: la baja aceptada deja el comprobante ANULADO y su evento en el historial, que nombra el canal que de verdad se usó (#286):
 * lo de boleta, el resumen diario.
 */
function anularMock(f: Comprobante) {
  if (f.estado_documento === "ANULADO") return;
  const mensaje = deBoletaMock(f) ? "Anulación aceptada por SUNAT en el resumen diario" : "Comunicación de baja aceptada por SUNAT";
  f.eventos = [...(f.eventos ?? []), { fecha: new Date().toISOString(), estado_anterior: f.estado_documento, estado_resultante: "ANULADO", mensaje }];
  f.estado_documento = "ANULADO";
}

/**
 * El CDR de una baja aceptada, como lo redacta SUNAT: el resumen diario (RC) «ha sido aceptado», la comunicación de baja (RA) «ha sido aceptada». Uno solo
 * para la aceptada en el acto y la que llega al consultar el ticket: eran dos copias y la del POST decía «La Comunicacion de baja …, ha sido aceptado» (352-H2).
 */
function cdrBajaAceptada(identificador: string): NonNullable<Baja["cdr"]> {
  const descripcion = identificador.startsWith("RC") ? `El Resumen diario ${identificador}, ha sido aceptado` : `La Comunicacion de baja ${identificador}, ha sido aceptada`;
  return { codigo: "0", descripcion, observaciones: [] };
}

export const handlers = [
  // Solo bajo API_MOCKING: `globalSetup` de Playwright lo llama al empezar cada corrida. Sin esto la suite no era
  // idempotente contra un dev server reutilizado (`reuseExistingServer` en local): cada corrida gastaba el tope 3286
  // de f-aceptada y dejaba f-obs anulada para siempre —corrida 2: 6 rojos; corrida 3: 10—, y un rojo que «a veces
  // pasa» enseña a ignorar el rojo.
  http.post(`${BASE}/v1/__test/reset`, () => {
    resetDb();
    // Lo que el mock guarda fuera de `db` (monitor #195, cola de errores #196, avisos #197, configuración #199) también vuelve al principio: sin esto, la segunda corrida contra un dev server reutilizado fallaba.
    lecturasDelMonitorMock = 0;
    erroresMock = erroresIniciales();
    avisosMock = avisosIniciales();
    configuracionMock = configuracionInicial();
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
    // Como el backend (#22): el correo queda sin verificar y se «manda» el enlace; el e2e lo arma con `verif-<correo>`.
    const usuario: Usuario = { id: nuevoId("u"), cuenta_id: nuevoId("c"), email, rol: "ADMIN", correo_verificado: false };
    db.usuariosPorEmail.set(email, { usuario, password: body.password });
    db.nombresDeCuenta.set(usuario.cuenta_id, body.nombre.trim());
    db.empresasPorCuenta.set(usuario.cuenta_id, []);
    db.verificaciones.set(`verif-${email}`, { email, usado: false, enviados: 1 });
    return ok(emitirTokens(usuario), 201);
  }),

  http.post(`${BASE}/v1/auth/verificar`, async ({ request }) => {
    const { token } = (await request.json()) as { token: string };
    const v = db.verificaciones.get(token);
    if (!v || v.usado) return fail(422, "TOKEN_INVALIDO", "El enlace de verificación es inválido o venció. Pide otro desde el portal");
    v.usado = true;
    const registro = db.usuariosPorEmail.get(v.email);
    if (registro) registro.usuario.correo_verificado = true;
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${BASE}/v1/auth/verificacion`, ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const registro = [...db.usuariosPorEmail.values()].find((r) => r.usuario.id === c.sub);
    if (!registro) return fail(404, "NO_ENCONTRADO", "Usuario no encontrado");
    if (registro.usuario.correo_verificado) return fail(409, "CORREO_YA_VERIFICADO", "Tu correo ya está verificado");
    // Como el backend: hasta 5 enlaces contando el del registro (el mock no los vence a las 24 h).
    const enviados = db.verificaciones.get(`verif-${registro.usuario.email}`)?.enviados ?? 0;
    if (enviados >= 5)
      return fail(429, "DEMASIADOS_ENLACES", "Ya te enviamos varios enlaces hoy. Revisa tu correo, también la carpeta de spam, o vuelve a pedirlo mañana");
    db.verificaciones.set(`verif-${registro.usuario.email}`, { email: registro.usuario.email, usado: false, enviados: enviados + 1 });
    return new HttpResponse(null, { status: 202 });
  }),

  http.post(`${BASE}/v1/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    const intento = reservarIntentoDeLogin(`cliente:${body.email}`);
    if (!intento) return fail(429, "DEMASIADOS_INTENTOS_LOGIN", "Demasiados intentos fallidos. Espera 15 minutos antes de volver a intentarlo");
    const registro = db.usuariosPorEmail.get(body.email);
    if (!registro || registro.password !== body.password) {
      return fail(401, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos");
    }
    intento.acerto();
    // Como el backend (#182): después de comprobar la contraseña, para no revelar el estado de una cuenta a quien no se identificó.
    if (db.cuentasSuspendidas.has(registro.usuario.cuenta_id)) return fail(403, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida. Contacta a soporte para reactivarla");
    return ok(emitirTokens(registro.usuario));
  }),

  http.post(`${BASE}/v1/auth/refresh`, async ({ request }) => {
    const body = (await request.json()) as { refresh: string };
    const sesion = db.sesionesPorToken.get(body.refresh);
    if (!sesion) return fail(401, "SESION_INVALIDA", "Sesión expirada o inválida");
    // Sin tocar la sesión: suspender no la revoca, y al reactivar la cuenta la misma sesión vuelve a servir (#182).
    if (db.cuentasSuspendidas.has(sesion.usuario.cuenta_id)) return fail(403, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida. Contacta a soporte para reactivarla");
    db.sesionesPorToken.delete(body.refresh);
    return ok(emitirTokens(sesion.usuario));
  }),

  http.post(`${BASE}/v1/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  // Recuperación de contraseña: 202 siempre (el backend no revela si el correo existe). No tenía mock: el e2e de
  // «Cambiar contraseña» llegaba al backend real en :8001 y, si estaba levantado, mandaba un correo de verdad.
  // Como el backend (#261): como mucho 3 correos por dirección por hora; pasado eso responde igual, pero no «manda» nada.
  http.post(`${BASE}/v1/auth/recuperar`, async ({ request }) => {
    const { email } = (await request.json()) as { email: string };
    reservarIntento(`recuperar:${email}`, 3, 60 * 60_000);
    return ok(null, 202);
  }),

  http.get(`${BASE}/v1/auth/me`, ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const registro = [...db.usuariosPorEmail.values()].find((r) => r.usuario.id === c.sub);
    if (!registro) return fail(404, "NO_ENCONTRADO", "Usuario no encontrado");
    // Una sesión de soporte (#184) lo dice en /me, con hasta cuándo vale; nunca qué administrador la abrió.
    // Solo del mock: el mundo de clientes es otro, así que el token de soporte lleva el correo y la cuenta (del backoffice) del usuario al que se mira, para que el
    // aviso diga a quién y el enlace de salida lleve a la cuenta correcta. El backend real devuelve el usuario y la cuenta reales.
    return ok(c.imp && c.exp ? { ...registro.usuario, email: c.ue ?? registro.usuario.email, cuenta_id: c.cx ?? registro.usuario.cuenta_id, soporte_hasta: new Date(c.exp * 1000).toISOString() } : registro.usuario);
  }),

  /**
   * Lo que el cliente ve de su cuenta (C1/C7), como `GET /v1/cuenta`: su nombre, su plan (la misma vista que el backoffice) y lo consumido este mes contra el
   * tope. Solo con sesión de cuenta.
   */
  http.get(`${BASE}/v1/cuenta`, ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Petición sin cuenta autenticada por JWT");
    const plan = vistaDePlanDeCuenta(c.cuenta);
    // El tope de hoy, como el backend (`Plan.vigenteEn`): si un cambio de límites programado ya entró en vigor, manda ese (265-H2).
    const planDelMock = db.planesAdmin.find((p) => p.id === plan.plan.id);
    const maximo = planDelMock ? topeDeHoyMock(planDelMock) : undefined;
    return ok({
      nombre: db.nombresDeCuenta.get(c.cuenta) ?? "Mi cuenta",
      plan,
      consumo: {
        mes: new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }).slice(0, 7),
        documentos: consumoDelMesMock(c.cuenta),
        ...(maximo === undefined ? {} : { maximo }),
      },
    });
  }),

  /** Los accesos de soporte a la cuenta del cliente (#184): uno completo y uno cuyo registro el backend no entiende (solo la fecha). Sin el administrador. */
  http.get(`${BASE}/v1/cuenta/accesos-de-soporte`, ({ request }) => {
    if (!claims(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    return ok([
      { ocurrido_en: "2026-10-02T15:00:00Z", usuario: "demo@example.com", duracion_segundos: 900 },
      { ocurrido_en: "2026-09-20T09:30:00Z" },
    ]);
  }),

  /**
   * Login del backoffice en dos pasos, como el backend (#177): la contraseña da un desafío; la sesión, el segundo factor. El mock no
   * guarda estado del 2FA (los e2e corren en paralelo contra el mismo servidor): `123456` es el código correcto de la app,
   * `ABCDE-FGHJK` uno de recuperación y `999999` simula el bloqueo por demasiados intentos. El anti-reuso y el bloqueo reales los
   * prueba el backend.
   */
  http.post(`${BASE}/v1/admin/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    const intento = reservarIntentoDeLogin(`administrador:${body.email}`);
    if (!intento) return fail(429, "DEMASIADOS_INTENTOS_LOGIN", "Demasiados intentos fallidos. Espera 15 minutos antes de volver a intentarlo");
    const registro = db.administradoresPorEmail.get(body.email);
    if (!registro || registro.password !== body.password) {
      return fail(401, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos");
    }
    intento.acerto();
    const desafio = fakeJwt({ sub: registro.administrador.id, tipo: "plataforma-desafio", email: registro.administrador.email });
    return ok({ desafio, paso: registro.segundoFactor ? "VERIFICAR_SEGUNDO_FACTOR" : "CONFIGURAR_SEGUNDO_FACTOR" });
  }),

  http.post(`${BASE}/v1/admin/auth/segundo-factor/configurar`, async ({ request }) => {
    const registro = delDesafio(((await request.json()) as { desafio: string }).desafio);
    if (!registro) return fail(401, "SESION_INVALIDA", "El inicio de sesión venció o no es válido");
    if (registro.segundoFactor) return fail(409, "SEGUNDO_FACTOR_YA_CONFIGURADO", "El segundo factor ya está configurado");
    return ok({ secreto: SECRETO_TOTP_MOCK, uri: `otpauth://totp/khipu:${encodeURIComponent(registro.administrador.email)}?secret=${SECRETO_TOTP_MOCK}`, qr_png: PNG_1X1 });
  }),

  http.post(`${BASE}/v1/admin/auth/segundo-factor/confirmar`, async ({ request }) => {
    const body = (await request.json()) as { desafio: string; codigo: string };
    const registro = delDesafio(body.desafio);
    if (!registro) return fail(401, "SESION_INVALIDA", "El inicio de sesión venció o no es válido");
    if (registro.segundoFactor) return fail(409, "SEGUNDO_FACTOR_YA_CONFIGURADO", "El segundo factor ya está configurado");
    if (body.codigo !== "123456") return fail(401, "CODIGO_INVALIDO", "El código no es válido");
    return ok({ ...sesionAdmin(registro.administrador), codigos_recuperacion: CODIGOS_RECUPERACION_MOCK });
  }),

  http.post(`${BASE}/v1/admin/auth/segundo-factor/verificar`, async ({ request }) => {
    const body = (await request.json()) as { desafio: string; codigo: string };
    const registro = delDesafio(body.desafio);
    if (!registro) return fail(401, "SESION_INVALIDA", "El inicio de sesión venció o no es válido");
    if (!registro.segundoFactor) return fail(409, "SEGUNDO_FACTOR_NO_CONFIGURADO", "El segundo factor no está configurado");
    if (body.codigo === "999999") return fail(429, "DEMASIADOS_INTENTOS", "Demasiados códigos incorrectos");
    const codigo = body.codigo.replace(/[\s-]/g, "").toUpperCase();
    if (codigo !== "123456" && codigo !== "ABCDEFGHJK") return fail(401, "CODIGO_INVALIDO", "El código no es válido");
    return ok(sesionAdmin(registro.administrador));
  }),

  http.get(`${BASE}/v1/admin/auth/me`, ({ request }) => {
    const c = claimsAdmin(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const registro = [...db.administradoresPorEmail.values()].find((r) => r.administrador.id === c.sub);
    if (!registro) return fail(404, "NO_ENCONTRADO", "Administrador no encontrado");
    return ok(registro.administrador);
  }),

  /**
   * Como el backend (#190): solo el administrador; ids que no son UUID, 400; planes que no existen, 404. Crear nace activo y con el nombre único; editar cambia el
   * nombre y el precio al instante pero deja los límites **programados** para el ciclo siguiente (poner otra vez los vigentes cancela el cambio). Desactivar no
   * toca a las cuentas; el plan de las cuentas nuevas no se desactiva ni se borra; un plan con cuentas o con historial no se borra (409 `PLAN_EN_USO`).
   * Las specs de la corrida comparten este mock en paralelo: **cada una crea y borra sus planes y no toca los sembrados**.
   */
  http.get(`${BASE}/v1/admin/planes`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    return ok(planesOrdenados().map(planVisible));
  }),

  http.post(`${BASE}/v1/admin/planes`, async ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const cuerpo = await request.json();
    const v = validarPlanMock(cuerpo);
    if ("error" in v) return v.error;
    // H20, como el backend: sin decirlo, un plan nuevo se publica.
    const visible = (cuerpo as { visible_en_publicidad?: unknown }).visible_en_publicidad !== false;
    const plan: PlanMock = { id: nuevoUuid(), nombre: v.nombre, precio_mensual: v.precio, limites: v.limites, estado: "ACTIVO", por_defecto: false, visible_en_publicidad: visible, cuentas: 0 };
    db.planesAdmin.push(plan);
    return ok(planVisible(plan), 201);
  }),

  /** Como el backend (H20): público, los planes activos y visibles, sin ids ni cuentas, del más barato al más caro. */
  http.get(`${BASE}/v1/planes`, () =>
    ok(
      db.planesAdmin
        .filter((p) => p.estado === "ACTIVO" && p.visible_en_publicidad)
        .sort((a, b) => a.precio_mensual - b.precio_mensual || a.nombre.localeCompare(b.nombre))
        .map((p) => ({ nombre: p.nombre, precio_mensual: p.precio_mensual, limites: p.limites })),
    ),
  ),

  http.put(`${BASE}/v1/admin/planes/:id`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const plan = db.planesAdmin.find((p) => p.id === params.id);
    if (!plan) return fail(404, "NO_ENCONTRADO", "El plan no existe");
    const cuerpo = await request.json();
    const v = validarPlanMock(cuerpo, plan.id);
    if ("error" in v) return v.error;
    plan.nombre = v.nombre;
    // H20, como el backend: sin decirlo, la visibilidad se queda como estaba.
    const visible = (cuerpo as { visible_en_publicidad?: unknown }).visible_en_publicidad;
    if (typeof visible === "boolean") plan.visible_en_publicidad = visible;
    plan.precio_mensual = v.precio;
    if (JSON.stringify(v.limites) === JSON.stringify(plan.limites)) delete plan.limites_programados;
    else plan.limites_programados = { limites: v.limites, aplica_desde: inicioDelProximoCiclo(new Date()) };
    return ok(planVisible(plan));
  }),

  http.post(`${BASE}/v1/admin/planes/:id/desactivar`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const plan = db.planesAdmin.find((p) => p.id === params.id);
    if (!plan) return fail(404, "NO_ENCONTRADO", "El plan no existe");
    if (plan.por_defecto) return fail(409, "PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no se puede desactivar");
    if (plan.estado === "INACTIVO") return fail(409, "PLAN_YA_INACTIVO", "El plan ya está inactivo");
    plan.estado = "INACTIVO";
    return ok(planVisible(plan));
  }),

  http.post(`${BASE}/v1/admin/planes/:id/activar`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const plan = db.planesAdmin.find((p) => p.id === params.id);
    if (!plan) return fail(404, "NO_ENCONTRADO", "El plan no existe");
    if (plan.estado === "ACTIVO") return fail(409, "PLAN_YA_ACTIVO", "El plan ya está activo");
    plan.estado = "ACTIVO";
    return ok(planVisible(plan));
  }),

  http.delete(`${BASE}/v1/admin/planes/:id`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const plan = db.planesAdmin.find((p) => p.id === params.id);
    if (!plan) return fail(404, "NO_ENCONTRADO", "El plan no existe");
    if (plan.por_defecto) return fail(409, "PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no se puede borrar");
    if (plan.cuentas > 0) return fail(409, "PLAN_EN_USO", `El plan lo tiene ${plan.cuentas} ${plan.cuentas === 1 ? "cuenta" : "cuentas"}: desactívalo en vez de borrarlo`);
    if (plan.historial) return fail(409, "PLAN_EN_USO", "El plan tiene historial de suscripciones: desactívalo en vez de borrarlo");
    db.planesAdmin = db.planesAdmin.filter((p) => p.id !== plan.id);
    return ok(null);
  }),

  /**
   * Como el backend (#191): solo el administrador; ids que no son UUID, 400; cuentas o planes que no existen, 404. **Subir de plan (o renovar) entra ya y cancela la
   * bajada que esperaba; bajar queda programado para el inicio del ciclo siguiente** y la cuenta sigue con el plan de hoy. Un plan de pago exige vencimiento, la gracia va
   * de 0 a 90 y un plan fuera de la oferta no se asigna. El estado se guarda por cuenta: las specs de la corrida comparten este mock en paralelo, así que **cada test
   * que cambia un plan usa su propia cuenta**.
   */
  http.get(`${BASE}/v1/admin/cuentas/:id/plan`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    if (!db.cuentasAdmin.some((c) => c.id === params.id)) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    return ok(vistaDePlanDeCuenta(String(params.id)));
  }),

  http.get(`${BASE}/v1/admin/cuentas/:id/plan/previsualizacion`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    if (!db.cuentasAdmin.some((c) => c.id === params.id)) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    const planId = new URL(request.url).searchParams.get("plan_id") ?? "";
    if (!esUuid(planId)) return parametroInvalido("plan_id");
    const nuevo = db.planesAdmin.find((p) => p.id === planId);
    if (!nuevo) return fail(404, "NO_ENCONTRADO", "El plan no existe");
    if (nuevo.estado !== "ACTIVO") return fail(409, "PLAN_INACTIVO", `El plan «${nuevo.nombre}» está fuera de la oferta: no se puede asignar`);
    const actual = db.planesAdmin.find((p) => p.id === planDeCuentaMock(String(params.id)).planId)!;
    const direccion = nuevo.id === actual.id ? "RENOVACION" : nuevo.precio_mensual < actual.precio_mensual ? "BAJADA" : "SUBIDA";
    const consumo = consumoDelMesMock(String(params.id));
    const limite = nuevo.limites.documentos_al_mes;
    // La bajada que espera (y todavía no llegó) queda sin efecto con cualquier cambio: el backend lo dice en la previsualización.
    const esperando = planDeCuentaMock(String(params.id)).programado;
    const planEsperando = esperando && Date.now() < Date.parse(esperando.aplicaDesde) ? db.planesAdmin.find((p) => p.id === esperando.planId) : undefined;
    return ok({
      ...(esperando && planEsperando ? { programado_que_se_descarta: { plan: resumenDePlan(planEsperando), aplica_desde: esperando.aplicaDesde } } : {}),
      cuenta_id: params.id,
      plan_actual: resumenDePlan(actual),
      plan_nuevo: resumenDePlan(nuevo),
      direccion,
      efecto: direccion === "BAJADA" ? "CICLO_SIGUIENTE" : "INMEDIATO",
      aplica_desde: direccion === "BAJADA" ? inicioDelProximoCiclo(new Date()) : new Date().toISOString(),
      mes: new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }).slice(0, 7),
      consumo_del_mes: consumo,
      limite_de_documentos: limite,
      supera_el_limite: !limite.ilimitado && consumo > (limite.maximo ?? 0),
    });
  }),

  http.post(`${BASE}/v1/admin/cuentas/:id/plan`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    if (!db.cuentasAdmin.some((c) => c.id === params.id)) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    const cuerpo = (await request.json()) as { plan_id?: string; vence_en?: string; dias_de_gracia?: number };
    if (!cuerpo.plan_id) return fail(422, "PLAN_REQUERIDO", "Falta el plan al que pasa la cuenta");
    const nuevo = db.planesAdmin.find((p) => p.id === cuerpo.plan_id);
    if (!nuevo) return fail(404, "NO_ENCONTRADO", "El plan no existe");
    if (nuevo.estado !== "ACTIVO") return fail(409, "PLAN_INACTIVO", `El plan «${nuevo.nombre}» está fuera de la oferta: no se puede asignar`);
    const gracia = cuerpo.dias_de_gracia ?? 0;
    if (!Number.isInteger(gracia) || gracia < 0 || gracia > 90) return fail(422, "GRACIA_INVALIDA", `Los días de gracia van de 0 a 90: ${gracia}`);
    if (nuevo.precio_mensual > 0 && !cuerpo.vence_en) return fail(422, "VENCIMIENTO_REQUERIDO", "Un plan de pago necesita una fecha de vencimiento: hasta cuándo está pagado");
    const actual = planDeCuentaMock(String(params.id));
    const planActual = db.planesAdmin.find((p) => p.id === actual.planId)!;
    const bajada = nuevo.id !== planActual.id && nuevo.precio_mensual < planActual.precio_mensual;
    const desde = bajada ? inicioDelProximoCiclo(new Date()) : new Date().toISOString();
    if (cuerpo.vence_en && Date.parse(cuerpo.vence_en) <= Date.parse(desde)) return fail(422, "SUSCRIPCION_FECHAS_INVALIDAS", "El vencimiento debe ser posterior al inicio");
    if (bajada) db.planesDeCuenta.set(String(params.id), { ...actual, programado: { planId: nuevo.id, aplicaDesde: desde, venceEn: cuerpo.vence_en, diasDeGracia: gracia } });
    else db.planesDeCuenta.set(String(params.id), { planId: nuevo.id, iniciaEn: desde, venceEn: cuerpo.vence_en, diasDeGracia: gracia });
    return ok(vistaDePlanDeCuenta(String(params.id)));
  }),

  /**
   * Como el backend (#194): el historial de pagos de una cuenta, del más reciente al más antiguo (por fecha de pago y luego por registro), con el total en la cabecera.
   * El estado se guarda por cuenta: las specs de la corrida comparten este mock en paralelo, así que **cada test que registra pagos usa referencias propias** y los
   * que mueven un vencimiento usan su propia cuenta.
   */
  http.get(`${BASE}/v1/admin/cuentas/:id/pagos`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    if (!db.cuentasAdmin.some((c) => c.id === params.id)) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    const url = new URL(request.url);
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    const todos = [...(db.pagosPorCuenta.get(String(params.id)) ?? [])].sort(
      (a, b) => b.fecha_de_pago.localeCompare(a.fecha_de_pago) || b.registrado_en.localeCompare(a.registrado_en) || a.id.localeCompare(b.id),
    );
    return HttpResponse.json(
      { estado: "exito", datos: todos.slice((pagina - 1) * porPagina, pagina * porPagina), mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(todos.length) } },
    );
  }),

  http.post(`${BASE}/v1/admin/cuentas/:id/pagos`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    if (!db.cuentasAdmin.some((c) => c.id === params.id)) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    let cuerpo: unknown;
    try {
      cuerpo = await request.json();
    } catch {
      return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
    }
    if (cuerpo === null || typeof cuerpo !== "object" || Array.isArray(cuerpo)) return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
    const r = registrarPagoMock(String(params.id), cuerpo as Record<string, unknown>);
    return "error" in r ? r.error : ok(r.pago, 201);
  }),

  /**
   * Como el backend (#198): verifica los comprobantes firmados emitidos entre `desde` y `hasta` (inclusive) y devuelve cuántos revisó y los problemas. Falta un parámetro o
   * una fecha no existe: 400 `PARAMETRO_INVALIDO`; `desde` posterior a `hasta`: 400 `RANGO_INVALIDO` (no 422). El mock siembra los hallazgos por fecha: si el rango incluye el
   * 10 de septiembre de 2026 hay tres problemas (dos comprobantes de «Panadería Sol» y uno de «Ferretería Luna»); si incluye el 15 de agosto, un almacenamiento inaccesible;
   * en cualquier otro rango, nada. Revisa 12 comprobantes por día, hasta 500.
   */
  http.post(`${BASE}/v1/admin/integridad`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const desde = url.searchParams.get("desde");
    const hasta = url.searchParams.get("hasta");
    if (!desde) return fail(400, "PARAMETRO_INVALIDO", "Falta el parámetro 'desde'");
    if (!hasta) return fail(400, "PARAMETRO_INVALIDO", "Falta el parámetro 'hasta'");
    if (!fechaIsoValidaMock(desde)) return fail(400, "PARAMETRO_INVALIDO", "El parámetro 'desde' no tiene un formato válido");
    if (!fechaIsoValidaMock(hasta)) return fail(400, "PARAMETRO_INVALIDO", "El parámetro 'hasta' no tiene un formato válido");
    if (hasta < desde) return fail(400, "RANGO_INVALIDO", "El rango de fechas es obligatorio y desde ≤ hasta");
    const incluye = (dia: string) => desde <= dia && dia <= hasta;
    const problema = (n: number, empresa: number, nombre: string, tipo: string, detalle: string) => ({
      comprobante_id: `00000000-0000-4000-c000-${String(n).padStart(12, "0")}`,
      tenant_id: idEmpresaMock(empresa),
      nombre_archivo: nombre,
      tipo,
      detalle,
    });
    const problemas = [
      ...(incluye("2026-09-10")
        ? [
            problema(1, 1, "20100047226-01-F001-14", "XML_CORRUPTO", "el DigestValue registrado (abc123=) no está en xml/20100047226-01-F001-14.xml"),
            problema(2, 1, "20100047226-03-B001-7", "CDR_FALTANTE", "cdr/R-20100047226-03-B001-7.zip"),
            problema(3, 2, "20100055121-01-F001-3", "XML_FALTANTE", "xml/20100055121-01-F001-3.xml"),
          ]
        : []),
      ...(incluye("2026-08-15") ? [problema(4, 2, "20100055121-01-F001-1", "STORAGE_INACCESIBLE", "Read timed out")] : []),
    ];
    const dias = (Date.parse(`${hasta}T00:00:00Z`) - Date.parse(`${desde}T00:00:00Z`)) / 86_400_000 + 1;
    return ok({ desde, hasta, verificados: Math.min(500, 12 * dias), problemas });
  }),

  /**
   * Como el backend (#195): el monitor global de emisión. Las 24 horas terminan en la hora actual y traen lo que pasó con los comprobantes (un patrón fijo por hora, para
   * que las pruebas puedan contar), el día de Lima suma las horas desde su medianoche, la cola del outbox arranca con 3 pendientes y **crece uno por cada lectura** (así una
   * prueba ve que el panel se actualizó sin depender del reloj) y SUNAT contesta salvo la consulta de CDR, que responde 503. Sin alerta: la alerta y los fallos de lectura se
   * fuerzan en las pruebas interceptando el BFF.
   */
  http.get(`${BASE}/v1/admin/monitor`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    lecturasDelMonitorMock += 1;
    const UNA_HORA = 3_600_000;
    const ahora = Date.now();
    const horaActual = Math.floor(ahora / UNA_HORA) * UNA_HORA;
    const horas = Array.from({ length: 24 }, (_, i) => {
      const aceptados = 10 + (i % 5) * 3;
      const rechazados = i % 7 === 0 ? 1 : 0;
      const con_error = i % 11 === 0 ? 1 : 0;
      const en_camino = i === 23 ? 4 : 0;
      const resueltos = aceptados + rechazados;
      return {
        desde: new Date(horaActual - (23 - i) * UNA_HORA).toISOString(),
        total: aceptados + rechazados + con_error + en_camino,
        aceptados,
        rechazados,
        con_error,
        en_camino,
        otros: 0,
        tasa_de_rechazo: rechazados / resueltos,
      };
    });
    const CINCO_HORAS = 5 * UNA_HORA;
    const medianoche = Math.floor((ahora - CINCO_HORAS) / 86_400_000) * 86_400_000 + CINCO_HORAS;
    const delDia = horas.filter((h) => Date.parse(h.desde) >= medianoche);
    const suma = (campo: "total" | "aceptados" | "rechazados" | "con_error" | "en_camino" | "otros") => delDia.reduce((s, h) => s + h[campo], 0);
    const resueltosDelDia = suma("aceptados") + suma("rechazados");
    return ok({
      generado_en: new Date(ahora).toISOString(),
      horas,
      hoy: {
        desde: new Date(medianoche).toISOString(),
        total: suma("total"),
        aceptados: suma("aceptados"),
        rechazados: suma("rechazados"),
        con_error: suma("con_error"),
        en_camino: suma("en_camino"),
        otros: suma("otros"),
        ...(resueltosDelDia === 0 ? {} : { tasa_de_rechazo: suma("rechazados") / resueltosDelDia }),
      },
      outbox: { pendientes: 2 + lecturasDelMonitorMock, vencidos: 0, mas_viejo_desde: new Date(ahora - 2 * UNA_HORA).toISOString(), alerta: false },
      // #362 B14: sin trámites pendientes pasado el umbral (6 h por defecto); como la alerta del outbox, se fuerza en las pruebas interceptando el BFF.
      resumenes_sin_respuesta: { cantidad: 0, umbral_horas: 6, alerta: false },
      // #356: sin boletas sin informar; se fuerza en las pruebas interceptando el BFF, como las otras alertas.
      boletas_sin_informar: { cantidad: 0, por_vencer_el_plazo: 0, alerta: false },
      sunat: [
        { servicio: "ENVIO_PRODUCCION", disponible: true, milisegundos: 140 },
        { servicio: "ENVIO_BETA", disponible: true, milisegundos: 90 },
        { servicio: "CONSULTA_DE_CDR", disponible: false, detalle: "HTTP 503" },
        { servicio: "CONSULTA_DE_VALIDEZ", disponible: true, milisegundos: 210 },
      ],
    });
  }),

  /**
   * Como el backend (#196): la cola global de errores de todas las empresas, de la emisión más antigua a la más reciente, con filtro por clase, empresa y texto (RUC por prefijo,
   * razón social y cuenta, sin tildes), paginada, y el total en `x-total-count`. Una clase que no existe o una empresa que no es un UUID: 400 `PARAMETRO_INVALIDO`.
   */
  http.get(`${BASE}/v1/admin/errores`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const clase = url.searchParams.get("clase");
    if (clase !== null && !["ERROR_DE_ENVIO", "ERROR_DE_FORMATO", "FUERA_DE_PLAZO", "BOLETA_SIN_ENVIAR"].includes(clase)) return fail(400, "PARAMETRO_INVALIDO", "El parámetro 'clase' no tiene un formato válido");
    const empresa = url.searchParams.get("empresa_id");
    if (empresa !== null && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(empresa)) return fail(400, "PARAMETRO_INVALIDO", "El parámetro 'empresa_id' no tiene un formato válido");
    const q = sinTildes((url.searchParams.get("q") ?? "").trim());
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    const filtradas = erroresMock
      .filter((e) => (clase === null || e.clase === clase) && (empresa === null || e.empresa_id === empresa))
      .filter((e) => q === "" || e.ruc.startsWith(q) || sinTildes(e.razon_social).includes(q) || sinTildes(e.cuenta_nombre ?? "").includes(q))
      .sort((a, b) => (a.fecha_emision === b.fecha_emision ? a.numero - b.numero : a.fecha_emision < b.fecha_emision ? -1 : 1));
    return HttpResponse.json(
      { estado: "exito", datos: filtradas.slice((pagina - 1) * porPagina, pagina * porPagina), mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(filtradas.length) } },
    );
  }),

  /**
   * Como el backend (#251): la ficha de un comprobante de cualquier empresa. Los de la cola de errores salen de ella (un error de envío trae su último error; un rechazo por
   * formato o fuera de plazo, la respuesta de SUNAT); los de la verificación de integridad, de `FICHAS_DE_INTEGRIDAD`. Un id que no es UUID: 400; uno que no está: 404.
   */
  http.get(`${BASE}/v1/admin/comprobantes/:id`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const e = erroresMock.find((x) => x.comprobante_id === params.id);
    if (e) {
      const conFault = e.fault ? (e.fault.codigo ? `${e.fault.codigo} - ${e.fault.mensaje}` : e.fault.mensaje) : undefined;
      return ok({
        id: e.comprobante_id, empresa_id: e.empresa_id, ruc: e.ruc, razon_social: e.razon_social, cuenta_id: e.cuenta_id, nombre_archivo: e.nombre_archivo,
        tipo: e.tipo, serie: e.serie, numero: e.numero, fecha_emision: e.fecha_emision, estado: e.estado, intentos: e.intentos,
        // Como el backend (273-H2): solo un rechazo de formato trae la respuesta de SUNAT (su CDR); un error de envío y un fuera de plazo (2108, que khipu
        // decide sin llamar a SUNAT y no deja CDR) dejan el motivo en `ultimo_error`.
        ...(e.clase === "ERROR_DE_FORMATO"
          ? { respuesta_sunat: e.fault?.codigo ? { codigo: e.fault.codigo, descripcion: e.fault.mensaje } : undefined }
          : { ultimo_error: conFault }),
        tiene_xml: true, tiene_cdr: false, bajas: [],
      });
    }
    const f = FICHAS_DE_INTEGRIDAD[String(params.id)];
    return f ? ok(f) : fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
  }),

  /**
   * Como el backend (#196): reintenta el envío de un comprobante en error de envío. Lo que pasa depende del comprobante (ver `REINTENTOS_MOCK`): vuelve a fallar (200 con
   * `ERROR_ENVIO` y un intento más), SUNAT lo acepta (sale de la cola), lo rechaza con un fault de formato (pasa a error de formato), se pasó el plazo (409 `FUERA_DE_PLAZO`) o ya
   * lo había resuelto otro administrador (409 `ESTADO_NO_ENVIABLE`). Lo que no está en error de envío: 409 `ESTADO_NO_ENVIABLE`.
   */
  http.post(`${BASE}/v1/admin/comprobantes/:id/reintento`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const e = erroresMock.find((x) => x.comprobante_id === params.id);
    if (!e) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    if (e.estado !== "ERROR_ENVIO") return fail(409, "ESTADO_NO_ENVIABLE", `El comprobante está en estado ${e.estado}`);
    switch (REINTENTOS_MOCK[e.comprobante_id] ?? "FALLA") {
      case "ACEPTA":
        erroresMock = erroresMock.filter((x) => x !== e);
        return ok({ comprobante_id: e.comprobante_id, estado: "ACEPTADO", intentos: e.intentos });
      case "RECHAZA": {
        const fault = { codigo: "1033", mensaje: "El comprobante fue registrado previamente con otros datos" };
        Object.assign(e, { estado: "RECHAZADO", clase: "ERROR_DE_FORMATO", fault, accionable: false, proximo_intento: undefined });
        return ok({ comprobante_id: e.comprobante_id, estado: "RECHAZADO", intentos: e.intentos, fault });
      }
      case "VENCE":
        Object.assign(e, { estado: "FUERA_DE_PLAZO", clase: "FUERA_DE_PLAZO", accionable: false, proximo_intento: undefined, fault: { codigo: "2108", mensaje: "Presentación fuera de fecha" } });
        return fail(409, "FUERA_DE_PLAZO", "2108 - El comprobante no se envió dentro del plazo: emita un comprobante nuevo");
      case "OBSOLETO":
        erroresMock = erroresMock.filter((x) => x !== e);
        return fail(409, "ESTADO_NO_ENVIABLE", "El comprobante está en estado ACEPTADO");
      default: {
        const fault = { codigo: "0000", mensaje: "SUNAT respondió HTTP 503" };
        e.intentos += 1;
        e.fault = fault;
        e.proximo_intento = new Date(Date.now() + 3_600_000).toISOString();
        return ok({ comprobante_id: e.comprobante_id, estado: "ERROR_ENVIO", intentos: e.intentos, fault });
      }
    }
  }),

  /**
   * Como el backend (#196): descarta un comprobante en error de envío. El motivo es obligatorio (422 `MOTIVO_REQUERIDO`) y de hasta 200 caracteres (422 `MOTIVO_LARGO`); lo que
   * no está en error de envío: 409 `ESTADO_NO_DESCARTABLE`. Descartado, sale de la cola.
   */
  http.post(`${BASE}/v1/admin/comprobantes/:id/descarte`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    let cuerpo: { motivo?: unknown } = {};
    try {
      cuerpo = (await request.json()) as { motivo?: unknown };
    } catch {
      return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
    }
    const motivo = typeof cuerpo.motivo === "string" ? cuerpo.motivo.trim() : "";
    if (motivo === "") return fail(422, "MOTIVO_REQUERIDO", "Indica por qué se descarta el comprobante");
    if (motivo.length > 200) return fail(422, "MOTIVO_LARGO", "El motivo no puede pasar de 200 caracteres");
    const e = erroresMock.find((x) => x.comprobante_id === params.id);
    if (!e) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    if (e.estado !== "ERROR_ENVIO") return fail(409, "ESTADO_NO_DESCARTABLE", `Solo se descarta un comprobante en error de envío; este está ${e.estado}`);
    erroresMock = erroresMock.filter((x) => x !== e);
    return ok({ comprobante_id: e.comprobante_id, estado: "DESCARTADO" });
  }),

  /**
   * Como el backend (#197): las empresas con el certificado vencido o por vencer, de la que vence antes a la que vence después (con `vigente_hasta` calculado desde hoy), paginadas,
   * con el total en `x-total-count`.
   */
  http.get(`${BASE}/v1/admin/avisos/certificados`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    const todas = [...avisosMock.certificados].sort((a, b) => a.dias_restantes - b.dias_restantes);
    return HttpResponse.json(
      {
        estado: "exito",
        datos: todas.slice((pagina - 1) * porPagina, pagina * porPagina).map((c) => ({ ...c, vigente_hasta: fechaDeLimaMock(c.dias_restantes) })),
        mensaje: null,
        codigo: null,
        errores: null,
      },
      { headers: { "x-total-count": String(todas.length) } },
    );
  }),

  /** Como el backend (#197): las empresas con comprobantes atascados por sus credenciales SOL, de la que más tiene a la que menos, paginadas. */
  http.get(`${BASE}/v1/admin/avisos/credenciales-sol`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    const todas = [...avisosMock.sol].sort((a, b) => b.comprobantes_afectados - a.comprobantes_afectados || a.ruc.localeCompare(b.ruc));
    return HttpResponse.json(
      { estado: "exito", datos: todas.slice((pagina - 1) * porPagina, pagina * porPagina), mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(todas.length) } },
    );
  }),

  /**
   * Como el backend (#197): avisarle a un cliente. Sin tipo o con uno desconocido: 422 `TIPO_INVALIDO`; una empresa que no está en ese problema: 409 `AVISO_SIN_MOTIVO` (404 si no existe
   * en ninguna lista); sin cuenta: 409 `EMPRESA_SIN_CUENTA`; ya avisada: 409 `AVISO_RECIENTE`. La empresa 10 simula un correo que falla (502 `CORREO_NO_ENVIADO`, sin registrar nada) y la 5
   * simula a otro administrador que se adelantó (409 `AVISO_RECIENTE`, y queda avisada). Si sale, la empresa queda avisada: ya no se puede repetir hasta dentro de una semana.
   */
  http.post(`${BASE}/v1/admin/empresas/:id/avisos`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    let cuerpo: { tipo?: unknown } = {};
    try {
      cuerpo = (await request.json()) as { tipo?: unknown };
    } catch {
      return jsonInvalido();
    }
    // Como el backend: sin tipo lo rechaza el servicio (422); un tipo que no existe ni siquiera se convierte del JSON (400).
    if (cuerpo.tipo === undefined || cuerpo.tipo === null) return fail(422, "TIPO_INVALIDO", "Indica qué se le avisa al cliente");
    if (cuerpo.tipo !== "CERTIFICADO" && cuerpo.tipo !== "CREDENCIALES_SOL") return jsonInvalido();
    const fila: FilaDeAvisoMock | undefined = (cuerpo.tipo === "CERTIFICADO" ? avisosMock.certificados : avisosMock.sol).find((f) => f.empresa_id === params.id);
    if (!fila) {
      const existe = [...avisosMock.certificados, ...avisosMock.sol].some((f) => f.empresa_id === params.id);
      return existe ? fail(409, "AVISO_SIN_MOTIVO", "No hay nada que avisar: la empresa no está en ese problema") : fail(404, "NO_ENCONTRADO", "La empresa no existe");
    }
    if (!fila.cuenta) return fail(409, "EMPRESA_SIN_CUENTA", "La empresa no tiene una cuenta con correo a quien avisarle");
    const ahora = Date.now();
    const registrar = () => {
      fila.ultimo_aviso = { enviado_en: new Date(ahora).toISOString(), destinatario: fila.cuenta!.email };
      fila.avisar_desde = new Date(ahora + 7 * DIA_MS).toISOString();
      fila.puede_avisar = false;
    };
    if (params.id === idEmpresaMock(10)) return fail(502, "CORREO_NO_ENVIADO", "No se pudo enviar el aviso: SMTP caído");
    if (params.id === idEmpresaMock(5) || !fila.puede_avisar) {
      if (fila.puede_avisar) registrar();
      return fail(409, "AVISO_RECIENTE", `Ya se avisó lo mismo el ${fila.ultimo_aviso?.enviado_en}: se puede repetir desde el ${fila.avisar_desde}`);
    }
    registrar();
    const motivo = cuerpo.tipo === "CREDENCIALES_SOL" ? "CREDENCIALES_SOL_INVALIDAS" : (fila as CertificadoDeAvisoMock).motivo;
    return ok({ empresa_id: fila.empresa_id, motivo, destinatario: fila.cuenta.email, enviado_en: fila.ultimo_aviso!.enviado_en, avisar_desde: fila.avisar_desde });
  }),

  /** Como el backend (#199): el remitente vigente, y el del servidor al que se vuelve. */
  http.get(`${BASE}/v1/admin/configuracion/correo`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    return ok(remitenteConfiguradoMock());
  }),

  /** Como el backend: el correo es obligatorio y una sola dirección en ASCII; el nombre, hasta 100 caracteres y sin saltos de línea, comillas ni < >. Si sirve, queda fijado. */
  http.put(`${BASE}/v1/admin/configuracion/correo`, async ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const c = (await request.json().catch(() => ({}))) as { nombre?: unknown; email?: unknown; responder_a?: unknown };
    const nombre = typeof c.nombre === "string" && c.nombre.trim() !== "" ? c.nombre.trim() : undefined;
    const email = typeof c.email === "string" ? c.email.trim() : "";
    const responder = typeof c.responder_a === "string" && c.responder_a.trim() !== "" ? c.responder_a.trim() : undefined;
    const direccion = /^[A-Za-z0-9._%+-]{1,64}@([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}$/;
    if (nombre && (nombre.length > 100 || /[\u0000-\u001f\u007f<>"\\]/.test(nombre))) return fail(422, "REMITENTE_INVALIDO", nombre.length > 100 ? "El nombre admite hasta 100 caracteres" : "El nombre no puede llevar saltos de línea, comillas ni < >");
    if (email === "") return fail(422, "REMITENTE_INVALIDO", "El correo del remitente es obligatorio");
    if (email.length > 254 || !direccion.test(email) || email.includes("..")) return fail(422, "REMITENTE_INVALIDO", "El correo del remitente no es una dirección de correo válida");
    if (responder && (responder.length > 254 || !direccion.test(responder) || responder.includes(".."))) return fail(422, "REMITENTE_INVALIDO", "El correo para las respuestas no es una dirección de correo válida");
    configuracionMock.remitente = { ...(nombre ? { nombre } : {}), email, ...(responder ? { responder_a: responder } : {}), actualizado_en: new Date().toISOString() };
    return ok(remitenteConfiguradoMock());
  }),

  http.delete(`${BASE}/v1/admin/configuracion/correo`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    configuracionMock.remitente = undefined;
    return ok(remitenteConfiguradoMock());
  }),

  http.get(`${BASE}/v1/admin/configuracion/plantillas`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    return ok(PLANTILLAS_MOCK.map(plantillaConfiguradaMock));
  }),

  /** Como el backend: un correo que no existe es 404; un texto que no sirve, 422 `PLANTILLA_INVALIDA` con lo que hay que corregir; si sirve, queda como el texto de ese correo. */
  http.put(`${BASE}/v1/admin/configuracion/plantillas/:tipo`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const p = PLANTILLAS_MOCK.find((x) => x.tipo === params.tipo);
    if (!p) return fail(404, "NO_ENCONTRADO", "Ese correo no existe");
    const c = (await request.json().catch(() => ({}))) as { asunto?: unknown; cuerpo?: unknown };
    const invalida = mensajeDePlantillaInvalidaMock(p, c.asunto, c.cuerpo);
    if (invalida) return fail(422, "PLANTILLA_INVALIDA", invalida);
    configuracionMock.plantillas[p.tipo] = {
      asunto: (c.asunto as string).trim(),
      cuerpo: (c.cuerpo as string).replace(/\r\n?/g, "\n").trimEnd(),
      actualizada_en: new Date().toISOString(),
    };
    return ok(plantillaConfiguradaMock(p));
  }),

  http.delete(`${BASE}/v1/admin/configuracion/plantillas/:tipo`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const p = PLANTILLAS_MOCK.find((x) => x.tipo === params.tipo);
    if (!p) return fail(404, "NO_ENCONTRADO", "Ese correo no existe");
    delete configuracionMock.plantillas[p.tipo];
    return ok(plantillaConfiguradaMock(p));
  }),

  http.post(`${BASE}/v1/admin/configuracion/plantillas/:tipo/vista-previa`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const p = PLANTILLAS_MOCK.find((x) => x.tipo === params.tipo);
    if (!p) return fail(404, "NO_ENCONTRADO", "Ese correo no existe");
    const c = (await request.json().catch(() => ({}))) as { asunto?: unknown; cuerpo?: unknown };
    const invalida = mensajeDePlantillaInvalidaMock(p, c.asunto, c.cuerpo);
    if (invalida) return fail(422, "PLANTILLA_INVALIDA", invalida);
    return ok({ asunto: renderizarConEjemplosMock(p, (c.asunto as string).trim()), cuerpo: renderizarConEjemplosMock(p, (c.cuerpo as string).replace(/\r\n?/g, "\n").trimEnd()) });
  }),

  /** Como el backend: el aviso publicado (o `datos` nulo) y si se está mostrando ahora. */
  http.get(`${BASE}/v1/admin/configuracion/banner`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const b = configuracionMock.banner;
    return ok(b ? { ...b, vigente_ahora: bannerVigenteMock() !== null } : null);
  }),

  http.put(`${BASE}/v1/admin/configuracion/banner`, async ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const c = (await request.json().catch(() => ({}))) as { texto?: unknown; desde?: unknown; hasta?: unknown };
    const invalido = mensajeDeBannerInvalidoMock(c);
    if (invalido) return fail(422, "BANNER_INVALIDO", invalido);
    configuracionMock.banner = { texto: (c.texto as string).trim(), desde: new Date(c.desde as string).toISOString(), hasta: new Date(c.hasta as string).toISOString(), actualizado_en: new Date().toISOString() };
    return ok({ ...configuracionMock.banner, vigente_ahora: bannerVigenteMock() !== null });
  }),

  http.delete(`${BASE}/v1/admin/configuracion/banner`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!configuracionMock.banner) return fail(404, "NO_ENCONTRADO", "No hay un aviso publicado");
    configuracionMock.banner = undefined;
    return ok(null);
  }),

  /** Público, sin credenciales: solo el texto y la vigencia del aviso que se muestra ahora; `datos` nulo si no hay ninguno. */
  http.get(`${BASE}/v1/banner`, () => {
    const b = bannerVigenteMock();
    return ok(b ? { texto: b.texto, desde: b.desde, hasta: b.hasta } : null);
  }),

  /** Como el backend (#193): consumo de todas las cuentas contra su plan de hoy, con filtro, orden, mes y página; el total, que refleja el filtro, va en la cabecera. */
  http.get(`${BASE}/v1/admin/consumo`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const p = parametrosDeConsumoMock(url);
    if (!p) return fail(400, "PARAMETRO_INVALIDO", "El mes, el filtro o el orden no son válidos");
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    const filas = filasDeConsumoMock(p.mes, p.filtro, p.orden);
    return HttpResponse.json(
      { estado: "exito", datos: { mes: p.mes, umbral_de_alerta: UMBRAL_DE_ALERTA_MOCK, cuentas: filas.slice((pagina - 1) * porPagina, pagina * porPagina) }, mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(filas.length) } },
    );
  }),

  /** Como el backend (#193): lo mismo, completo y sin paginar, como CSV que se descarga. */
  http.get(`${BASE}/v1/admin/consumo/exportacion`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const p = parametrosDeConsumoMock(new URL(request.url));
    if (!p) return fail(400, "PARAMETRO_INVALIDO", "El mes, el filtro o el orden no son válidos");
    return new HttpResponse(csvDeConsumoMock(filasDeConsumoMock(p.mes, p.filtro, p.orden)), {
      headers: { "Content-Type": "text/csv;charset=UTF-8", "Content-Disposition": `attachment; filename="consumo-${p.mes}.csv"` },
    });
  }),

  /** Como el backend (#180): solo el administrador; `q` en correo, nombre, razón social (fragmento) y RUC (prefijo); total en cabecera. */
  http.get(`${BASE}/v1/admin/cuentas`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const q = (url.searchParams.get("q") ?? "").trim().toLowerCase();
    const bajas = visibilidadDeBajas(url);
    // Mismo código que el backend: `bajas` es un enum en el @RequestParam (MethodArgumentTypeMismatchException).
    if (!bajas) return parametroInvalido("bajas");
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    // La misma tabla que el `translate` del backend (#214, JdbcCuentasAdminRepository): solo las vocales con marca pierden la marca, y
    // la Ñ pasa a ñ sin volverse n. Una regla más amplia (quitar cualquier marca) haría pasar contra el mock búsquedas que el backend
    // no encuentra, como «conceicao» → «Conceição».
    const CON_TILDE = "áéíóúàèìòùäëïöüâêîôûÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÂÊÎÔÛÑ";
    const SIN_TILDE = "aeiouaeiouaeiouaeiouAEIOUAEIOUAEIOUAEIOUñ";
    const sinTildes = (s: string) =>
      Array.from(s, (ch) => {
        const i = CON_TILDE.indexOf(ch);
        return i < 0 ? ch : SIN_TILDE[i];
      }).join("");
    // El backend normaliza a NFC solo el texto buscado (una tilde que llega como letra + acento combinado).
    const qSinTildes = sinTildes(q.normalize("NFC"));
    const coincide = (c: (typeof db.cuentasAdmin)[number]) =>
      !q ||
      c.email.toLowerCase().includes(q) ||
      sinTildes(c.nombre.toLowerCase()).includes(qSinTildes) ||
      c.empresas.some((e) => e.ruc.startsWith(q) || sinTildes(e.razon_social.toLowerCase()).includes(qSinTildes));
    const visible = (c: (typeof db.cuentasAdmin)[number]) => bajas === "INCLUIDAS" || (bajas === "SOLO") === Boolean(c.baja_en);
    const lista = db.cuentasAdmin.filter((c) => coincide(c) && visible(c)).sort((a, b) => b.creada_en.localeCompare(a.creada_en) || a.id.localeCompare(b.id));
    const datos = lista
      .slice((pagina - 1) * porPagina, pagina * porPagina)
      // eslint-disable-next-line @typescript-eslint/no-unused-vars -- el historial va solo en el detalle, como en el backend.
      .map(({ empresas, historial_estado, ...cuenta }) => ({ ...cuenta, empresas: empresas.length, estado: estadoDeCuenta(cuenta) }));
    return HttpResponse.json(
      { estado: "exito", datos, mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(lista.length) } },
    );
  }),

  /**
   * Como el backend (#185): solo el administrador; filtros por `entorno` y por `certificado` (un valor desconocido es 400, no se ignora);
   * total en cabecera, de la más reciente a la más antigua. El estado del certificado sale de «hoy» con la misma regla que el backend:
   * vencido antes de hoy, por vencer con menos de 30 días, y con 30 justos todavía vigente. Las empresas de integración no traen cuenta.
   */
  http.get(`${BASE}/v1/admin/empresas`, ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const url = new URL(request.url);
    const entorno = url.searchParams.get("entorno");
    const certificado = url.searchParams.get("certificado");
    if (entorno && !["BETA", "PRODUCCION"].includes(entorno)) return parametroInvalido("entorno");
    if (certificado && !["SIN_CERTIFICADO", "SIN_FECHA", "VIGENTE", "POR_VENCER", "VENCIDO"].includes(certificado))
      return parametroInvalido("certificado");
    const bajas = visibilidadDeBajas(url);
    // Mismo código que el backend: `bajas` es un enum en el @RequestParam (MethodArgumentTypeMismatchException).
    if (!bajas) return parametroInvalido("bajas");
    const pagina = Math.max(1, Number(url.searchParams.get("pagina") ?? 1) || 1);
    const porPagina = Math.min(100, Math.max(1, Number(url.searchParams.get("por_pagina") ?? 20) || 20));
    const desdeHoy = (n: number) => sumarDias(hoyLima(), n);
    const filas = db.empresasAdmin.map((e) => {
      // Una empresa sin cuenta (de integración) nunca está de baja; las demás dependen de su cuenta, que es la única fuente.
      const cuentaDeBajaEn = e.cuenta ? db.cuentasAdmin.find((c) => c.id === e.cuenta?.id)?.baja_en : undefined;
      const estado =
        e.certificado === null ? "SIN_CERTIFICADO" : e.certificado === "sin_fecha" ? "SIN_FECHA" : e.certificado < 0 ? "VENCIDO" : e.certificado < 30 ? "POR_VENCER" : "VIGENTE";
      return {
        estado,
        entorno: e.entorno,
        id: e.id,
        creada_en: e.creada_en,
        json: {
          id: e.id,
          ruc: e.ruc,
          razon_social: e.razon_social,
          cuenta_id: e.cuenta?.id,
          cuenta_nombre: e.cuenta?.nombre,
          entorno: e.entorno,
          certificado: estado,
          ...(typeof e.certificado === "number" ? { certificado_vigente_hasta: desdeHoy(e.certificado), certificado_dias_restantes: e.certificado } : {}),
          tiene_credenciales_sol: e.tiene_credenciales_sol,
          series: e.series,
          comprobantes_del_mes: e.comprobantes_del_mes,
          ultima_emision: e.ultima_emision_hace === null ? undefined : desdeHoy(-e.ultima_emision_hace),
          cuenta_de_baja_en: cuentaDeBajaEn,
        },
        cuentaDeBaja: Boolean(cuentaDeBajaEn),
      };
    });
    // H17, como el backend: RUC por prefijo y razón social por subcadena, sin mayúsculas (el mock no cubre las tildes: eso lo prueba el backend).
    const q = url.searchParams.get("q")?.trim().toLowerCase() ?? "";
    const lista = filas
      .filter((f) => (!entorno || f.entorno === entorno) && (!certificado || f.estado === certificado))
      .filter((f) => !q || f.json.ruc.startsWith(q) || f.json.razon_social.toLowerCase().includes(q))
      .filter((f) => bajas === "INCLUIDAS" || (bajas === "SOLO") === f.cuentaDeBaja)
      .sort((a, b) => b.creada_en.localeCompare(a.creada_en) || a.id.localeCompare(b.id));
    return HttpResponse.json(
      { estado: "exito", datos: lista.slice((pagina - 1) * porPagina, pagina * porPagina).map((f) => f.json), mensaje: null, codigo: null, errores: null },
      { headers: { "x-total-count": String(lista.length) } },
    );
  }),

  /**
   * Como el backend (#186): solo el administrador; un id que no es UUID es 400 y uno que no existe, 404. «Panadería Sol» trae un detalle
   * completo (domicilio, tres series, un establecimiento, dos API keys, PDF con logo, tres comprobantes —uno con observaciones del CDR,
   * uno rechazado y uno sin respuesta—, sus cambios de estado y doce tareas pendientes); las demás, lo mínimo. Sin secretos, como el
   * backend: de las API keys, solo el prefijo; del logo, solo si hay uno. El estado del certificado es el del listado.
   */
  http.get(`${BASE}/v1/admin/empresas/:id`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException).
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const e = db.empresasAdmin.find((x) => x.id === params.id);
    if (!e) return fail(404, "NO_ENCONTRADO", "La empresa no existe");
    const completa = e.id === idEmpresaMock(1);
    const estado =
      e.certificado === null ? "SIN_CERTIFICADO" : e.certificado === "sin_fecha" ? "SIN_FECHA" : e.certificado < 0 ? "VENCIDO" : e.certificado < 30 ? "POR_VENCER" : "VIGENTE";
    const hoy = hoyLima();
    return ok({
      id: e.id,
      ruc: e.ruc,
      razon_social: e.razon_social,
      nombre_comercial: completa ? "LA PANADERIA" : undefined,
      entorno: e.entorno,
      creada_en: e.creada_en,
      cuenta_id: e.cuenta?.id,
      cuenta_nombre: e.cuenta?.nombre,
      certificado: estado,
      ...(typeof e.certificado === "number" ? { certificado_vigente_hasta: sumarDias(hoy, e.certificado), certificado_dias_restantes: e.certificado } : {}),
      tiene_credenciales_sol: e.tiene_credenciales_sol,
      domicilio: completa
        ? { ubigeo: "150122", direccion: "AV. LARCO 345", urbanizacion: "URB. SOL", distrito: "MIRAFLORES", provincia: "LIMA", departamento: "LIMA", codigo_establecimiento: "0000" }
        : undefined,
      cuenta_detracciones: completa ? "00-123-456789" : undefined,
      padron_tasa_especial_igv: completa,
      pdf: completa
        ? { plantilla: "MODERNO", color_primario: "#0F766E", tiene_logo: true, pie_de_pagina: "Gracias por su compra", observaciones_por_defecto: "Pago a 30 días" }
        : { plantilla: "CLASICO", color_primario: "#1E1E24", tiene_logo: false },
      series: completa
        ? [
            { tipo: "01", codigo: "F001", ultimo_numero: 12, activa: true, establecimiento: "0000" },
            { tipo: "01", codigo: "F002", ultimo_numero: 0, activa: false, establecimiento: "0001" },
            { tipo: "03", codigo: "B001", ultimo_numero: 3, activa: true, establecimiento: "0000" },
          ]
        : [],
      establecimientos: completa
        ? [{ codigo: "0001", nombre: "Tienda Surco", domicilio: { ubigeo: "150140", direccion: "AV. CAMINOS DEL INCA 100", distrito: "SANTIAGO DE SURCO", provincia: "LIMA", departamento: "LIMA", codigo_establecimiento: "0001" }, activo: true }]
        : [],
      api_keys: completa
        ? [
            { id: idApiKeyMock(2), prefijo: "fk_sol0002", activa: true, creada_en: "2026-09-10T15:00:00Z" },
            { id: idApiKeyMock(1), prefijo: "fk_sol0001", activa: false, creada_en: "2026-09-01T15:00:00Z", revocada_en: "2026-09-09T12:00:00Z" },
          ]
        : [],
      comprobantes: completa
        ? [
            {
              id: "f-sol-12", tipo: "01", serie: "F001", numero: 12, fecha_emision: sumarDias(hoy, -1), estado: "ACEPTADO_CON_OBS", moneda: "PEN", total: 118, intentos: 2,
              cdr: { codigo: "0", descripcion: "La Factura numero F001-12, ha sido aceptada", observaciones: ["4287 - El dato ingresado como parte de la dirección no cumple el formato"] },
            },
            { id: "f-sol-11", tipo: "01", serie: "F001", numero: 11, fecha_emision: sumarDias(hoy, -2), estado: "RECHAZADO", moneda: "PEN", total: 59, intentos: 1, ultimo_error: "RUC del receptor no existe en SUNAT", cdr: { codigo: "2017", descripcion: "El RUC del receptor no es válido", observaciones: [] } },
            { id: "f-sol-13", tipo: "03", serie: "B001", numero: 3, fecha_emision: sumarDias(hoy, -3), estado: "FIRMADO", moneda: "PEN", total: 25.5, intentos: 0 },
          ]
        : [],
      eventos: completa
        ? [
            { comprobante: "F001-00000012", estado_anterior: "ENVIADO", estado_nuevo: "ACEPTADO_CON_OBS", detalle: "CDR recibido con observaciones", ocurrido_en: `${sumarDias(hoy, -1)}T15:00:00Z` },
            { comprobante: "F001-00000012", estado_anterior: "FIRMADO", estado_nuevo: "ENVIADO", ocurrido_en: `${sumarDias(hoy, -1)}T14:59:00Z` },
            { comprobante: "F001-00000011", estado_nuevo: "RECHAZADO", detalle: "SUNAT rechazó el comprobante", ocurrido_en: `${sumarDias(hoy, -2)}T10:00:00Z` },
          ]
        : [],
      outbox: completa
        ? {
            total: 12,
            proximas: [
              { agregado: "DOCUMENTO", agregado_id: "f-sol-13", accion: "ENVIAR", intentos: 3, siguiente_intento: `${hoy}T16:00:00Z`, ultimo_error: "SUNAT no responde" },
              { agregado: "DOCUMENTO", agregado_id: "f-sol-14", accion: "ENVIAR", intentos: 0, siguiente_intento: `${hoy}T16:05:00Z` },
            ],
          }
        : { total: 0, proximas: [] },
    });
  }),

  /**
   * Como el backend (#187): solo el administrador; un id que no es UUID es 400 y una empresa que no existe, 404. Pasar al entorno que ya tiene es 409
   * `ENTORNO_SIN_CAMBIOS`; con envíos pendientes en el outbox (la sembrada «Panadería Sol» tiene doce), 409 `EMPRESA_CON_ENVIOS_PENDIENTES`.
   * Revocar una key ya revocada es 409 `API_KEY_YA_REVOCADA`; una key que no existe, 404. La prueba de conexión sin credenciales SOL es 409
   * `SOL_NO_CARGADAS`; con ellas, el resultado depende de la empresa (Sol: conectado; Luna: error definitivo de SUNAT; Cliente 04: sin respuesta útil).
   * **Los cambios que sí proceden no se guardan**: cambiar el entorno o revocar una key altera lo que cuentan las demás specs que corren a la vez contra
   * este mock (los filtros por entorno, las keys del detalle); que lo hagan de verdad lo prueba `AccionesDeEmpresaE2ETest` y los componentes.
   */
  /**
   * Como el backend (#184): solo el administrador; un id que no es UUID es 400; el usuario se busca DENTRO de la cuenta de la ruta (404 si es de otra) y uno
   * desactivado es 409 `USUARIO_INACTIVO`. Devuelve una sesión de soporte de 15 minutos. **El mock del cliente es otro mundo** (sus cuentas y usuarios no son los
   * del backoffice), así que el token es el del cliente de demostración con la marca de soporte: lo que se comprueba aquí es el recorrido, no los datos.
   */
  http.post(`${BASE}/v1/admin/cuentas/:id/usuarios/:usuarioId/impersonar`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("cuentaId");
    if (!esUuid(String(params.usuarioId))) return parametroInvalido("usuarioId");
    const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
    const usuario = cuenta ? usuariosDeCuenta(cuenta).find((u) => u.id === params.usuarioId) : undefined;
    if (!cuenta || !usuario) return fail(404, "NO_ENCONTRADO", "El usuario no existe en esta cuenta");
    if (!usuario.activo) return fail(409, "USUARIO_INACTIVO", `El usuario ${usuario.email} está desactivado`);
    const expira = Math.floor(Date.now() / 1000) + 15 * 60;
    return ok({
      access_token: fakeJwt({ sub: "u-demo", cuenta: "c-demo", rol: "ADMIN", imp: "admin-demo", exp: expira, ue: usuario.email, cx: cuenta.id }),
      expira_en: new Date(expira * 1000).toISOString(),
      usuario: { id: usuario.id, cuenta_id: cuenta.id, email: usuario.email, rol: usuario.rol, correo_verificado: Boolean(usuario.correo_verificado_en) },
    });
  }),

  http.post(`${BASE}/v1/admin/empresas/:id/entorno`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const e = db.empresasAdmin.find((x) => x.id === params.id);
    if (!e) return fail(404, "NO_ENCONTRADO", "La empresa no existe");
    const { entorno } = (await request.json()) as { entorno?: string };
    // Como el backend: sin entorno lo rechaza el servicio (422 ENTORNO_INVALIDO); uno que no existe no se puede leer como enum (400 JSON_INVALIDO).
    if (entorno === undefined || entorno === null) return fail(422, "ENTORNO_INVALIDO", "El entorno es obligatorio");
    if (entorno !== "BETA" && entorno !== "PRODUCCION") return jsonInvalido();
    if (e.entorno === entorno) return fail(409, "ENTORNO_SIN_CAMBIOS", `La empresa ya está en ${entorno}`);
    if (e.id === idEmpresaMock(1)) return fail(409, "EMPRESA_CON_ENVIOS_PENDIENTES", "La empresa tiene envíos pendientes a SUNAT: espera a que terminen antes de cambiar el entorno");
    return ok({ empresa_id: e.id, desde: e.entorno, hacia: entorno });
  }),

  http.post(`${BASE}/v1/admin/empresas/:id/api-keys/:apiKeyId/revocar`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    if (!esUuid(String(params.apiKeyId))) return parametroInvalido("apiKeyId");
    const e = db.empresasAdmin.find((x) => x.id === params.id);
    if (!e) return fail(404, "NO_ENCONTRADO", "La empresa no existe");
    // Solo «Panadería Sol» trae keys sembradas: la 2 está vigente y la 1 ya revocada.
    if (e.id !== idEmpresaMock(1) || (params.apiKeyId !== idApiKeyMock(1) && params.apiKeyId !== idApiKeyMock(2))) return fail(404, "NO_ENCONTRADO", "La API key no existe");
    if (params.apiKeyId === idApiKeyMock(1)) return fail(409, "API_KEY_YA_REVOCADA", "La API key ya estaba revocada");
    return ok({ api_key_id: params.apiKeyId, revocada_en: new Date().toISOString() });
  }),

  http.post(`${BASE}/v1/admin/empresas/:id/prueba-de-conexion`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const e = db.empresasAdmin.find((x) => x.id === params.id);
    if (!e) return fail(404, "NO_ENCONTRADO", "La empresa no existe");
    if (!e.tiene_credenciales_sol) return fail(409, "SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL cargadas");
    if (e.id === idEmpresaMock(2)) return ok({ resultado: "RECHAZADO", entorno: e.entorno, codigo: "1033", mensaje: "El ticket no existe" });
    if (e.id === idEmpresaMock(4)) return ok({ resultado: "SIN_RESPUESTA", entorno: e.entorno, codigo: "0109", mensaje: "Tiempo de espera agotado llamando a SUNAT" });
    return ok({ resultado: "CONECTADO", entorno: e.entorno });
  }),

  /**
   * Como el backend (#181): solo el administrador; 404 `NO_ENCONTRADO` si no existe. La primera cuenta sembrada («Panadería Sol») trae
   * un detalle completo (dos usuarios, un certificado por vencer, un comprobante, una acción de la bitácora); las demás, lo mínimo.
   * Fechas del certificado relativas a hoy, para que «por vencer» no caduque con el calendario.
   */
  http.get(`${BASE}/v1/admin/cuentas/:id`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    // Un id que no es UUID no llega a buscarse: el backend lo rechaza al convertir la ruta. Si la página no lo filtrara, sería un 400, no un 404.
    // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException).
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
    if (!cuenta) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    const enDias = (n: number) => sumarDias(hoyLima(), n);
    const completa = cuenta.id === idCuentaMock(1);
    const { empresas, historial_estado: cambiosDelMock, ...base } = cuenta;
    const detalle = {
      ...base,
      estado: estadoDeCuenta(cuenta),
      usuarios: usuariosDeCuenta(cuenta),
      empresas: empresas.map((e, i) => ({
        id: `e-${cuenta.id}-${i}`,
        ruc: e.ruc,
        razon_social: e.razon_social,
        entorno: "BETA",
        tiene_certificado: completa,
        ...(completa ? { certificado_vigente_hasta: enDias(10) } : {}),
        tiene_credenciales_sol: completa,
      })),
      comprobantes: completa
        ? [
            {
              id: "f-sol-1",
              empresa_id: `e-${cuenta.id}-0`,
              ruc: empresas[0].ruc,
              tipo: "01",
              serie: "F001",
              numero: 7,
              fecha_emision: "2026-10-01",
              estado: "ACEPTADO",
              moneda: "PEN",
              total: 118,
            },
          ]
        : [],
      // Del más reciente al más antiguo, como el backend. `ACCION_FUTURA` no está en el catálogo del portal: se muestra con su código.
      // El correo del administrador (H11) falta en la acción de la clave de la plataforma y en la de un administrador que ya no existe (ACCION_FUTURA).
      eventos: completa
        ? [
            { accion: "ACCION_FUTURA", actor: "ADMINISTRADOR", ocurrido_en: "2026-09-04T09:00:00Z" },
            {
              accion: "SUSPENDER_CUENTA",
              actor: "ADMINISTRADOR",
              administrador: "admin@khipu.pe",
              ocurrido_en: "2026-09-03T09:00:00Z",
              detalle: "motivo=Factura de agosto sin pagar",
            },
            { accion: "CREAR_TENANT", actor: "CLAVE_PLATAFORMA", ocurrido_en: "2026-09-02T10:00:00Z" },
            { accion: "CREAR_CUENTA", actor: "ADMINISTRADOR", administrador: "admin@khipu.pe", ocurrido_en: "2026-09-01T15:00:00Z", detalle: "ruc=20100047226" },
          ]
        : [],
      // Como el backend (H15): todos los cambios de estado, del más reciente al más antiguo; los que hizo el mock van primero.
      historial_estado: [
        ...(cambiosDelMock ?? []),
        ...(completa
          ? [
              {
                accion: "SUSPENDER_CUENTA",
                actor: "ADMINISTRADOR",
                administrador: "admin@khipu.pe",
                ocurrido_en: "2026-09-03T09:00:00Z",
                detalle: "motivo=Factura de agosto sin pagar",
              },
            ]
          : []),
      ],
    };
    return ok(detalle);
  }),

  /**
   * Como el backend (#182): solo el administrador; un id que no es UUID es 400 y uno que no existe, 404. Suspender una cuenta ya suspendida y
   * reactivar una activa son 409, con su código propio. Un motivo de más de 200 caracteres (recortado) es 422 y no suspende.
   */
  http.post(`${BASE}/v1/admin/cuentas/:id/suspender`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException).
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
    if (!cuenta) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    const texto = await request.text();
    const motivo = texto ? ((JSON.parse(texto) as { motivo?: string }).motivo ?? "").trim() : "";
    if (motivo.length > 200) return fail(422, "MOTIVO_INVALIDO", "El motivo no puede pasar de 200 caracteres");
    if (cuenta.suspendida_en) return fail(409, "CUENTA_YA_SUSPENDIDA", "La cuenta ya está suspendida");
    cuenta.suspendida_en = new Date().toISOString();
    anotarCambioDeEstado(cuenta, "SUSPENDER_CUENTA", cuenta.suspendida_en, motivo);
    // Como el backend (revisión de #201): el estado es el mismo que en el detalle; una cuenta de baja responde BAJA y su fecha de baja.
    return ok({ cuenta_id: cuenta.id, estado: estadoDeCuenta(cuenta), suspendida_en: cuenta.suspendida_en, ...(cuenta.baja_en ? { baja_en: cuenta.baja_en } : {}) });
  }),

  http.post(`${BASE}/v1/admin/cuentas/:id/reactivar`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException).
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
    if (!cuenta) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    if (!cuenta.suspendida_en) return fail(409, "CUENTA_NO_SUSPENDIDA", "La cuenta no está suspendida");
    delete cuenta.suspendida_en;
    anotarCambioDeEstado(cuenta, "REACTIVAR_CUENTA", new Date().toISOString());
    return ok({ cuenta_id: cuenta.id, estado: estadoDeCuenta(cuenta), ...(cuenta.baja_en ? { baja_en: cuenta.baja_en } : {}) });
  }),

  /**
   * Como el backend (#201): solo el administrador; un id que no es UUID es 400 y uno que no existe, 404. Dar de baja una cuenta ya de baja y reponer
   * una que no lo está son 409, con su código propio; un motivo de más de 200 caracteres (recortado) es 422. La baja es independiente de la
   * suspensión y no corta el acceso del cliente.
   */
  http.post(`${BASE}/v1/admin/cuentas/:id/baja`, async ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException).
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
    if (!cuenta) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    const texto = await request.text();
    const motivo = texto ? ((JSON.parse(texto) as { motivo?: string }).motivo ?? "").trim() : "";
    if (motivo.length > 200) return fail(422, "MOTIVO_INVALIDO", "El motivo no puede pasar de 200 caracteres");
    if (cuenta.baja_en) return fail(409, "CUENTA_YA_DE_BAJA", "La cuenta ya está dada de baja");
    cuenta.baja_en = new Date().toISOString();
    anotarCambioDeEstado(cuenta, "DAR_DE_BAJA_CUENTA", cuenta.baja_en, motivo);
    return ok({ cuenta_id: cuenta.id, baja_en: cuenta.baja_en });
  }),

  http.post(`${BASE}/v1/admin/cuentas/:id/reponer`, ({ request, params }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException).
    if (!esUuid(String(params.id))) return parametroInvalido("id");
    const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
    if (!cuenta) return fail(404, "NO_ENCONTRADO", "La cuenta no existe");
    if (!cuenta.baja_en) return fail(409, "CUENTA_NO_DE_BAJA", "La cuenta no está dada de baja");
    delete cuenta.baja_en;
    anotarCambioDeEstado(cuenta, "REPONER_CUENTA", new Date().toISOString());
    return ok({ cuenta_id: cuenta.id });
  }),

  /**
   * Como el backend (#183): solo el administrador; ids que no son UUID, 400; el usuario se busca DENTRO de la cuenta de la ruta (404
   * `NO_ENCONTRADO` si es de otra); un usuario desactivado, 409 `USUARIO_INACTIVO`; reenviar la verificación a quien ya la tiene, 409
   * `CORREO_YA_VERIFICADO`; y un correo que empieza con `sin-correo` simula un servidor sin SMTP: 503 `CORREO_NO_CONFIGURADO`.
   * **No guarda nada** (ni la bitácora): las specs de la corrida comparten este mock en paralelo y la bitácora de «Panadería Sol» se cuenta.
   * Que el envío quede registrado lo prueba el e2e real del backend (`SoporteDeAccesoE2ETest`).
   */
  ...(["restablecimiento", "verificacion"] as const).map((accion) =>
    http.post(`${BASE}/v1/admin/cuentas/:id/usuarios/:usuarioId/${accion}`, ({ request, params }) => {
      if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
      // Mismo código que el backend (`GlobalExceptionHandler`, MethodArgumentTypeMismatchException), que nombra el primer parámetro que no convierte.
      const invalido = !esUuid(String(params.id)) ? "cuentaId" : !esUuid(String(params.usuarioId)) ? "usuarioId" : null;
      if (invalido) return parametroInvalido(invalido);
      const cuenta = db.cuentasAdmin.find((c) => c.id === params.id);
      const usuario = cuenta ? usuariosDeCuenta(cuenta).find((u) => u.id === params.usuarioId) : undefined;
      if (!usuario) return fail(404, "NO_ENCONTRADO", "El usuario no existe en esta cuenta");
      if (!usuario.activo) return fail(409, "USUARIO_INACTIVO", `El usuario ${usuario.email} está desactivado`);
      if (accion === "verificacion" && usuario.correo_verificado_en) return fail(409, "CORREO_YA_VERIFICADO", `El correo de ${usuario.email} ya está verificado`);
      if (usuario.email.startsWith("sin-correo")) return fail(503, "CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado en el servidor: no se mandó nada");
      return ok({ usuario_id: usuario.id, correo: usuario.email });
    }),
  ),

  /**
   * Como el backend (#188): solo el administrador; valida todo antes de escribir; el correo y el RUC son únicos en TODA la plataforma.
   * La API key viaja solo en esta respuesta. Un correo que empieza con `sin-correo` simula un SMTP caído: el alta queda hecha y
   * `invitacion_enviada` es `false`.
   *
   * **No guarda la cuenta**: valida contra los datos sembrados, pero no los modifica. Las specs de la corrida comparten este mock en
   * paralelo y `admin-cuentas.spec.ts` cuenta exactamente 12 cuentas; guardar altas aquí las rompería y haría chocar a las propias
   * specs del alta por correo y RUC repetidos. Que la cuenta aparezca en el listado lo prueba el e2e real del backend
   * (`AltaAsistidaE2ETest`).
   */
  http.post(`${BASE}/v1/admin/cuentas`, async ({ request }) => {
    if (!claimsAdmin(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const b = (await request.json()) as {
      nombre?: string;
      email?: string;
      telefono?: string;
      empresa?: { ruc?: string; razon_social?: string; entorno?: "BETA" | "PRODUCCION" };
      serie?: { tipo?: string; serie?: string };
    };
    const nombre = (b.nombre ?? "").trim();
    const email = (b.email ?? "").trim().toLowerCase();
    const ruc = b.empresa?.ruc ?? "";
    const razonSocial = (b.empresa?.razon_social ?? "").trim();
    // El celular es obligatorio, como en el backend (`@NotBlank` en AltaAsistidaRequest).
    if (!b.empresa || !b.serie || !nombre || !email || !razonSocial || !(b.telefono ?? "").trim()) return fail(422, "VALIDACION", "Faltan datos obligatorios");
    if (nombre.length > 150 || email.length > 254) return fail(422, "VALIDACION", "Nombre o correo demasiado largos");
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return fail(422, "EMAIL_INVALIDO", "Correo electrónico inválido");
    if (!telefonoSchema.safeParse(b.telefono).success) return fail(422, "TELEFONO_INVALIDO", "El celular debe tener 9 dígitos y empezar con 9 (Perú)");
    if (!rucValido(ruc)) return fail(422, "RUC_INVALIDO", `Empresa: el dígito verificador del RUC ${ruc} no es válido; revise el número`);
    if (!serieCoincideConTipo(b.serie.tipo ?? "", b.serie.serie ?? "")) return fail(422, "SERIE_INVALIDA", `Serie ${b.serie.serie} no válida para el tipo ${b.serie.tipo}`);
    if (db.cuentasAdmin.some((c) => c.email === email) || db.usuariosPorEmail.has(email)) return fail(409, "DUPLICADO", "Ya existe una cuenta con ese correo");
    const rucRepetido =
      db.cuentasAdmin.some((c) => c.empresas.some((e) => e.ruc === ruc)) || [...db.empresasPorCuenta.values()].flat().some((e) => e.ruc === ruc);
    if (rucRepetido) return fail(409, "DUPLICADO", `Ya existe una empresa con RUC ${ruc}`);

    // Idempotency-Key (#219), como el backend: el mismo pedido con la misma clave devuelve la misma respuesta (la misma API key) con
    // 200; con otro pedido, 422. El mock no guarda el alta, así que no hay ventana: la respuesta se recuerda mientras corra el servidor.
    const clave = request.headers.get("idempotency-key");
    const huella = JSON.stringify(b);
    const previa = clave ? db.clavesAlta.get(clave) : undefined;
    if (previa) {
      if (previa.huella !== huella) return fail(422, "IDEMPOTENCIA_INVALIDA", `La clave de idempotencia ${clave} ya se usó con otro contenido`);
      return ok(previa.respuesta, 200);
    }

    const respuesta = {
      cuenta_id: nuevoId("ca"),
      tenant_id: nuevoId("t"),
      ruc,
      api_key: `fk_mock_${contador}${Math.random().toString(36).slice(2, 12)}`,
      serie: { tipo: b.serie.tipo ?? "", serie: (b.serie.serie ?? "").toUpperCase() },
      invitacion_enviada: !email.startsWith("sin-correo"),
    };
    if (clave) db.clavesAlta.set(clave, { huella, respuesta });
    return ok(respuesta, 201);
  }),

  http.get(`${BASE}/v1/empresas`, ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    return ok(db.empresasPorCuenta.get(c.cuenta) ?? []);
  }),

  http.post(`${BASE}/v1/empresas`, async ({ request }) => {
    const c = claims(request);
    if (!c) return fail(401, "NO_AUTORIZADO", "Token inválido");
    if (!correoVerificado(c.sub)) return fail(403, "CORREO_SIN_VERIFICAR", "Verifica tu correo para continuar: te enviamos un enlace");
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
    // Como el backend (#107): credenciales nuevas levantan el rechazo y los envíos en espera se reanudan.
    empresa.credenciales_sol_rechazadas = null;
    return new HttpResponse(null, { status: 204 });
  }),

  http.put(`${BASE}/v1/empresa/datos-fiscales`, async ({ request }) => {
    const empresaId = request.headers.get("x-empresa");
    const empresa = [...db.empresasPorCuenta.values()].flat().find((e) => e.id === empresaId);
    if (!empresa) return fail(404, "NO_ENCONTRADO", "Empresa no encontrada");
    const body = (await request.json()) as { domicilio?: { ubigeo: string; direccion: string; urbanizacion?: string; codigo_establecimiento?: string } | null; cuenta_detracciones?: string | null; nombre_comercial?: string | null; padron_tasa_especial_igv?: boolean };
    if (body.domicilio) {
      const u = UBIGEOS.find((x) => x.codigo === body.domicilio?.ubigeo);
      if (!u) return fail(422, "DOMICILIO_INVALIDO", "4093 - El ubigeo debe ser un código de 6 dígitos del catálogo 13 (INEI)");
      empresa.domicilio = { ubigeo: u.codigo, direccion: body.domicilio.direccion, urbanizacion: body.domicilio.urbanizacion ?? null,
        distrito: u.extra.Distrito, provincia: u.extra.Provincia, departamento: u.extra.Departamento, codigo_establecimiento: body.domicilio.codigo_establecimiento || "0000" };
    } else empresa.domicilio = null;
    empresa.tiene_domicilio = Boolean(empresa.domicilio);
    empresa.cuenta_detracciones = body.cuenta_detracciones ?? null;
    empresa.nombre_comercial = body.nombre_comercial ?? null;
    // Campo de dinero: el padrón decide si el IGV de los comprobantes de esta empresa sale al 10.5 % o al 18 %
    // (se lee más abajo, al emitir). Sin persistirlo, la casilla se marcaba, se guardaba «con éxito» y volvía
    // desmarcada al recargar, y ningún e2e podía verlo.
    empresa.padron_tasa_especial_igv = body.padron_tasa_especial_igv ?? false;
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

  /**
   * Consulta del padrón reducido de SUNAT (#361), con los mismos desenlaces que `ConsultarPadronRucService`. 20100070970 está activo y habido, 20160109441 de baja y
   * no habido, 20602160280 activo y no habido; 20999999990 agota el cupo (429), 20777777778 dice que el padrón no está cargado (503) y 20888888889 corta la
   * conexión. Cualquier otro RUC válido no figura (404).
   */
  http.get(`${BASE}/v1/consultas/ruc/:ruc`, ({ params }) => {
    const ruc = String(params.ruc);
    if (!/^\d{11}$/.test(ruc)) return fail(400, "PARAMETRO_INVALIDO", "Consulta de RUC: el RUC tiene 11 dígitos");
    if (ruc === "20999999990") return fail(429, "DEMASIADAS_CONSULTAS", "Se superó el máximo de consultas de RUC. Espera un momento antes de volver a consultar");
    if (ruc === "20777777778") return fail(503, "PADRON_NO_CARGADO", "El padrón de SUNAT todavía no está cargado: no se puede confirmar si el RUC existe");
    if (ruc === "20888888889") return HttpResponse.error();
    const c = padronMock[ruc];
    if (!c) return fail(404, "NO_ENCONTRADO", "El RUC no figura en el padrón de SUNAT. Puede ser reciente: el padrón se actualiza cada día");
    // Como el backend (`non_null`): un RUC sin dirección no trae la clave `direccion`, no `direccion: null`.
    const { direccion, ...resto } = c;
    return ok({ ruc, ...resto, ...(direccion ? { direccion } : {}), activo: c.estado === "ACTIVO", habido: c.condicion === "HABIDO", ubigeo: "150101", actualizado_al: new Date().toISOString() });
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

  // #405: el cálculo sin emitir. El mock usa el mismo helper local que la emisión simulada (sin descuentos, cargos ni ISC) y le suma el redondeo, con la misma
  // regla del dominio (3303: hasta ±1.00 y sin dejar el total en negativo).
  // Los descuentos y cargos (#310) los recibe pero no los aplica: reproducirlos sería portar `Totales` al mock. El cálculo real se prueba contra el backend.
  http.post(`${BASE}/v1/facturas/calcular`, async ({ request }) => {
    const body = (await request.json()) as {
      items: Array<{ cantidad: number; precio_unitario: number; tipo_afectacion_igv: string }>;
      redondeo?: number;
      fecha_emision?: string;
      forma_pago?: FormaPagoPedido;
      tipo_operacion?: string;
      cliente: { tipo_doc: string; pais?: string };
      exportacion?: { pais_uso?: string };
      serie?: string;
      moneda?: string;
      detraccion?: DetraccionPedido;
      percepcion?: PercepcionPedido;
      anticipos?: AnticipoPedido[];
      retencion_igv?: RetencionPedido;
      tipo_cambio?: number;
    };
    const porExportacion = rechazoPorExportacionMock(body, (body.serie ?? "").startsWith("B"));
    if (porExportacion) return porExportacion;
    const redondeo = body.redondeo ?? 0;
    if (Math.abs(redondeo) > 1) return fail(422, "REDONDEO_INVALIDO", "3303 - El redondeo del importe total admite 2 decimales y no puede superar 1.00 en valor absoluto");
    const t = calcularTotales(
      body.items.map((i) => ({ cantidad: i.cantidad, precioUnitario: i.precio_unitario, tipoAfectacionIgv: i.tipo_afectacion_igv })),
      empresaDe(request)?.padron_tasa_especial_igv ? 10.5 : 18,
    );
    const ant = anticiposMock(body.anticipos, t, empresaDe(request)?.padron_tasa_especial_igv ? 10.5 : 18, body.moneda ?? "PEN", "");
    if (ant.error) return ant.error;
    const tAnt = ant.totales;
    const total = Math.round((tAnt.total + redondeo) * 100) / 100;
    if (total < 0) return fail(422, "REDONDEO_INVALIDO", `3303 - El redondeo deja el importe total en negativo (${total.toFixed(2)})`);
    const forma = formaPagoMock(body.forma_pago, body.fecha_emision ?? "0000-00-00", total);
    if ("error" in forma && forma.error) return forma.error;
    const det = detraccionMock(body.tipo_operacion, body.detraccion, body.moneda ?? "PEN", total);
    if (det.error) return det.error;
    const per = percepcionMock(body.tipo_operacion, body.percepcion, body.moneda ?? "PEN", body.forma_pago?.tipo === "credito", total);
    if (per.error) return per.error;
    const ret = retencionMock(body.retencion_igv, (body.serie ?? "").startsWith("B"), total);
    if (ret.error) return ret.error;
    const mon = monedaMock(body.moneda ?? "PEN", body.tipo_cambio, total);
    if (mon.error) return mon.error;
    return ok({ detraccion: det.detraccion ?? null, percepcion: per.percepcion ?? null, retencion_igv: ret.retencion ?? null, tipo_cambio: body.tipo_cambio ?? null, total_en_soles: mon.totalEnSoles, anticipos: ant.anticipos, totales: { gravado: tAnt.gravado, exonerado: tAnt.exonerado, inafecto: tAnt.inafecto, gratuito: t.gratuito, igv: tAnt.igv, total_anticipos: tAnt.total_anticipos, redondeo, total } });
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
      tipo_operacion?: string;
      items: Array<{ descripcion: string; unidad: string; cantidad: number; precio_unitario: number; tipo_afectacion_igv: string; icbper?: boolean }>;
    };

    // #303: el `@NotBlank` de `ClienteDto` corre antes que el servicio y que el dominio (347-H2), y en blanco es lo que `String.trim()` de
    // Java deja vacío: solo los caracteres hasta U+0020, no el espacio de no separación (que llega al dominio y vuelve como 2022).
    const razonCliente = body.cliente?.razon_social ?? "";
    const enBlanco = erroresDeUnidadEnBlanco(body.items);
    if (enBlancoParaJava(razonCliente)) enBlanco["cliente.razonSocial"] = ["no debe estar vacío"];
    if (Object.keys(enBlanco).length) return validacionFallida(enBlanco);

    // #20: como el backend, la letra de la serie decide el tipo (F factura, B boleta) y cada uno valida su comprador.
    const boleta = body.serie?.startsWith("B");
    const tipo = boleta ? "03" : "01";
    const series = db.seriesPorEmpresa.get(empresaId) ?? [];
    const serie = series.find((s) => s.serie === body.serie && s.tipo === tipo && s.activa);
    if (!serie) return fail(422, "SERIE_NO_CONFIGURADA", `La serie ${body.serie} no está registrada como serie de ${boleta ? "boleta" : "factura"} activa`);
    const { tipo_doc: tipoDoc, num_doc: numDoc } = body.cliente ?? { tipo_doc: "", num_doc: "" };
    // Una exportación (#311) lleva el documento del exterior, no un RUC; sus reglas del receptor las dice `rechazoPorExportacionMock`.
    const esExp = /^020[0-8]$/.test((body as { tipo_operacion?: string }).tipo_operacion ?? "");
    if (!boleta && !esExp && !/^\d{11}$/.test(numDoc ?? "")) return fail(422, "RECEPTOR_INVALIDO", "2017 - El RUC del adquirente debe tener 11 dígitos");
    if (boleta && tipoDoc === "1" && !/^\d{8}$/.test(numDoc)) return fail(422, "RECEPTOR_INVALIDO", "4207 - El DNI del comprador tiene 8 dígitos");
    // Sin código, como `Receptor` (274-H3): Boleta2_0 no tiene regla propia para el comprador sin documento.
    if (boleta && tipoDoc === "-" && numDoc !== "-") return fail(422, "RECEPTOR_INVALIDO", "Sin documento (tipo_doc «-»), el número de documento también es «-»");
    // #303: la 2022 de `Receptor`, contada sin los espacios de los extremos (los mismos que quita `trim` de JS); en boleta rige igual sin documento.
    const nombreCliente = razonCliente.trim();
    if (nombreCliente.length < 3 || nombreCliente.length > 1500 || /[\x00-\x1F\x7F-\x9F]/.test(razonCliente))
      return fail(422, "RECEPTOR_INVALIDO", `2022 - ${boleta ? "El nombre del comprador" : "La razón social del receptor"} tiene de 3 a 1500 caracteres, sin saltos de línea`);
    if (!body.items?.length) return fail(422, "ITEMS_REQUERIDOS", "Un comprobante necesita al menos un ítem");
    // #292: el plazo del backend (`PlazoEnvio`): ni futura ni más atrás que 3 días (factura, 2108) o 7 (boleta, 1079).
    if (body.fecha_emision > hoyLima()) return fail(422, "FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
    if (body.fecha_emision < fechaMinimaDeEmision(boleta ? "boleta" : "factura", hoyLima()))
      return fail(422, "FECHA_INVALIDA", boleta
        ? `1079 - Pasado el envío individual, SUNAT solo recibe esta boleta en un resumen diario, y el plazo para informarla en él venció el ${sumarDias(body.fecha_emision, PLAZO_ENVIO.boleta.dias)} (${PLAZO_ENVIO.boleta.dias} días calendario) (fecha de emisión ${body.fecha_emision})`
        : `2108 - Presentación fuera de fecha: el plazo de envío venció el ${sumarDias(body.fecha_emision, PLAZO_ENVIO.factura.dias)} (${PLAZO_ENVIO.factura.dias} días calendario) (fecha de emisión ${body.fecha_emision})`);
    const porExportacion = rechazoPorExportacionMock(body as Parameters<typeof rechazoPorExportacionMock>[0], boleta);
    if (porExportacion) return porExportacion;
    // #308: antes de numerar, como el backend.
    const porUnidad = rechazoPorUnidadMock(body.items);
    if (porUnidad) return porUnidad;
    // #354: el tope de líneas, el ICBPER, el precio en cero y el NRUS, también antes de numerar.
    const porLineas = rechazoPorLineasMock(body.items, boleta ? "boleta" : "factura", body.tipo_operacion, empresaDe(request)?.padron_tasa_especial_igv ? 10.5 : 18, body.fecha_emision);
    if (porLineas) return porLineas;

    // Idempotency-Key (#115), como el backend: la misma clave con el mismo pedido devuelve la factura ya emitida con 200; con otro
    // pedido, 422. La huella es el JSON del cuerpo, que el formulario arma siempre igual.
    const clave = request.headers.get("idempotency-key");
    const huella = JSON.stringify(body);
    const previa = clave ? db.clavesEmision.get(`${empresaId}|${clave}`) : undefined;
    if (previa) {
      if (previa.huella !== huella) return fail(422, "IDEMPOTENCIA_INVALIDA", `La clave de idempotencia ${clave} ya se usó con otro contenido`);
      const emitida = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === previa.id);
      if (emitida) return okConAvisos(emitida, avisosDelComprador(emitida.receptor?.num_doc), 200);
    }
    const porTope = rechazoPorTopeDelPlanMock(request);
    if (porTope) return porTope;

    serie.ultimo_numero += 1;
    // La tasa sale de la empresa, igual que en el diálogo: si el fixture entra al padrón de tasa especial, mock y
    // formulario siguen de acuerdo en vez de romper el e2e con un descuadre que parecería un bug del helper.
    const tasaIgv = empresaDe(request)?.padron_tasa_especial_igv ? 10.5 : 18;
    const t0 = calcularTotales(
      body.items.map((i) => ({ cantidad: i.cantidad, precioUnitario: i.precio_unitario, tipoAfectacionIgv: i.tipo_afectacion_igv })),
      tasaIgv,
    );
    // #312: los anticipos descuentan de la base de su afectación; el anticipo se busca entre los comprobantes guardados de la empresa.
    const ant = anticiposMock((body as { anticipos?: AnticipoPedido[] }).anticipos, t0, tasaIgv, body.moneda, numDoc, db.facturasPorEmpresa.get(empresaId) ?? []);
    if (ant.error) return ant.error;
    const t = { ...t0, ...ant.totales };
    const mon = monedaMock(body.moneda, (body as { tipo_cambio?: number }).tipo_cambio, t.total);
    if (mon.error) return mon.error;
    // Con tipo de cambio (#316) el tope de S/ 700 se mide en soles; sin él, una moneda extranjera siempre pide el documento.
    if (boleta && tipoDoc === "-" && (body.moneda === "PEN" ? t.total > 700 : mon.totalEnSoles == null || mon.totalEnSoles > 700))
      return fail(422, "RECEPTOR_INVALIDO", `Una boleta de más de S/ 700.00 (o en otra moneda) identifica al comprador con su documento; esta es de ${body.moneda} ${t.total.toFixed(2)}`);
    const forma = formaPagoMock((body as { forma_pago?: FormaPagoPedido }).forma_pago, body.fecha_emision, t.total);
    if ("error" in forma && forma.error) return forma.error;
    const det = detraccionMock((body as { tipo_operacion?: string }).tipo_operacion, (body as { detraccion?: DetraccionPedido }).detraccion, body.moneda, t.total);
    if (det.error) return det.error;
    const per = percepcionMock((body as { tipo_operacion?: string }).tipo_operacion, (body as { percepcion?: PercepcionPedido }).percepcion, body.moneda, (body as { forma_pago?: FormaPagoPedido }).forma_pago?.tipo === "credito", t.total);
    if (per.error) return per.error;
    const refs = referenciasMock(body as ReferenciasPedido, boleta);
    if (refs.error) return refs.error;
    const ret = retencionMock((body as { retencion_igv?: RetencionPedido }).retencion_igv, boleta, t.total);
    if (ret.error) return ret.error;
    const id = nuevoId("f");
    const comprobante: Comprobante = {
      id,
      tipo,
      serie: body.serie,
      numero: serie.ultimo_numero,
      fecha_emision: body.fecha_emision,
      moneda: body.moneda,
      tipo_cambio: (body as { tipo_cambio?: number }).tipo_cambio ?? null,
      total_en_soles: mon.totalEnSoles,
      tipo_operacion: (body as { tipo_operacion?: string }).tipo_operacion ?? "0101",
      receptor: { ...body.cliente, direccion: body.cliente.direccion ?? null },
      items: body.items.map((i) => ({ codigo: null, ...i })),
      estado_documento: "ACEPTADO",
      hash: `hash-${id}`,
      nombre_archivo: `20123456786-${tipo}-${body.serie}-${String(serie.ultimo_numero).padStart(8, "0")}`,
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: `La ${boleta ? "Boleta" : "Factura"} numero ${body.serie}-${serie.ultimo_numero}, ha sido aceptada`, observaciones: [] },
      totales: { gravado: t.gravado, exonerado: t.exonerado, inafecto: t.inafecto, igv: t.igv, total: t.total, total_anticipos: t.total_anticipos },
      anticipos: ant.anticipos ?? undefined,
      forma_pago: "formaPago" in forma ? forma.formaPago : { tipo: "contado", monto_pendiente: null, cuotas: [] },
      detraccion: det.detraccion ?? null,
      percepcion: per.percepcion ?? null,
      retencion_igv: ret.retencion ?? null,
      referencias: refs.referencias,
      observaciones: refs.observaciones,
      enlaces: { xml: `/v1/facturas/${id}/xml`, pdf: `/v1/facturas/${id}/pdf`, cdr: `/v1/facturas/${id}/cdr` },
    };
    db.facturasPorEmpresa.set(empresaId, [comprobante, ...(db.facturasPorEmpresa.get(empresaId) ?? [])]);
    if (clave) db.clavesEmision.set(`${empresaId}|${clave}`, { huella, id });
    return okConAvisos(comprobante, avisosDelComprador(numDoc), 201);
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

  /**
   * Como el backend (#15): el resumen de los comprobantes de la empresa en un rango de fecha de emisión (inclusive; un lado ausente lo deja abierto). Emitidos: todo menos lo
   * que nunca se firmó; aceptados: con o sin observaciones; atención requerida: rechazados, error de envío y fuera de plazo, cada uno aparte; facturado por moneda con lo
   * aceptado y lo que está en camino, las notas de crédito restando. **Va antes de `/v1/facturas/:id`**: si no, «resumen» se tomaría por un id.
   */
  http.get(`${BASE}/v1/facturas/resumen`, ({ request }) => {
    if (!claims(request)) return fail(401, "NO_AUTORIZADO", "Token inválido");
    const empresaId = request.headers.get("x-empresa") ?? "";
    const url = new URL(request.url);
    const desde = url.searchParams.get("desde");
    const hasta = url.searchParams.get("hasta");
    if ((desde && !fechaIsoValidaMock(desde)) || (hasta && !fechaIsoValidaMock(hasta))) return fail(400, "PARAMETRO_INVALIDO", "Una fecha no tiene un formato válido");
    if (desde && hasta && desde > hasta) return fail(400, "RANGO_INVALIDO", `desde (${desde}) no puede ser posterior a hasta (${hasta})`);
    const del = (db.facturasPorEmpresa.get(empresaId) ?? []).filter((f) => (!desde || f.fecha_emision >= desde) && (!hasta || f.fecha_emision <= hasta));
    const cuenta = (estados: string[]) => del.filter((f) => estados.includes(f.estado_documento)).length;
    const facturables = ["ACEPTADO", "ACEPTADO_CON_OBS", "ENVIADO", "FIRMADO", "ERROR_ENVIO", "PENDIENTE_AGRUPACION"];
    const porMoneda = new Map<string, number>();
    for (const f of del.filter((x) => facturables.includes(x.estado_documento))) {
      porMoneda.set(f.moneda, Math.round(((porMoneda.get(f.moneda) ?? 0) + (f.tipo === "07" ? -f.totales.total : f.totales.total)) * 100) / 100);
    }
    const rechazados = cuenta(["RECHAZADO"]);
    const erroresDeEnvio = cuenta(["ERROR_ENVIO"]);
    const fueraDePlazo = cuenta(["FUERA_DE_PLAZO"]);
    // #356: una boleta (o nota B que va en el resumen) FIRMADA sin enviar desde el 5.º día; el envío que falla ya se cuenta arriba.
    const hoy = hoyLima();
    const boletasSinEnviar = del.filter((f) => f.estado_documento === "FIRMADO" && avisoDePlazo(f, hoy) !== null).length;
    return ok({
      ...(desde ? { desde } : {}),
      ...(hasta ? { hasta } : {}),
      emitidos: del.filter((f) => !["RECIBIDO", "INVALIDO"].includes(f.estado_documento)).length,
      aceptados_con_cdr: cuenta(["ACEPTADO", "ACEPTADO_CON_OBS"]),
      atencion_requerida: { total: rechazados + erroresDeEnvio + fueraDePlazo + boletasSinEnviar, rechazados, errores_de_envio: erroresDeEnvio, fuera_de_plazo: fueraDePlazo, boletas_sin_enviar: boletasSinEnviar },
      facturado: [...porMoneda.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([moneda, total]) => ({ moneda, total })),
    });
  }),

  http.get(`${BASE}/v1/facturas/:id`, ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    const factura = lista.find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    // Como el backend: una factura o una boleta (#293) lista las notas emitidas sobre ella.
    const notas = factura.tipo === "01" || factura.tipo === "03"
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
    type ItemNota = { descripcion: string; unidad: string; cantidad: number; precio_unitario: number; tipo_afectacion_igv: string; icbper?: boolean; descuento?: Ajuste; cargos?: Ajuste[]; isc?: { sistema: string; tasa?: number; monto_unitario?: number; base_pvp?: number } };
    type CuerpoNota = { tipo: "07" | "08"; serie: string; fecha_emision: string; documento_afectado: { serie: string; numero: number }; motivo: string; descripcion: string; items?: ItemNota[]; descuento_global?: Ajuste; cargos?: Ajuste[]; observaciones?: string };
    let body: CuerpoNota;
    try {
      body = (await request.json()) as CuerpoNota;
    } catch {
      return fail(400, "JSON_INVALIDO", "El cuerpo de la petición no es JSON válido");
    }
    const unidadesEnBlanco = erroresDeUnidadEnBlanco(body.items);
    if (Object.keys(unidadesEnBlanco).length) return validacionFallida(unidadesEnBlanco);
    const lista = db.facturasPorEmpresa.get(empresaId) ?? [];
    // Idempotency-Key (#355), como el backend (`ClaveDeIdempotencia` y `EmitirComprobanteService.emitirNota`): la clave se valida tras el cuerpo, y un
    // reintento se contesta ANTES de mirar el estado del documento afectado, el plazo y la serie (la nota ya existe: que luego el documento se diera de baja
    // no es motivo para negarla). La misma clave con el mismo pedido devuelve la nota ya emitida con 200; con otro pedido, 422. Las claves de las notas son un
    // espacio aparte de las de las facturas (`clavesNota`): la misma cadena en las dos no se cruza. La huella es el JSON del cuerpo, que el formulario arma siempre igual.
    const clave = request.headers.get("idempotency-key");
    if (clave !== null && !/^[A-Za-z0-9_-]{8,100}$/.test(clave)) return fail(422, "IDEMPOTENCIA_INVALIDA", "Idempotency-Key debe tener de 8 a 100 letras, dígitos, guiones o guiones bajos (se recomienda un UUID)");
    const huella = JSON.stringify(body);
    const previa = clave ? db.clavesNota.get(`${empresaId}|${clave}`) : undefined;
    if (previa) {
      if (previa.huella !== huella) return fail(422, "IDEMPOTENCIA_INVALIDA", `La clave de idempotencia ${clave} ya se usó con otro contenido: genere una clave nueva para otra nota`);
      const emitida = lista.find((f) => f.id === previa.id);
      if (emitida) return okConAvisos(emitida, avisosDelComprador(emitida.receptor?.num_doc), 200);
    }
    // #293, como `EmitirComprobanteService.emitirNota`: la letra de la serie afectada dice qué se modifica (B una boleta, F una
    // factura), y el motivo se valida antes de buscar el documento.
    const tipoAfectado = body.documento_afectado.serie.startsWith("B") ? "03" : "01";
    const nombreAfectado = tipoAfectado === "03" ? "boleta" : "factura";
    const catalogo = CATALOGOS.find((c) => c.id === (body.tipo === "07" ? "09" : "10"))!;
    const entradaMotivo = catalogo.entradas.find((e) => e.codigo === body.motivo);
    if (!entradaMotivo) return fail(422, "NOTA_INVALIDA", `2172 - El motivo ${body.motivo} no existe en el catálogo ${catalogo.id}`);
    if (tipoAfectado === "03" && body.tipo === "07" && body.motivo === "13") return fail(422, "NOTA_INVALIDA", "3259 - Una boleta no tiene cuotas que corregir: la nota de crédito 13 solo modifica facturas al crédito");
    if (tipoAfectado === "03" && body.tipo === "07" && ["04", "05", "08"].includes(body.motivo))
      return fail(422, "NOTA_INVALIDA", "4367 - Un descuento o una bonificación a un consumidor final se aplica en la misma boleta (RCP art. 10 num. 1.4), no en una nota de crédito 04, 05 u 08; para devolver o corregir el importe use el motivo 01, 06, 07 o 09");
    const factura = lista.find((f) => f.tipo === tipoAfectado && f.serie === body.documento_afectado.serie && f.numero === body.documento_afectado.numero);
    if (!factura) return fail(422, "NOTA_INVALIDA", `2119 - La ${nombreAfectado} ${body.documento_afectado.serie}-${body.documento_afectado.numero} no existe en esta empresa`);
    if (factura.estado_documento === "ANULADO") return fail(422, "NOTA_INVALIDA", `2120 - La ${nombreAfectado} ${factura.serie}-${factura.numero} está anulada`);
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(422, "NOTA_INVALIDA", `2119 - La ${nombreAfectado} ${factura.serie}-${factura.numero} no está aceptada por SUNAT (estado ${factura.estado_documento})`);
    const serie = (db.seriesPorEmpresa.get(empresaId) ?? []).find((s) => s.tipo === body.tipo && s.serie === body.serie);
    if (!serie) return fail(422, "SERIE_NO_CONFIGURADA", `Serie no configurada: ${body.serie}`);

    // Lo que el backend real valida ANTES de numerar, con los códigos de la tabla oficial. Sin esto los e2e afirmaban
    // sobre lo que el mock inventaba: una nota con fecha 2020, sin forma de pago en la NC 13 o por 10× la factura
    // pasaba igual, y ocho mutaciones del formulario sobrevivían a la suite.
    const letra = factura.serie.charAt(0);
    if (!body.serie.startsWith(letra)) return fail(422, "SERIE_INVALIDA", `1001/2116 - La serie de una nota sobre ${factura.serie}-${factura.numero} debe ser ${letra}### : ${body.serie}`);
    if (body.fecha_emision < factura.fecha_emision) return fail(422, "NOTA_INVALIDA", `2885 - La fecha de la nota no puede ser anterior a la de la ${nombreAfectado} que modifica (${factura.fecha_emision})`);
    if (body.fecha_emision > hoyLima()) return fail(422, "FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
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
    // #354: más de 999 líneas (2137 en la nota), antes que las reglas de cada ítem, como `NotaBuilder.crear`.
    const porTopeDeLineas = body.items ? rechazoPorTopeDeLineasMock(body.items, "nota") : null;
    if (porTopeDeLineas) return fail(422, "ITEM_INVALIDO", porTopeDeLineas);
    for (const i of nc13 ? [] : (body.items ?? [])) {
      if (exportacion && i.tipo_afectacion_igv !== "40") return fail(422, "AFECTACION_INVALIDA", `2642 - En una exportación (${factura.tipo_operacion}) todos los ítems llevan tipo_afectacion_igv 40`);
      if (!exportacion && i.tipo_afectacion_igv === "40") return fail(422, "AFECTACION_INVALIDA", `3107 - La afectación 40 (exportación) exige un tipo de operación 0200–0208; recibido ${factura.tipo_operacion}`);
      if (!(i.cantidad > 0) || decimales(i.cantidad) > 10) return fail(422, "ITEM_INVALIDO", "2025 - La cantidad debe ser positiva, con hasta 12 enteros y 10 decimales");
      // #354, `Item`: bolsas del ICBPER (2892, 4320) y precio en cero (2369/2367), antes del formato numérico del precio.
      const porLinea = rechazoPorLineaMock(i);
      if (porLinea) return fail(422, "ITEM_INVALIDO", porLinea);
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
      // #308: los ítems propios. La nota total también cruza las unidades que copia (`copiaDelDocumento`, #348-H1), pero el mock no tiene
      // documentos con una unidad fuera del catálogo (los rechaza al emitirlos y los datos sembrados usan NIU y ZZ).
      const porUnidad = rechazoPorUnidadMock([i]);
      if (porUnidad) return porUnidad;
      // #354, `ItemCalculado.exigirValorDeVentaPositivo` (2370); la línea de la NC 13 no entra (el dominio la arma con valor 0, 3315).
      const porValorDeVenta = rechazoPorValorDeVentaMock(i, 18, body.fecha_emision);
      if (porValorDeVenta) return fail(422, "ITEM_INVALIDO", porValorDeVenta);
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
    // #314: descuento global y cargos propios de la nota parcial (NotaRequest): solo con ítems, y afectan la base del IGV (SUNAT no admite los otros, 3271/3277).
    if ((body.descuento_global || body.cargos?.length) && !body.items?.length) return fail(422, "NOTA_INVALIDA", "El descuento global y los cargos solo se indican con `items`: la nota total copia los de la factura");
    if (body.descuento_global?.afecta_base_igv === false) return fail(422, "DESCUENTO_INVALIDO", "Una nota no admite un descuento que no afecte la base del IGV (3271, 3277)");
    if ((body.observaciones ?? "").length > 1000) return fail(422, "VALIDACION", "observaciones: tamaño debe estar entre 0 y 1000");
    // Como `Totales`: el descuento 02 y el cargo 49 se calculan sobre la base gravada y exigen líneas gravadas onerosas, y no se mezclan con otros tributos (9999).
    if ((body.descuento_global || body.cargos?.length) && t.gravado <= 0) return fail(422, body.descuento_global ? "DESCUENTO_INVALIDO" : "CARGO_INVALIDO", "El descuento global y el cargo exigen líneas gravadas: la nota no tiene ninguna");
    const ajusteGlobal = (a: Ajuste | undefined, signo: 1 | -1) => (a ? signo * (a.monto ?? (t.gravado * (a.porcentaje ?? 0)) / 100) : 0);
    const delta = ajusteGlobal(body.descuento_global, -1) + (body.cargos ?? []).reduce((s, c) => s + ajusteGlobal(c, 1), 0);
    if (delta !== 0) {
      t.gravado += delta;
      t.igv += delta * 0.18;
      t.total += delta * 1.18;
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
      // Sin tolerancia sobre facturas (NotaCredito2_0 fila 111) y +1 sobre boletas (fila 113, #293); exento en el motivo 10,
      // como el backend desde #123. Con margen de flotante (0.005) para que 118.44 − 118.44 no dé 1e-14.
      const tolerancia = tipoAfectado === "03" ? 1 : 0;
      if (body.motivo !== "10" && totalNota + acreditado - factura.totales.total > tolerancia + 0.005) return fail(422, "NOTA_INVALIDA", `3286 - El importe total de la nota (${totalNota}) supera el de la ${nombreAfectado} ${factura.serie}-${factura.numero} (${factura.totales.total})${acreditado > 0 ? `: ya acreditado ${acreditado} en otras notas de crédito` : ""}`);
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
          if (nota + previo - fact > 1) return fail(422, "NOTA_INVALIDA", `3503 - El ${concepto} de la nota (${nota.toFixed(2)}) supera el de la ${nombreAfectado} ${factura.serie}-${factura.numero} (${fact.toFixed(2)})${previo > 0 ? `: ya acreditado ${previo.toFixed(2)} en otras notas de crédito` : ""}`);
        }
      }
    }
    // #18: una nota consume del plan como una factura.
    const porTope = rechazoPorTopeDelPlanMock(request);
    if (porTope) return porTope;
    serie.ultimo_numero += 1;
    const id = nuevoId("n");
    // La nota guarda las líneas con la forma de la respuesta (sin los ajustes en forma de request).
    const itemsNota = items.map((i) => ({ codigo: i.codigo ?? null, descripcion: i.descripcion, unidad: i.unidad, cantidad: i.cantidad, precio_unitario: i.precio_unitario, tipo_afectacion_igv: i.tipo_afectacion_igv }));
    const nota: Comprobante = {
      id, tipo: body.tipo, serie: body.serie, numero: serie.ultimo_numero, fecha_emision: body.fecha_emision, observaciones: body.observaciones?.trim() || null, moneda: factura.moneda,
      tipo_operacion: factura.tipo_operacion, receptor: factura.receptor, items: itemsNota, estado_documento: "ACEPTADO", hash: "hashnota==",
      nombre_archivo: `20123456786-${body.tipo}-${body.serie}-${String(serie.ultimo_numero).padStart(8, "0")}`, intentos: 1, ultimo_error: null,
      cdr: { codigo: "0", descripcion: `La Nota de ${body.tipo === "07" ? "Credito" : "Debito"} numero ${body.serie}-${serie.ultimo_numero}, ha sido aceptada`, observaciones: [] },
      totales: { gravado: t.gravado, exonerado: t.exonerado, inafecto: t.inafecto, igv: t.igv, ivap: t.ivap || undefined, exportacion: t.exportacion || undefined, gratuito: t.gratuito || undefined, igv_gratuitas: t.igvGratuitas || undefined, redondeo: redondeo || undefined, total: totalNota },
      forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
      nota: { tipo_afectado: tipoAfectado, documento_afectado: `${factura.serie}-${factura.numero}`, motivo: body.motivo, motivo_descripcion: motivos[body.motivo] ?? "Otros", descripcion: body.descripcion },
      enlaces: { xml: `/v1/facturas/${id}/xml`, pdf: `/v1/facturas/${id}/pdf`, cdr: `/v1/facturas/${id}/cdr` },
    };
    lista.unshift(nota);
    if (clave) db.clavesNota.set(`${empresaId}|${clave}`, { huella, id });
    return okConAvisos(nota, avisosDelComprador(nota.receptor?.num_doc), 201);
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
    if (diasEntre(factura.fecha_emision, hoyLima()) > 7) return fail(422, "BAJA_INVALIDA", `2957 - El plazo para dar de baja ${factura.serie}-${factura.numero} venció: se emitió el ${factura.fecha_emision} y la comunicación debe presentarse dentro de los 7 días calendario siguientes a su emisión`);
    const motivo = (body.motivo ?? "").trim();
    if (motivo.length < 3 || motivo.length > 100 || /[\x00-\x1F\x7F]/.test(motivo)) return fail(422, "BAJA_INVALIDA", "2315 - El motivo de la baja debe tener de 3 a 100 caracteres, sin saltos de línea");
    if (factura.baja && factura.baja.estado !== "RECHAZADA") return fail(422, "BAJA_INVALIDA", `Ya hay una comunicación de baja en curso para ${factura.serie}-${factura.numero}`);
    const hoy = hoyLima().replace(/-/g, "");
    const simulada = motivo.startsWith("[ENVIADA]") ? "ENVIADA" : motivo.startsWith("[RECHAZADA]") ? "RECHAZADA" : "ACEPTADA";
    // #20/#293: la boleta y sus notas se anulan en el resumen diario (RC), las demás en una comunicación de baja (RA), como en el backend.
    const deBoleta = deBoletaMock(factura);
    const identificador = `${deBoleta ? "RC" : "RA"}-${hoy}-1`;
    const baja: Baja = {
      id: nuevoId("b"), identificador, comprobante: `${factura.serie}-${factura.numero}`, tipo_comprobante: factura.tipo, fecha_generacion: hoyLima(), fecha_referencia: factura.fecha_emision,
      motivo, condicion: "BAJA", fuera_de_plazo: false, estado: simulada, ticket: "1758200000123",
      cdr: simulada === "ACEPTADA" ? cdrBajaAceptada(identificador)
        : simulada === "RECHAZADA" ? { codigo: "2323", descripcion: "Existe documento ya informado anteriormente en una comunicacion de baja", observaciones: [] } : null,
      intentos: 1, ultimo_error: simulada === "ENVIADA" ? "98 - SUNAT sigue procesando el ticket 1758200000123" : null,
    };
    factura.baja = baja;
    if (simulada === "ACEPTADA") anularMock(factura);
    db.bajas.set(baja.id, baja);
    return ok(baja, 201);
  }),
  // Como `BajaController.obtener`: si está ENVIADA consulta el ticket en el acto. En el mock SUNAT ya terminó: se acepta y anula.
  http.get(`${BASE}/v1/bajas/:id`, ({ params }) => {
    const baja = db.bajas.get(String(params.id));
    if (!baja) return fail(404, "NO_ENCONTRADO", "Comunicación de baja no encontrada");
    if (baja.estado === "ENVIADA") {
      baja.estado = "ACEPTADA";
      baja.cdr = cdrBajaAceptada(baja.identificador);
      baja.ultimo_error = null;
      baja.intentos += 1;
      for (const lista of db.facturasPorEmpresa.values()) for (const f of lista) if (f.baja?.id === baja.id) anularMock(f);
    }
    return ok(baja);
  }),

  // #315: `getStatusCdr`. Como el backend: solo facturas y sus notas (serie F), solo si falta la constancia (409 CDR_YA_DISPONIBLE). Si SUNAT la tiene (el mock lo simula salvo en
  // `f-enviada-sin-cdr`), aplica el resultado; si no, devuelve el comprobante sin cambios.
  http.post(`${BASE}/v1/facturas/:id/cdr/recuperar`, ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    if (deBoletaMock(factura)) return fail(409, "CDR_NO_CONSULTABLE", "SUNAT no expone getStatusCdr para boletas ni notas de boleta: su constancia es la del envío individual o la del resumen diario");
    if (factura.cdr) return fail(409, "CDR_YA_DISPONIBLE", "El comprobante ya tiene su constancia de SUNAT");
    if (params.id === "f-enviada-sin-cdr") return ok(factura);
    factura.estado_documento = "ACEPTADO";
    factura.cdr = { codigo: "0", descripcion: `La Factura numero ${factura.serie}-${factura.numero}, ha sido aceptada`, observaciones: [] };
    factura.ultimo_error = null;
    factura.enlaces = { ...factura.enlaces, cdr: `/v1/facturas/${factura.id}/cdr` };
    return ok(factura);
  }),

  // #315: `validaCDPcriterios` por los datos del comprobante. El mock responde con lo que la empresa tiene guardado: aceptado, rechazado o de baja según su estado,
  // «no existe» si SUNAT no lo recibió o no está, y «ajeno» si el RUC no es el de la empresa.
  http.get(`${BASE}/v1/consultas/validez`, ({ request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const q = new URL(request.url).searchParams;
    const ruc = q.get("ruc") ?? "";
    const serie = q.get("serie") ?? "";
    const numero = Number(q.get("numero"));
    const tipo = q.get("tipo") ?? "";
    if (!/^\d{11}$/.test(ruc)) return fail(400, "PARAMETRO_INVALIDO", "ruc debe tener 11 dígitos");
    if (!serie || !(numero > 0) || !tipo) return fail(400, "PARAMETRO_INVALIDO", "Faltan serie, número o tipo");
    const propio = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.tipo === tipo && f.serie === serie && f.numero === numero);
    const rucDeLaEmpresa = propio?.nombre_archivo?.split("-")[0];
    if (propio && rucDeLaEmpresa && rucDeLaEmpresa !== ruc) return ok({ estado: "AJENO", codigo: "0012", mensaje: "El comprobante pertenece a otro emisor" });
    if (!propio) return ok({ estado: "NO_EXISTE", codigo: "0011", mensaje: "El comprobante no existe" });
    if (propio.estado_documento === "ACEPTADO" || propio.estado_documento === "ACEPTADO_CON_OBS") return ok({ estado: "ACEPTADO", codigo: "0001", mensaje: "El comprobante existe y está aceptado" });
    if (propio.estado_documento === "RECHAZADO") return ok({ estado: "RECHAZADO", codigo: "0002", mensaje: "El comprobante fue rechazado" });
    if (propio.estado_documento === "ANULADO") return ok({ estado: "DE_BAJA", codigo: "0003", mensaje: "El comprobante está dado de baja" });
    return ok({ estado: "NO_EXISTE", codigo: "0011", mensaje: "El comprobante no existe" });
  }),

  http.post(`${BASE}/v1/facturas/:id/enviar`, ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    const dias = diasEntre(factura.fecha_emision, hoyLima());
    const deBoleta = deBoletaMock(factura);
    const sinResumen = (factura.tipo === "07" && factura.nota?.motivo === "10") || (factura.tipo === "08" && factura.nota?.motivo === "03");
    if (deBoleta && !sinResumen && dias > PLAZO_ENVIO.boleta.envioIndividual) {
      // Como `EnviarDocumentoService` → `DarDeBajaService.informar`: pasado el envío individual la boleta va en su propio resumen de alta (RC); pasado el
      // 7.º día, fuera de plazo (#331). SUNAT no la rechaza; el mock simula que la observa con la 4443 (la norma lo prevé; e-beta la aceptó sin observaciones).
      // La respuesta trae ese resumen en `baja`.
      const fueraDePlazo = dias > PLAZO_ENVIO.boleta.dias;
      const identificador = `RC-${hoyLima().replace(/-/g, "")}-1`;
      const observaciones = fueraDePlazo ? ["4443 - El comprobante fue informado fuera del plazo establecido"] : [];
      const cdr = { codigo: "0", descripcion: `El Resumen diario ${identificador}, ha sido aceptado`, observaciones };
      const alta: Baja = {
        id: nuevoId("b"), identificador, comprobante: `${factura.serie}-${factura.numero}`, tipo_comprobante: factura.tipo, fecha_generacion: hoyLima(), fecha_referencia: factura.fecha_emision,
        motivo: fueraDePlazo ? "Pasó el 7.º día: se informa en el resumen diario fuera de plazo (SUNAT puede observarla con 4443)" : "Pasó el envío individual: se informa en el resumen diario",
        condicion: "ALTA", fuera_de_plazo: fueraDePlazo, estado: "ACEPTADA", ticket: "1758200000124", cdr, intentos: 1, ultimo_error: null,
      };
      db.bajas.set(alta.id, alta);
      factura.baja = alta;
      factura.estado_documento = fueraDePlazo ? "ACEPTADO_CON_OBS" : "ACEPTADO";
      // #362 B10: el CDR del comprobante es el del resumen, y la respuesta dice de cuál (`cdr.resumen`); el del resumen mismo (`baja.cdr`) no lo lleva.
      factura.cdr = { ...cdr, resumen: identificador };
      factura.ultimo_error = null;
      factura.enlaces = { ...factura.enlaces, cdr: `/v1/facturas/${factura.id}/cdr` };
      return ok(factura);
    }
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
  // Envío por correo al adquirente: solo comprobantes aceptados; el backend valida el email (422 VALIDACION). Un correo que empieza con
  // `sin-smtp` simula un servidor sin correo configurado (#218): 503 CORREO_NO_CONFIGURADO y no se envía nada.
  http.post(`${BASE}/v1/facturas/:id/correo`, async ({ params, request }) => {
    const empresaId = request.headers.get("x-empresa") ?? "";
    const factura = (db.facturasPorEmpresa.get(empresaId) ?? []).find((f) => f.id === params.id);
    if (!factura) return fail(404, "NO_ENCONTRADO", "Comprobante no encontrado");
    const body = (await request.json()) as { email: string; mensaje?: string | null };
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email ?? "")) {
      return HttpResponse.json({ estado: "error", datos: null, mensaje: "Validación fallida", codigo: "VALIDACION", errores: { email: ["debe ser una dirección de correo electrónico con formato correcto"] } }, { status: 422 });
    }
    if (factura.estado_documento !== "ACEPTADO" && factura.estado_documento !== "ACEPTADO_CON_OBS") return fail(409, "NO_ACEPTADO", `Solo se envían comprobantes aceptados por SUNAT; ${factura.serie}-${factura.numero} está ${factura.estado_documento}`);
    if (body.email.startsWith("sin-smtp")) {
      return fail(503, "CORREO_NO_CONFIGURADO", `El envío de correos no está habilitado en el servidor: ${factura.serie}-${factura.numero} no se envió. Descargue el PDF y envíelo por su cuenta, o contacte a soporte.`);
    }
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
  http.get(`${BASE}/v1/facturas/:id/cdr`, ({ params, request }) => {
    // #362 B10: como el backend, el CDR de una boleta informada por resumen diario trae `X-Cdr-Resumen` con el identificador de ese resumen; el nombre del archivo no cambia.
    const factura = (db.facturasPorEmpresa.get(request.headers.get("x-empresa") ?? "") ?? []).find((f) => f.id === params.id);
    const resumen = factura?.cdr?.resumen ? { "x-cdr-resumen": factura.cdr.resumen } : undefined;
    if (new URL(request.url).searchParams.get("formato") === "xml") {
      return new HttpResponse(
        `<?xml version="1.0" encoding="UTF-8"?><ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"><cbc:ID>0</cbc:ID><cac:DocumentResponse><cac:Response><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Factura numero F001-1, ha sido aceptada</cbc:Description></cac:Response></cac:DocumentResponse></ar:ApplicationResponse>`,
        { headers: { "content-type": "application/xml", ...resumen } },
      );
    }
    return new HttpResponse(new Uint8Array([80, 75]), { headers: { "content-type": "application/zip", ...resumen } });
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
