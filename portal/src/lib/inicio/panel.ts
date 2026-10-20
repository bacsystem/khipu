import type { EmpresaDetalle } from "@/lib/api/empresas";
import type { EmisionPorDia, EstadoDocumento, ResumenDeFacturas } from "@/lib/api/facturas";
import type { IdPaso, PasoGuia } from "@/lib/empresa/guia-emision";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { diasEntre, formatearFecha, sumarDias } from "@/lib/formato";

/** Un rango de fechas de emisión `YYYY-MM-DD`, inclusive en los dos extremos. */
export type Rango = { desde: string; hasta: string };

/** Días antes del vencimiento del certificado desde los que el panel avisa (#24). */
export const DIAS_AVISO_CERTIFICADO = 30;

function ultimoDiaDelMes(anio: number, mes: number): number {
  return new Date(Date.UTC(anio, mes, 0)).getUTCDate();
}

/** El mes en curso hasta hoy (Lima): del día 1 a `hoy`. Es lo que resume el panel. */
export function mesEnCurso(hoy: string): Rango {
  return { desde: `${hoy.slice(0, 7)}-01`, hasta: hoy };
}

/** Días que espera una boleta antes de pedir atención si nadie la envió (`PlazoEnvio.DIAS_ENVIO_INDIVIDUAL_BOLETA`, `BoletasPorInformar.corte`). */
export const DIAS_ENVIO_INDIVIDUAL_BOLETA = 5;
/** El plazo más largo que todavía se puede salvar: el resumen diario de una boleta cierra al 7.º día (`PlazoEnvio.DIAS_RESUMEN_DIARIO`). */
export const DIAS_RESUMEN_DIARIO = 7;

/**
 * El rango del que salen los avisos de comprobantes: el mes en curso, pero nunca menos de los últimos 7 días. Si se mirara solo el mes, del 1 al 6 una boleta FIRMADA del
 * 28 del mes anterior —en su 5.º o 6.º día, a punto de perder el plazo— o un rechazo de fin de mes no avisarían, y el panel diría «Sin avisos».
 */
export function ventanaDeAtencion(hoy: string): Rango {
  const mes = mesEnCurso(hoy);
  const semana = sumarDias(hoy, -DIAS_RESUMEN_DIARIO);
  return { desde: semana < mes.desde ? semana : mes.desde, hasta: hoy };
}

/**
 * Una empresa que nunca emitió: todas sus series siguen en el número 0 (o no tiene ninguna). Solo eso merece el estado vacío con «tu primer comprobante»: una empresa con
 * historia que hoy, día 1, no emitió nada no es nueva. Cuentan las series de todos los tipos: una empresa que solo emitió retenciones o percepciones (20/40), que el
 * resumen no suma a `emitidos` (solo ventas), tampoco es nueva. Sin series (no se pudieron leer, `null`) no se afirma que lo sea.
 */
export function esEmpresaNueva(series: Array<{ ultimo_numero: number }> | null): boolean {
  return series !== null && series.every((s) => s.ultimo_numero === 0);
}

/**
 * El mismo tramo del mes anterior, para comparar parejo: del 1 al mismo día (el 20 de octubre se compara con el 1–20 de setiembre). Si el mes anterior es más corto, hasta su
 * último día (el 31 de marzo contra el 1–28 de febrero).
 */
export function mismoTramoDelMesAnterior(hoy: string): Rango {
  const [anio, mes, dia] = hoy.split("-").map(Number);
  const anioAnterior = mes === 1 ? anio - 1 : anio;
  const mesAnterior = mes === 1 ? 12 : mes - 1;
  const prefijo = `${anioAnterior}-${String(mesAnterior).padStart(2, "0")}`;
  const hasta = Math.min(dia, ultimoDiaDelMes(anioAnterior, mesAnterior));
  return { desde: `${prefijo}-01`, hasta: `${prefijo}-${String(hasta).padStart(2, "0")}` };
}

/** Un día del gráfico: lo emitido y lo facturado por moneda (cero si ese día no hubo nada). */
export type PuntoDelDia = { fecha: string; emitidos: number; facturado: Record<string, number> };

/**
 * La serie del gráfico: un punto por cada día del rango, en orden, con los días sin comprobantes en cero. La API solo manda los días que tienen algo (y uno fuera del rango,
 * que no debería llegar, se descarta en vez de estirar el eje).
 */
export function serieDiaria(emision: Pick<EmisionPorDia, "dias">, rango: Rango): PuntoDelDia[] {
  const porFecha = new Map(emision.dias.map((d) => [d.fecha, d]));
  const total = diasEntre(rango.desde, rango.hasta);
  const serie: PuntoDelDia[] = [];
  for (let i = 0; i <= total; i++) {
    const fecha = sumarDias(rango.desde, i);
    const dia = porFecha.get(fecha);
    serie.push({ fecha, emitidos: dia?.emitidos ?? 0, facturado: Object.fromEntries((dia?.facturado ?? []).map((f) => [f.moneda, f.total])) });
  }
  return serie;
}

