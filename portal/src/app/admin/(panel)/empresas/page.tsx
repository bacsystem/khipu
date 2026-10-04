import Link from "next/link";
import { redirect } from "next/navigation";
import { EmpresasTabla } from "@/components/admin/empresas-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefEmpresas, hrefEmpresasSiFueraDeRango, listarEmpresasAdmin, paramsEmpresasDesdeUrl } from "@/lib/api/admin-empresas";
import { messages } from "@/lib/messages";

export const metadata = { title: "Empresas · Backoffice" };

/**
 * Listado de empresas (#185). Los filtros viven en la URL y se aplican ya en el render del servidor. No captura el 401 por separado: el
 * layout del panel valida la sesión del administrador en cada render, así que un token vencido ya redirigió a /admin/login.
 */
export default async function AdminEmpresasPage({
  searchParams,
}: {
  searchParams: Promise<{ entorno?: string; certificado?: string; bajas?: string; pagina?: string; por_pagina?: string }>;
}) {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsEmpresasDesdeUrl(await searchParams);
  const resultado = await listarEmpresasAdmin(access, params).then(
    (pagina) => ({ pagina }),
    () => ({ pagina: null }),
  );
  // Fuera del `.then`/catch de arriba: `redirect` lanza, y no debe confundirse con un fallo del backend.
  const corregida = resultado.pagina ? hrefEmpresasSiFueraDeRango(params, resultado.pagina.total) : null;
  if (corregida) redirect(corregida);

  const t = messages.admin.empresas;
  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descripcion}</p>
      </div>

      {resultado.pagina ? (
        <EmpresasTabla datos={resultado.pagina.datos} total={resultado.pagina.total} params={params} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{t.error}</span>
          <Link href={hrefEmpresas(params)} className="font-medium underline">
            {t.reintentar}
          </Link>
        </div>
      )}
    </div>
  );
}
