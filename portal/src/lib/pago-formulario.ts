import { MEDIOS_DE_PAGO, type CuerpoDePago, type MedioDePagoAdmin } from "@/lib/api/admin-pagos";
import { fechaValida } from "@/lib/cambio-de-plan";
import { messages } from "@/lib/messages";

const t = messages.admin.pagos.dialogo.errores;

/** Lo que el administrador tiene escrito en el formulario de pago. Las fechas son `YYYY-MM-DD`; el monto, el texto tal cual. */
export type ValoresDePago = { desde: string; hasta: string; monto: string; medio: string; fecha: string; referencia: string; nota: string; extender: boolean };

export type ErroresDePago = Partial<Record<"desde" | "hasta" | "monto" | "medio" | "fecha" | "referencia" | "nota" | "extender", string>>;

/**
 * Lo que el formulario sabe de afuera: `hoy` en Lima, si el plan de la cuenta vence y hasta qué día (inclusive) está pagada hoy; este último falta si no se pudo
 * cargar el plan o no vence.
 */
export type ContextoDePago = { hoy: string; planVence: boolean; pagadoHastaActual?: string };

const REFERENCIA_MAX = 100;
const NOTA_MAX = 200;
const MONTO_MAX = 9_999_999.99;

/** El último día que admite un periodo que empieza en `desde`: un año después menos un día, ajustando el 29 de febrero igual que el backend (`plusYears(1).minusDays(1)`). */
function ultimoDiaAdmitido(desde: string): string {
  const [a, m, d] = desde.split("-").map(Number);
  const mismoDiaDelAnioSiguiente = new Date(Date.UTC(a + 1, m - 1, d));
  // El 29 de febrero de un año bisiesto no existe al año siguiente: se queda en el último día de ese mes.
  if (mismoDiaDelAnioSiguiente.getUTCMonth() !== m - 1) mismoDiaDelAnioSiguiente.setTime(Date.UTC(a + 1, m, 0));
  mismoDiaDelAnioSiguiente.setUTCDate(mismoDiaDelAnioSiguiente.getUTCDate() - 1);
  return mismoDiaDelAnioSiguiente.toISOString().slice(0, 10);
}

function validarFecha(texto: string, requerido: string): string | undefined {
  if (!texto) return requerido;
  return fechaValida(texto) ? undefined : t.fechaInvalida;
}

/**
 * Valida lo evidente antes de enviar y arma el cuerpo del pago (#194). El backend vuelve a decidir todo (periodo, monto, medio, fecha, duplicados, extensión): esto evita
 * ir y volver por un campo vacío. El monto admite punto o coma decimal y se manda como número. «Extender» pide que el plan venza y que el periodo adelante el
 * vencimiento: si no, el backend lo rechazaría.
 */
export function validarPago(v: ValoresDePago, ctx: ContextoDePago): { errores: ErroresDePago } | { cuerpo: CuerpoDePago } {
  const errores: ErroresDePago = {};

  const errDesde = validarFecha(v.desde, t.desdeRequerido);
  const errHasta = validarFecha(v.hasta, t.hastaRequerido);
  if (errDesde) errores.desde = errDesde;
  if (errHasta) errores.hasta = errHasta;
  if (!errDesde && !errHasta) {
    if (v.hasta < v.desde) errores.hasta = t.periodoAlReves;
    else if (v.hasta > ultimoDiaAdmitido(v.desde)) errores.hasta = t.periodoLargo;
  }

  const montoTexto = v.monto.trim().replace(",", ".");
  let monto = 0;
  if (!montoTexto) errores.monto = t.montoRequerido;
  else if (!/^\d+(\.\d{1,2})?$/.test(montoTexto) || (monto = Number(montoTexto)) <= 0) errores.monto = t.montoInvalido;
  else if (monto > MONTO_MAX) errores.monto = t.montoMuyGrande;

  if (!(MEDIOS_DE_PAGO as readonly string[]).includes(v.medio)) errores.medio = t.medioRequerido;

  const errFecha = validarFecha(v.fecha, t.fechaRequerida);
  if (errFecha) errores.fecha = errFecha;
  else if (v.fecha > ctx.hoy) errores.fecha = t.fechaFutura;

  const referencia = v.referencia.trim();
  const nota = v.nota.trim();
  if (referencia.length > REFERENCIA_MAX) errores.referencia = t.referenciaLarga;
  if (nota.length > NOTA_MAX) errores.nota = t.notaLarga;

  if (v.extender) {
    if (!ctx.planVence) errores.extender = messages.admin.pagos.dialogo.extenderNoVence;
    else if (!errHasta && ctx.pagadoHastaActual && v.hasta <= ctx.pagadoHastaActual) errores.extender = t.extenderSinEfecto;
  }

  if (Object.keys(errores).length > 0) return { errores };
  return {
    cuerpo: {
      periodo_desde: v.desde,
      periodo_hasta: v.hasta,
      monto,
      medio: v.medio as MedioDePagoAdmin,
      fecha_de_pago: v.fecha,
      ...(referencia ? { referencia } : {}),
      ...(nota ? { nota } : {}),
      extender_vencimiento: v.extender,
    },
  };
}