/** Las monedas que aparecen en la serie, ordenadas (PEN primero, como en el resumen, que va por orden alfabético). */
export function monedasDeLaSerie(serie: PuntoDelDia[]): string[] {
  return [...new Set(serie.flatMap((p) => Object.keys(p.facturado)))].sort();
}

/**
 * Cuánto cambió un valor respecto del mismo tramo del mes anterior, en por ciento entero. `null` cuando no hay base para comparar (el mes anterior fue cero o no se pudo
 * leer): un «+∞ %» no le dice nada a nadie.
 */
export function variacion(actual: number, anterior: number | null | undefined): number | null {
  if (anterior === null || anterior === undefined || anterior === 0) return null;
  return Math.round(((actual - anterior) / Math.abs(anterior)) * 100);
}

/** La variación en palabras: «+12 % vs. el mes pasado», «−5 % vs. …», «igual que …». */
export function variacionEnPalabras(v: number): string {
  if (v === 0) return "igual que el mes pasado";
  return `${v > 0 ? "+" : "−"}${Math.abs(v)} % vs. el mes pasado`;
}

/** Lo facturado de una moneda en un resumen (0 si no tiene esa moneda). */
export function facturadoEn(resumen: Pick<ResumenDeFacturas, "facturado">, moneda: string): number {
  return resumen.facturado.find((f) => f.moneda === moneda)?.total ?? 0;
}

export type TonoAlerta = "error" | "aviso";

/** Un aviso del panel: qué pasa, por qué importa y a dónde ir a resolverlo. */
export type AlertaDelPanel = { id: string; tono: TonoAlerta; titulo: string; detalle: string; enlace: string; accion: string };

type EstadoEmpresa = Pick<EmpresaDetalle, "tiene_certificado" | "certificado_vigencia_hasta" | "tiene_credenciales_sol" | "credenciales_sol_rechazadas">;

function plural(n: number, uno: string, varios: string): string {
  return `${n} ${n === 1 ? uno : varios}`;
}

/** El enlace al listado filtrado por un estado, en el rango que resume el panel: así el número del aviso es el que se ve al llegar. */
export function enlaceAComprobantes(estado: EstadoDocumento, rango: Rango, tipo?: string): string {
  const qs = new URLSearchParams({ estado, desde: rango.desde, hasta: rango.hasta });
  if (tipo) qs.set("tipo", tipo);
  return `/comprobantes?${qs}`;
}

/** Lo que la empresa tiene que mirar por su certificado y sus credenciales SOL. `hoy` en `YYYY-MM-DD` (Lima); el certificado que vence hoy todavía firma. */
export function alertasDeLaEmpresa(empresa: EstadoEmpresa, hoy: string): AlertaDelPanel[] {
  const alertas: AlertaDelPanel[] = [];
  const vence = empresa.certificado_vigencia_hasta;
  if (!empresa.tiene_certificado) {
    alertas.push({
      id: "certificado-ausente",
      tono: "error",
      titulo: "No hay certificado digital",
      detalle: "Sin certificado no se firma ningún comprobante. Carga el .p12 o .pfx de la empresa.",
      enlace: enlaceASeccion("certificado"),
      accion: "Cargar certificado",
    });
  } else if (vence && vence < hoy) {
    alertas.push({
      id: "certificado-vencido",
      tono: "error",
      titulo: `El certificado digital venció el ${formatearFecha(vence)}`,
      detalle: "Ya no firma: hasta que cargues uno vigente no puedes emitir.",
      enlace: enlaceASeccion("certificado"),
      accion: "Renovar certificado",
    });
  } else if (vence && diasEntre(hoy, vence) < DIAS_AVISO_CERTIFICADO) {
    const dias = diasEntre(hoy, vence);
    alertas.push({
      id: "certificado-por-vencer",
      tono: "aviso",
      titulo: dias === 0 ? "El certificado digital vence hoy" : `El certificado digital vence en ${plural(dias, "día", "días")}`,
      detalle: `Vence el ${formatearFecha(vence)}. Renuévalo antes para no cortar la emisión.`,
      enlace: enlaceASeccion("certificado"),
      accion: "Renovar certificado",
    });
  }

  if (empresa.credenciales_sol_rechazadas) {
    alertas.push({
      id: "sol-rechazadas",
      tono: "error",
      titulo: "SUNAT rechazó las credenciales SOL",
      detalle: `${empresa.credenciales_sol_rechazadas.motivo}. Los envíos esperan hasta que las corrijas.`,
      enlace: enlaceASeccion("sol"),
      accion: "Corregir credenciales",
    });
  } else if (!empresa.tiene_credenciales_sol) {
    alertas.push({
      id: "sol-ausentes",
      tono: "aviso",
      titulo: "Faltan las credenciales SOL",
      detalle: "Sin el usuario SOL secundario los comprobantes se firman pero no llegan a SUNAT.",
      enlace: enlaceASeccion("sol"),
      accion: "Guardar credenciales",
    });
  }
  return alertas;
}

