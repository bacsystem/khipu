import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { apiRequest } from "./browser";

/** Lo que responde `GET /v1/consultas/ruc/{ruc}` (#361): el contribuyente tal como lo publica el padrón reducido de SUNAT. */
export type ContribuyenteSunat = {
  ruc: string;
  razon_social: string;
  /** Texto de SUNAT, truncado a 20 caracteres en el archivo: `ACTIVO`, `BAJA DE OFICIO`, `SUSPENSION TEMPORAL`… */
  estado: string;
  /** `HABIDO`, `NO HABIDO`, `PENDIENTE`, `NO HALLADO …`, `-`. */
  condicion: string;
  activo: boolean;
  habido: boolean;
  /** El backend no serializa los nulos (`non_null`): sin ubigeo la clave no viene. */
  ubigeo?: string | null;
  /** Falta en casi todos los RUC 10 (personas naturales); la clave no viene, no es `null`. */
  direccion?: string | null;
  /** Cuándo terminó la carga del padrón que respondió: un RUC creado después todavía no aparece. */
  actualizado_al: string;
};

export type ResultadoConsultaRuc =
  | { tipo: "encontrado"; contribuyente: ContribuyenteSunat }
  /** 404: el padrón está al día y no tiene ese RUC. Puede ser uno recién inscrito (el padrón se renueva cada día). */
  | { tipo: "no-encontrado" }
  /** 503: el padrón nunca se cargó, así que no se puede afirmar nada del RUC. */
  | { tipo: "sin-padron" }
  /** 429: se agotó el cupo de consultas de quien consulta (60 por minuto, 5 000 por día; de la empresa, de la cuenta sin empresa o del administrador). */
  | { tipo: "cupo" }
  /** Red caída, respuesta que no es JSON o con otra forma: se digita a mano. */
  | { tipo: "error" };

/**
 * Desde dónde se consulta (#575): el portal del cliente, con o sin empresa elegida (el onboarding), por su proxy; o el backoffice, por su propia ruta y
 * con la sesión de administrador. El backend responde lo mismo en los dos.
 */
export type OrigenConsultaRuc = "portal" | "admin";

const RUTA: Record<OrigenConsultaRuc, string> = {
  portal: "/api/proxy/consultas/ruc/",
  admin: "/api/admin/consultas/ruc/",
};

/**
 * Consulta un RUC en el padrón de SUNAT. No lanza: cada desenlace es un resultado, porque el formulario sigue siendo utilizable a mano en todos (un RUC
 * nuevo o un padrón caído no bloquean nada).
 */
export async function consultarRuc(ruc: string, origen: OrigenConsultaRuc = "portal"): Promise<ResultadoConsultaRuc> {
  const res = await apiRequest<ContribuyenteSunat>(`${RUTA[origen]}${encodeURIComponent(ruc)}`, { method: "GET" });
  if (res.estado === "exito") {
    const c = res.datos;
    // Una respuesta de otra forma (un proxy intermedio, un mock que contesta lo mismo a todo) no se interpreta como un contribuyente.
    return c && typeof c.razon_social === "string" && typeof c.estado === "string" ? { tipo: "encontrado", contribuyente: c } : { tipo: "error" };
  }
  switch (res.codigo) {
    case "NO_ENCONTRADO":
      return { tipo: "no-encontrado" };
    case "PADRON_NO_CARGADO":
      return { tipo: "sin-padron" };
    case "DEMASIADAS_CONSULTAS":
      return { tipo: "cupo" };
    default:
      return { tipo: "error" };
  }
}

export type ObservacionDeSunat = { codigo: "4013" | "4014"; texto: string };

/**
 * Lo que SUNAT observará del comprobante por el estado del RUC (las mismas reglas que `ValidarCompradorEnPadron`, #378): 4013 si no está activo y 4014 solo
 * con la condición «NO HABIDO» —«NO HALLADO …», «PENDIENTE» y «POR VERIFICAR» no se observan—. SUNAT acepta el comprobante con la observación.
 */
export function observacionesDeSunat(c: Pick<ContribuyenteSunat, "estado" | "condicion" | "activo">, messages: Mensajes): ObservacionDeSunat[] {
  const t = messages.consultaRuc.observaciones;
  const observaciones: ObservacionDeSunat[] = [];
  if (!c.activo) observaciones.push({ codigo: "4013", texto: interpolar(t.noActivo, { estado: c.estado }) });
  if (c.condicion === "NO HABIDO") observaciones.push({ codigo: "4014", texto: t.noHabido });
  return observaciones;
}

/**
 * Lo que conviene saber cuando el RUC es el del emisor (#575: el onboarding, una empresa nueva, el alta asistida): un emisor que no está activo, o que no está
 * habido, puede ver sus comprobantes rechazados. No bloquea: el padrón es de ayer y el cliente puede estar regularizándolo.
 */
export function avisosDeEmisor(c: Pick<ContribuyenteSunat, "estado" | "condicion" | "activo">, messages: Mensajes): string[] {
  const t = messages.consultaRuc.emisor;
  const avisos: string[] = [];
  if (!c.activo) avisos.push(interpolar(t.noActivo, { estado: c.estado }));
  if (c.condicion === "NO HABIDO") avisos.push(t.noHabido);
  return avisos;
}
