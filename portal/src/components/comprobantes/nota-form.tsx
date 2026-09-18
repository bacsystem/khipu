"use client";

import { FileMinusIcon, SendIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import type { Comprobante } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearMonto, formatearNumero } from "@/lib/formato";
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
  const [cantidades, setCantidades] = useState<number[]>(factura.items.map((i) => Number(i.cantidad)));
  const [nd, setNd] = useState({ descripcion: "", importe: "" });
  const [cuotas, setCuotas] = useState<Array<{ monto: string; vencimiento: string }>>(
    (factura.forma_pago.cuotas ?? []).map((q) => ({ monto: String(q.monto), vencimiento: q.vencimiento })),
  );
  const [catalogos, setCatalogos] = useState<Record<Tipo, CatalogoSunat | null>>({ "07": null, "08": null });
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let vigente = true;
    Promise.all([apiRequest<CatalogoSunat>("/api/proxy/catalogos/09", { method: "GET" }), apiRequest<CatalogoSunat>("/api/proxy/catalogos/10", { method: "GET" })]).then(([nc, ndc]) => {
      if (!vigente) return;
      setCatalogos({ "07": nc.datos, "08": ndc.datos });
      if (!nc.datos || !ndc.datos) setError("No se pudieron cargar los motivos (catálogos 09/10). Reintente.");
    });
    return () => {
      vigente = false;
    };
  }, []);

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
      fecha_emision: new Date().toLocaleDateString("sv-SE", { timeZone: "America/Lima" }),
      documento_afectado: { serie: factura.serie, numero: factura.numero },
      motivo,
      descripcion: descripcion.trim(),
    };
    if (esParcial) body.items = itemsParciales;
    if (esCuotas) {
      body.forma_pago = { tipo: "credito", monto_pendiente: cuotas.reduce((acc, q) => acc + Number(q.monto || 0), 0), cuotas: cuotas.map((q) => ({ monto: Number(q.monto), vencimiento: q.vencimiento })) };
    }
    if (!esNc) body.items = [{ descripcion: nd.descripcion.trim(), unidad: "ZZ", cantidad: 1, precio_unitario: Number(nd.importe), tipo_afectacion_igv: "10" }];
    setEnviando(true);
    const res = await apiRequest<Comprobante>("/api/proxy/notas", { method: "POST", body });
    setEnviando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    router.push(`/comprobantes/${res.datos.id}`);
  }

  const motivos = catalogos[tipo]?.entradas ?? [];
  const listo = serie !== "" && motivo !== "" && descripcion.trim() !== "" && (!esParcial || itemsParciales.length > 0) && (esNc || (nd.descripcion.trim() !== "" && Number(nd.importe) > 0)) && (!esCuotas || cuotas.length > 0);

  return (
    <form onSubmit={onSubmit} className="space-y-5" data-testid="nota-form">
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
        <p className="rounded-lg border border-border bg-muted px-3 py-2 text-[12px] leading-relaxed text-muted-foreground" data-testid="nota-total">
          Nota <strong className="text-foreground">total</strong>: khipu copia los ítems, el descuento global y los cargos de la factura {factura.serie}-{factura.numero} ({formatearMonto(factura.moneda, factura.totales.total)}).
        </p>
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
        </div>
      ) : null}

      {esCuotas ? (
        <div className="space-y-2 rounded-lg border border-border p-3">
          <p className="text-[12px] text-muted-foreground">Cuotas corregidas de la factura (la nota sale con importe 0; SUNAT 3315). El neto pendiente será la suma de las cuotas.</p>
          {cuotas.map((q, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2">
              <span className="w-20 font-mono text-[11px] text-muted-foreground">Cuota{String(i + 1).padStart(3, "0")}</span>
              <input type="number" min={0.01} step="0.01" value={q.monto} aria-label={`Monto de la cuota ${i + 1}`} onChange={(e) => setCuotas((cs) => cs.map((c, j) => (j === i ? { ...c, monto: e.target.value } : c)))} className={cn(CAMPO, "h-8 w-32 font-mono")} />
              <input type="date" value={q.vencimiento} aria-label={`Vencimiento de la cuota ${i + 1}`} onChange={(e) => setCuotas((cs) => cs.map((c, j) => (j === i ? { ...c, vencimiento: e.target.value } : c)))} className={cn(CAMPO, "h-8 w-40 font-mono")} />
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
            <label htmlFor="nd-importe" className={ETIQUETA_CAMPO}>Importe con IGV ({factura.moneda})</label>
            <input id="nd-importe" type="number" min={0.01} step="0.01" value={nd.importe} onChange={(e) => setNd({ ...nd, importe: e.target.value })} className={cn(CAMPO, "font-mono")} />
          </div>
        </div>
      ) : null}

      {error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}

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
