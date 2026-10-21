import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { Comprobante } from "@/lib/api/facturas";
import es from "@/messages/es.json";

/**
 * #312: anticipos. Una factura (o boleta) de anticipo es un comprobante común: SUNAT no la marca. La que la regulariza lleva `anticipos` con la serie-número de aquella y
 * el valor sin IGV que descuenta. Aquí se eligen los comprobantes aceptados del mismo cliente y moneda, con lo que aún se puede descontar de cada uno.
 */
export type Afectacion = "gravado" | "exonerado" | "inafecto";
export const AFECTACIONES_ANTICIPO: Afectacion[] = ["gravado", "exonerado", "inafecto"];

export type Candidato = {
  /** `F001-120` / `B001-7`. */
  etiqueta: string;
  serie: string;
  numero: number;
  fecha: string;
  moneda: string;
  /** Valor de venta que facturó, por afectación. */
  facturado: Record<Afectacion, number>;
  /** Lo que ya se regularizó en otros comprobantes, por afectación. */
  regularizado: Record<Afectacion, number>;
  /** Lo que se puede descontar todavía: facturado − regularizado. */
  disponible: Record<Afectacion, number>;
};

export type AnticipoForm = { serie: string; numero: number; monto: string; afectacion: Afectacion };

const ACEPTADOS = new Set(["ACEPTADO", "ACEPTADO_CON_OBS"]);
/** Las finales que no regularizaron nada: como `JdbcComprobanteRepository.montoRegularizado`, solo las rechazadas o inválidas. Una final aceptada y luego dada de baja sigue contando. */
const NO_CUENTAN = new Set(["RECHAZADO", "INVALIDO"]);

const redondear = (n: number) => Math.round(n * 100) / 100;

/**
 * Los comprobantes que pueden ser el anticipo de esta venta: facturas o boletas (no notas) aceptadas por SUNAT, al mismo cliente y en la misma moneda, con algo por
 * descontar. `lista` son los comprobantes de la empresa (la primera página del listado); sus propios `anticipos` dicen cuánto se regularizó ya.
 */
export function candidatosDeAnticipo(lista: Comprobante[], cliente: { numDoc: string }, moneda: string): Candidato[] {
  const regularizado = new Map<string, Record<Afectacion, number>>();
  for (const c of lista) {
    if (NO_CUENTAN.has(c.estado_documento)) continue;
    for (const a of c.anticipos ?? []) {
      const clave = `${a.serie}-${a.numero}`;
      const suma = regularizado.get(clave) ?? { gravado: 0, exonerado: 0, inafecto: 0 };
      suma[a.afectacion] += a.monto;
      regularizado.set(clave, suma);
    }
  }
  const candidatos: Candidato[] = [];
  for (const c of lista) {
    if ((c.tipo !== "01" && c.tipo !== "03") || !ACEPTADOS.has(c.estado_documento) || c.moneda !== moneda) continue;
    if (!cliente.numDoc || c.receptor?.num_doc !== cliente.numDoc) continue;
    const facturado = { gravado: c.totales.gravado, exonerado: c.totales.exonerado, inafecto: c.totales.inafecto };
    const ya = regularizado.get(`${c.serie}-${c.numero}`) ?? { gravado: 0, exonerado: 0, inafecto: 0 };
    // El backend suma lo regularizado de **todas** las afectaciones y lo compara con lo facturado en la que se descuenta (`validarFacturaDeAnticipo`): se espeja esa regla, aunque sea
    // más estricta que restar por afectación, para no ofrecer un monto que la emisión rechazaría.
    const yaTotal = ya.gravado + ya.exonerado + ya.inafecto;
    const disponible = {
      gravado: redondear(Math.max(0, facturado.gravado - yaTotal)),
      exonerado: redondear(Math.max(0, facturado.exonerado - yaTotal)),
      inafecto: redondear(Math.max(0, facturado.inafecto - yaTotal)),
    };
    if (disponible.gravado + disponible.exonerado + disponible.inafecto <= 0) continue;
    candidatos.push({ etiqueta: `${c.serie}-${c.numero}`, serie: c.serie, numero: c.numero, fecha: c.fecha_emision, moneda: c.moneda, facturado, regularizado: ya, disponible });
  }
  return candidatos;
}

/** La afectación por la que conviene descontar por defecto: la primera con algo disponible (gravado, exonerado, inafecto). */
export function afectacionPorDefecto(c: Candidato): Afectacion {
  return AFECTACIONES_ANTICIPO.find((a) => c.disponible[a] > 0) ?? "gravado";
}

/** El anticipo elegido con todo lo que se puede descontar de esa afectación como monto inicial. */
export function anticipoInicial(c: Candidato): AnticipoForm {
  const afectacion = afectacionPorDefecto(c);
  return { serie: c.serie, numero: c.numero, monto: c.disponible[afectacion].toFixed(2), afectacion };
}

/** Por qué los anticipos elegidos no valen, o `null`. El resto (que el anticipo exista, sea del cliente y no exceda lo facturado en esta venta) lo dice el backend al calcular. */
export function errorDeAnticipos(elegidos: AnticipoForm[], candidatos: Candidato[], messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.anticipo;
  for (const a of elegidos) {
    const etiqueta = `${a.serie}-${a.numero}`;
    const monto = a.monto.trim();
    if (!/^\d+(\.\d{1,2})?$/.test(monto) || Number(monto) <= 0) return interpolar(t.monto, { etiqueta });
    const c = candidatos.find((x) => x.serie === a.serie && x.numero === a.numero);
    const yaTotal = c ? c.regularizado.gravado + c.regularizado.exonerado + c.regularizado.inafecto : 0;
    if (c && Number(monto) > c.disponible[a.afectacion] + 0.005)
      return interpolar(t.disponible, {
        etiqueta,
        disponible: c.disponible[a.afectacion].toFixed(2),
        afectacion: messages.documentos.afectacionAnticipo[a.afectacion],
        ya: yaTotal > 0 ? interpolar(t.ya, { monto: yaTotal.toFixed(2) }) : "",
      });
  }
  const claves = elegidos.map((a) => `${a.serie}-${a.numero}`);
  if (new Set(claves).size < claves.length) return t.repetido;
  return null;
}

export type AnticipoApi = { serie: string; numero: number; monto: number; afectacion: Afectacion };

export function anticiposAApi(elegidos: AnticipoForm[], candidatos: Candidato[]): AnticipoApi[] | undefined {
  if (elegidos.length === 0 || errorDeAnticipos(elegidos, candidatos) !== null) return undefined;
  return elegidos.map((a) => ({ serie: a.serie, numero: a.numero, monto: Number(a.monto), afectacion: a.afectacion }));
}
