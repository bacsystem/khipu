import Link from "next/link";
import { ComprobantesTable } from "@/components/comprobantes/comprobantes-table";
import { porPaginaValido } from "@/lib/paginacion";
import { listarFacturas } from "@/lib/api/facturas";
import { getServerSession } from "@/lib/session-server";

function Metrica({ etiqueta, ayuda }: { etiqueta: string; ayuda: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5 opacity-60">
      <span className="truncate text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{etiqueta}</span>
      <span className="font-mono text-lg font-semibold tracking-tight text-foreground">—</span>
      <span className="truncate text-[11px] text-muted-foreground">{ayuda}</span>
    </div>
  );
}

export default async function ComprobantesPage({
  searchParams,
}: {
  searchParams: Promise<{ pagina?: string; por_pagina?: string }>;
}) {
  const { pagina, por_pagina } = await searchParams;
  const { access, empresaId } = await getServerSession();
  const paginaNum = Number(pagina ?? 1) || 1;
  const porPagina = porPaginaValido(por_pagina);

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

  const { datos, total } = await listarFacturas(access, empresaId, { pagina: paginaNum, porPagina });

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section
        title="Métricas agregadas: próximamente (requieren un endpoint de resumen en la API)"
        className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5"
      >
        <Metrica etiqueta="Total facturado" ayuda="requiere endpoint de resumen" />
        <Metrica etiqueta="Emitidos en el período" ayuda="requiere endpoint de resumen" />
        <Metrica etiqueta="Aceptados con CDR" ayuda="requiere endpoint de resumen" />
        <Metrica etiqueta="Atención requerida" ayuda="requiere endpoint de resumen" />
      </section>

      <ComprobantesTable inicial={{ datos, total }} pagina={paginaNum} porPagina={porPagina} />
    </div>
  );
}
