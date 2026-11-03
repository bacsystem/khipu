/**
 * #557: lo poco frecuente del comprobante, a pedido. Cada uno es un botón «+» en la fila «Agregar:» del modal de emisión que abre su panel. El orden es el de la fila y el
 * de los paneles.
 */
export const EXTRAS = ["credito", "detraccion", "percepcion", "retencion", "operacion", "masOpciones", "anticipos", "referencias", "correo"] as const;
export type Extra = (typeof EXTRAS)[number];

/** #560: los que se ofrecen siempre. Los demás (percepción, retención, tipo de operación, más opciones, anticipos y referencias) solo con «Mostrar opciones avanzadas». */
export const EXTRAS_BASICOS: ReadonlySet<Extra> = new Set<Extra>(["credito", "detraccion", "correo"]);

export type ExtrasOfrecidos = {
  /** Los «+» de la fila, en el orden de `EXTRAS`. */
  ofrecidos: Extra[];
  /** Hay alguno que este comprobante admitiría pero que la preferencia de la empresa oculta: el modal dice dónde activarlos. */
  hayOcultos: boolean;
};

/**
 * Qué «+» ofrece el modal. Detracción, percepción y retención son de la venta interna (0101) y se excluyen entre sí: mientras una más fuerte está activa, la otra no se
 * ofrece (la detracción a las otras dos, la percepción a la retención). El crédito y la retención, solo en factura.
 *
 * Con `avanzadas` apagada se ofrecen solo los básicos (crédito, detracción, correo), salvo un avanzado que ya está activo —una copia de otro comprobante que trae una
 * exportación, referencias o un descuento global—: ese sigue a la vista con su «+» para no esconder datos que se van a emitir.
 */
export function extrasOfrecidos({
  tipo,
  operacion,
  avanzadas,
  activos,
}: {
  tipo: "factura" | "boleta";
  /** Tipo de operación (catálogo 51) elegido en el formulario. */
  operacion: string;
  /** La preferencia «Mostrar opciones avanzadas al emitir» de la empresa. */
  avanzadas: boolean;
  /** Qué panel está abierto (o trae datos). */
  activos: Record<Extra, boolean>;
}): ExtrasOfrecidos {
  const boleta = tipo === "boleta";
  const ventaInterna = operacion === "0101";
  const admitido: Record<Extra, boolean> = {
    credito: !boleta,
    detraccion: ventaInterna,
    percepcion: ventaInterna && !activos.detraccion,
    retencion: !boleta && ventaInterna && !activos.detraccion && !activos.percepcion,
    operacion: true,
    masOpciones: true,
    anticipos: true,
    referencias: true,
    correo: true,
  };
  const ofrecidos = EXTRAS.filter((e) => admitido[e] && (avanzadas || EXTRAS_BASICOS.has(e) || activos[e]));
  return { ofrecidos, hayOcultos: EXTRAS.some((e) => admitido[e] && !ofrecidos.includes(e)) };
}
