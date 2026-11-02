import { FormulariosDeEscritura } from "@/lib/solo-lectura";
import { EnlaceVolver } from "khipu-design-system/patrones/cabecera-pagina";
import { notFound, redirect } from "next/navigation";
import { NotaForm } from "@/components/comprobantes/nota-form";
import { admiteNotas, etiquetasTipoDoc, obtenerFactura } from "@/lib/api/facturas";
import { listarSeries } from "@/lib/api/series";
import { ApiError } from "@/lib/api/types";
import { CONTENEDOR_PAGINA, TARJETA } from "@/lib/estilos";
import { TITULO_REGISTRO } from "@/lib/detalle";
import { formatearMonto } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import type { Metadata } from "next";
import { getLocale, getMessages } from "next-intl/server";
import { interpolar } from "@/i18n/mensajes";
import type { Locale } from "@/i18n/config";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.comprobantes.notaPagina.titulo} · ${messages.app.nombre}` };
}

export default async function NotaPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");
  if (!empresaId) redirect("/onboarding");

  const [factura, series] = await Promise.all([
    obtenerFactura(access, empresaId, id).catch((e: unknown) => {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }),
    listarSeries(access, empresaId),
  ]);
  if (!factura) notFound();
  // Solo una factura o boleta aceptada admite notas (#294); sobre cualquier otro comprobante se vuelve al detalle.
  if (!admiteNotas(factura)) redirect(`/comprobantes/${id}`);
  const messages = await getMessages();
  const locale = (await getLocale()) as Locale;
  const t = messages.comprobantes.notaPagina;
  const boleta = factura.tipo === "03";
  const etiquetasDoc = etiquetasTipoDoc(messages);
  const receptor = factura.receptor;
  // La nota no pide comprador: el backend copia el de la boleta, también el «sin documento» (tipo y número «-»).
  const comprador = receptor
    ? `${receptor.razon_social} (${receptor.tipo_doc === "-" ? etiquetasDoc["-"].toLowerCase() : `${etiquetasDoc[receptor.tipo_doc] ?? t.docGenerico} ${receptor.num_doc}`})`
    : null;

  return (
    <div className={CONTENEDOR_PAGINA}>
      <div>
        <EnlaceVolver href={`/comprobantes/${id}`}>{boleta ? t.volverABoleta : t.volverAFactura}</EnlaceVolver>
      </div>
      <section className={cn(TARJETA, "p-4")}>
        {/* El h1 de la página lo pone el top bar (la miga); este es el título de la tarjeta. */}
        <h2 className={TITULO_REGISTRO}>{interpolar(t.emitirSobre, { numero: `${factura.serie}-${factura.numero}` })}</h2>
        <p className="mt-1 text-control text-muted-foreground" data-testid="nota-comprador">
          {comprador} · {formatearMonto(factura.moneda, factura.totales.total, locale)} · {boleta ? t.tomaDeBoleta : t.tomaDeFactura}
        </p>
        <div className="mt-6">
          <FormulariosDeEscritura>
            <NotaForm factura={factura} series={series} />
          </FormulariosDeEscritura>
        </div>
      </section>
    </div>
  );
}
