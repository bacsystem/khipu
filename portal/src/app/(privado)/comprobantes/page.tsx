import Link from "next/link";
import { ComprobantesTable } from "@/components/comprobantes/comprobantes-table";
import { porPaginaValido } from "@/lib/paginacion";
import { filtrosDesdeParams, listarFacturas } from "@/lib/api/facturas";
import { listarSeries } from "@/lib/api/series";
import { getServerSession } from "@/lib/session-server";
import { Metrica } from "@/components/ui/metrica";

export default async function ComprobantesPage({
  searchParams,
}: {
  searchParams: Promise<{ pagina?: string; por_pagina?: string; estado?: string; desde?: string; hasta?: string; serie?: string }>;
}) {
  const { pagina, por_pagina, ...resto } = await searchParams;
  const { access, empresaId } = await getServerSession();
  const paginaNum = Number(pagina ?? 1) || 1;
  const porPagina = porPaginaValido(por_pagina);
  // Los filtros viven en la URL (compartible) y se aplican ya en el render del servidor (#6).
  const filtros = filtrosDesdeParams(resto);

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Primero registra una empresa para ver tus comprobantes.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
        </Link>
      </p>
    );
  }

  const [{ datos, total }, series] = await Promise.all([
    listarFacturas(access, empresaId, { ...filtros, pagina: paginaNum, porPagina }),
    listarSeries(access, empresaId).catch(() => []),
  ]);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section
        title="Métricas agregadas: próximamente (requieren un endpoint de resumen en la API)"
        className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5"
      >
        <Metrica etiqueta="Total facturado" ayuda="requiere endpoint de resumen" pendiente />
        <Metrica etiqueta="Emitidos en el período" ayuda="requiere endpoint de resumen" pendiente />
        <Metrica etiqueta="Aceptados con CDR" ayuda="requiere endpoint de resumen" pendiente />
        <Metrica etiqueta="Atención requerida" ayuda="requiere endpoint de resumen" pendiente />
      </section>

      <ComprobantesTable inicial={{ datos, total }} pagina={paginaNum} porPagina={porPagina} filtros={filtros} series={series.map((s) => s.serie)} />
    </div>
  );
}
