import { ArrowLeftIcon, BanIcon, FileMinusIcon, FileTextIcon, IdCardIcon, LinkIcon, Rows3Icon } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { BotonCopiar } from "@/components/ui/boton-copiar";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { BajaButton } from "@/components/comprobantes/baja-button";
import { CorreoButton } from "@/components/comprobantes/correo-button";
import { ReenviarButton } from "@/components/comprobantes/reenviar-button";
import { VistaPrevia } from "@/components/comprobantes/vista-previa";
import { admiteBaja, admiteCorreo, admiteNotas, type Detraccion, ETIQUETAS_AFECTACION, ETIQUETAS_DOC_RELACIONADO, ETIQUETAS_GUIA, ETIQUETAS_TIPO, ETIQUETAS_TIPO_DOC, type Comprobante, type FormaPago, obtenerFactura, tieneConstanciaCdr } from "@/lib/api/facturas";
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
          {admiteNotas(c) ? (
            <Link href={`/comprobantes/${c.id}/nota`} className={cn(BOTON, "h-8")} data-testid="emitir-nota">
              <FileMinusIcon className="size-4" />
              Emitir nota
            </Link>
          ) : null}
          {admiteBaja(c) ? <BajaButton id={c.id} numero={numero} /> : null}
          <VistaPrevia id={c.id} numero={numero} nombreArchivo={c.nombre_archivo} tieneCdr={tieneConstanciaCdr(c)} />
          {c.enlaces?.pdf ? (
            <a
              href={`/api/proxy/facturas/${c.id}/pdf`}
              target="_blank"
              rel="noopener"
              title="Representación impresa con QR y hash"
              className="inline-flex h-8 items-center gap-1.5 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground shadow-2xs transition-colors hover:bg-primary/90"
              data-testid="ver-pdf"
            >
              <FileTextIcon className="size-4" />
              Ver PDF
            </a>
          ) : null}
          {admiteCorreo(c) ? <CorreoButton id={c.id} numero={numero} /> : null}
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
            {c.fecha_vencimiento ? <span className="block text-[11px] text-muted-foreground">Vence {formatearFecha(c.fecha_vencimiento)}</span> : null}
            {c.fecha_limite_envio && (c.estado_documento === "FIRMADO" || c.estado_documento === "ERROR_ENVIO") ? (
              <span className="block text-[11px] text-warning-foreground">Enviar a SUNAT hasta el {formatearFecha(c.fecha_limite_envio)}</span>
            ) : null}
            {c.fecha_limite_envio && c.estado_documento === "FUERA_DE_PLAZO" ? (
              <span className="block text-[11px] text-destructive">Plazo de envío vencido el {formatearFecha(c.fecha_limite_envio)}: emita un comprobante nuevo</span>
            ) : null}
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

      {c.baja ? (
        <section
          className={cn("rounded-xl border p-5 shadow-2xs", c.baja.estado === "ACEPTADA" ? "border-destructive/40 bg-destructive/5" : c.baja.estado === "RECHAZADA" ? "border-warning-border bg-warning/40" : "border-border bg-card")}
          data-testid="baja"
        >
          <div className={cn(TITULO_SECCION, "mb-3")}>
            <BanIcon className="size-4" />
            Comunicación de baja {c.baja.identificador}
          </div>
          <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-4">
            <Campo etiqueta="Estado">
              <span className="font-semibold text-foreground">
                {{ GENERADA: "Generada, pendiente de envío", ENVIADA: "Enviada: SUNAT la está procesando", ERROR_ENVIO: "Error de envío: se reintentará", ACEPTADA: "Aceptada: comprobante anulado", RECHAZADA: "Rechazada por SUNAT" }[c.baja.estado]}
              </span>
            </Campo>
            <Campo etiqueta="Motivo">
              <p className="leading-snug text-foreground/80">{c.baja.motivo}</p>
            </Campo>
            <Campo etiqueta="Ticket SUNAT">
              <span className="font-mono text-foreground/80">{c.baja.ticket ?? "—"}</span>
            </Campo>
            <Campo etiqueta={c.baja.cdr ? "CDR" : "Último intento"}>
              <span className="font-mono text-foreground/80">{c.baja.cdr ? `${c.baja.cdr.codigo} · ${c.baja.cdr.descripcion}` : (c.baja.ultimo_error ?? "—")}</span>
            </Campo>
          </div>
        </section>
      ) : null}

      {c.observaciones ? (
        <section className="rounded-xl border border-border bg-card p-5 shadow-2xs" data-testid="observaciones">
          <div className={cn(TITULO_SECCION, "mb-2")}>Observaciones (solo PDF)</div>
          <p className="text-[13px] leading-relaxed whitespace-pre-line text-foreground/90">{c.observaciones}</p>
        </section>
      ) : null}

      {c.nota ? (
        <section className="rounded-xl border border-accent-border bg-accent/40 p-5 shadow-2xs" data-testid="nota">
          <div className={cn(TITULO_SECCION, "mb-3")}>
            <FileMinusIcon className="size-4" />
            {c.tipo === "07" ? "Nota de crédito sobre" : "Nota de débito sobre"}
          </div>
          <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-3">
            <Campo etiqueta="Factura modificada">
              <span className="font-mono text-sm font-semibold text-foreground">{c.nota.documento_afectado}</span>
            </Campo>
            <Campo etiqueta="Motivo">
              <span className="text-foreground">
                <span className="mr-1.5 rounded bg-secondary px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">{c.nota.motivo}</span>
                {c.nota.motivo_descripcion}
              </span>
            </Campo>
            <Campo etiqueta="Sustento">
              <p className="leading-snug text-foreground/80">{c.nota.descripcion}</p>
            </Campo>
          </div>
        </section>
      ) : null}

      {c.notas?.length ? (
        <section className="rounded-xl border border-border bg-card p-5 shadow-2xs" data-testid="notas">
          <div className={cn(TITULO_SECCION, "mb-3")}>
            <FileMinusIcon className="size-4" />
            Notas emitidas sobre esta factura
          </div>
          <ul className="divide-y divide-border/60 text-xs">
            {c.notas.map((n) => (
              <li key={n.id} className="flex flex-wrap items-center gap-3 py-2">
                <Link href={`/comprobantes/${n.id}`} className="font-mono font-semibold text-primary hover:underline">
                  {n.comprobante}
                </Link>
                <span className="text-muted-foreground">{n.tipo === "07" ? "Nota de crédito" : "Nota de débito"}</span>
                <span className="text-muted-foreground">{formatearFecha(n.fecha_emision)}</span>
                <span className="text-foreground/80">
                  <span className="mr-1 font-mono text-[11px] text-muted-foreground">{n.motivo}</span>
                  {n.motivo_descripcion}
                </span>
                <span className="ml-auto font-mono tabular-nums text-foreground">{formatearMonto(c.moneda, n.total)}</span>
                <EstadoBadge estado={n.estado_documento} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {c.referencias ? (
        <section className="rounded-xl border border-border bg-card p-5 shadow-2xs" data-testid="referencias">
          <div className={cn(TITULO_SECCION, "mb-3")}>
            <LinkIcon className="size-4" />
            Documentos relacionados
          </div>
          <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-3">
            <Campo etiqueta="Orden de compra">
              {c.referencias.orden_compra ? <span className="font-mono font-medium text-foreground">{c.referencias.orden_compra}</span> : <span className="text-muted-foreground/60">—</span>}
            </Campo>
            <Campo etiqueta="Guías de remisión">
              {c.referencias.guias?.length ? (
                <ul className="space-y-0.5">
                  {c.referencias.guias.map((g) => (
                    <li key={`${g.tipo}-${g.numero}`} className="font-mono text-foreground">
                      {g.numero} <span className="text-[10px] text-muted-foreground">{g.tipo} · {ETIQUETAS_GUIA[g.tipo] ?? "Guía"}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <span className="text-muted-foreground/60">—</span>
              )}
            </Campo>
            <Campo etiqueta="Otros documentos">
              {c.referencias.documentos_relacionados?.length ? (
                <ul className="space-y-0.5">
                  {c.referencias.documentos_relacionados.map((d) => (
                    <li key={`${d.tipo}-${d.numero}`} className="font-mono text-foreground">
                      {d.numero} <span className="text-[10px] text-muted-foreground">{d.tipo} · {ETIQUETAS_DOC_RELACIONADO[d.tipo] ?? "Documento"}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <span className="text-muted-foreground/60">—</span>
              )}
            </Campo>
          </div>
        </section>
      ) : null}

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
                <th className="w-28 px-4 py-2.5 text-right font-medium">Cargos</th>
                <th className="w-28 px-4 py-2.5 text-right font-medium">Subtotal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {c.items.map((item, i) => (
                <tr key={i} className="transition-colors hover:bg-muted/60">
                  <td className="px-4 py-3 text-center font-mono text-muted-foreground/70">{i + 1}</td>
                  <td className="px-3 py-3 font-mono font-medium text-muted-foreground">{item.codigo ?? "—"}</td>
                  <td className="px-4 py-3 font-medium text-foreground">
                    {item.descripcion}
                    {item.codigo_sunat || item.gtin ? (
                      <span className="mt-0.5 block font-mono text-[10px] font-normal text-muted-foreground">
                        {item.codigo_sunat ? `SUNAT ${item.codigo_sunat}` : null}
                        {item.codigo_sunat && item.gtin ? " · " : null}
                        {item.gtin ? `${item.gtin.tipo} ${item.gtin.codigo}` : null}
                      </span>
                    ) : null}
                  </td>
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
                  <td className="px-4 py-3 text-right font-mono text-foreground/80 tabular-nums">
                    {item.cargos?.length ? (
                      <span title={item.cargos.map((cg) => `Código SUNAT ${cg.codigo} · ${cg.afecta_base_igv ? "afecta la base del IGV" : "no afecta la base del IGV"}`).join(" / ")}>
                        +{formatearNumero(item.cargos.reduce((acc, cg) => acc + Number(cg.monto), 0))}
                        <span className="ml-1 text-[10px] text-muted-foreground">{item.cargos.map((cg) => cg.codigo).join("+")}</span>
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
            {c.totales.cargos?.filter((cg) => cg.afecta_base_igv).map((cg, i) => (
              <Importe key={`cg-base-${i}`} etiqueta={`Cargo global (${cg.codigo}, afecta la base)`} moneda={c.moneda} valor={cg.monto} />
            ))}
            {c.totales.isc ? <Importe etiqueta="Total ISC" moneda={c.moneda} valor={c.totales.isc} /> : null}
            {c.totales.icbper ? <Importe etiqueta="Total ICBPER (bolsas)" moneda={c.moneda} valor={c.totales.icbper} /> : null}
            <Importe etiqueta={c.totales.tasa_igv != null ? `Total IGV (${formatearNumero(c.totales.tasa_igv)} %)` : "Total IGV"} moneda={c.moneda} valor={c.totales.igv} />
            {c.totales.gratuito ? (
              <>
                <Importe etiqueta="Operaciones gratuitas (no se cobran)" moneda={c.moneda} valor={c.totales.gratuito} />
                <Importe etiqueta="IGV de gratuitas (informativo)" moneda={c.moneda} valor={c.totales.igv_gratuitas ?? 0} />
              </>
            ) : null}
            {c.totales.total_descuentos || c.totales.total_cargos || c.totales.total_anticipos ? (
              <Importe etiqueta="Precio de venta" moneda={c.moneda} valor={c.totales.total_precio_venta ?? c.totales.total} />
            ) : null}
            {c.totales.total_cargos ? (
              <Importe
                etiqueta={`Otros cargos sin IGV${c.totales.cargos?.some((cg) => !cg.afecta_base_igv) ? ` (${c.totales.cargos.filter((cg) => !cg.afecta_base_igv).map((cg) => cg.codigo).join(", ")})` : ""}`}
                moneda={c.moneda}
                valor={c.totales.total_cargos}
              />
            ) : null}
            {c.totales.total_descuentos ? (
              <Importe etiqueta="Descuentos que no afectan el IGV" moneda={c.moneda} valor={-c.totales.total_descuentos} />
            ) : null}
            {c.totales.total_anticipos ? (
              <Importe etiqueta="Anticipos ya pagados (con IGV)" moneda={c.moneda} valor={-c.totales.total_anticipos} />
            ) : null}
            {c.totales.redondeo ? <Importe etiqueta="Redondeo" moneda={c.moneda} valor={c.totales.redondeo} /> : null}
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
          {c.anticipos?.length ? (
            <div className="mt-4 border-t border-border/60 pt-3 text-xs" data-testid="anticipos">
              <span className={ETIQUETA}>Anticipos regularizados</span>
              <ul className="mt-1 space-y-1">
                {c.anticipos.map((a) => (
                  <li key={a.comprobante} className="flex items-baseline justify-between gap-3">
                    <span className="text-muted-foreground">
                      <span className="font-mono text-foreground">{a.comprobante}</span> · {a.afectacion} ({a.codigo_sunat})
                      {a.fecha_pago ? ` · pagado el ${a.fecha_pago}` : ""}
                    </span>
                    <span className="font-mono tabular-nums text-foreground/80">
                      −{formatearMonto(c.moneda, a.importe_pagado)}
                      <span className="text-[11px] text-muted-foreground/80"> ({formatearMonto(c.moneda, a.monto)} sin IGV)</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
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
