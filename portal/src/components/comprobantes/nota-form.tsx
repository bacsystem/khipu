"use client";

import { FileMinusIcon, SendIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import type { Comprobante } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
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
  const esParcial = esNc && motivo !== "" && !esTotal && !esCuotas;
  const itemsParciales = factura.items
    .map((i, idx) => ({ item: i, cantidad: cantidades[idx] ?? 0 }))
    .filter((x) => x.cantidad > 0)
    .map(({ item, cantidad }) => ({
      codigo: item.codigo ?? undefined,
      descripcion: item.descripcion,
      unidad: item.unidad,
      cantidad,
      precio_unitario: item.precio_unitario,
      tipo_afectacion_igv: item.tipo_afectacion_igv,
      descuento: item.descuento && cantidad === Number(item.cantidad) ? { [item.descuento.tipo === "PORCENTAJE" ? "porcentaje" : "monto"]: item.descuento.valor, afecta_base_igv: item.descuento.afecta_base_igv } : undefined,
      isc: item.isc ? { sistema: item.isc.sistema, tasa: item.isc.tasa } : undefined,
      icbper: item.icbper ? true : undefined,
    }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const body: Record<string, unknown> = {
      tipo,
      serie,
      fecha_emision: hoyLima(),
      documento_afectado: { serie: factura.serie, numero: factura.numero },
      motivo,
      descripcion: descripcion.trim(),
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
    // La afectación de la línea de la ND sigue a la factura, no un «10» fijo: sobre una exportación el dominio
    // exige 40 (2642) —con el 10 fijo no se podía emitir ninguna ND sobre exportaciones— y sobre IVAP, 17.
    if (!esNc) body.items = [{ descripcion: nd.descripcion.trim(), unidad: "ZZ", cantidad: 1, precio_unitario: Number(nd.importe), tipo_afectacion_igv: afectacionNd }];
    setEnviando(true);
    let res: Awaited<ReturnType<typeof apiRequest<Comprobante>>>;
    try {
      res = await apiRequest<Comprobante>("/api/proxy/notas", { method: "POST", body });
    } catch {
      // `fetch` RECHAZA ante un corte de conexión: sin este catch el botón quedaba en «Emitiendo…» para siempre,
      // sin alerta, y el POST pudo haber llegado y consumido correlativo. Reintentar a ciegas duplica la nota.
      setEnviando(false);
      setError(
        `Se cortó la conexión mientras se emitía. La nota pudo haberse emitido igual: revisá las notas de la factura ${factura.serie}-${factura.numero} antes de volver a intentarlo, para no duplicarla.`,
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
  const afectacionNd = esPenalidad ? "30" : esExportacion ? "40" : esIvap ? "17" : "10";

  // NC 13: lo que exige SUNAT (3253 monto > 0, 3321 vencimiento posterior a la factura, 3320 neto ≤ total) antes
  // «listo» solo pedía que hubiera al menos una cuota, y una cuota en blanco viajaba como monto 0 y fecha vacía.
  const cuotasValidas =
    cuotas.length > 0 &&
    cuotas.every((q) => Number(q.monto) > 0 && q.vencimiento !== "" && q.vencimiento > factura.fecha_emision) &&
    redondear(cuotas.reduce((acc, q) => acc + Number(q.monto), 0), 2) <= factura.totales.total;
  const motivos = (catalogos[tipo]?.entradas ?? []).filter((m) => {
    if (m.codigo === "11") return esExportacion;
    if (m.codigo === "12") return esIvap;
    if (m.codigo === "13" && esNc) return alCredito;
    return true;
  });

  // Importe de la nota y su tope. El tope es lo que SUNAT compara (3286): el total de la factura, menos lo que ya
  // acreditaron otras NC vigentes (el backend lo suma con lock de fila; acá se anticipa para no gastar un viaje ni
  // consumir número).
  //
  // El importe se calcula como Σ precio × cantidad por línea, redondeado a 2: `precio_unitario` ya es el precio de
  // venta con impuesto incluido en 10/17 y el valor sin IGV en 20/30/40, así que la suma es el total a pagar de la
  // línea para CUALQUIER afectación. La primera versión pasaba por `calcularTotales`, que solo entiende 10/20/30
  // y devuelve 0 para 40 (exportación) y 17 (IVAP): el tope era inerte y el usuario leía «$ 0.00» antes de emitir.
  // Vale también para la nota total (01/02/06): copia la factura entera, y si ya hay NC vigentes la supera.
  const importeNota = esParcial
    ? redondear(itemsParciales.reduce((s, i) => s + redondear(i.precio_unitario * i.cantidad, 2), 0), 2)
    : esTotal
      ? factura.totales.total
      : 0;
  const acreditado = redondear(
    (factura.notas ?? [])
      .filter((n) => n.tipo === "07" && n.estado_documento !== "RECHAZADO" && n.estado_documento !== "INVALIDO" && n.estado_documento !== "ANULADO")
      .reduce((s, n) => s + n.total, 0),
    2,
  );
  const tope = redondear(factura.totales.total - acreditado, 2);
  const superaTope = (esParcial || esTotal) && importeNota > tope;

  const listo =
    serie !== "" &&
    motivo !== "" &&
    descripcion.trim() !== "" &&
    (!esParcial || (itemsParciales.length > 0 && !superaTope)) &&
    (!esTotal || !superaTope) &&
    (esNc || (nd.descripcion.trim() !== "" && Number(nd.importe) > 0)) &&
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
          {acreditado > 0 ? (
            <p className="mt-1" data-testid="nota-importe">
              Tope: <span className="font-mono tabular-nums">{formatearMonto(factura.moneda, tope)}</span> (ya acreditado {formatearMonto(factura.moneda, acreditado)} en otras notas de crédito).
              {superaTope ? <span role="alert" className="block"> Supera el tope: SUNAT la rechazaría (3286). Solo queda por acreditar {formatearMonto(factura.moneda, tope)}; elegí un motivo parcial.</span> : null}
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
                      onChange={(e) => setCantidades((c) => c.map((v, i) => (i === idx ? Math.min(Number(item.cantidad), Math.max(0, Number(e.target.value))) : v)))}
                      className={cn(CAMPO, "h-8 w-28 text-right font-mono")}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className={cn(AYUDA_CAMPO, "border-t border-border/60 px-3 py-2")}>
            {conAnticipos && MOTIVOS_NC_TOTAL.has(motivo) ? `La factura regularizó anticipos (neto ${formatearMonto(factura.moneda, factura.totales.total)}): ajuste las cantidades para que la nota no supere ese importe. ` : ""}
            Ponga 0 en los ítems que no entran en la nota. El descuento de línea solo se conserva si la cantidad es la facturada.
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
                Supera el tope: SUNAT la rechazaría (3286). Bajá cantidades, o para anular o devolver todo elegí el motivo 01 o 06.
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
              <button type="button" onClick={() => setCuotas((cs) => cs.filter((_, j) => j !== i))} className="text-[12px] text-muted-foreground hover:text-destructive">Quitar</button>
            </div>
          ))}
          <button type="button" onClick={() => setCuotas((cs) => [...cs, { monto: "", vencimiento: "" }])} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>Añadir cuota</button>
        </div>
      ) : null}

      {!esNc ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-[1fr_180px]">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="nd-descripcion" className={ETIQUETA_CAMPO}>Concepto</label>
            <input id="nd-descripcion" value={nd.descripcion} onChange={(e) => setNd({ ...nd, descripcion: e.target.value })} placeholder="Ej.: intereses por mora de 30 días" className={CAMPO} />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="nd-importe" className={ETIQUETA_CAMPO}>{esPenalidad ? `Importe inafecto, sin IGV (${factura.moneda})` : `Importe con IGV (${factura.moneda})`}</label>
            <input id="nd-importe" type="number" min={0.01} step="0.01" value={nd.importe} onChange={(e) => setNd({ ...nd, importe: e.target.value })} className={cn(CAMPO, "font-mono")} />
          </div>
        </div>
      ) : null}

      {error ? (
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-sm text-destructive" role="alert">{error}</p>
          {!catalogos[tipo] ? (
            <button type="button" onClick={() => { setError(null); setIntentoCatalogos((n) => n + 1); }} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>
              Reintentar
            </button>
          ) : null}
        </div>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button type="submit" disabled={!listo || enviando} className={BOTON_PRIMARIO}>
          {enviando ? <SendIcon className="size-4 animate-pulse" /> : <FileMinusIcon className="size-4" />}
          {enviando ? "Emitiendo y enviando a SUNAT…" : `Emitir ${esNc ? "nota de crédito" : "nota de débito"}`}
        </button>
        <Link href={`/comprobantes/${factura.id}`} className={BOTON_SECUNDARIO}>Cancelar</Link>
      </div>
    </form>
  );
}
