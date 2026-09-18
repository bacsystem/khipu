import { ArrowLeftIcon, FileTextIcon, IdCardIcon, MoreHorizontalIcon, Rows3Icon } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { BotonCopiar } from "@/components/ui/boton-copiar";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { ReenviarButton } from "@/components/comprobantes/reenviar-button";
import { VistaPrevia } from "@/components/comprobantes/vista-previa";
import { type Detraccion, ETIQUETAS_AFECTACION, ETIQUETAS_TIPO, ETIQUETAS_TIPO_DOC, type Comprobante, type FormaPago, obtenerFactura, tieneConstanciaCdr } from "@/lib/api/facturas";
import { ApiError } from "@/lib/api/types";
import { formatearFecha, formatearMonto, formatearNumero } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

const TIPOS_OPERACION: Record<string, string> = {
  "0101": "Venta interna",
  "0200": "Exportación de bienes",
  "0201": "Exportación de servicios",
};

const MONEDAS: Record<string, string> = {
  PEN: "PEN (Soles · S/)",
  USD: "USD (Dólares · $)",
};

const ESTADOS_CON_RESPUESTA: Record<string, "exito" | "error" | "aviso"> = {
  ACEPTADO: "exito",
  ACEPTADO_CON_OBS: "aviso",
  RECHAZADO: "error",
  INVALIDO: "error",
  ERROR_ENVIO: "error",
};

const BOTON =
  "inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-card px-3 text-xs font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground";
const ETIQUETA = "block text-[11px] font-medium tracking-wider text-muted-foreground/80 uppercase";
const TITULO_SECCION = "flex items-center gap-1.5 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";

