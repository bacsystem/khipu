import { redirect } from "next/navigation";
import Link from "next/link";
import { CuentasTabla } from "@/components/admin/cuentas-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefCuentas, hrefSiFueraDeRango, listarCuentasAdmin, paramsCuentasDesdeUrl } from "@/lib/api/admin-cuentas";
import { messages } from "@/lib/messages";

export const metadata = { title: "Cuentas · Backoffice" };

/**
 * Listado de cuentas (#180). Los filtros viven en la URL y se aplican ya en el render del servidor. No captura el 401
 * por separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido
 * ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminCuentasPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; pagina?: string; por_pagina?: string }>;
}) {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsCuentasDesdeUrl(await searchParams);
  const resultado = await listarCuentasAdmin(access, params).then(
    (pagina) => ({ pagina }),
    () => ({ pagina: null }),
  );
  // Fuera del `.then`/catch de arriba: `redirect` lanza, y no debe confundirse con un fallo del backend.
  const corregida = resultado.pagina ? hrefSiFueraDeRango(params, resultado.pagina.total) : null;
  if (corregida) redirect(corregida);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{messages.admin.cuentas.titulo}</h1>
        <p className="text-sm text-muted-foreground">{messages.admin.cuentas.descripcion}</p>
      </div>

      {resultado.pagina ? (
        <CuentasTabla datos={resultado.pagina.datos} total={resultado.pagina.total} params={params} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{messages.admin.cuentas.error}</span>
          <Link href={hrefCuentas(params)} className="font-medium underline">
            {messages.admin.cuentas.reintentar}
          </Link>
        </div>
      )}
    </div>
  );
}
