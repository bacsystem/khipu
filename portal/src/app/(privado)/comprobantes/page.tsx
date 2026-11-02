import Link from "next/link";
import { ComprobantesTable } from "@/components/comprobantes/comprobantes-table";
import { GuiaParaEmitir } from "@/components/empresa/guia-para-emitir";
import { ResumenDeComprobantes } from "@/components/comprobantes/resumen-de-comprobantes";
import { porPaginaValido } from "khipu-design-system/lib/paginacion";
import { filtrosDesdeParams, listarFacturas, periodoDelResumen, resumirFacturas } from "@/lib/api/facturas";
import { hoyLima } from "@/lib/formato";
import { obtenerEmpresaActual } from "@/lib/api/empresas";
import { listarSeries } from "@/lib/api/series";
import { pasosParaEmitir } from "@/lib/empresa/guia-emision";
import { getServerSession } from "@/lib/session-server";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.comprobantes.pagina.titulo} · ${messages.app.nombre}` };
}

export default async function ComprobantesPage({
  searchParams,
}: {
  searchParams: Promise<{ pagina?: string; por_pagina?: string; estado?: string; desde?: string; hasta?: string; serie?: string; tipo?: string; cliente?: string; numero?: string }>;
}) {
  const { pagina, por_pagina, ...resto } = await searchParams;
  const { access, empresaId } = await getServerSession();
  const paginaNum = Number(pagina ?? 1) || 1;
  const porPagina = porPaginaValido(por_pagina);
  // Los filtros viven en la URL (compartible) y se aplican ya en el render del servidor (#6).
  const filtros = filtrosDesdeParams(resto);

  if (!access || !empresaId) {
    const t = (await getMessages()).comprobantes.pagina;
    return (
      <p className="text-cuerpo text-muted-foreground">
        {t.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.configurarEmpresa}
        </Link>
      </p>
    );
  }

  // La franja de métricas resume el período de los filtros de fecha (o el mes en curso) y, si no carga, no tumba la lista: se muestra atenuada y lo dice.
  const periodo = periodoDelResumen(filtros, hoyLima());
  const [{ datos, total }, series, resumen, empresa, messages] = await Promise.all([
    listarFacturas(access, empresaId, { ...filtros, pagina: paginaNum, porPagina }),
    // `null` = no se pudo leer: la guía no afirma que falten series y la tabla sigue sin el filtro por serie.
    listarSeries(access, empresaId).catch(() => null),
    resumirFacturas(access, empresaId, periodo).catch(() => null),
    // La guía es una ayuda: si la empresa no se puede leer, la lista de comprobantes sigue sin ella.
    obtenerEmpresaActual(access, empresaId).catch(() => null),
    getMessages(),
  ]);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      {empresa ? <GuiaParaEmitir pasos={pasosParaEmitir(empresa, series, hoyLima(), messages)} /> : null}

      <ResumenDeComprobantes resumen={resumen} periodo={periodo} />

      <ComprobantesTable empresaId={empresaId} inicial={{ datos, total }} pagina={paginaNum} porPagina={porPagina} filtros={filtros} series={(series ?? []).map((s) => s.serie)} />
    </div>
  );
}
