import { ArrowLeftIcon } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { esIdDeComprobante, hrefDetalleComprobante, obtenerComprobanteAdmin, type ComprobanteAdmin } from "@/lib/api/admin-comprobante";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import { ETIQUETAS_TIPO } from "@/lib/api/facturas";
import { ApiError } from "@/lib/api/types";
import { ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

export const metadata = { title: "Comprobante · Backoffice" };

function Dato({ etiqueta, children, testId }: { etiqueta: string; children: React.ReactNode; testId?: string }) {
  return (
    <div className="min-w-0">
      <dt className={cn(ETIQUETA_DATO, "mb-1")}>{etiqueta}</dt>
      <dd className="text-[13px] break-words text-foreground" data-testid={testId}>
        {children}
      </dd>
    </div>
  );
}

function Ficha({ c }: { c: ComprobanteAdmin }) {
  const t = messages.admin.comprobanteDetalle;
  return (
    <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2" data-testid="comprobante-ficha">
      <section className={cn(TARJETA, "p-5")} aria-labelledby="titulo-comprobante">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
          <h2 id="titulo-comprobante" className={cn(TITULO_SECCION, "font-mono")}>
            {c.nombre_archivo}
          </h2>
          <EstadoBadge estado={c.estado} />
        </div>
        <dl className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2">
          <Dato etiqueta={t.empresa}>
            {c.razon_social} <span className="font-mono text-muted-foreground">· RUC {c.ruc}</span>
            <br />
            <Link href={hrefDetalleEmpresa(c.empresa_id)} className="text-[12px] text-primary hover:underline">
              {t.verEmpresa}
            </Link>
          </Dato>
          <Dato etiqueta={t.tipo}>
            {ETIQUETAS_TIPO[c.tipo] ?? c.tipo} <span className="font-mono text-muted-foreground">{c.serie}</span>
          </Dato>
          <Dato etiqueta={t.fechaEmision}>{formatearFecha(c.fecha_emision)}</Dato>
          <Dato etiqueta={t.intentos} testId="comprobante-intentos">
            {c.intentos}
          </Dato>
        </dl>
      </section>

      <section className={cn(TARJETA, "p-5")} aria-labelledby="titulo-sunat">
        <h2 id="titulo-sunat" className={cn(TITULO_SECCION, "border-b border-border/60 pb-3")}>
          {t.respuestaSunat}
        </h2>
        <dl className="grid grid-cols-1 gap-4 pt-4">
          <Dato etiqueta={t.respuestaSunat} testId="comprobante-respuesta">
            {c.respuesta_sunat ? (
              <>
                <span className="font-mono">{c.respuesta_sunat.codigo}</span>
                {c.respuesta_sunat.descripcion ? ` — ${c.respuesta_sunat.descripcion}` : null}
              </>
            ) : (
              <span className="text-muted-foreground">{t.sinRespuesta}</span>
            )}
          </Dato>
          <Dato etiqueta={t.ultimoError} testId="comprobante-ultimo-error">
            {c.ultimo_error ? <span className="font-mono text-[12px]">{c.ultimo_error}</span> : <span className="text-muted-foreground">{t.sinError}</span>}
          </Dato>
          <Dato etiqueta={t.archivos}>
            <ul className="grid gap-1">
              <li data-testid="comprobante-xml">
                {t.xml}: <span className={c.tiene_xml ? "text-success-foreground" : "text-muted-foreground"}>{c.tiene_xml ? t.guardado : t.noGuardado}</span>
              </li>
              <li data-testid="comprobante-cdr">
                {t.cdr}: <span className={c.tiene_cdr ? "text-success-foreground" : "text-muted-foreground"}>{c.tiene_cdr ? t.guardado : t.noGuardado}</span>
              </li>
            </ul>
            <p className="mt-2 text-[12px] text-muted-foreground">{t.ayudaArchivos}</p>
          </Dato>
        </dl>
      </section>
    </div>
  );
}

/**
 * La ficha de un comprobante (#251), solo lectura. Se llega desde la cola de errores y la verificación de integridad. Un id que no es un UUID ni siquiera llega al
 * backend; un 404 del backend es «no existe». Cualquier otro fallo muestra el error con «Reintentar». El 401 lo atiende el layout del panel.
 */
export default async function AdminComprobantePage({ params }: { params: Promise<{ id: string }> }) {
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
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <Link href="/admin/errores" className="inline-flex w-fit items-center gap-1 text-xs text-primary hover:underline">
          <ArrowLeftIcon className="size-3" />
          {t.volver}
        </Link>
        <h1 className="font-heading text-2xl">{t.titulo}</h1>
      </div>

      {resultado.comprobante ? (
        <Ficha c={resultado.comprobante} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{t.error}</span>
          <Link href={hrefDetalleComprobante(id)} className="font-medium underline">
            {t.reintentar}
          </Link>
        </div>
      )}
    </div>
  );
}
