import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina, EnlaceVolver } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { EmpresaDetalle } from "@/components/admin/empresa-detalle";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { esIdDeEmpresa, hrefDetalleEmpresa, obtenerEmpresaAdmin } from "@/lib/api/admin-empresa-detalle";
import { ApiError } from "@/lib/api/types";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.empresa };
}

/**
 * Detalle de una empresa (#186), solo lectura. Un id que no es un UUID ni siquiera llega al backend; un 404 del backend es «no existe».
 * Cualquier otro fallo muestra el error con «Reintentar» (la misma página). El 401 lo atiende el layout del panel, como en el listado.
 */
export default async function AdminEmpresaPage({ params }: { params: Promise<{ id: string }> }) {
  const messages = await getMessages();
  const { id } = await params;
  if (!esIdDeEmpresa(id)) notFound();

  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const resultado = await obtenerEmpresaAdmin(access, id).then(
    (empresa) => ({ empresa, error: null }),
    (error: unknown) => ({ empresa: null, error }),
  );
  // Fuera del `.then`: `notFound` lanza, y no debe confundirse con un fallo del backend.
  if (resultado.error instanceof ApiError && resultado.error.status === 404) notFound();

  const t = messages.admin.empresaDetalle;
  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina
        volver={<EnlaceVolver href="/admin/empresas">{t.volver}</EnlaceVolver>}
        titulo={resultado.empresa?.razon_social ?? messages.admin.empresas.titulo}
      />

      {resultado.empresa ? (
        <EmpresaDetalle empresa={resultado.empresa} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefDetalleEmpresa(id)} className="font-medium underline">
              {messages.admin.empresas.reintentar}
            </Link>
          }
        >
          {t.error}
        </Alerta>
      )}
    </div>
  );
}
