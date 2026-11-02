import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CloudArrowUpIcon, FileTextIcon } from "@phosphor-icons/react/dist/ssr";
import { ListaDatos } from "khipu-design-system/datos/lista-datos";
import { CabeceraPagina, EnlaceVolver } from "khipu-design-system/patrones/cabecera-pagina";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { BajasDeComprobante } from "@/components/admin/bajas-de-comprobante";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { esIdDeComprobante, hrefDetalleComprobante, obtenerComprobanteAdmin, type ComprobanteAdmin } from "@/lib/api/admin-comprobante";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import { ApiError } from "@/lib/api/types";
import { CONTENEDOR_PAGINA, TARJETA } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useLocale, useMessages } from "next-intl";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.comprobante };
}

function Ficha({ c }: { c: ComprobanteAdmin }) {
  const messages = useMessages();
  const t = messages.admin.comprobanteDetalle;
  const locale = useLocale();
  return (
    <div className="grid grid-cols-1 items-start gap-3 lg:grid-cols-2" data-testid="comprobante-ficha">
      <section className={TARJETA} aria-labelledby="titulo-comprobante">
        <CabeceraSeccion
          icon={FileTextIcon}
          titulo={
            <span id="titulo-comprobante" className="font-mono">
              {c.nombre_archivo}
            </span>
          }
          derecha={<EstadoBadge estado={c.estado} />}
        />
        <ListaDatos
          className="px-4 pt-2 pb-4"
          datos={[
            {
              etiqueta: t.empresa,
              valor: (
                <>
                  {c.razon_social} <span className="font-mono text-muted-foreground">{interpolar(t.rucDe, { ruc: c.ruc })}</span>
                  <br />
                  <Link href={hrefDetalleEmpresa(c.empresa_id)} className="text-control text-primary hover:underline">
                    {t.verEmpresa}
                  </Link>
                </>
              ),
            },
            {
              etiqueta: t.tipo,
              valor: (
                <>
                  {(messages.documentos.tipos as Record<string, string>)[c.tipo] ?? c.tipo} <span className="font-mono text-muted-foreground">{c.serie}</span>
                </>
              ),
            },
            { etiqueta: t.fechaEmision, valor: formatearFecha(c.fecha_emision, locale) },
            {
              etiqueta: t.intentos,
              valor: (
                <>
                  <span data-testid="comprobante-intentos">{c.intentos}</span>
                  <span className="mt-0.5 block text-etiqueta text-muted-foreground">{t.ayudaIntentos}</span>
                </>
              ),
            },
          ]}
        />
      </section>

      <section className={TARJETA} aria-labelledby="titulo-sunat">
        <CabeceraSeccion icon={CloudArrowUpIcon} titulo={<span id="titulo-sunat">{t.tituloSunatYArchivos}</span>} />
        <ListaDatos
          className="px-4 pt-2 pb-4"
          datos={[
            {
              etiqueta: t.respuestaSunat,
              valor: (
                <span data-testid="comprobante-respuesta" className="break-words">
                  {c.respuesta_sunat ? (
                    <>
                      <span className="font-mono">{c.respuesta_sunat.codigo}</span>
                      {c.respuesta_sunat.descripcion ? ` — ${c.respuesta_sunat.descripcion}` : null}
                    </>
                  ) : (
                    // Fuera de plazo o descartado ya no va a llegar a SUNAT: «todavía no respondió» sugeriría que falta poco (273-H2).
                    <span className="text-muted-foreground">{c.estado === "FUERA_DE_PLAZO" || c.estado === "DESCARTADO" ? t.noLlego : t.sinRespuesta}</span>
                  )}
                </span>
              ),
            },
            {
              etiqueta: t.ultimoError,
              valor: (
                <span data-testid="comprobante-ultimo-error" className="break-words">
                  {c.ultimo_error ? <span className="font-mono text-control">{c.ultimo_error}</span> : <span className="text-muted-foreground">{t.sinError}</span>}
                </span>
              ),
            },
            {
              etiqueta: t.archivos,
              valor: (
                <>
                  <ul className="grid gap-1">
                    <li data-testid="comprobante-xml">
                      {t.xml}: <span className={c.tiene_xml ? "text-success-foreground" : "text-muted-foreground"}>{c.tiene_xml ? t.guardado : t.noGuardado}</span>
                    </li>
                    <li data-testid="comprobante-cdr">
                      {t.cdr}: <span className={c.tiene_cdr ? "text-success-foreground" : "text-muted-foreground"}>{c.tiene_cdr ? t.guardado : t.noGuardado}</span>
                    </li>
                  </ul>
                  <p className="mt-2 text-control text-muted-foreground">{t.ayudaArchivos}</p>
                </>
              ),
            },
          ]}
        />
      </section>

      <BajasDeComprobante bajas={c.bajas} tipo={c.tipo} />
    </div>
  );
}

/**
 * La ficha de un comprobante (#251), solo lectura. Se llega desde la cola de errores y la verificación de integridad. Un id que no es un UUID ni siquiera llega al
 * backend; un 404 del backend es «no existe». Cualquier otro fallo muestra el error con «Reintentar». El 401 lo atiende el layout del panel.
 */
export default async function AdminComprobantePage({ params }: { params: Promise<{ id: string }> }) {
  const messages = await getMessages();
  const { id } = await params;
  if (!esIdDeComprobante(id)) notFound();

  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const resultado = await obtenerComprobanteAdmin(access, id).then(
    (comprobante) => ({ comprobante, error: null }),
    (error: unknown) => ({ comprobante: null, error }),
  );
  // Fuera del `.then`: `notFound` lanza, y no debe confundirse con un fallo del backend.
  if (resultado.error instanceof ApiError && resultado.error.status === 404) notFound();

  const t = messages.admin.comprobanteDetalle;
  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina volver={<EnlaceVolver href="/admin/errores">{t.volver}</EnlaceVolver>} titulo={t.titulo} />

      {resultado.comprobante ? (
        <Ficha c={resultado.comprobante} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefDetalleComprobante(id)} className="font-medium underline">
              {t.reintentar}
            </Link>
          }
        >
          {t.error}
        </Alerta>
      )}
    </div>
  );
}
