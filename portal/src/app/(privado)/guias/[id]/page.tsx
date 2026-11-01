import { ArrowLeftIcon, ArrowSquareOutIcon, DownloadSimpleIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { getLocale, getMessages } from "next-intl/server";
import { AvisoExperimental } from "@/components/guias/aviso-experimental";
import { EstadoGuiaBadge } from "@/components/guias/estado-guia-badge";
import { GuiaAcciones } from "@/components/guias/guia-acciones";
import type { Locale } from "@/i18n/config";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { enlaceDeConsultaSeguro, numeroDeGuia, obtenerGuia, type Guia, type PuntoDeGuia } from "@/lib/api/guias";
import { ApiError } from "@/lib/api/types";
import { ACCION_SECUNDARIA, ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha, formatearPrecioUnitario } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.guias.detalle.titulo} · ${messages.app.nombre}` };
}

type T = Mensajes["guias"]["detalle"];

function Dato({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <span className={ETIQUETA_DATO}>{etiqueta}</span>
      <div className="text-[13px] break-words text-foreground">{children}</div>
    </div>
  );
}

function Seccion({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section className={`${TARJETA} flex flex-col gap-3 p-4`}>
      <h2 className={TITULO_SECCION}>{titulo}</h2>
      {children}
    </section>
  );
}

/** Un punto del traslado: el establecimiento de la empresa si lo es, y si no su ubigeo y su dirección. */
function Punto({ punto, t }: { punto: PuntoDeGuia; t: T }) {
  return (
    <div className="flex flex-col gap-0.5">
      {punto.direccion ? <span>{punto.direccion}</span> : null}
      <span className="font-mono text-[11px] text-muted-foreground">
        {[
          punto.establecimiento ? interpolar(t.establecimiento, { codigo: punto.establecimiento }) : null,
          punto.ubigeo ? interpolar(t.ubigeo, { codigo: punto.ubigeo }) : null,
          punto.ruc ? interpolar(t.ruc, { ruc: punto.ruc }) : null,
        ]
          .filter(Boolean)
          .join(" · ")}
      </span>
    </div>
  );
}

export default async function GuiaPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { access, empresaId } = await getServerSession();
  const messages = await getMessages();
  const locale = (await getLocale()) as Locale;
  const t = messages.guias.detalle;
  if (!access || !empresaId) notFound();

  let g: Guia;
  try {
    g = await obtenerGuia(access, empresaId, id);
  } catch (error) {
    // Una guía que no existe o es de otra empresa (404) o un id mal formado (400): la misma página de «no encontrada», sin distinguir cuál.
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) notFound();
    throw error;
  }

  const numero = numeroDeGuia(g);
  const envio = g.traslado;
  const hora = g.hora_emision;

  return (
    <div className="mx-auto grid w-full max-w-[1100px] min-w-0 grid-cols-1 gap-4">
      <Link href="/guias" className="flex w-fit items-center gap-1.5 text-[12px] text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon weight={PESO_ICONO} className="size-3.5" />
        {t.volver}
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-3">
            <span className="font-mono text-xl font-semibold tracking-tight" data-testid="guia-numero">
              {numero}
            </span>
            <EstadoGuiaBadge estado={g.estado} />
          </div>
          <span className="text-[12px] text-muted-foreground">{messages.guias.tipos[g.tipo]}</span>
          <span className="text-[12px] text-muted-foreground">
            {hora ? interpolar(t.emitida, { fecha: formatearFecha(g.fecha_emision, locale), hora }) : interpolar(t.emitidaSinHora, { fecha: formatearFecha(g.fecha_emision, locale) })}
          </span>
        </div>
        <GuiaAcciones guia={g} />
      </header>

      <AvisoExperimental />

      <Seccion titulo={t.estadoAnteSunat}>
        <p className="text-[13px] text-muted-foreground" data-testid="guia-estado-ayuda">
          {messages.guias.estadoAyuda[g.estado]}
        </p>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {g.ticket ? (
            <Dato etiqueta={t.ticket}>
              <span className="font-mono text-[12px]">{g.ticket}</span>
            </Dato>
          ) : null}
          {g.fecha_recepcion ? <Dato etiqueta={t.recepcion}>{g.fecha_recepcion.replace("T", " ")}</Dato> : null}
          <Dato etiqueta={t.intentos}>{g.intentos}</Dato>
          <Dato etiqueta={t.consultasTicket}>{g.consultas_ticket}</Dato>
        </div>
        {g.ultimo_error ? (
          <div className="rounded-lg border border-destructive-border bg-destructive/10 p-3 text-[13px] text-destructive" data-testid="guia-ultimo-error">
            <strong className="block text-[11px] tracking-wider uppercase">{t.ultimoError}</strong>
            <span className="break-words">{g.ultimo_error}</span>
            {g.reintento_automatico !== undefined ? (
              <span className="mt-1 block text-[12px] opacity-80">{g.reintento_automatico ? t.reintentoAutomatico : t.sinReintentoAutomatico}</span>
            ) : null}
          </div>
        ) : null}
        {g.cdr ? (
          <div className="rounded-lg border border-border bg-muted p-3 text-[13px]" data-testid="guia-cdr">
            <strong className="block text-[11px] tracking-wider text-muted-foreground uppercase">{t.constancia}</strong>
            <span>
              <span className="font-mono">{g.cdr.codigo}</span> · {g.cdr.descripcion}
            </span>
            {g.cdr.observaciones && g.cdr.observaciones.length > 0 ? (
              <ul className="mt-2 list-disc pl-5 text-[12px] text-muted-foreground">
                {g.cdr.observaciones.map((o) => (
                  <li key={o}>{o}</li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}
        {enlaceDeConsultaSeguro(g.enlace_consulta) ? (
          <a href={enlaceDeConsultaSeguro(g.enlace_consulta)!} target="_blank" rel="noopener noreferrer" className="flex w-fit items-center gap-1.5 text-[12px] text-primary hover:underline">
            <ArrowSquareOutIcon weight={PESO_ICONO} className="size-3.5" />
            {t.enlaceConsulta}
          </a>
        ) : null}
      </Seccion>

      <Seccion titulo={t.destinatario}>
        <Dato etiqueta={t.destinatario}>
          <span className="font-medium">{g.destinatario.nombre}</span>
          <span className="block font-mono text-[11px] text-muted-foreground">
            {g.destinatario.tipo_doc} · {g.destinatario.num_doc}
          </span>
        </Dato>
        {g.remitente ? (
          <Dato etiqueta={t.remitente}>
            <span className="font-medium">{g.remitente.nombre}</span>
            <span className="block font-mono text-[11px] text-muted-foreground">
              {g.remitente.tipo_doc} · {g.remitente.num_doc}
            </span>
          </Dato>
        ) : null}
      </Seccion>

      <Seccion titulo={t.traslado}>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {g.motivo ? (
            <Dato etiqueta={t.motivo}>
              <span className="font-mono">{g.motivo}</span>
              {g.descripcion_motivo ? <span> · {g.descripcion_motivo}</span> : null}
            </Dato>
          ) : null}
          {g.modalidad ? <Dato etiqueta={t.modalidad}>{(t.modalidades as Record<string, string>)[g.modalidad] ?? g.modalidad}</Dato> : null}
          <Dato etiqueta={t.fechaInicio}>{formatearFecha(envio.fecha_inicio, locale)}</Dato>
          <Dato etiqueta={t.pesoBruto}>
            <span className="font-mono">
              {formatearPrecioUnitario(envio.peso_bruto, locale)} {envio.unidad_peso}
            </span>
          </Dato>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Dato etiqueta={t.partida}>
            <Punto punto={envio.partida} t={t} />
          </Dato>
          <Dato etiqueta={t.llegada}>
            <Punto punto={envio.llegada} t={t} />
          </Dato>
        </div>
        {g.transportista ? (
          <Dato etiqueta={t.transportista}>
            <span className="font-medium">{g.transportista.razon_social}</span>
            <span className="block font-mono text-[11px] text-muted-foreground">
              {[interpolar(t.ruc, { ruc: g.transportista.ruc }), g.transportista.registro_mtc ? interpolar(t.registroMtc, { registro: g.transportista.registro_mtc }) : null].filter(Boolean).join(" · ")}
            </span>
          </Dato>
        ) : null}
        {g.registro_mtc ? <Dato etiqueta={t.transportista}>{interpolar(t.registroMtc, { registro: g.registro_mtc })}</Dato> : null}
        {envio.vehiculos.length > 0 ? (
          <Dato etiqueta={t.vehiculos}>
            <ul className="flex flex-wrap gap-x-4 gap-y-1">
              {envio.vehiculos.map((v) => (
                <li key={v.placa} className="font-mono">
                  {v.placa}
                  {v.principal ? <span className="ml-1 font-sans text-[11px] text-muted-foreground">({t.principal})</span> : null}
                </li>
              ))}
            </ul>
          </Dato>
        ) : null}
        {envio.conductores.length > 0 ? (
          <Dato etiqueta={t.conductores}>
            <ul className="flex flex-col gap-1">
              {envio.conductores.map((c) => (
                <li key={`${c.tipo_doc}-${c.num_doc}`}>
                  {c.nombres} {c.apellidos}
                  <span className="ml-1 font-mono text-[11px] text-muted-foreground">
                    {c.tipo_doc} · {c.num_doc} · {interpolar(t.licencia, { licencia: c.licencia })}
                    {c.principal ? ` · ${t.principal}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          </Dato>
        ) : null}
        {envio.indicadores.length > 0 ? (
          <Dato etiqueta={t.indicadores}>
            <ul className="list-disc pl-5">
              {envio.indicadores.map((i) => (
                <li key={i}>{(t.indicadoresNombres as Record<string, string>)[i] ?? i}</li>
              ))}
            </ul>
          </Dato>
        ) : null}
      </Seccion>

      <Seccion titulo={t.bienes}>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-[13px]">
            <thead>
              <tr className="border-b border-border text-[11px] tracking-wider text-muted-foreground uppercase">
                <th className="py-1.5 pr-3 font-semibold">{t.bienesCabeceras.codigo}</th>
                <th className="py-1.5 pr-3 font-semibold">{t.bienesCabeceras.descripcion}</th>
                <th className="py-1.5 pr-3 font-semibold">{t.bienesCabeceras.unidad}</th>
                <th className="py-1.5 text-right font-semibold">{t.bienesCabeceras.cantidad}</th>
              </tr>
            </thead>
            <tbody>
              {g.items.map((i, n) => (
                <tr key={`${i.codigo ?? ""}-${n}`} className="border-b border-border/60">
                  <td className="py-1.5 pr-3 font-mono text-[12px] text-muted-foreground">{i.codigo ?? "—"}</td>
                  <td className="py-1.5 pr-3">{i.descripcion}</td>
                  <td className="py-1.5 pr-3 font-mono text-[12px]">{i.unidad}</td>
                  <td className="py-1.5 text-right font-mono">{formatearPrecioUnitario(i.cantidad, locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Seccion>

      {g.documentos_relacionados.length > 0 ? (
        <Seccion titulo={t.documentos}>
          <ul className="flex flex-col gap-1 text-[13px]">
            {g.documentos_relacionados.map((d) => (
              <li key={`${d.tipo}-${d.numero}`}>
                <span className="font-mono">{d.numero}</span>
                <span className="ml-2 text-muted-foreground">
                  {d.descripcion ?? d.tipo}
                  {d.ruc_emisor ? ` · ${interpolar(t.ruc, { ruc: d.ruc_emisor })}` : ""}
                </span>
              </li>
            ))}
          </ul>
        </Seccion>
      ) : null}

      {g.observaciones ? (
        <Seccion titulo={t.observacionesDeLaGuia}>
          <p className="text-[13px] break-words">{g.observaciones}</p>
        </Seccion>
      ) : null}

      {g.enlaces ? (
        <Seccion titulo={t.descargas}>
          <div className="flex flex-wrap gap-2" data-testid="guia-descargas">
            <a href={`/api/proxy/guias-remision/${encodeURIComponent(g.id)}/xml`} className={ACCION_SECUNDARIA}>
              <DownloadSimpleIcon weight={PESO_ICONO} className="size-4" />
              {t.xml}
            </a>
            <a href={`/api/proxy/guias-remision/${encodeURIComponent(g.id)}/pdf`} target="_blank" rel="noopener noreferrer" className={ACCION_SECUNDARIA}>
              <DownloadSimpleIcon weight={PESO_ICONO} className="size-4" />
              {t.pdf}
            </a>
            {g.enlaces.cdr ? (
              <a href={`/api/proxy/guias-remision/${encodeURIComponent(g.id)}/cdr`} className={ACCION_SECUNDARIA}>
                <DownloadSimpleIcon weight={PESO_ICONO} className="size-4" />
                {t.cdr}
              </a>
            ) : null}
          </div>
        </Seccion>
      ) : null}
    </div>
  );
}
