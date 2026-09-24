"use client";

import { FileMinusIcon, SendIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import type { Comprobante } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
import { afectacionPredominante, importeLineaNota, impuestoRedondeaACero, itemParaNota, lineaRedondeaACero, topePorTributo } from "@/lib/comprobantes/notas";
import { redondear } from "@/lib/comprobantes/totales";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearMonto, formatearNumero, hoyLima, sumarDias } from "@/lib/formato";
import { sinEnvioImplicito } from "@/lib/formularios";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

type Tipo = "07" | "08";
/** Motivos de NC en los que la nota es total y se copian los ítems de la factura; el 13 no mueve importes. */
const MOTIVOS_NC_TOTAL = new Set(["01", "02", "06"]);
/**
 * Motivos de NC que acreditan un importe, no unidades (catálogo 09: descuento global, descuento por ítem, bonificación,
 * disminución en el valor, otros conceptos): una sola línea propia por el monto, como la ND. SUNAT no impone otra
 * estructura (solo 4367 observa 04/05/08 sobre boletas) y la compara con la factura por total (3286) y por tributo (3503).
 */
const MOTIVOS_NC_IMPORTE = new Set(["04", "05", "08", "09", "10"]);
/** Importes de dinero: hasta 12 enteros y 2 decimales (3250/3253 en cuotas; 2025 en la línea de la ND, «12 enteros y 10 decimales»). */
const DOS_DECIMALES = /^\d{1,12}(\.\d{1,2})?$/;

/**
 * Emite una nota de crédito o débito sobre una factura aceptada (`POST /v1/notas`). La NC puede ser total (copia la
 * factura), parcial (ítems de la factura con la cantidad a devolver) o de corrección de cuotas (13, importe 0); la ND lleva
 * una línea propia (interés, penalidad, aumento). Los motivos salen de los catálogos 09/10 y las series de tipo 07/08.
 */
