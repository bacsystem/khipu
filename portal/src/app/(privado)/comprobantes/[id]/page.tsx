import { ChatTextIcon, ClockCounterClockwiseIcon, CodeIcon, FileMinusIcon, FileTextIcon, IdentificationCardIcon, LinkIcon, ReceiptIcon, RowsIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { EnlaceVolver } from "khipu-design-system/patrones/cabecera-pagina";
import { Chip, ChipCodigo, PillEstado } from "khipu-design-system/patrones/pill-estado";
import { TarjetaDestacada } from "khipu-design-system/patrones/tarjeta-destacada";
import { CONTENEDOR_TABLA, FilaVacia } from "khipu-design-system/patrones/pie-tabla";
import { ListaDatos, type DatoLista } from "khipu-design-system/datos/lista-datos";
import { Timeline } from "khipu-design-system/datos/timeline";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { EstadoBadge, etiquetasEstado, TONOS_ESTADO } from "@/components/comprobantes/estado-badge";
import { BajaButton } from "@/components/comprobantes/baja-button";
import { BajaEstado } from "@/components/comprobantes/baja-estado";
import { CorreoButton } from "@/components/comprobantes/correo-button";
import { EnvioAlClienteEstado } from "@/components/comprobantes/envio-al-cliente-estado";
import { AvisoBoletaSinInformar } from "@/components/comprobantes/aviso-boleta-sin-informar";
import { AvisosDeEmision } from "@/components/comprobantes/avisos-de-emision";
import { DocumentosDeAgente, ImportesDeAgente } from "@/components/comprobantes/agente-detalle";
import { DuplicarButton } from "@/components/comprobantes/duplicar-button";
import { RecuperarCdrButton } from "@/components/comprobantes/recuperar-cdr-button";
import { datosDeValidez, puedeRecuperarCdr } from "@/lib/comprobantes/consultas-sunat";
import { ReenviarButton } from "@/components/comprobantes/reenviar-button";
import { ValidezButton } from "@/components/comprobantes/validez-button";
import { VistaPrevia } from "@/components/comprobantes/vista-previa";
import { admiteBaja, admiteCorreo, admiteNotas, esDeAgente, esDeBoleta, type Detraccion, type Exportacion, etiquetasAfectacion, etiquetasDocRelacionado, etiquetasGuia, etiquetasTipo, etiquetasTipoDoc, type Comprobante, type FormaPago, obtenerFactura, seInformaFueraDePlazo, tieneConstanciaCdr } from "@/lib/api/facturas";
import { sePuedeDuplicar } from "@/lib/comprobantes/duplicar";
import { ApiError } from "@/lib/api/types";
import { avisoDePlazo } from "@/lib/comprobantes/plazo";
import { formatearFecha, formatearFechaHora, formatearMonto, formatearNumero, hoyLima } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA, CABECERA_TABLA, CONTENEDOR_PAGINA, ETIQUETA_DATO, TARJETA } from "@/lib/estilos";
import { TITULO_REGISTRO } from "@/lib/detalle";
import type { Metadata } from "next";
import { getLocale, getMessages } from "next-intl/server";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { Locale } from "@/i18n/config";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.comprobantes.detalle.titulo} · ${messages.app.nombre}` };
}

/** Los textos de esta página (#25): los subcomponentes los reciben de la página, que los lee con el locale de la sesión. */
type T = Mensajes["comprobantes"]["detalle"];
const catalogo = (c: object): Record<string, string> => c as Record<string, string>;

/** Los nombres oficiales de los Incoterms (ICC): se escriben en inglés en cualquier idioma, así que no van a los textos traducibles. */
const INCOTERMS: Record<string, string> = {
  /* eslint-disable khipu/no-literal-de-ui -- nombres oficiales de la ICC, iguales en todo idioma */
  EXW: "Ex Works", FCA: "Free Carrier", FAS: "Free Alongside Ship", FOB: "Free On Board", CFR: "Cost and Freight", CIF: "Cost, Insurance and Freight",
  CPT: "Carriage Paid To", CIP: "Carriage and Insurance Paid To", DAP: "Delivered At Place", DPU: "Delivered at Place Unloaded", DDP: "Delivered Duty Paid",
  /* eslint-enable khipu/no-literal-de-ui */
};

/** El tono de la `Alerta` con la respuesta de SUNAT; un estado sin respuesta propia va en `info`. */
const ESTADOS_CON_RESPUESTA: Record<string, "ok" | "error" | "aviso"> = {
  ACEPTADO: "ok",
  ACEPTADO_CON_OBS: "aviso",
  RECHAZADO: "error",
  INVALIDO: "error",
  ERROR_ENVIO: "error",
};


/** Forma de pago (RS 193-2020): contado, o crédito con el neto pendiente y el calendario de cuotas. */
function FormaPagoDetalle({ formaPago, moneda, t, locale }: { formaPago: FormaPago; moneda: string; t: T; locale: Locale }) {
  const credito = formaPago.tipo === "credito";
  return (
    <div className="mt-4 border-t border-border/60 pt-3 text-etiqueta" data-testid="forma-pago">
      <div className="flex items-center justify-between">
        <span className={ETIQUETA_DATO}>{t.formaPago.titulo}</span>
        <PillEstado tono={credito ? "aviso" : "neutro"}>{credito ? t.formaPago.credito : t.formaPago.contado}</PillEstado>
      </div>
      {credito ? (
        <div className="mt-2 space-y-1.5">
          <div className="flex items-baseline justify-between">
            <span className="text-muted-foreground">{t.formaPago.netoPendiente}</span>
            <span className="font-mono font-semibold text-foreground tabular-nums">{formatearMonto(moneda, formaPago.monto_pendiente ?? 0, locale)}</span>
          </div>
          {formaPago.cuotas.map((q) => (
            <div key={q.id} className="flex items-baseline justify-between text-muted-foreground">
              <span>
                <span className="font-mono text-foreground/80">{q.id}</span>
                {interpolar(t.formaPago.cuotaVence, { fecha: formatearFecha(q.vencimiento, locale) })}
              </span>
              <span className="font-mono tabular-nums">{formatearMonto(moneda, q.monto, locale)}</span>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** Exportación (0200–0208): Incoterm de la venta y, en servicios, el país donde se usa. */
function ExportacionDetalle({ exportacion, t }: { exportacion: Exportacion; t: T }) {
  return (
    <div className="mt-4 border-t border-border/60 pt-3 text-etiqueta" data-testid="exportacion">
      <span className={ETIQUETA_DATO}>{t.exportacion.titulo}</span>
      <div className="mt-2 space-y-1.5 text-muted-foreground">
        <div className="flex items-baseline justify-between gap-3">
          <span>{t.exportacion.incoterm}</span>
          <span className="font-mono text-foreground/80">{exportacion.incoterm ? `${exportacion.incoterm} · ${INCOTERMS[exportacion.incoterm] ?? ""}`.trim() : "—"}</span>
        </div>
        {exportacion.pais_uso ? (
          <div className="flex items-baseline justify-between gap-3">
            <span>{t.exportacion.paisDeUso}</span>
            <span className="font-mono text-foreground/80">{exportacion.pais_uso}</span>
          </div>
        ) : null}
        <p className="text-etiqueta text-muted-foreground/80">{t.exportacion.sinIgv}</p>
      </div>
    </div>
  );
}

/** Detracción (SPOT): lo que el adquirente deposita en la cuenta del Banco de la Nación del emisor, siempre en soles. */
function DetraccionDetalle({ detraccion, t, locale }: { detraccion: Detraccion; t: T; locale: Locale }) {
  return (
    <div className="mt-4 border-t border-border/60 pt-3 text-etiqueta" data-testid="detraccion">
      <div className="flex items-center justify-between">
        <span className={ETIQUETA_DATO}>{t.detraccion.titulo}</span>
        <PillEstado tono="aviso">{detraccion.porcentaje}%</PillEstado>
      </div>
      <div className="mt-2 space-y-1.5 text-muted-foreground">
        <div className="flex items-baseline justify-between gap-3">
          <span className="min-w-0 truncate">
            <span className="font-mono text-foreground/80">{detraccion.codigo_bien_servicio}</span> · {detraccion.descripcion}
          </span>
          <span className="shrink-0 font-mono font-semibold text-foreground tabular-nums">{formatearMonto("PEN", detraccion.monto, locale)}</span>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <span>{t.detraccion.cuenta}</span>
          <span className="font-mono text-foreground/80">{detraccion.cuenta_banco_nacion}</span>
        </div>
        <p className="text-etiqueta text-muted-foreground/80">{interpolar(t.detraccion.deposito, { medio: detraccion.medio_pago })}</p>
      </div>
    </div>
  );
}

/** Un valor vacío de la ficha: la raya, apagada. */
const VACIO = <span className="text-muted-foreground/60">—</span>;

/**
 * Una fila de importes: etiqueta y monto alineado a la derecha. No es `ListaDatos`: la columna de montos tiene que alinearse a la derecha
 * (y el cero, apagado) para sumarse de un vistazo, y la lista del DS deja el valor alineado a la izquierda a continuación de la etiqueta.
 */
function Importe({ etiqueta, moneda, valor, locale, destacado = false }: { etiqueta: string; moneda: string; valor: number; locale: Locale; destacado?: boolean }) {
  return (
    <div className="flex justify-between py-1 text-muted-foreground">
      <span>{etiqueta}</span>
      <span className={cn("font-mono tabular-nums", destacado || Number(valor) !== 0 ? "font-medium text-foreground" : "text-muted-foreground/70")}>
        {formatearMonto(moneda, valor, locale)}
      </span>
    </div>
  );
}

function CajaRespuesta({ comprobante, t }: { comprobante: Comprobante; t: T }) {
  const tono = ESTADOS_CON_RESPUESTA[comprobante.estado_documento];
  const texto = comprobante.cdr?.descripcion ?? comprobante.ultimo_error;
  if (!texto) {
    return (
      <Alerta tono="info" titulo={t.respuesta.titulo}>
        <span className="font-mono text-etiqueta">{t.respuesta.sinRespuesta}</span>
      </Alerta>
    );
  }
  return (
    <Alerta
      tono={tono ?? "info"}
      titulo={
        comprobante.cdr
          ? t.respuesta.cdr
          : comprobante.intentos > 0
            ? interpolar(comprobante.intentos === 1 ? t.respuesta.ultimoErrorUno : t.respuesta.ultimoErrorVarios, { n: comprobante.intentos })
            : t.respuesta.ultimoEstado
      }
    >
      <span className="font-mono text-etiqueta font-medium">&ldquo;{texto}&rdquo;</span>
    </Alerta>
  );
}

/** Historial de intentos (#7): un punto por cambio de estado, en hora de Lima, con el motivo; «Sin historial» si el backend no lo envía. */
function Historial({ eventos, t, estados, locale }: { eventos: Comprobante["eventos"]; t: T; estados: Record<string, string>; locale: Locale }) {
  return (
    <section className={TARJETA} data-testid="historial">
      <CabeceraSeccion icon={ClockCounterClockwiseIcon} titulo={t.historial.titulo} />
      <div className="px-4 pb-4">
        {eventos && eventos.length > 0 ? (
          <Timeline
            className="ml-1.5"
            eventos={eventos.map((e, i) => ({
              id: String(i),
              titulo: estados[e.estado_resultante] ?? e.estado_resultante,
              fecha: formatearFechaHora(e.fecha, locale),
              tono: TONOS_ESTADO[e.estado_resultante as keyof typeof TONOS_ESTADO] ?? "neutro",
              detalle:
                e.estado_anterior || e.mensaje ? (
                  <>
                    {e.estado_anterior ? <span className="block">{interpolar(t.historial.desde, { estado: estados[e.estado_anterior] ?? e.estado_anterior })}</span> : null}
                    {e.mensaje ? <span className="block font-mono text-etiqueta text-foreground/80">{e.mensaje}</span> : null}
                  </>
                ) : undefined,
            }))}
          />
        ) : (
          <p className="text-control text-muted-foreground">{t.historial.vacio}</p>
        )}
      </div>
    </section>
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

  const messages = await getMessages();
  const locale = (await getLocale()) as Locale;
  const t = messages.comprobantes.detalle;
  const tipos = etiquetasTipo(messages);
  const monedas = catalogo(t.monedas);
  const numero = `${c.serie}-${String(c.numero).padStart(8, "0")}`;
  const datosValidez = datosDeValidez(c);
  const tipoLabel = tipos[c.tipo] ?? c.tipo;
  const tipoOperacion = c.tipo_operacion ? `${c.tipo_operacion} · ${catalogo(t.tiposOperacion)[c.tipo_operacion] ?? t.otraOperacion}` : null;
  const tipoDoc = c.receptor ? (etiquetasTipoDoc(messages)[c.receptor.tipo_doc] ?? interpolar(t.receptor.tipo, { tipo: c.receptor.tipo_doc })) : null;
  // #356: una boleta (o nota B) que sigue sin informarse desde el 5.º día se avisa con lo que le queda.
  const aviso = avisoDePlazo(c, hoyLima());
  const hashCorto = c.hash && c.hash.length > 16 ? `${c.hash.slice(0, 8)}…${c.hash.slice(-5)}` : c.hash;

  return (
    <div className={CONTENEDOR_PAGINA}>
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <EnlaceVolver href="/comprobantes">{t.volver}</EnlaceVolver>

        <div className="flex flex-wrap items-center gap-2">
          {c.estado_documento === "ERROR_ENVIO" ? <ReenviarButton id={c.id} /> : null}
          {c.estado_documento === "FIRMADO" ? <ReenviarButton id={c.id} etiqueta={t.enviarASunat} enCurso={t.enviando} /> : null}
          {/* #315: la constancia de un comprobante atascado (solo facturas y sus notas: SUNAT no la expone para boletas) y la consulta de validez. */}
          {puedeRecuperarCdr(c) ? <RecuperarCdrButton id={c.id} estado={c.estado_documento} /> : null}
          {datosValidez ? <ValidezButton datos={datosValidez} /> : null}
          {sePuedeDuplicar(c) ? <DuplicarButton id={c.id} numero={numero} /> : null}
          {admiteNotas(c) ? (
            <Link href={`/comprobantes/${c.id}/nota`} className={ACCION_SECUNDARIA} data-testid="emitir-nota">
              <FileMinusIcon weight={PESO_ICONO} className="size-4" />
              {t.emitirNota}
            </Link>
          ) : null}
          {admiteBaja(c) ? <BajaButton id={c.id} numero={numero} fechaEmision={c.fecha_emision} boleta={esDeBoleta(c)} reversion={esDeAgente(c)} notasVigentes={(c.notas ?? []).filter((n) => n.estado_documento !== "RECHAZADO" && n.estado_documento !== "INVALIDO" && n.estado_documento !== "ANULADO").length} /> : null}
          <VistaPrevia id={c.id} numero={numero} nombreArchivo={c.nombre_archivo} tieneCdr={tieneConstanciaCdr(c)} />
          {c.enlaces?.pdf ? (
            <a
              href={`/api/proxy/facturas/${c.id}/pdf`}
              target="_blank"
              rel="noopener"
              title={t.pdfTitulo}
              className={ACCION_PRINCIPAL}
              data-testid="ver-pdf"
            >
              <FileTextIcon weight={PESO_ICONO} className="size-4" />
              {t.verPdf}
            </a>
          ) : null}
          {admiteCorreo(c) ? <CorreoButton id={c.id} numero={numero} correoDelCliente={c.receptor?.email ?? null} /> : null}
        </div>
      </div>

      <AvisosDeEmision id={c.id} />

      {aviso ? <AvisoBoletaSinInformar aviso={aviso} que={c.tipo === "03" ? "boleta" : "nota"} /> : null}

      <section className={TARJETA}>
        <div className="flex flex-col justify-between gap-4 px-4 pt-4 pb-3 md:flex-row md:items-center">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2.5">
              <h2 className={TITULO_REGISTRO}>{interpolar(t.tipoElectronico, { tipo: tipoLabel })}</h2>
              <ChipCodigo>{numero}</ChipCodigo>
            </div>
            <p className="mt-1 truncate font-mono text-etiqueta text-muted-foreground">
              {t.documentoId} <span className="text-foreground/80">{c.id}</span>
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <EstadoBadge estado={c.estado_documento} />
            {c.cdr ? (
              <span
                data-testid="cdr-codigo"
                // #362 B10: una boleta informada por resumen diario no tiene CDR propio: guarda y descarga el del resumen (el archivo se sigue llamando R-<boleta>).
                title={
                  c.cdr.resumen
                    ? interpolar(t.cdrDelResumen, { resumen: c.cdr.resumen })
                    : tieneConstanciaCdr(c) ? t.cdrConstancia : t.cdrSinConstancia
                }
              >
                <Chip className="font-mono">
                  {tieneConstanciaCdr(c) ? t.etiquetaCdr : t.etiquetaCodigoSunat}: {c.cdr.codigo}
                </Chip>
              </span>
            ) : null}
          </div>
        </div>

        <div className="px-4 pb-4">
          <ListaDatos
            columnas={2}
            datos={[
              {
                etiqueta: t.fechaEmision,
                valor: (
                  <>
                    <span className="font-mono">{formatearFecha(c.fecha_emision, locale)}</span>
                    {c.fecha_vencimiento ? <span className="block text-etiqueta text-muted-foreground">{interpolar(t.vence, { fecha: formatearFecha(c.fecha_vencimiento, locale) })}</span> : null}
                    {c.fecha_limite_envio && seInformaFueraDePlazo(c) ? (
                      // #331: no vence; al enviarla va en un resumen diario fuera de plazo y SUNAT no la rechaza (puede observarla con la 4443).
                      <span className="block text-etiqueta text-warning-foreground" data-testid="aviso-envio-fuera-de-plazo">
                        {interpolar(t.plazoVencidoResumen, { fecha: formatearFecha(c.fecha_limite_envio, locale) })}
                      </span>
                    ) : c.fecha_limite_envio && (c.estado_documento === "FIRMADO" || c.estado_documento === "ERROR_ENVIO") ? (
                      <span className="block text-etiqueta text-warning-foreground">{interpolar(t.enviarHasta, { fecha: formatearFecha(c.fecha_limite_envio, locale) })}</span>
                    ) : null}
                    {c.fecha_limite_envio && c.estado_documento === "FUERA_DE_PLAZO" ? (
                      <span className="block text-etiqueta text-destructive">{interpolar(t.plazoVencido, { fecha: formatearFecha(c.fecha_limite_envio, locale) })}</span>
                    ) : null}
                    {c.estado_documento === "DESCARTADO" ? <span className="block text-etiqueta text-muted-foreground">{t.descartado}</span> : null}
                  </>
                ),
              },
              { etiqueta: t.moneda, valor: monedas[c.moneda] ?? c.moneda },
              { etiqueta: t.tipoOperacion, valor: tipoOperacion ?? VACIO, mono: !!tipoOperacion },
              {
                etiqueta: t.firma,
                valor: c.hash ? (
                  <span className="flex min-w-0 items-center gap-1">
                    <span className="truncate font-mono text-foreground/80" title={c.hash}>
                      {hashCorto}
                    </span>
                    <BotonCopiar texto={c.hash} titulo={t.copiarHash} />
                  </span>
                ) : (
                  <span className="text-muted-foreground/60">{t.sinFirmar}</span>
                ),
              },
            ]}
          />
        </div>
        {c.leyendas && c.leyendas.length > 0 ? (
          <div className="flex flex-col gap-1 px-4 pb-4" data-testid="leyendas">
            <span className={ETIQUETA_DATO}>{t.leyendas}</span>
            {c.leyendas.map((l) => (
              <p key={l.codigo} className="text-control text-foreground/90">
                <span className="font-mono text-muted-foreground">{l.codigo}</span> · {l.texto}
              </p>
            ))}
          </div>
        ) : null}
      </section>

      <section className={TARJETA}>
        <CabeceraSeccion icon={IdentificationCardIcon} titulo={t.receptor.titulo} />
        <div className="px-4 pb-4">
          {c.receptor ? (
            <ListaDatos
              columnas={2}
              datos={[
                { etiqueta: t.receptor.razonSocial, valor: <span className="font-semibold">{c.receptor.razon_social}</span> },
                {
                  etiqueta: t.receptor.documento,
                  // #20: una boleta de hasta S/ 700 puede no identificar al comprador («-»): se dice así, sin un «Tipo -» ni un número «-».
                  valor:
                    c.receptor.tipo_doc === "-" ? (
                      tipoDoc
                    ) : (
                      <span className="flex flex-wrap items-center gap-1.5">
                        <Chip className="font-mono">
                          {interpolar(t.receptor.tipo, { tipo: c.receptor.tipo_doc })} · {tipoDoc}
                        </Chip>
                        <span className="font-mono font-semibold">{c.receptor.num_doc}</span>
                      </span>
                    ),
                },
                { etiqueta: t.receptor.direccion, valor: c.receptor.direccion ?? VACIO },
                ...(c.receptor.pais ? [{ etiqueta: t.receptor.pais, valor: <span data-testid="receptor-pais">{c.receptor.pais}</span>, mono: true }] : []),
                ...(c.receptor.email ? [{ etiqueta: t.receptor.correo, valor: <span className="break-all" data-testid="receptor-email">{c.receptor.email}</span>, mono: true }] : []),
              ] satisfies DatoLista[]}
            />
          ) : (
            <p className="text-control text-muted-foreground">{t.receptor.vacio}</p>
          )}
        </div>
      </section>

      {/* Arriba del historial: es lo primero que hay que ver tras confirmar una baja, y antes quedaba fuera de la vista. */}
      {c.baja ? <BajaEstado baja={c.baja} /> : null}

      {c.envio_al_cliente ? <EnvioAlClienteEstado envio={c.envio_al_cliente} /> : null}

      <Historial eventos={c.eventos} t={t} estados={etiquetasEstado(messages)} locale={locale} />


      {c.observaciones ? (
        <section className={TARJETA} data-testid="observaciones">
          <CabeceraSeccion icon={ChatTextIcon} titulo={t.observaciones} />
          <p className="px-4 pb-4 text-control leading-relaxed whitespace-pre-line text-foreground/90">{c.observaciones}</p>
        </section>
      ) : null}

      {c.nota ? (
        <TarjetaDestacada
          tono="info"
          data-testid="nota"
          icono={<FileMinusIcon weight={PESO_ICONO} />}
          titulo={c.tipo === "07" ? t.nota.creditoSobre : t.nota.debitoSobre}
        >
          <ListaDatos
            columnas={2}
            datos={[
              { etiqueta: c.nota.tipo_afectado === "03" ? t.nota.boletaModificada : t.nota.facturaModificada, valor: <span className="font-semibold">{c.nota.documento_afectado}</span>, mono: true },
              {
                etiqueta: t.nota.motivo,
                valor: (
                  <>
                    {/* Chip y no ChipCodigo: el código va sobre la tarjeta de acento, y ChipCodigo es del mismo color. */}
                    <Chip className="mr-1.5 font-mono">{c.nota.motivo}</Chip>
                    {c.nota.motivo_descripcion}
                  </>
                ),
              },
              { etiqueta: t.nota.sustento, valor: c.nota.descripcion },
            ]}
          />
        </TarjetaDestacada>
      ) : null}

      {c.notas?.length ? (
        <section className={TARJETA} data-testid="notas">
          <CabeceraSeccion icon={FileMinusIcon} titulo={c.tipo === "03" ? t.notas.sobreBoleta : t.notas.sobreFactura} />
          <ul className="divide-y divide-border/60 px-4 pb-2 text-control">
            {c.notas.map((n) => (
              <li key={n.id} className="flex flex-wrap items-center gap-3 py-2">
                <Link href={`/comprobantes/${n.id}`} className="font-mono font-semibold text-primary hover:underline">
                  {n.comprobante}
                </Link>
                <span className="text-muted-foreground">{tipos[n.tipo]}</span>
                <span className="text-muted-foreground">{formatearFecha(n.fecha_emision, locale)}</span>
                <span className="text-foreground/80">
                  <span className="mr-1 font-mono text-etiqueta text-muted-foreground">{n.motivo}</span>
                  {n.motivo_descripcion}
                </span>
                <span className="ml-auto font-mono tabular-nums text-foreground">{formatearMonto(c.moneda, n.total, locale)}</span>
                <EstadoBadge estado={n.estado_documento} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {c.referencias ? (
        <section className={TARJETA} data-testid="referencias">
          <CabeceraSeccion icon={LinkIcon} titulo={t.referencias.titulo} />
          <div className="px-4 pb-4">
            <ListaDatos
              columnas={2}
              datos={[
                { etiqueta: t.referencias.ordenCompra, valor: c.referencias.orden_compra ?? VACIO, mono: !!c.referencias.orden_compra },
                {
                  etiqueta: t.referencias.guias,
                  valor: c.referencias.guias?.length ? (
                    <ul className="space-y-0.5">
                      {c.referencias.guias.map((g) => (
                        <li key={`${g.tipo}-${g.numero}`} className="font-mono">
                          {g.numero} <span className="text-etiqueta text-muted-foreground">{g.tipo} · {etiquetasGuia(messages)[g.tipo] ?? t.referencias.guia}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    VACIO
                  ),
                },
                {
                  etiqueta: t.referencias.otros,
                  valor: c.referencias.documentos_relacionados?.length ? (
                    <ul className="space-y-0.5">
                      {c.referencias.documentos_relacionados.map((d) => (
                        <li key={`${d.tipo}-${d.numero}`} className="font-mono">
                          {d.numero} <span className="text-etiqueta text-muted-foreground">{d.tipo} · {etiquetasDocRelacionado(messages)[d.tipo] ?? t.referencias.documento}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    VACIO
                  ),
                },
              ]}
            />
          </div>
        </section>
      ) : null}

      {/* #40: una retención o una percepción no tiene líneas de venta: muestra los documentos que paga o cobra. */}
      {c.agente ? (
        <DocumentosDeAgente agente={c.agente} retencion={c.tipo === "20"} />
      ) : (
      <section className={CONTENEDOR_TABLA}>
        <CabeceraSeccion
          icon={RowsIcon}
          titulo={t.items.titulo}
          className="px-2"
          derecha={<Chip>{interpolar(c.items.length === 1 ? t.items.uno : t.items.varios, { n: c.items.length })}</Chip>}
        />
          <Table className="text-control">
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className={cn(CABECERA_TABLA, "w-12 text-center")}>#</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-28")}>{t.items.codigo}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.items.descripcion}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-20 text-right")}>{t.items.cantidad}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-24 text-center")}>{t.items.unidad}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-48 text-center")}>{t.items.afectacion}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-28 text-right")}>{t.items.precioUnitario}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-28 text-right")}>{t.items.descuento}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-28 text-right")}>{t.items.cargos}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "w-28 text-right")}>{t.items.subtotal}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {c.items.map((item, i) => (
                <TableRow key={i}>
                  <TableCell className="px-3 py-2 text-center font-mono text-muted-foreground">{i + 1}</TableCell>
                  <TableCell className="px-3 py-2 font-mono font-medium text-muted-foreground">{item.codigo ?? "—"}</TableCell>
                  <TableCell className="px-3 py-2 font-medium whitespace-normal text-foreground">
                    {item.descripcion}
                    {item.codigo_sunat || item.gtin ? (
                      <span className="mt-0.5 block font-mono text-etiqueta font-normal text-muted-foreground">
                        {item.codigo_sunat ? interpolar(t.items.codigoSunat, { codigo: item.codigo_sunat }) : null}
                        {item.codigo_sunat && item.gtin ? " · " : null}
                        {item.gtin ? `${item.gtin.tipo} ${item.gtin.codigo}` : null}
                      </span>
                    ) : null}
                    {item.hidrobiologico ? (
                      <span className="mt-1 block text-etiqueta font-normal leading-snug text-muted-foreground" data-testid="hidrobiologico">
                        {interpolar(t.items.hidrobiologico, {
                          nombre: item.hidrobiologico.nombre_embarcacion,
                          matricula: item.hidrobiologico.matricula,
                          especie: item.hidrobiologico.especie,
                          cantidad: formatearNumero(item.hidrobiologico.cantidad, locale),
                          lugar: item.hidrobiologico.lugar_descarga,
                          fecha: formatearFecha(item.hidrobiologico.fecha_descarga, locale),
                        })}
                      </span>
                    ) : null}
                    {item.transporte ? (
                      <span className="mt-1 block text-etiqueta font-normal leading-snug text-muted-foreground" data-testid="transporte">
                        {item.transporte.origen.direccion} ({item.transporte.origen.ubigeo}) → {item.transporte.destino.direccion} ({item.transporte.destino.ubigeo}) · {item.transporte.detalle_viaje}
                        <span className="block font-mono">
                          {interpolar(t.items.valoresReferenciales, {
                            servicio: formatearMonto("PEN", item.transporte.valor_referencial.servicio, locale),
                            efectiva: formatearMonto("PEN", item.transporte.valor_referencial.carga_efectiva, locale),
                            util: formatearMonto("PEN", item.transporte.valor_referencial.carga_util_nominal, locale),
                          })}
                          {item.transporte.tramos?.length
                            ? interpolar(item.transporte.tramos.length === 1 ? t.items.tramoUno : t.items.tramoVarios, { n: item.transporte.tramos.length })
                            : ""}
                        </span>
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell className="px-3 py-2 text-right font-mono text-foreground/90 tabular-nums">{formatearNumero(item.cantidad, locale)}</TableCell>
                  <TableCell className="px-3 py-2 text-center font-mono">
                    <Chip>{item.unidad}</Chip>
                  </TableCell>
                  <TableCell className="px-3 py-2 text-center">
                    <Chip>
                      {item.tipo_afectacion_igv} · {etiquetasAfectacion(messages)[item.tipo_afectacion_igv] ?? t.items.otraAfectacion}
                    </Chip>
                    {item.isc ? (
                      <span className="ml-1" title={interpolar(t.items.iscTitulo, { sistema: item.isc.sistema, monto: formatearNumero(item.isc.monto, locale) })}>
                        <PillEstado tono="aviso">{interpolar(t.items.iscTasa, { tasa: item.isc.tasa })}</PillEstado>
                      </span>
                    ) : null}
                    {item.icbper ? (
                      <span className="ml-1" title={t.items.icbperTitulo}>
                        <PillEstado tono="aviso">{interpolar(t.items.icbperMonto, { monto: formatearNumero(item.icbper, locale) })}</PillEstado>
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell className="px-3 py-2 text-right font-mono text-foreground/80 tabular-nums">{formatearNumero(item.precio_unitario, locale)}</TableCell>
                  <TableCell className="px-3 py-2 text-right font-mono text-foreground/80 tabular-nums">
                    {item.descuento ? (
                      <span title={interpolar(t.items.codigoAfecta, { codigo: item.descuento.codigo, afecta: item.descuento.afecta_base_igv ? t.items.afectaBase : t.items.noAfectaBase })}>
                        −{formatearNumero(item.descuento.monto, locale)}
                        <span className="ml-1 text-etiqueta text-muted-foreground">
                          {item.descuento.tipo === "PORCENTAJE" ? `${item.descuento.valor}%` : item.descuento.codigo}
                        </span>
                      </span>
                    ) : (
                      <span className="text-muted-foreground/50">—</span>
                    )}
                  </TableCell>
                  <TableCell className="px-3 py-2 text-right font-mono text-foreground/80 tabular-nums">
                    {item.cargos?.length ? (
                      <span title={item.cargos.map((cg) => interpolar(t.items.codigoAfecta, { codigo: cg.codigo, afecta: cg.afecta_base_igv ? t.items.afectaBase : t.items.noAfectaBase })).join(" / ")}>
                        +{formatearNumero(item.cargos.reduce((acc, cg) => acc + Number(cg.monto), 0), locale)}
                        <span className="ml-1 text-etiqueta text-muted-foreground">{item.cargos.map((cg) => cg.codigo).join("+")}</span>
                      </span>
                    ) : (
                      <span className="text-muted-foreground/50">—</span>
                    )}
                  </TableCell>
                  <TableCell className="px-3 py-2 text-right font-mono font-medium text-foreground tabular-nums">
                    {item.gratuita ? (
                      <span title={t.items.gratuitaTitulo}>
                        <Chip>{t.items.gratuita}</Chip>
                      </span>
                    ) : (
                      formatearNumero(item.precio_venta ?? (item.valor_venta != null && item.igv != null ? Number(item.valor_venta) + Number(item.igv) : Number(item.cantidad) * Number(item.precio_unitario)), locale)
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {c.items.length === 0 ? <FilaVacia columnas={10}>{t.items.vacio}</FilaVacia> : null}
            </TableBody>
          </Table>
      </section>
      )}

      <div className="grid grid-cols-1 items-start gap-3 lg:grid-cols-12">
        <section className={cn(TARJETA, "lg:col-span-7")}>
          <CabeceraSeccion icon={CodeIcon} titulo={t.tecnica.titulo} derecha={<Chip className="font-mono">{t.tecnica.tabla}</Chip>} />
          <div className="space-y-4 px-4 pb-4">
            <CajaRespuesta comprobante={c} t={t} />
            <ListaDatos
              datos={[
                { etiqueta: "nombre_archivo", valor: <span className="break-all select-all">{c.nombre_archivo ?? "—"}</span>, mono: true },
                {
                  etiqueta: "cdr_observaciones",
                  valor: c.cdr && c.cdr.observaciones.length > 0 ? c.cdr.observaciones.join(" · ") : <Chip>{t.tecnica.sinObservaciones}</Chip>,
                  mono: !!(c.cdr && c.cdr.observaciones.length > 0),
                },
                { etiqueta: "intentos", valor: <span className="font-semibold">{c.intentos}</span>, mono: true },
                /* Sin fila de idempotency_key (264-H2): la API no la expone, y un «—» deshabilitado con la explicación en un tooltip es justo lo que C6 quita. */
              ]}
            />
          </div>
        </section>

        <section className={cn(TARJETA, "lg:col-span-5")}>
          <CabeceraSeccion icon={ReceiptIcon} titulo={t.importes.titulo} derecha={<Chip>{monedas[c.moneda] ?? c.moneda}</Chip>} />
          <div className="space-y-3 px-4 pb-4">
          {c.agente ? (
            <ImportesDeAgente agente={c.agente} retencion={c.tipo === "20"} />
          ) : (
          <>
          <div className="space-y-2 text-etiqueta">
            {c.totales.exportacion ? (
              <Importe etiqueta={t.importes.exportacion} moneda={c.moneda} valor={c.totales.exportacion} locale={locale} />
            ) : (
              <Importe etiqueta={c.totales.ivap ? t.importes.sujetoIvap : t.importes.gravado} moneda={c.moneda} valor={c.totales.gravado} locale={locale} />
            )}
            <Importe etiqueta={t.importes.exonerado} moneda={c.moneda} valor={c.totales.exonerado} locale={locale} />
            <Importe etiqueta={t.importes.inafecto} moneda={c.moneda} valor={c.totales.inafecto} locale={locale} />
            {c.totales.descuento_global ? (
              <Importe
                etiqueta={interpolar(t.importes.descuentoGlobal, {
                  codigo: c.totales.descuento_global.codigo,
                  afecta: c.totales.descuento_global.afecta_base_igv ? t.importes.afectaLaBase : "",
                })}
                moneda={c.moneda}
                valor={-c.totales.descuento_global.monto}
                locale={locale}
              />
            ) : null}
            {c.totales.cargos?.filter((cg) => cg.afecta_base_igv).map((cg, i) => (
              <Importe key={`cg-base-${i}`} etiqueta={interpolar(t.importes.cargoGlobal, { codigo: cg.codigo })} moneda={c.moneda} valor={cg.monto} locale={locale} />
            ))}
            {c.totales.isc ? <Importe etiqueta={t.importes.isc} moneda={c.moneda} valor={c.totales.isc} locale={locale} /> : null}
            {c.totales.icbper ? <Importe etiqueta={t.importes.icbper} moneda={c.moneda} valor={c.totales.icbper} locale={locale} /> : null}
            {c.totales.ivap ? (
              <Importe etiqueta={t.importes.ivap} moneda={c.moneda} valor={c.totales.ivap} locale={locale} />
            ) : (
              <Importe
                etiqueta={c.totales.tasa_igv != null ? interpolar(t.importes.igvTasa, { tasa: formatearNumero(c.totales.tasa_igv, locale) }) : t.importes.igv}
                moneda={c.moneda}
                valor={c.totales.igv}
                locale={locale}
              />
            )}
            {c.totales.gratuito ? (
              <>
                <Importe etiqueta={t.importes.gratuitas} moneda={c.moneda} valor={c.totales.gratuito} locale={locale} />
                <Importe etiqueta={t.importes.igvGratuitas} moneda={c.moneda} valor={c.totales.igv_gratuitas ?? 0} locale={locale} />
              </>
            ) : null}
            {c.totales.total_descuentos || c.totales.total_cargos || c.totales.total_anticipos ? (
              <Importe etiqueta={t.importes.precioVenta} moneda={c.moneda} valor={c.totales.total_precio_venta ?? c.totales.total} locale={locale} />
            ) : null}
            {c.totales.total_cargos ? (
              <Importe
                etiqueta={interpolar(t.importes.otrosCargos, {
                  codigos: c.totales.cargos?.some((cg) => !cg.afecta_base_igv) ? ` (${c.totales.cargos.filter((cg) => !cg.afecta_base_igv).map((cg) => cg.codigo).join(", ")})` : "",
                })}
                moneda={c.moneda}
                valor={c.totales.total_cargos}
                locale={locale}
              />
            ) : null}
            {c.totales.total_descuentos ? (
              <Importe etiqueta={t.importes.descuentos} moneda={c.moneda} valor={-c.totales.total_descuentos} locale={locale} />
            ) : null}
            {c.totales.total_anticipos ? (
              <Importe etiqueta={t.importes.anticipos} moneda={c.moneda} valor={-c.totales.total_anticipos} locale={locale} />
            ) : null}
            {c.totales.redondeo ? <Importe etiqueta={t.importes.redondeo} moneda={c.moneda} valor={c.totales.redondeo} locale={locale} /> : null}
            <div className="my-2 h-px bg-border" />
            <div className="flex items-baseline justify-between pt-1">
              <div>
                <span className="block text-etiqueta font-semibold text-foreground">{t.importes.totalAPagar}</span>
                <span className="text-etiqueta text-muted-foreground/80">{t.importes.netoFinal}</span>
              </div>
              <span className="font-mono text-titulo-pagina font-bold tracking-tight text-foreground tabular-nums">{formatearMonto(c.moneda, c.totales.total, locale)}</span>
            </div>
            {c.tipo_cambio != null && c.total_en_soles != null ? (
              <p className="mt-1 text-right text-etiqueta text-muted-foreground/80" data-testid="equivalente-en-soles">
                {interpolar(t.importes.equivale, { monto: formatearMonto("PEN", c.total_en_soles, locale), tipoCambio: c.tipo_cambio })}
              </p>
            ) : null}
          </div>
          <FormaPagoDetalle formaPago={c.forma_pago} moneda={c.moneda} t={t} locale={locale} />
          </>
          )}
          {c.detraccion ? <DetraccionDetalle detraccion={c.detraccion} t={t} locale={locale} /> : null}
          {c.exportacion ? <ExportacionDetalle exportacion={c.exportacion} t={t} /> : null}
          {c.anticipos?.length ? (
            <div className="mt-4 border-t border-border/60 pt-3 text-etiqueta" data-testid="anticipos">
              <span className={ETIQUETA_DATO}>{t.anticipos.titulo}</span>
              <ul className="mt-1 space-y-1">
                {c.anticipos.map((a) => (
                  <li key={a.comprobante} className="flex items-baseline justify-between gap-3">
                    <span className="text-muted-foreground">
                      <span className="font-mono text-foreground">{a.comprobante}</span> · {messages.documentos.afectacionAnticipo[a.afectacion] ?? a.afectacion} ({a.codigo_sunat})
                      {a.fecha_pago ? interpolar(t.anticipos.pagado, { fecha: a.fecha_pago }) : ""}
                    </span>
                    <span className="font-mono tabular-nums text-foreground/80">
                      −{formatearMonto(c.moneda, a.importe_pagado, locale)}
                      <span className="text-etiqueta text-muted-foreground/80">{interpolar(t.anticipos.sinIgv, { monto: formatearMonto(c.moneda, a.monto, locale) })}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          {c.retencion_igv ? (
            <div className="mt-4 border-t border-border/60 pt-3 text-etiqueta" data-testid="retencion">
              <div className="flex items-center justify-between">
                <span className={ETIQUETA_DATO}>{interpolar(t.retencion.titulo, { porcentaje: c.retencion_igv.porcentaje })}</span>
                <span className="font-mono tabular-nums text-foreground/80">−{formatearMonto(c.moneda, c.retencion_igv.monto, locale)}</span>
              </div>
              <div className="mt-1 flex items-baseline justify-between text-muted-foreground">
                <span>{t.retencion.neto}</span>
                <span className="font-mono font-semibold text-foreground tabular-nums">{formatearMonto(c.moneda, c.retencion_igv.neto_cobrar, locale)}</span>
              </div>
            </div>
          ) : null}
          {c.percepcion ? (
            <div className="mt-4 border-t border-border/60 pt-3 text-etiqueta" data-testid="percepcion">
              <div className="flex items-center justify-between">
                <span className={ETIQUETA_DATO}>{interpolar(t.percepcion.titulo, { regimen: c.percepcion.regimen, porcentaje: c.percepcion.porcentaje })}</span>
                <span className="font-mono tabular-nums text-foreground/80">+{formatearMonto("PEN", c.percepcion.monto, locale)}</span>
              </div>
              <div className="mt-1 flex items-baseline justify-between text-muted-foreground">
                <span>{t.percepcion.total}</span>
                <span className="font-mono font-semibold text-foreground tabular-nums">{formatearMonto("PEN", c.percepcion.total_con_percepcion, locale)}</span>
              </div>
              <p className="mt-1 text-etiqueta text-muted-foreground/80">{c.percepcion.descripcion}</p>
            </div>
          ) : null}
          </div>
        </section>
      </div>
    </div>
  );
}
