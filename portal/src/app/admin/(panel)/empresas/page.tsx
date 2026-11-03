import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import { redirect } from "next/navigation";
import { EmpresasTabla } from "@/components/admin/empresas-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefEmpresas, hrefEmpresasSiFueraDeRango, listarEmpresasAdmin, paramsEmpresasDesdeUrl } from "@/lib/api/admin-empresas";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.empresas };
}

/**
 * Listado de empresas (#185). Los filtros viven en la URL y se aplican ya en el render del servidor. No captura el 401 por separado: el
 * layout del panel valida la sesión del administrador en cada render, así que un token vencido ya redirigió a /admin/login.
 */
export default async function AdminEmpresasPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; entorno?: string; certificado?: string; bajas?: string; pagina?: string; por_pagina?: string }>;
}) {
  const messages = await getMessages();
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
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={t.titulo} descripcion={t.descripcion} />

      {resultado.pagina ? (
        <EmpresasTabla datos={resultado.pagina.datos} total={resultado.pagina.total} params={params} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefEmpresas(params)} className="font-medium underline">
              {t.reintentar}
            </Link>
          }
        >
          {t.error}
        </Alerta>
      )}
    </div>
  );
}
