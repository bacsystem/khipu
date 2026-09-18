import { ArrowLeftIcon } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { NotaForm } from "@/components/comprobantes/nota-form";
import { admiteNotas, obtenerFactura } from "@/lib/api/facturas";
import { listarSeries } from "@/lib/api/series";
import { ApiError } from "@/lib/api/types";
import { BOTON_SECUNDARIO, TARJETA } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

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
  // Solo una factura aceptada admite notas; sobre cualquier otro comprobante se vuelve al detalle.
  if (!admiteNotas(factura)) redirect(`/comprobantes/${id}`);

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <Link href={`/comprobantes/${id}`} className={cn(BOTON_SECUNDARIO, "h-auto px-2.5 py-1.5 text-xs")}>
          <ArrowLeftIcon className="size-3.5" />
          Volver a la factura
        </Link>
      </div>
      <section className={cn(TARJETA, "p-6")}>
        <h1 className="font-heading text-xl font-bold tracking-tight text-foreground">Emitir nota sobre {factura.serie}-{factura.numero}</h1>
        <p className="mt-1 text-[13px] text-muted-foreground">
          {factura.receptor?.razon_social} · {formatearMonto(factura.moneda, factura.totales.total)} · la nota toma el cliente y la moneda de la factura y se envía a SUNAT al emitirla.
        </p>
        <div className="mt-6">
          <NotaForm factura={factura} series={series} />
        </div>
      </section>
    </div>
  );
}
