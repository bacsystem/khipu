import Link from "next/link";
import { redirect } from "next/navigation";
import { ColaDeErroresTabla } from "@/components/admin/cola-de-errores-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefErrores, hrefSiFueraDeRango, listarErrores, paramsErroresDesdeUrl } from "@/lib/api/admin-errores";
import { messages } from "@/lib/messages";

export const metadata = { title: "Errores · Backoffice" };

/**
 * La cola global de errores (#196). El tipo de error, la empresa, la búsqueda y la página viven en la URL y se aplican ya en el render del servidor. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminErroresPage({ searchParams }: { searchParams: Promise<{ clase?: string; empresa_id?: string; q?: string; pagina?: string; por_pagina?: string }> }) {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsErroresDesdeUrl(await searchParams);
  const resultado = await listarErrores(access, params).then(
    (pagina) => ({ pagina }),
    () => ({ pagina: null }),
  );
  // Fuera del `.then`/catch de arriba: `redirect` lanza, y no debe confundirse con un fallo del backend.
  const corregida = resultado.pagina ? hrefSiFueraDeRango(params, resultado.pagina.total) : null;
  if (corregida) redirect(corregida);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{messages.admin.errores.titulo}</h1>
        <p className="max-w-4xl text-sm text-muted-foreground">{messages.admin.errores.descripcion}</p>
      </div>

      {resultado.pagina ? (
        <ColaDeErroresTabla errores={resultado.pagina.errores} total={resultado.pagina.total} params={params} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{messages.admin.errores.error}</span>
          <Link href={hrefErrores(params)} className="font-medium underline">
            {messages.admin.errores.volverACargar}
          </Link>
        </div>
      )}
    </div>
  );
}
