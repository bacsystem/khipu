import { ArrowSquareOutIcon, ChatTextIcon, DownloadSimpleIcon, FilesIcon, IdentificationCardIcon, PackageIcon, ShieldCheckIcon, TruckIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO, type IconoComponente } from "khipu-design-system/lib/iconos";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { ListaDatos, type DatoLista } from "khipu-design-system/datos/lista-datos";
import { EnlaceVolver } from "khipu-design-system/patrones/cabecera-pagina";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { CONTENEDOR_TABLA } from "khipu-design-system/patrones/pie-tabla";
import { ChipCodigo } from "khipu-design-system/patrones/pill-estado";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
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
import { TITULO_REGISTRO } from "@/lib/detalle";
import { ACCION_SECUNDARIA, CABECERA_TABLA, CONTENEDOR_PAGINA, TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { formatearFecha, formatearPrecioUnitario } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.guias.detalle.titulo} · ${messages.app.nombre}` };
}

type T = Mensajes["guias"]["detalle"];

/** Una sección de la ficha: tarjeta con la cabecera del design system y su contenido. */
function Seccion({ titulo, icon, children }: { titulo: string; icon: IconoComponente; children: React.ReactNode }) {
  return (
    <section className={TARJETA}>
      <CabeceraSeccion icon={icon} titulo={titulo} />
      <div className="flex flex-col gap-3 px-4 pb-4">{children}</div>
    </section>
  );
}

/** Quita las filas que no aplican a esta guía (`null`) antes de pasarlas a `ListaDatos`. */
const filas = (datos: (DatoLista | null)[]): DatoLista[] => datos.filter((d): d is DatoLista => d !== null);

/** Un punto del traslado: el establecimiento de la empresa si lo es, y si no su ubigeo y su dirección. */
function Punto({ punto, t }: { punto: PuntoDeGuia; t: T }) {
  return (
    <div className="flex flex-col gap-0.5">
      {punto.direccion ? <span>{punto.direccion}</span> : null}
      <span className="font-mono text-etiqueta text-muted-foreground">
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
    <div className={CONTENEDOR_PAGINA}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <EnlaceVolver href="/guias">{t.volver}</EnlaceVolver>
        <GuiaAcciones guia={g} />
      </div>

      <section className={cn(TARJETA, "flex flex-col gap-1 p-4")}>
        <div className="flex flex-wrap items-center gap-2.5">
          <h2 className={TITULO_REGISTRO}>{messages.guias.tipos[g.tipo]}</h2>
          <span data-testid="guia-numero">
            <ChipCodigo>{numero}</ChipCodigo>
          </span>
          <EstadoGuiaBadge estado={g.estado} />
        </div>
        <span className="text-control text-muted-foreground">
          {hora ? interpolar(t.emitida, { fecha: formatearFecha(g.fecha_emision, locale), hora }) : interpolar(t.emitidaSinHora, { fecha: formatearFecha(g.fecha_emision, locale) })}
        </span>
      </section>

      <AvisoExperimental />

      <Seccion titulo={t.estadoAnteSunat} icon={ShieldCheckIcon}>
        <p className="text-control text-muted-foreground" data-testid="guia-estado-ayuda">
          {messages.guias.estadoAyuda[g.estado]}
        </p>
        <ListaDatos
          columnas={2}
          datos={filas([
            g.ticket ? { etiqueta: t.ticket, valor: g.ticket, mono: true } : null,
            g.fecha_recepcion ? { etiqueta: t.recepcion, valor: g.fecha_recepcion.replace("T", " "), mono: true } : null,
            { etiqueta: t.intentos, valor: g.intentos, mono: true },
            { etiqueta: t.consultasTicket, valor: g.consultas_ticket, mono: true },
          ])}
        />
        {g.ultimo_error ? (
          <div data-testid="guia-ultimo-error">
            <Alerta tono="error" titulo={t.ultimoError}>
              <span className="break-words">{g.ultimo_error}</span>
              {g.reintento_automatico !== undefined ? (
                <span className="mt-1 block opacity-80">{g.reintento_automatico ? t.reintentoAutomatico : t.sinReintentoAutomatico}</span>
              ) : null}
            </Alerta>
          </div>
        ) : null}
        {g.cdr ? (
          <div data-testid="guia-cdr">
            <Alerta tono="info" titulo={t.constancia}>
              <span>
                <span className="font-mono">{g.cdr.codigo}</span> · {g.cdr.descripcion}
              </span>
              {g.cdr.observaciones && g.cdr.observaciones.length > 0 ? (
                <ul className="mt-2 list-disc pl-5">
                  {g.cdr.observaciones.map((o) => (
                    <li key={o}>{o}</li>
                  ))}
                </ul>
              ) : null}
            </Alerta>
          </div>
        ) : null}
        {enlaceDeConsultaSeguro(g.enlace_consulta) ? (
          <a href={enlaceDeConsultaSeguro(g.enlace_consulta)!} target="_blank" rel="noopener noreferrer" className="flex w-fit items-center gap-1.5 text-control text-primary hover:underline">
            <ArrowSquareOutIcon weight={PESO_ICONO} className="size-3.5" />
            {t.enlaceConsulta}
          </a>
        ) : null}
      </Seccion>

      <Seccion titulo={t.destinatario} icon={IdentificationCardIcon}>
        <ListaDatos
          columnas={2}
          datos={filas([
            {
              etiqueta: t.destinatario,
              valor: (
                <>
                  <span className="font-medium">{g.destinatario.nombre}</span>
                  <span className="block font-mono text-etiqueta text-muted-foreground">
                    {g.destinatario.tipo_doc} · {g.destinatario.num_doc}
                  </span>
                </>
              ),
            },
            g.remitente
              ? {
                  etiqueta: t.remitente,
                  valor: (
                    <>
                      <span className="font-medium">{g.remitente.nombre}</span>
                      <span className="block font-mono text-etiqueta text-muted-foreground">
                        {g.remitente.tipo_doc} · {g.remitente.num_doc}
                      </span>
                    </>
                  ),
                }
              : null,
          ])}
        />
      </Seccion>

      <Seccion titulo={t.traslado} icon={TruckIcon}>
        <ListaDatos
          columnas={2}
          datos={filas([
            g.motivo
              ? {
                  etiqueta: t.motivo,
                  valor: (
                    <>
                      <span className="font-mono">{g.motivo}</span>
                      {g.descripcion_motivo ? <span> · {g.descripcion_motivo}</span> : null}
                    </>
                  ),
                }
              : null,
            g.modalidad ? { etiqueta: t.modalidad, valor: (t.modalidades as Record<string, string>)[g.modalidad] ?? g.modalidad } : null,
            { etiqueta: t.fechaInicio, valor: formatearFecha(envio.fecha_inicio, locale) },
            { etiqueta: t.pesoBruto, valor: `${formatearPrecioUnitario(envio.peso_bruto, locale)} ${envio.unidad_peso}`, mono: true },
            { etiqueta: t.partida, valor: <Punto punto={envio.partida} t={t} /> },
            { etiqueta: t.llegada, valor: <Punto punto={envio.llegada} t={t} /> },
            g.transportista
              ? {
                  etiqueta: t.transportista,
                  valor: (
                    <>
                      <span className="font-medium">{g.transportista.razon_social}</span>
                      <span className="block font-mono text-etiqueta text-muted-foreground">
                        {[interpolar(t.ruc, { ruc: g.transportista.ruc }), g.transportista.registro_mtc ? interpolar(t.registroMtc, { registro: g.transportista.registro_mtc }) : null].filter(Boolean).join(" · ")}
                      </span>
                    </>
                  ),
                }
              : null,
            g.registro_mtc ? { etiqueta: t.transportista, valor: interpolar(t.registroMtc, { registro: g.registro_mtc }) } : null,
            envio.vehiculos.length > 0
              ? {
                  etiqueta: t.vehiculos,
                  valor: (
                    <ul className="flex flex-wrap gap-x-4 gap-y-1">
                      {envio.vehiculos.map((v) => (
                        <li key={v.placa} className="font-mono">
                          {v.placa}
                          {v.principal ? <span className="ml-1 font-sans text-etiqueta text-muted-foreground">({t.principal})</span> : null}
                        </li>
                      ))}
                    </ul>
                  ),
                }
              : null,
            envio.conductores.length > 0
              ? {
                  etiqueta: t.conductores,
                  valor: (
                    <ul className="flex flex-col gap-1">
                      {envio.conductores.map((c) => (
                        <li key={`${c.tipo_doc}-${c.num_doc}`}>
                          {c.nombres} {c.apellidos}
                          <span className="ml-1 font-mono text-etiqueta text-muted-foreground">
                            {c.tipo_doc} · {c.num_doc} · {interpolar(t.licencia, { licencia: c.licencia })}
                            {c.principal ? ` · ${t.principal}` : ""}
                          </span>
                        </li>
                      ))}
                    </ul>
                  ),
                }
              : null,
            envio.indicadores.length > 0
              ? {
                  etiqueta: t.indicadores,
                  valor: (
                    <ul className="list-disc pl-5">
                      {envio.indicadores.map((i) => (
                        <li key={i}>{(t.indicadoresNombres as Record<string, string>)[i] ?? i}</li>
                      ))}
                    </ul>
                  ),
                }
              : null,
          ])}
        />
      </Seccion>

      <section className={CONTENEDOR_TABLA}>
        <CabeceraSeccion icon={PackageIcon} titulo={t.bienes} className="px-2" />
        <Table className="text-control">
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={CABECERA_TABLA}>{t.bienesCabeceras.codigo}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.bienesCabeceras.descripcion}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.bienesCabeceras.unidad}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "text-right")}>{t.bienesCabeceras.cantidad}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {g.items.map((i, n) => (
              <TableRow key={`${i.codigo ?? ""}-${n}`}>
                <TableCell className="px-3 py-2 font-mono text-muted-foreground">{i.codigo ?? "—"}</TableCell>
                <TableCell className="px-3 py-2 whitespace-normal">{i.descripcion}</TableCell>
                <TableCell className="px-3 py-2 font-mono">{i.unidad}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono">{formatearPrecioUnitario(i.cantidad, locale)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>

      {g.documentos_relacionados.length > 0 ? (
        <Seccion titulo={t.documentos} icon={FilesIcon}>
          <ul className="flex flex-col gap-1 text-control">
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
        <Seccion titulo={t.observacionesDeLaGuia} icon={ChatTextIcon}>
          <p className="text-control break-words">{g.observaciones}</p>
        </Seccion>
      ) : null}

      {g.enlaces ? (
        <Seccion titulo={t.descargas} icon={DownloadSimpleIcon}>
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