export function NotaForm({ factura, series }: { factura: Comprobante; series: Serie[] }) {
  const router = useRouter();
  const [tipo, setTipo] = useState<Tipo>("07");
  const [serie, setSerie] = useState("");
  const [motivo, setMotivo] = useState("");
  const [descripcion, setDescripcion] = useState("");
  // En 0, no en lo facturado: con las cantidades precargadas al total, elegir «09 Disminución en el valor», escribir
  // el sustento y un clic emitía una NC por el 100 % de la factura. Y sobre una factura con descuento global,
  // cargos o anticipos, el 100 % de los ítems supera el total (3286): el usuario elige qué acredita.
  const [cantidades, setCantidades] = useState<number[]>(factura.items.map(() => 0));
  const [nd, setNd] = useState({ descripcion: "", importe: "" });
  const [cuotas, setCuotas] = useState<Array<{ monto: string; vencimiento: string }>>(
    (factura.forma_pago.cuotas ?? []).map((q) => ({ monto: String(q.monto), vencimiento: q.vencimiento })),
  );
  const [catalogos, setCatalogos] = useState<Record<Tipo, CatalogoSunat | null>>({ "07": null, "08": null });
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  // Tras un error el botón se deshabilita y el foco caía a `body`: un lector de pantalla no llegaba al mensaje.
  const alertaRef = useRef<HTMLParagraphElement>(null);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  const [intentoCatalogos, setIntentoCatalogos] = useState(0);
  useEffect(() => {
    let vigente = true;
    const sinMotivos = () => setError("No se pudieron cargar los motivos (catálogos 09/10). Reintente.");
    Promise.all([apiRequest<CatalogoSunat>("/api/proxy/catalogos/09", { method: "GET" }), apiRequest<CatalogoSunat>("/api/proxy/catalogos/10", { method: "GET" })])
      .then(([nc, ndc]) => {
        if (!vigente) return;
        setCatalogos({ "07": nc.datos, "08": ndc.datos });
        if (!nc.datos || !ndc.datos) sinMotivos();
      })
      // Un 500 ya caía en la rama de arriba; un `fetch` RECHAZADO (red caída) no: el motivo quedaba en
      // «Cargando…» deshabilitado para siempre y la página no decía nada.
      .catch(() => vigente && sinMotivos());
    return () => {
      vigente = false;
    };
  }, [intentoCatalogos]);

  const seriesDelTipo = useMemo(() => series.filter((s) => s.tipo === tipo && s.activa && s.serie.startsWith("F")), [series, tipo]);
  useEffect(() => {
    setSerie(seriesDelTipo[0]?.serie ?? "");
    setMotivo("");
  }, [seriesDelTipo]);

  const esNc = tipo === "07";
  // Con anticipos regularizados la nota "total" no puede copiar la factura (iría por el bruto, SUNAT compara con el neto): se pide por ítems.
  const conAnticipos = (factura.anticipos?.length ?? 0) > 0;
  const esTotal = esNc && MOTIVOS_NC_TOTAL.has(motivo) && !conAnticipos;
  const esCuotas = esNc && motivo === "13";
  const esImporte = esNc && MOTIVOS_NC_IMPORTE.has(motivo);
  const esParcial = esNc && motivo !== "" && !esTotal && !esCuotas && !esImporte;
  // La ND y la NC por importe comparten la línea propia (concepto + importe).
  const lineaPropia = !esNc || esImporte;
  const lineasParciales = factura.items.map((item, idx) => ({ item, cantidad: cantidades[idx] ?? 0 })).filter((x) => x.cantidad > 0);
  const itemsParciales = lineasParciales.map(({ item, cantidad }) => itemParaNota(item, cantidad));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const body: Record<string, unknown> = {
      tipo,
      serie,
      fecha_emision: hoyLima(),
      documento_afectado: { serie: factura.serie, numero: factura.numero },
      motivo,
      // Un `<input>` de una línea no admite saltos, pero sí un tabulador pegado (una celda de Excel): el backend lo
      // rechaza (2135, `isISOControl`). Cualquier espacio en blanco se normaliza a un espacio antes de viajar.
      descripcion: descripcion.replace(/\s+/g, " ").trim(),
    };
    if (esParcial) body.items = itemsParciales;
    if (esCuotas) {
      // Redondeado a 2: la suma en punto flotante de 10.10 + 20.20 viaja como 30.299999999999997 y el backend la
      // rechaza (3250: hasta 2 decimales; 3319: la suma de cuotas debe ser el pendiente). Los fixtures (61.5+61.5)
      // son exactos en binario, por eso ningún e2e lo veía.
      body.forma_pago = {
        tipo: "credito",
        monto_pendiente: redondear(cuotas.reduce((acc, q) => acc + Number(q.monto || 0), 0), 2),
        cuotas: cuotas.map((q) => ({ monto: redondear(Number(q.monto), 2), vencimiento: q.vencimiento })),
      };
    }
    // La afectación de la línea propia sigue a la factura, no un «10» fijo: sobre una exportación el dominio
    // exige 40 (2642) —con el 10 fijo no se podía emitir ninguna ND sobre exportaciones— y sobre IVAP, 17.
    if (lineaPropia) body.items = [{ descripcion: nd.descripcion.trim(), unidad: "ZZ", cantidad: 1, precio_unitario: Number(nd.importe), tipo_afectacion_igv: afectacionLinea }];
    setEnviando(true);
    const res = await apiRequest<Comprobante>("/api/proxy/notas", { method: "POST", body });
    // Un corte de conexión no dice si el POST llegó, y pudo haber consumido correlativo: reintentar a ciegas
    // duplica la nota. (El cliente ya no lanza: devuelve el sobre de error, así que esto no puede ir en un catch.)
    if (noSeSabeSiLlego(res)) {
      setEnviando(false);
      setError(
        `Se cortó la conexión mientras se emitía. La nota pudo haberse emitido igual: revisa las notas de la factura ${factura.serie}-${factura.numero} antes de volver a intentarlo, para no duplicarla.`,
      );
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setEnviando(false);
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    // A propósito NO se rehabilita el botón en el camino feliz: se desmonta con la navegación. Rehabilitarlo antes
    // de `router.push` dejaba una ventana en la que un segundo clic emitía otra nota (medido: 3 notas con 3 clics).
    router.push(`/comprobantes/${res.datos.id}`);
  }

  // Los motivos que dependen de cómo es la factura no se ofrecen si no aplican. Con el catálogo completo, «11
  // Ajustes de exportación» o «12 Ajustes IVAP» sobre una factura interna salían numeradas y SUNAT las rechazaba
  // (2642/3107) con el correlativo consumido; y «13 corrección de cuotas» sobre una factura al contado viola 3260.
  const esExportacion = /^020[0-8]$/.test(factura.tipo_operacion ?? "");
  const esIvap = factura.items.some((i) => i.tipo_afectacion_igv === "17");
  const alCredito = factura.forma_pago.tipo === "credito";
  // ND 13 «Penalidades»: SUNAT las declara operaciones INAFECTAS (regla 3507, tres variantes en la hoja
  // NotaDebito2_0: con IGV/IVAP, con 9995/9997 o con tributo 1000/1016 → ERROR). La única afectación que pasa es
  // 30. Antes salía gravada (10, o 40 en exportación): numerada, firmada y rechazada, con el correlativo consumido.
  const esPenalidad = !esNc && motivo === "13";
  // La NC por importe descuenta lo que la factura cobró: si tiene alguna línea gravada va con IGV (10); si toda la
  // factura es exonerada/inafecta, con esa afectación (3503 compara por tributo). La ND de intereses va gravada.
  const afectacionLinea = esPenalidad ? "30" : esExportacion ? "40" : esIvap ? "17" : esImporte ? afectacionPredominante(factura.items) : "10";
  // La etiqueta dice qué impuesto lleva el importe tecleado: «con IGV» mentía en exportación (40, sin IGV) e IVAP (17, 4 %).
  const etiquetaImporte = { "30": "Importe inafecto, sin IGV", "40": "Importe sin IGV, exportación", "17": "Importe con IVAP", "20": "Importe exonerado, sin IGV", "10": "Importe con IGV" }[afectacionLinea];

  // NC 13: lo que exige SUNAT (3253 monto > 0, 3321 vencimiento posterior a la factura, 3320 neto ≤ total) antes
  // «listo» solo pedía que hubiera al menos una cuota, y una cuota en blanco viajaba como monto 0 y fecha vacía.
  const cuotasValidas =
    cuotas.length > 0 &&
    // Hasta 2 decimales por cuota (3253: «12 enteros y hasta 2 decimales»): `10.123` pasaba el formulario y el
    // backend lo rechazaba después. `step=0.01` no frena lo tipeado, solo las flechas.
    cuotas.every((q) => Number(q.monto) > 0 && DOS_DECIMALES.test(q.monto.trim()) && q.vencimiento !== "" && q.vencimiento > factura.fecha_emision) &&
    redondear(cuotas.reduce((acc, q) => acc + Number(q.monto), 0), 2) <= factura.totales.total;
  const motivos = (catalogos[tipo]?.entradas ?? []).filter((m) => {
    if (m.codigo === "11") return esExportacion;
    if (m.codigo === "12") return esIvap;
    // ND 13 sobre una exportación no tiene salida: la penalidad va inafecta (3507) y el dominio exige 40 en toda
    // línea de una exportación (2642). Se ofrecía igual y el mock la daba por buena.
    if (m.codigo === "13") return esNc ? alCredito : !esExportacion;
    // Factura IVAP: la línea sale con 17 y SUNAT exige el motivo 12 en la NC (NotaCredito2_0 fila 223) y en la ND
    // (NotaDebito2_0 fila 206): 3230. Solo escapa el 13 (NC: línea gravada de importe 0; ND: penalidad con 30). La
    // primera corrección lo aplicó solo a la ND; la NC 01/07 seguía saliendo numerada y rechazada.
    if (esIvap) return false;
    return true;
  });

  // Importe de la nota y su tope. El tope es lo que SUNAT compara (3286): el total de la factura, menos lo que ya
  // acreditaron otras NC vigentes (el backend lo suma con lock de fila; acá se anticipa para no gastar un viaje ni
  // consumir número).
  //
  // El importe es Σ de lo que paga el cliente por cada línea (`importeLineaNota`: el `precio_venta` del backend con
  // la cantidad facturada; en proporción, con los ajustes que viajan, con menos), válido para CUALQUIER afectación.
  // La primera versión pasaba por `calcularTotales`, que solo entiende 10/20/30 y devuelve 0 para 40 (exportación) y
  // 17 (IVAP): el tope era inerte y el usuario leía «$ 0.00» antes de emitir. La segunda hacía precio × cantidad y no
  // veía los cargos de línea. Vale también para la nota total (01/02/06): copia la factura entera, y si ya hay NC
  // vigentes la supera.
  //
  // La nota total copia ítems, descuento global, cargos y —desde #123— también el redondeo del importe total, así
  // que sale exactamente por el PayableAmount de la factura (3286 sin tolerancia en facturas).
  const importeNota = esParcial
    ? redondear(lineasParciales.reduce((s, { item, cantidad }) => s + importeLineaNota(item, cantidad), 0), 2)
    : esTotal
      ? factura.totales.total
      : esImporte
        ? redondear(Number(nd.importe) || 0, 2)
        : 0;
  const acreditado = redondear(
    (factura.notas ?? [])
      .filter((n) => n.tipo === "07" && n.estado_documento !== "RECHAZADO" && n.estado_documento !== "INVALIDO" && n.estado_documento !== "ANULADO")
      .reduce((s, n) => s + n.total, 0),
    2,
  );
  // En la NC por importe el límite real puede ser el del tributo de la línea (3503), no el total (3286): en una
  // factura con ISC, anticipos o mixta el total queda por encima del gravado + IGV, y el tope mostrado invitaba al 422.
  const topeTributo = esImporte ? topePorTributo(factura.totales, afectacionLinea) : factura.totales.total;
  const limitaTributo = esImporte && topeTributo < factura.totales.total;
  const tope = redondear(Math.min(factura.totales.total, topeTributo) - acreditado, 2);
  // El motivo 10 «Otros conceptos» está exento de los ocho límites: NotaCredito2_0 fila 111 (3286) y filas 114–122
  // (3503) empiezan todas con «diferente de '10'». El backend lo exime igual desde #141.
  const exentoDeTope = esNc && motivo === "10";
  const superaTope = (esParcial || esTotal || esImporte) && !exentoDeTope && importeNota > tope;
  const conceptoTributo = { "10": "gravado + IGV", "20": "exonerado", "30": "inafecto", "40": "exportación", "17": "IVAP" }[afectacionLinea];
  // 3111: en una línea IVAP un importe de 0.07 a 0.12 deja el impuesto en 0.00 y SUNAT rechaza ya numerada.
  const ivapEnCero = lineaPropia && impuestoRedondeaACero(afectacionLinea, Number(nd.importe) || 0);

  // Por qué el botón está deshabilitado cuando el campo se ve lleno: un importe con 3 decimales no avisaba nada.
  const conDecimalesDeMas = (v: string) => v.trim() !== "" && !DOS_DECIMALES.test(v.trim());
  const lineaEnCero = esParcial && lineasParciales.some(({ item, cantidad }) => lineaRedondeaACero(item, cantidad));
  // Lo que SUNAT prohíbe no es una línea gratuita dentro de la nota, sino que el importe TOTAL sea 0 (f401, 2062): una NC
  // solo de bonificaciones no acredita nada. Una gratuita sí puede acompañar a una onerosa.
  const notaSinImporte = esParcial && itemsParciales.length > 0 && importeNota < 0.01;
  const avisoDecimales = lineaPropia && conDecimalesDeMas(nd.importe)
    ? "El importe admite hasta 2 decimales."
    : esCuotas && cuotas.some((q) => conDecimalesDeMas(q.monto))
      ? "Cada cuota admite hasta 2 decimales."
      : lineaEnCero
        ? "Hay una línea cuyo importe, cargo o impuesto redondea a 0.00: sube la cantidad o ponla en 0."
        : notaSinImporte
          ? "La nota no acredita ningún importe (SUNAT 2062): las líneas gratuitas no se cobran, así que incluye también la línea que devuelves."
        : ivapEnCero
          ? "El IVAP de la línea redondearía a 0.00 (SUNAT 3111): el importe debe ser 0.13 o más."
        : lineaPropia && nd.descripcion.trim() !== "" && nd.descripcion.trim().length < 3
          ? "El concepto necesita al menos 3 caracteres (SUNAT 4084)."
          : esParcial && descripcion.trim() !== "" && lineasParciales.length === 0
            ? "Pon una cantidad mayor que 0 en al menos un ítem."
            : null;

  const listo =
    serie !== "" &&
    motivo !== "" &&
    descripcion.trim() !== "" &&
    (!esParcial || (itemsParciales.length > 0 && !superaTope && !lineaEnCero && !notaSinImporte)) &&
    (!esTotal || !superaTope) &&
    (!esImporte || !superaTope) &&
    !ivapEnCero &&
    // Importe de la línea propia con hasta 12 enteros y 2 decimales (2025); concepto de 3 a 500 (4084 observa con menos de 3).
    (!lineaPropia || (nd.descripcion.trim().length >= 3 && Number(nd.importe) > 0 && DOS_DECIMALES.test(nd.importe.trim()))) &&
    (!esCuotas || cuotasValidas);

  return (
    <form onSubmit={onSubmit} onKeyDown={sinEnvioImplicito} className="space-y-5" data-testid="nota-form">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-tipo" className={ETIQUETA_CAMPO}>Tipo de nota</label>
          <select id="nota-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as Tipo)} className={CAMPO}>
            <option value="07">Nota de crédito (07)</option>
            <option value="08">Nota de débito (08)</option>
          </select>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-serie" className={ETIQUETA_CAMPO}>Serie</label>
          {seriesDelTipo.length ? (
            <select id="nota-serie" value={serie} onChange={(e) => setSerie(e.target.value)} className={cn(CAMPO, "font-mono")}>
              {seriesDelTipo.map((s) => (
                <option key={s.serie} value={s.serie}>{s.serie}</option>
              ))}
            </select>
          ) : (
            <p className="text-[12px] leading-relaxed text-destructive">
              No hay series de {esNc ? "nota de crédito" : "nota de débito"} sobre facturas. <Link href="/series" className="underline">Cree una</Link> (p. ej. {esNc ? "FC01" : "FD01"}).
            </p>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-motivo" className={ETIQUETA_CAMPO}>Motivo (catálogo {esNc ? "09" : "10"})</label>
          <select id="nota-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} className={CAMPO} disabled={!catalogos[tipo]}>
            <option value="">{catalogos[tipo] ? "Seleccione…" : "Cargando…"}</option>
            {motivos.map((m) => (
              <option key={m.codigo} value={m.codigo}>{m.codigo} · {m.descripcion.length > 70 ? `${m.descripcion.slice(0, 70)}…` : m.descripcion}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="nota-descripcion" className={ETIQUETA_CAMPO}>Sustento</label>
        <input id="nota-descripcion" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={500} placeholder="Ej.: el cliente devolvió la mercadería completa" className={CAMPO} />
        <span className={AYUDA_CAMPO}>Hasta 500 caracteres, sin saltos de línea (SUNAT 2135). Va en el XML y en la representación impresa.</span>
      </div>

      {esTotal ? (
        <div className={cn("rounded-lg border px-3 py-2 text-[12px] leading-relaxed", superaTope ? "border-destructive/50 bg-destructive/5 text-destructive" : "border-border bg-muted text-muted-foreground")} data-testid="nota-total">
          <p>
            Nota <strong className="text-foreground">total</strong>: khipu copia los ítems, el descuento global y los cargos de la factura {factura.serie}-{factura.numero} ({formatearMonto(factura.moneda, factura.totales.total)}).
          </p>
          {/* Una nota total sobre una factura ya acreditada por otras NC vigentes la supera (3286): es el escenario de
              la doble acreditación (#83), y antes esta pantalla no decía nada y dejaba emitir. */}
          {acreditado > 0 || superaTope ? (
            <p className="mt-1" data-testid="nota-importe">
              Importe de la nota: <span className="font-mono tabular-nums">{formatearMonto(factura.moneda, importeNota)}</span>. Tope: <span className="font-mono tabular-nums">{formatearMonto(factura.moneda, tope)}</span>
              {acreditado > 0 ? ` (ya acreditado ${formatearMonto(factura.moneda, acreditado)} en otras notas de crédito)` : ""}.
              {superaTope ? <span role="alert" className="block"> Supera el tope: SUNAT la rechazaría (3286). Solo queda por acreditar {formatearMonto(factura.moneda, tope)}; elige un motivo parcial.</span> : null}
            </p>
          ) : null}
        </div>
      ) : null}

      {esParcial ? (
        <div className="overflow-hidden rounded-lg border border-border">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-border/70 bg-muted font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
                <th className="px-3 py-2 font-medium">Ítem de la factura</th>
                <th className="w-24 px-3 py-2 text-right font-medium">Facturado</th>
                <th className="w-32 px-3 py-2 text-right font-medium">Cantidad en la nota</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {factura.items.map((item, idx) => (
                <tr key={idx}>
                  <td className="px-3 py-2 text-foreground">{item.descripcion}</td>
                  <td className="px-3 py-2 text-right font-mono tabular-nums text-muted-foreground">{formatearNumero(item.cantidad)} {item.unidad}</td>
                  <td className="px-3 py-2 text-right">
                    <input
                      type="number"
                      min={0}
                      max={Number(item.cantidad)}
                      step="any"
                      value={cantidades[idx] ?? 0}
                      aria-label={`Cantidad de ${item.descripcion} en la nota`}
                      // Hasta 10 decimales (2025): con 11 el backend rechazaba después. Y entre 0 y lo facturado.
                      onChange={(e) => setCantidades((c) => c.map((v, i) => (i === idx ? Math.min(Number(item.cantidad), Math.max(0, Number(Number(e.target.value).toFixed(10)))) : v)))}
                      className={cn(CAMPO, "h-8 w-28 text-right font-mono")}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className={cn(AYUDA_CAMPO, "border-t border-border/60 px-3 py-2")}>
            {conAnticipos && MOTIVOS_NC_TOTAL.has(motivo) ? `La factura regularizó anticipos (neto ${formatearMonto(factura.moneda, factura.totales.total)}): ajuste las cantidades para que la nota no supere ese importe. ` : ""}
            Ponga 0 en los ítems que no entran en la nota. Un descuento o cargo de línea en porcentaje acompaña a la cantidad; los de monto fijo solo se conservan con la cantidad facturada.
          </p>
          {/* El importe que va a salir, contra lo que SUNAT compara. Antes el formulario no mostraba ninguna cifra
              de la nota y el usuario descubría el 3286 después de emitir. */}
          <div className={cn("flex flex-wrap items-baseline justify-between gap-2 border-t border-border/60 px-3 py-2 text-[12px]", superaTope ? "text-destructive" : "text-muted-foreground")} data-testid="nota-importe">
            <span>
              Importe de la nota: <strong className="font-mono tabular-nums">{formatearMonto(factura.moneda, importeNota)}</strong>
            </span>
            <span>
              Tope: <span className="font-mono tabular-nums">{formatearMonto(factura.moneda, tope)}</span>
              {acreditado > 0 ? ` (factura ${formatearMonto(factura.moneda, factura.totales.total)} menos ${formatearMonto(factura.moneda, acreditado)} ya acreditado)` : ""}
            </span>
            {superaTope ? (
              <span role="alert" className="basis-full">
                Supera el tope: SUNAT la rechazaría (3286). Baja cantidades, o para anular o devolver todo elige el motivo 01 o 06.
              </span>
            ) : null}
          </div>
        </div>
      ) : null}

      {esCuotas ? (
        <div className="space-y-2 rounded-lg border border-border p-3">
          <p className="text-[12px] text-muted-foreground">Cuotas corregidas de la factura (la nota sale con importe 0; SUNAT 3315). El neto pendiente será la suma de las cuotas.</p>
          {cuotas.map((q, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2">
              <span className="w-20 font-mono text-[11px] text-muted-foreground">Cuota{String(i + 1).padStart(3, "0")}</span>
              <input type="number" min={0.01} step="0.01" value={q.monto} aria-label={`Monto de la cuota ${i + 1}`} onChange={(e) => setCuotas((cs) => cs.map((c, j) => (j === i ? { ...c, monto: e.target.value } : c)))} className={cn(CAMPO, "h-8 w-32 font-mono")} />
              <input type="date" value={q.vencimiento} min={sumarDias(factura.fecha_emision, 1)} aria-label={`Vencimiento de la cuota ${i + 1}`} onChange={(e) => setCuotas((cs) => cs.map((c, j) => (j === i ? { ...c, vencimiento: e.target.value } : c)))} className={cn(CAMPO, "h-8 w-40 font-mono")} />
              <button type="button" aria-label={`Quitar la cuota ${i + 1}`} onClick={() => setCuotas((cs) => cs.filter((_, j) => j !== i))} className="text-[12px] text-muted-foreground hover:text-destructive">Quitar</button>
            </div>
          ))}
          <button type="button" onClick={() => setCuotas((cs) => [...cs, { monto: "", vencimiento: "" }])} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>Añadir cuota</button>
        </div>
      ) : null}

      {lineaPropia ? (
        <div className="space-y-2">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-[1fr_180px]">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="nd-descripcion" className={ETIQUETA_CAMPO}>Concepto</label>
              {/* Hasta 500 como el sustento (2027, `Item`): la recert #4 midió 501 caracteres viajando y un 422 evitable. */}
              <input id="nd-descripcion" value={nd.descripcion} onChange={(e) => setNd({ ...nd, descripcion: e.target.value })} maxLength={500} placeholder={esNc ? "Ej.: descuento por pronto pago" : "Ej.: intereses por mora de 30 días"} className={CAMPO} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="nd-importe" className={ETIQUETA_CAMPO}>{etiquetaImporte} ({factura.moneda})</label>
              <input id="nd-importe" type="number" min={0.01} step="0.01" value={nd.importe} onChange={(e) => setNd({ ...nd, importe: e.target.value })} className={cn(CAMPO, "font-mono")} />
            </div>
          </div>
          {esImporte ? (
            <div className={cn("flex flex-wrap items-baseline justify-between gap-2 rounded-lg border border-border px-3 py-2 text-[12px]", superaTope ? "text-destructive" : "text-muted-foreground")} data-testid="nota-importe">
              <span>
                Importe de la nota: <strong className="font-mono tabular-nums">{formatearMonto(factura.moneda, importeNota)}</strong>
              </span>
              <span>
                {exentoDeTope ? "Sin tope: SUNAT exime al motivo 10 del 3286 y del 3503 (filas 111 y 114–122)." : <>Tope: <span className="font-mono tabular-nums">{formatearMonto(factura.moneda, tope)}</span>{limitaTributo ? ` (por tributo, 3503: ${conceptoTributo} de la factura ${formatearMonto(factura.moneda, topeTributo)})` : ""}{acreditado > 0 ? ` (menos ${formatearMonto(factura.moneda, acreditado)} ya acreditado)` : ""}</>}
              </span>
              {superaTope ? <span role="alert" className="basis-full">Supera el tope: SUNAT la rechazaría ({limitaTributo ? "3503, por tributo" : "3286"}). Baja el importe.</span> : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {error ? (
        <div className="flex flex-wrap items-center gap-3">
          <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">{error}</p>
          {/* Si falló UNO de los dos catálogos, la otra pestaña funciona pero la alerta queda pegada: el botón tiene
              que seguir ahí para recargar el que falta, no solo cuando falta el de la pestaña actual. */}
          {!catalogos["07"] || !catalogos["08"] ? (
            <button type="button" onClick={() => { setError(null); setIntentoCatalogos((n) => n + 1); }} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>
              Reintentar
            </button>
          ) : null}
        </div>
      ) : null}

      {avisoDecimales ? (
        <p id="nota-aviso-decimales" role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")}>{avisoDecimales}</p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button type="submit" disabled={!listo || enviando} aria-describedby={avisoDecimales ? "nota-aviso-decimales" : undefined} className={BOTON_PRIMARIO}>
          {enviando ? <SendIcon className="size-4 animate-pulse" /> : <FileMinusIcon className="size-4" />}
          {enviando ? "Emitiendo y enviando a SUNAT…" : `Emitir ${esNc ? "nota de crédito" : "nota de débito"}`}
        </button>
        <Link href={`/comprobantes/${factura.id}`} className={BOTON_SECUNDARIO}>Cancelar</Link>
        {/* El cambio de texto del botón no se anuncia; esto sí. */}
        <span role="status" className="sr-only">{enviando ? "Emitiendo y enviando a SUNAT…" : ""}</span>
      </div>
    </form>
  );
}
