import type { CargoAplicado, Comprobante, DescuentoAplicado, ItemComprobante } from "@/lib/api/facturas";
import { AJUSTE_VACIO, type AjusteForm } from "./ajustes";
import { ISC_VACIO, type IscForm } from "./tributos";

/**
 * #323: duplicar un comprobante. «Nuevo comprobante» se abre con el cliente, la moneda y las líneas del que se copia (fecha de hoy, sin número). Solo lo que describe
 * la venta: la detracción, la percepción, la retención, los anticipos, la forma de pago y los datos adicionales son de esa operación y se vuelven a indicar.
 */
export type LineaPlantilla = {
  descripcion: string;
  cantidad: number | null;
  precioUnitario: number | null;
  unidad: string;
  tipoAfectacionIgv: string;
  descuento: AjusteForm;
  cargo: AjusteForm;
  isc: IscForm;
  icbper: boolean;
};

export type Plantilla = {
  /** Qué diálogo abre: la factura (serie F) o la boleta (serie B). */
  tipo: "factura" | "boleta";
  moneda: string;
  /** Solo si el formulario la ofrece (venta interna, NRUS en boleta, exportaciones); si no, `0101`. */
  operacion: string;
  cliente: { tipoDoc: string; numDoc: string; razonSocial: string; direccion: string; pais: string };
  lineas: LineaPlantilla[];
};

/** Los tipos de operación que el formulario sabe armar sin los datos propios de la operación (detracción, percepción…). */
const OPERACIONES_COPIABLES = /^(0101|0113|020[0-8])$/;
const NO_SE_OFRECE = new Set(["0202", "0205"]);

function ajusteDesde(a: DescuentoAplicado | CargoAplicado | null | undefined): AjusteForm {
  if (!a) return AJUSTE_VACIO;
  return { modo: a.tipo === "PORCENTAJE" ? "porcentaje" : "monto", valor: String(a.valor), afectaBase: a.afecta_base_igv };
}

function iscDesde(isc: ItemComprobante["isc"]): IscForm {
  if (!isc) return ISC_VACIO;
  const sistema = isc.sistema === "01" || isc.sistema === "02" || isc.sistema === "03" ? isc.sistema : "";
  if (sistema === "") return ISC_VACIO;
  return {
    sistema,
    tasa: sistema === "02" ? "" : String(isc.tasa),
    montoUnitario: sistema === "02" && isc.monto_unitario != null ? String(isc.monto_unitario) : "",
    basePvp: sistema === "03" && isc.base_pvp != null ? String(isc.base_pvp) : "",
  };
}

export function lineaDesde(i: ItemComprobante): LineaPlantilla {
  return {
    descripcion: i.descripcion,
    cantidad: Number(i.cantidad),
    precioUnitario: Number(i.precio_unitario),
    unidad: i.unidad,
    tipoAfectacionIgv: i.tipo_afectacion_igv,
    descuento: ajusteDesde(i.descuento),
    // El formulario lleva un cargo por línea: si la original tenía varios, se copia el primero.
    cargo: ajusteDesde(i.cargos?.[0]),
    isc: iscDesde(i.isc),
    icbper: !!i.icbper,
  };
}

/** Se puede duplicar una factura o una boleta; una nota es un ajuste a otro comprobante y no se copia. */
export function sePuedeDuplicar(c: Pick<Comprobante, "tipo">): boolean {
  return c.tipo === "01" || c.tipo === "03";
}

export function plantillaDesde(c: Comprobante): Plantilla | null {
  if (!sePuedeDuplicar(c)) return null;
  const operacionOriginal = c.tipo_operacion ?? "0101";
  const operacion = OPERACIONES_COPIABLES.test(operacionOriginal) && !NO_SE_OFRECE.has(operacionOriginal) ? operacionOriginal : "0101";
  const exportacion = /^020[0-8]$/.test(operacion);
  // Con una operación que no se copia (detracción 1001–1004, percepción 2001…) las líneas siguen igual: son gravadas comunes.
  const r = c.receptor;
  return {
    tipo: c.tipo === "03" ? "boleta" : "factura",
    moneda: c.moneda,
    operacion,
    cliente: {
      tipoDoc: r?.tipo_doc ?? (c.tipo === "03" ? "1" : "6"),
      numDoc: r?.tipo_doc === "-" ? "" : (r?.num_doc ?? ""),
      razonSocial: r?.tipo_doc === "-" ? "" : (r?.razon_social ?? ""),
      direccion: r?.direccion ?? "",
      pais: exportacion ? (r?.pais ?? "") : "",
    },
    lineas: c.items.length ? c.items.map(lineaDesde) : [],
  };
}

const EVENTO = "khipu:duplicar";

/** Le pide al diálogo «Nuevo comprobante» (que vive en la barra superior) que se abra con la copia del comprobante `id`. */
export function pedirDuplicado(id: string): void {
  window.dispatchEvent(new CustomEvent<string>(EVENTO, { detail: id }));
}

/** Suscripción del diálogo al pedido; devuelve cómo darse de baja. */
export function alPedirDuplicado(escuchar: (id: string) => void): () => void {
  const manejador = (e: Event) => escuchar((e as CustomEvent<string>).detail);
  window.addEventListener(EVENTO, manejador);
  return () => window.removeEventListener(EVENTO, manejador);
}