/** Lo que pide atención en los comprobantes del rango (el del resumen), cada clase con su enlace al listado filtrado. */
export function alertasDeComprobantes(atencion: ResumenDeFacturas["atencion_requerida"], rango: Rango): AlertaDelPanel[] {
  const alertas: AlertaDelPanel[] = [];
  if (atencion.fuera_de_plazo > 0)
    alertas.push({
      id: "fuera-de-plazo",
      tono: "error",
      titulo: `${plural(atencion.fuera_de_plazo, "comprobante quedó", "comprobantes quedaron")} fuera de plazo`,
      detalle: "No llegaron a SUNAT a tiempo y ya no se pueden enviar: hay que emitirlos de nuevo.",
      enlace: enlaceAComprobantes("FUERA_DE_PLAZO", rango),
      accion: "Ver fuera de plazo",
    });
  if (atencion.rechazados > 0)
    alertas.push({
      id: "rechazados",
      tono: "error",
      titulo: `SUNAT rechazó ${plural(atencion.rechazados, "comprobante", "comprobantes")}`,
      detalle: "Revisa el motivo de cada uno, corrígelo y emite de nuevo.",
      enlace: enlaceAComprobantes("RECHAZADO", rango),
      accion: "Ver rechazados",
    });
  if (atencion.errores_de_envio > 0)
    alertas.push({
      id: "errores-de-envio",
      tono: "aviso",
      titulo: `${plural(atencion.errores_de_envio, "comprobante tiene", "comprobantes tienen")} error de envío`,
      detalle: "Se reintentan solos; si el error sigue, revisa el detalle o reenvíalos.",
      enlace: enlaceAComprobantes("ERROR_ENVIO", rango),
      accion: "Ver errores de envío",
    });
  if (atencion.boletas_sin_enviar > 0)
    alertas.push({
      id: "boletas-sin-enviar",
      tono: "aviso",
      titulo: `${plural(atencion.boletas_sin_enviar, "boleta sigue", "boletas siguen")} sin enviar`,
      // El número cuenta también las notas que modifican una boleta (van en el mismo resumen diario); el enlace lista las boletas: el listado no filtra por «nota de boleta».
      detalle: "Ya pasaron 5 días o más desde su emisión: envíalas antes del 7.º, cuando cierra su plazo. La cuenta incluye las notas de esas boletas, que se ven filtrando por nota.",
      // Solo las FIRMADAS que ya cuentan (emitidas hasta `BoletasPorInformar.corte`: hoy − 5), no las de esta semana, que todavía no piden nada.
      enlace: enlaceAComprobantes("FIRMADO", { desde: rango.desde, hasta: sumarDias(rango.hasta, -DIAS_ENVIO_INDIVIDUAL_BOLETA) }, "03"),
      accion: "Ver boletas firmadas",
    });
  return alertas;
}

/** El paso de la guía «Para empezar a emitir» que ya dice lo mismo que cada alerta de la empresa. */
const PASO_DE_LA_ALERTA: Record<string, IdPaso> = {
  "certificado-ausente": "certificado",
  "certificado-vencido": "certificado",
  "sol-ausentes": "sol",
  "sol-rechazadas": "sol",
};

/**
 * Sin las alertas que repiten un paso pendiente de la guía: la guía ya lo dice, con su enlace, y en orden; dos tarjetas con lo mismo solo hacen ruido. El certificado por
 * vencer no es un paso pendiente (todavía firma), así que su aviso queda.
 */
export function sinLoQueYaDiceLaGuia(alertas: AlertaDelPanel[], pasos: Pick<PasoGuia, "id" | "listo">[] | null): AlertaDelPanel[] {
  if (!pasos) return alertas;
  const pendientes = new Set(pasos.filter((p) => !p.listo).map((p) => p.id));
  return alertas.filter((a) => !(a.id in PASO_DE_LA_ALERTA && pendientes.has(PASO_DE_LA_ALERTA[a.id])));
}

/** Todas las alertas del panel: primero las de error y después los avisos, cada grupo en el orden en que se arman. Lo que no se pudo leer (`null`) no avisa nada. */
export function alertasDelPanel(empresa: EstadoEmpresa | null, resumen: Pick<ResumenDeFacturas, "atencion_requerida"> | null, rango: Rango, hoy: string): AlertaDelPanel[] {
  const todas = [...(empresa ? alertasDeLaEmpresa(empresa, hoy) : []), ...(resumen ? alertasDeComprobantes(resumen.atencion_requerida, rango) : [])];
  return [...todas.filter((a) => a.tono === "error"), ...todas.filter((a) => a.tono === "aviso")];
}
