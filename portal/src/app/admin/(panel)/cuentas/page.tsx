import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import { redirect } from "next/navigation";
import Link from "next/link";
import { CuentasTabla } from "@/components/admin/cuentas-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefCuentas, hrefSiFueraDeRango, listarCuentasAdmin, paramsCuentasDesdeUrl } from "@/lib/api/admin-cuentas";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.cuentas };
}

/**
 * Listado de cuentas (#180). Los filtros viven en la URL y se aplican ya en el render del servidor. No captura el 401
 * por separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido
 * ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminCuentasPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; bajas?: string; pagina?: string; por_pagina?: string }>;
}) {
  const messages = await getMessages();
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
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={messages.admin.cuentas.titulo} descripcion={messages.admin.cuentas.descripcion} />

      {resultado.pagina ? (
        <CuentasTabla datos={resultado.pagina.datos} total={resultado.pagina.total} params={params} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefCuentas(params)} className="font-medium underline">
              {messages.admin.cuentas.reintentar}
            </Link>
          }
        >
          {messages.admin.cuentas.error}
        </Alerta>
      )}
    </div>
  );
}
