import Link from "next/link";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { porPaginaValido } from "khipu-design-system/lib/paginacion";
import { AvisoExperimental } from "@/components/guias/aviso-experimental";
import { GuiasTable } from "@/components/guias/guias-table";
import { filtrosDeGuiasDesdeParams, listarGuias } from "@/lib/api/guias";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.guias.pagina.titulo} · ${messages.app.nombre}` };
}

export default async function GuiasPage({
  searchParams,
}: {
  searchParams: Promise<{ pagina?: string; por_pagina?: string; estado?: string; tipo?: string; serie?: string; desde?: string; hasta?: string }>;
}) {
  const { pagina, por_pagina, ...resto } = await searchParams;
  const { access, empresaId } = await getServerSession();
  const t = (await getMessages()).guias.pagina;

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        {t.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.configurarEmpresa}
        </Link>
      </p>
    );
  }

  const paginaNum = Math.max(1, Number(pagina ?? 1) || 1);
  const porPagina = porPaginaValido(por_pagina);
  // Los filtros viven en la URL (compartible) y se aplican ya en el render del servidor.
  const filtros = filtrosDeGuiasDesdeParams(resto);
  const { datos, total } = await listarGuias(access, empresaId, { ...filtros, pagina: paginaNum, porPagina });

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      {/* El título de la página (h1) lo pone la barra superior con la miga. */}
      <AvisoExperimental />
      <GuiasTable datos={datos} total={total} pagina={paginaNum} porPagina={porPagina} filtros={filtros} />
    </div>
  );
}
