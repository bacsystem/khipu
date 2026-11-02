import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ColaDeErroresTabla } from "@/components/admin/cola-de-errores-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefErrores, hrefSiFueraDeRango, listarErrores, paramsErroresDesdeUrl } from "@/lib/api/admin-errores";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.errores };
}

/**
 * La cola global de errores (#196). El tipo de error, la empresa, la búsqueda y la página viven en la URL y se aplican ya en el render del servidor. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminErroresPage({ searchParams }: { searchParams: Promise<{ clase?: string; empresa_id?: string; q?: string; pagina?: string; por_pagina?: string }> }) {
  const messages = await getMessages();
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
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={messages.admin.errores.titulo} descripcion={messages.admin.errores.descripcion} />

      {resultado.pagina ? (
        <ColaDeErroresTabla errores={resultado.pagina.errores} total={resultado.pagina.total} params={params} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefErrores(params)} className="font-medium underline">
              {messages.admin.errores.volverACargar}
            </Link>
          }
        >
          {messages.admin.errores.error}
        </Alerta>
      )}
    </div>
  );
}