/** Forma de pago (RS 193-2020): contado, o crédito con el neto pendiente y el calendario de cuotas. */
function FormaPagoDetalle({ formaPago, moneda }: { formaPago: FormaPago; moneda: string }) {
  const credito = formaPago.tipo === "credito";
  return (
    <div className="mt-4 border-t border-border/60 pt-3 text-xs" data-testid="forma-pago">
      <div className="flex items-center justify-between">
        <span className={ETIQUETA}>Forma de pago</span>
        <span className={cn("rounded border px-2 py-0.5 text-[11px] font-medium", credito ? "border-warning-border bg-warning text-warning-foreground" : "border-border bg-secondary text-secondary-foreground")}>
          {credito ? "Crédito" : "Contado"}
        </span>
      </div>
      {credito ? (
        <div className="mt-2 space-y-1.5">
          <div className="flex items-baseline justify-between">
            <span className="text-muted-foreground">Neto pendiente de pago</span>
            <span className="font-mono font-semibold text-foreground tabular-nums">{formatearMonto(moneda, formaPago.monto_pendiente ?? 0)}</span>
          </div>
          {formaPago.cuotas.map((q) => (
            <div key={q.id} className="flex items-baseline justify-between text-muted-foreground">
              <span>
                <span className="font-mono text-foreground/80">{q.id}</span> · vence {formatearFecha(q.vencimiento)}
              </span>
              <span className="font-mono tabular-nums">{formatearMonto(moneda, q.monto)}</span>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** Detracción (SPOT): lo que el adquirente deposita en la cuenta del Banco de la Nación del emisor, siempre en soles. */
function DetraccionDetalle({ detraccion }: { detraccion: Detraccion }) {
  return (
    <div className="mt-4 border-t border-border/60 pt-3 text-xs" data-testid="detraccion">
      <div className="flex items-center justify-between">
        <span className={ETIQUETA}>Detracción (SPOT)</span>
        <span className="rounded border border-warning-border bg-warning px-2 py-0.5 text-[11px] font-medium text-warning-foreground">{detraccion.porcentaje}%</span>
      </div>
      <div className="mt-2 space-y-1.5 text-muted-foreground">
        <div className="flex items-baseline justify-between gap-3">
          <span className="min-w-0 truncate">
            <span className="font-mono text-foreground/80">{detraccion.codigo_bien_servicio}</span> · {detraccion.descripcion}
          </span>
          <span className="shrink-0 font-mono font-semibold text-foreground tabular-nums">{formatearMonto("PEN", detraccion.monto)}</span>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <span>Cuenta Banco de la Nación</span>
          <span className="font-mono text-foreground/80">{detraccion.cuenta_banco_nacion}</span>
        </div>
        <p className="text-[11px] text-muted-foreground/80">El adquirente deposita este monto en soles y paga el resto al emisor (medio de pago {detraccion.medio_pago}).</p>
      </div>
    </div>
  );
}

function Campo({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <span className={cn(ETIQUETA, "mb-1")}>{etiqueta}</span>
      {children}
    </div>
  );
}

function Tecnico({ nombre, children }: { nombre: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded bg-muted px-2.5 py-1.5">
      <span className="text-muted-foreground">{nombre}:</span>
      <span className="min-w-0 truncate text-right font-medium text-foreground/90">{children}</span>
    </div>
  );
}

function Importe({ etiqueta, moneda, valor, destacado = false }: { etiqueta: string; moneda: string; valor: number; destacado?: boolean }) {
  return (
    <div className="flex justify-between py-1 text-muted-foreground">
      <span>{etiqueta}</span>
      <span className={cn("font-mono tabular-nums", destacado || Number(valor) !== 0 ? "font-medium text-foreground" : "text-muted-foreground/70")}>
        {formatearMonto(moneda, valor)}
      </span>
    </div>
  );
}

function CajaRespuesta({ comprobante }: { comprobante: Comprobante }) {
  const tono = ESTADOS_CON_RESPUESTA[comprobante.estado_documento];
  const texto = comprobante.cdr?.descripcion ?? comprobante.ultimo_error;
  if (!texto) {
    return (
      <div className="rounded-lg border border-border bg-muted p-3">
        <span className={cn(ETIQUETA, "mb-1")}>Respuesta SUNAT</span>
        <p className="font-mono text-xs text-muted-foreground">Todavía sin respuesta de SUNAT.</p>
      </div>
    );
  }
  return (
    <div
      className={cn(
        "rounded-lg border p-3",
        tono === "exito" && "border-success-border bg-success",
        tono === "aviso" && "border-warning-border bg-warning",
        tono === "error" && "border-destructive-border bg-destructive/10",
        !tono && "border-border bg-muted",
      )}
    >
      <span
        className={cn(
          ETIQUETA,
          "mb-1 font-semibold",
          tono === "exito" && "text-success-foreground",
          tono === "aviso" && "text-warning-foreground",
          tono === "error" && "text-destructive",
        )}
      >
        {comprobante.cdr ? "Respuesta CDR SUNAT" : `Último intento (${comprobante.intentos})`}
      </span>
      <p className={cn("font-mono text-xs font-medium", tono === "error" ? "text-destructive" : "text-foreground/90")}>&ldquo;{texto}&rdquo;</p>
    </div>
  );
}

export default async function ComprobanteDetallePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");
  if (!empresaId) redirect("/onboarding");

  // Solo un 404 de la API es "no existe"; cualquier otro fallo sube al error boundary.
  const c = await obtenerFactura(access, empresaId, id).catch((e: unknown) => {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  });
  if (!c) notFound();

  const numero = `${c.serie}-${String(c.numero).padStart(8, "0")}`;
  const tipoLabel = ETIQUETAS_TIPO[c.tipo] ?? c.tipo;
  const tipoOperacion = c.tipo_operacion ? `${c.tipo_operacion} · ${TIPOS_OPERACION[c.tipo_operacion] ?? "Otra operación"}` : null;
  const tipoDoc = c.receptor ? (ETIQUETAS_TIPO_DOC[c.receptor.tipo_doc] ?? `Tipo ${c.receptor.tipo_doc}`) : null;
  const hashCorto = c.hash && c.hash.length > 16 ? `${c.hash.slice(0, 8)}…${c.hash.slice(-5)}` : c.hash;

  return (
    <div className="mx-auto grid w-full max-w-6xl min-w-0 grid-cols-1 gap-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex min-w-0 flex-wrap items-center gap-3 text-sm">
          <Link href="/comprobantes" className={cn(BOTON, "h-auto px-2.5 py-1.5")}>
            <ArrowLeftIcon className="size-3.5" />
            Volver a comprobantes
          </Link>
          <span className="text-muted-foreground/40">/</span>
          <span className="text-xs text-muted-foreground">Comprobantes</span>
          <span className="text-muted-foreground/40">/</span>
          <span className="rounded border border-border bg-secondary px-2 py-0.5 font-mono text-xs font-semibold text-foreground">{numero}</span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {c.estado_documento === "ERROR_ENVIO" ? <ReenviarButton id={c.id} /> : null}
          <VistaPrevia id={c.id} numero={numero} nombreArchivo={c.nombre_archivo} tieneCdr={tieneConstanciaCdr(c)} />
          <button
            disabled
            title="Representación impresa (PDF): próximamente"
            className="inline-flex h-8 cursor-not-allowed items-center gap-1.5 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground opacity-60 shadow-2xs"
          >
            <FileTextIcon className="size-4" />
            Ver PDF
          </button>
          <button
            disabled
            title="Más acciones (reenviar por correo, comunicación de baja): próximamente"
            className="flex size-8 cursor-not-allowed items-center justify-center rounded-md border border-border bg-card text-muted-foreground/60 shadow-2xs"
          >
            <MoreHorizontalIcon className="size-4" />
          </button>
        </div>
      </div>

      <section className="rounded-xl border border-border bg-card p-6 shadow-2xs">
        <div className="flex flex-col justify-between gap-4 border-b border-border/60 pb-5 md:flex-row md:items-center">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2.5">
              <h2 className="font-heading text-xl font-bold tracking-tight text-foreground">{tipoLabel} electrónica</h2>
              <span className="rounded-md border border-accent-border bg-accent px-2.5 py-0.5 font-mono text-sm font-semibold text-accent-foreground">
                {numero}
              </span>
            </div>
            <p className="mt-1 truncate font-mono text-xs text-muted-foreground">
              documento_id: <span className="text-foreground/80">{c.id}</span>
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <EstadoBadge estado={c.estado_documento} />
            {c.cdr ? (
              <span
                title={tieneConstanciaCdr(c) ? "Código de la constancia de recepción (CDR)" : "Código de respuesta de SUNAT; no se emitió constancia (CDR)"}
                className="rounded border border-border bg-secondary px-2 py-0.5 font-mono text-[11px] font-medium text-muted-foreground"
              >
                {tieneConstanciaCdr(c) ? "CDR" : "Código SUNAT"}: {c.cdr.codigo}
              </span>
            ) : null}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6 pt-5 sm:grid-cols-4">
          <Campo etiqueta="Fecha de emisión">
            <span className="font-mono text-xs font-medium text-foreground">{formatearFecha(c.fecha_emision)}</span>
          </Campo>
          <Campo etiqueta="Moneda">
            <span className="text-xs font-semibold text-foreground">{MONEDAS[c.moneda] ?? c.moneda}</span>
          </Campo>
          <Campo etiqueta="Tipo de operación">
            {tipoOperacion ? (
              <span className="font-mono text-xs font-medium text-foreground">{tipoOperacion}</span>
            ) : (
              <span className="text-xs text-muted-foreground/60">—</span>
            )}
          </Campo>
          <Campo etiqueta="Firma digital (hash)">
            {c.hash ? (
              <div className="flex items-center gap-1">
                <span className="truncate font-mono text-xs text-foreground/80" title={c.hash}>
                  {hashCorto}
                </span>
                <BotonCopiar texto={c.hash} titulo="Copiar hash de la firma" />
              </div>
            ) : (
              <span className="text-xs text-muted-foreground/60">Sin firmar</span>
            )}
          </Campo>
        </div>
      </section>

      <section className="rounded-xl border border-border bg-card p-5 shadow-2xs">
        <div className={cn(TITULO_SECCION, "mb-3")}>
          <IdCardIcon className="size-4" />
          Datos del receptor
        </div>
        {c.receptor ? (
          <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-3">
            <Campo etiqueta="Razón social">
              <p className="text-sm font-semibold text-foreground">{c.receptor.razon_social}</p>
            </Campo>
            <Campo etiqueta="Documento de identidad">
              <div className="flex items-center gap-1.5 font-mono">
                <span className="rounded bg-secondary px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">
                  Tipo {c.receptor.tipo_doc} · {tipoDoc}
                </span>
                <span className="font-semibold text-foreground">{c.receptor.num_doc}</span>
              </div>
            </Campo>
            <Campo etiqueta="Dirección declarada">
              <p className="leading-snug text-foreground/80">{c.receptor.direccion ?? "—"}</p>
            </Campo>
          </div>
        ) : (
          <p className="text-xs text-muted-foreground/60">Sin datos de receptor.</p>
        )}
      </section>

      <section className="overflow-hidden rounded-xl border border-border bg-card shadow-2xs">
        <div className="flex items-center justify-between border-b border-border/60 px-5 py-3">
          <div className={TITULO_SECCION}>
            <Rows3Icon className="size-4" />
            Detalle de bienes y servicios
          </div>
          <span className="font-mono text-xs text-muted-foreground">
            {c.items.length} {c.items.length === 1 ? "ítem" : "ítems"}
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left text-xs">
            <thead>
              <tr className="border-b border-border/70 bg-muted font-mono text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
                <th className="w-12 px-4 py-2.5 text-center font-medium">#</th>
                <th className="w-28 px-3 py-2.5 font-medium">Código</th>
                <th className="px-4 py-2.5 font-medium">Descripción</th>
                <th className="w-20 px-3 py-2.5 text-right font-medium">Cant.</th>
                <th className="w-24 px-3 py-2.5 text-center font-medium">Unidad</th>
                <th className="w-48 px-4 py-2.5 text-center font-medium">Afectación IGV</th>
                <th className="w-28 px-4 py-2.5 text-right font-medium">P. unitario</th>
                <th className="w-28 px-4 py-2.5 text-right font-medium">Descuento</th>
                <th className="w-28 px-4 py-2.5 text-right font-medium">Subtotal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {c.items.map((item, i) => (
                <tr key={i} className="transition-colors hover:bg-muted/60">
                  <td className="px-4 py-3 text-center font-mono text-muted-foreground/70">{i + 1}</td>
                  <td className="px-3 py-3 font-mono font-medium text-muted-foreground">{item.codigo ?? "—"}</td>
                  <td className="px-4 py-3 font-medium text-foreground">{item.descripcion}</td>
                  <td className="px-3 py-3 text-right font-mono text-foreground/90 tabular-nums">{formatearNumero(item.cantidad)}</td>
                  <td className="px-3 py-3 text-center font-mono">
                    <span className="rounded bg-secondary px-1.5 py-0.5 text-[11px] text-muted-foreground">{item.unidad}</span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className="inline-flex rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium text-foreground/80">
                      {item.tipo_afectacion_igv} · {ETIQUETAS_AFECTACION[item.tipo_afectacion_igv] ?? "Otra afectación"}
                    </span>
                    {item.isc ? (
                      <span className="ml-1 inline-flex rounded-full bg-warning px-2 py-0.5 text-[11px] font-medium text-warning-foreground" title={`ISC sistema ${item.isc.sistema}: ${formatearNumero(item.isc.monto)}`}>
                        ISC {item.isc.tasa}%
                      </span>
                    ) : null}
                    {item.icbper ? (
                      <span className="ml-1 inline-flex rounded-full bg-warning px-2 py-0.5 text-[11px] font-medium text-warning-foreground" title="Impuesto a las bolsas de plástico">
                        ICBPER {formatearNumero(item.icbper)}
                      </span>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-right font-mono text-foreground/80 tabular-nums">{formatearNumero(item.precio_unitario)}</td>
                  <td className="px-4 py-3 text-right font-mono text-foreground/80 tabular-nums">
                    {item.descuento ? (
                      <span title={`Código SUNAT ${item.descuento.codigo} · ${item.descuento.afecta_base_igv ? "afecta la base del IGV" : "no afecta la base del IGV"}`}>
                        −{formatearNumero(item.descuento.monto)}
                        <span className="ml-1 text-[10px] text-muted-foreground">
                          {item.descuento.tipo === "PORCENTAJE" ? `${item.descuento.valor}%` : item.descuento.codigo}
                        </span>
                      </span>
                    ) : (
                      <span className="text-muted-foreground/50">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right font-mono font-medium text-foreground tabular-nums">
                    {item.gratuita ? (
                      <span className="rounded bg-secondary px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground" title="Operación gratuita: no se cobra">
                        Gratuita
                      </span>
                    ) : (
                      formatearNumero(item.precio_venta ?? (item.valor_venta != null && item.igv != null ? Number(item.valor_venta) + Number(item.igv) : Number(item.cantidad) * Number(item.precio_unitario)))
                    )}
                  </td>
                </tr>
              ))}
              {c.items.length === 0 ? (
                <tr>
                  <td colSpan={9} className="px-4 py-8 text-center text-muted-foreground">
                    Este comprobante no tiene ítems.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <section className="space-y-4 rounded-xl border border-border bg-card p-5 shadow-2xs lg:col-span-7">
          <div className="flex items-center justify-between border-b border-border/60 pb-2">
            <span className={TITULO_SECCION}>Información técnica SUNAT</span>
            <span className="font-mono text-[11px] text-muted-foreground/70">Tabla: documento</span>
          </div>
          <CajaRespuesta comprobante={c} />
          <div className="space-y-2 font-mono text-xs">
            <Tecnico nombre="nombre_archivo">
              <span className="select-all">{c.nombre_archivo ?? "—"}</span>
            </Tecnico>
            <Tecnico nombre="cdr_observaciones">
              {c.cdr && c.cdr.observaciones.length > 0 ? (
                c.cdr.observaciones.join(" · ")
              ) : (
                <span className="rounded border border-border bg-card px-2 py-0.5 text-[11px] font-normal text-muted-foreground">Sin observaciones</span>
              )}
            </Tecnico>
            <Tecnico nombre="intentos">
              <span className="font-semibold">{c.intentos}</span>
            </Tecnico>
            <div
              title="Clave de idempotencia: no expuesta todavía por la API"
              className="flex cursor-not-allowed items-center justify-between gap-3 rounded bg-muted px-2.5 py-1.5 opacity-60"
            >
              <span className="text-muted-foreground">idempotency_key:</span>
              <span className="text-[11px] text-muted-foreground">—</span>
            </div>
          </div>
        </section>

        <section className="space-y-3 rounded-xl border border-border bg-card p-5 shadow-2xs lg:col-span-5">
          <div className="flex items-center justify-between border-b border-border/60 pb-2">
            <span className={TITULO_SECCION}>Liquidación de importes</span>
            <span className="font-mono text-[11px] text-muted-foreground/70">{MONEDAS[c.moneda] ?? c.moneda}</span>
          </div>
          <div className="space-y-2 text-xs">
            <Importe etiqueta="Total gravado" moneda={c.moneda} valor={c.totales.gravado} />
            <Importe etiqueta="Total exonerado" moneda={c.moneda} valor={c.totales.exonerado} />
            <Importe etiqueta="Total inafecto" moneda={c.moneda} valor={c.totales.inafecto} />
            {c.totales.descuento_global ? (
              <Importe
                etiqueta={`Descuento global (${c.totales.descuento_global.codigo}${c.totales.descuento_global.afecta_base_igv ? ", afecta la base" : ""})`}
                moneda={c.moneda}
                valor={-c.totales.descuento_global.monto}
              />
            ) : null}
            {c.totales.isc ? <Importe etiqueta="Total ISC" moneda={c.moneda} valor={c.totales.isc} /> : null}
            {c.totales.icbper ? <Importe etiqueta="Total ICBPER (bolsas)" moneda={c.moneda} valor={c.totales.icbper} /> : null}
            <Importe etiqueta="Total IGV" moneda={c.moneda} valor={c.totales.igv} />
            {c.totales.gratuito ? (
              <>
                <Importe etiqueta="Operaciones gratuitas (no se cobran)" moneda={c.moneda} valor={c.totales.gratuito} />
                <Importe etiqueta="IGV de gratuitas (informativo)" moneda={c.moneda} valor={c.totales.igv_gratuitas ?? 0} />
              </>
            ) : null}
            {c.totales.total_descuentos ? (
              <>
                <Importe etiqueta="Precio de venta" moneda={c.moneda} valor={c.totales.total_precio_venta ?? c.totales.total} />
                <Importe etiqueta="Descuentos que no afectan el IGV" moneda={c.moneda} valor={-c.totales.total_descuentos} />
              </>
            ) : null}
            <div className="my-2 h-px bg-border" />
            <div className="flex items-baseline justify-between pt-1">
              <div>
                <span className="block text-xs font-semibold tracking-wide text-foreground uppercase">Total a pagar</span>
                <span className="text-[11px] text-muted-foreground/80">Importe neto final</span>
              </div>
              <span className="font-mono text-xl font-bold tracking-tight text-foreground tabular-nums">{formatearMonto(c.moneda, c.totales.total)}</span>
            </div>
          </div>
          <FormaPagoDetalle formaPago={c.forma_pago} moneda={c.moneda} />
          {c.detraccion ? <DetraccionDetalle detraccion={c.detraccion} /> : null}
          {c.retencion_igv ? (
            <div className="mt-4 border-t border-border/60 pt-3 text-xs" data-testid="retencion">
              <div className="flex items-center justify-between">
                <span className={ETIQUETA}>Retención del IGV ({c.retencion_igv.porcentaje}%)</span>
                <span className="font-mono tabular-nums text-foreground/80">−{formatearMonto(c.moneda, c.retencion_igv.monto)}</span>
              </div>
              <div className="mt-1 flex items-baseline justify-between text-muted-foreground">
                <span>Neto a cobrar (el cliente entera la retención a SUNAT)</span>
                <span className="font-mono font-semibold text-foreground tabular-nums">{formatearMonto(c.moneda, c.retencion_igv.neto_cobrar)}</span>
              </div>
            </div>
          ) : null}
          {c.percepcion ? (
            <div className="mt-4 border-t border-border/60 pt-3 text-xs" data-testid="percepcion">
              <div className="flex items-center justify-between">
                <span className={ETIQUETA}>Percepción {c.percepcion.regimen} ({c.percepcion.porcentaje}%)</span>
                <span className="font-mono tabular-nums text-foreground/80">+{formatearMonto("PEN", c.percepcion.monto)}</span>
              </div>
              <div className="mt-1 flex items-baseline justify-between text-muted-foreground">
                <span>Total con percepción (lo que paga el cliente)</span>
                <span className="font-mono font-semibold text-foreground tabular-nums">{formatearMonto("PEN", c.percepcion.total_con_percepcion)}</span>
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground/80">{c.percepcion.descripcion}</p>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}
