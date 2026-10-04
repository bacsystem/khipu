import Link from "next/link";
import { ComprobantesTable } from "@/components/comprobantes/comprobantes-table";
import { ResumenDeComprobantes } from "@/components/comprobantes/resumen-de-comprobantes";
import { porPaginaValido } from "@/lib/paginacion";
import { filtrosDesdeParams, listarFacturas, periodoDelResumen, resumirFacturas } from "@/lib/api/facturas";
import { hoyLima } from "@/lib/formato";
import { listarSeries } from "@/lib/api/series";
import { getServerSession } from "@/lib/session-server";

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

  // La franja de métricas resume el período de los filtros de fecha (o el mes en curso) y, si no carga, no tumba la lista: se muestra atenuada y lo dice.
  const periodo = periodoDelResumen(filtros, hoyLima());
  const [{ datos, total }, series, resumen] = await Promise.all([
    listarFacturas(access, empresaId, { ...filtros, pagina: paginaNum, porPagina }),
    listarSeries(access, empresaId).catch(() => []),
    resumirFacturas(access, empresaId, periodo).catch(() => null),
  ]);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <ResumenDeComprobantes resumen={resumen} periodo={periodo} />

      <ComprobantesTable empresaId={empresaId} inicial={{ datos, total }} pagina={paginaNum} porPagina={porPagina} filtros={filtros} series={series.map((s) => s.serie)} />
    </div>
  );
}
