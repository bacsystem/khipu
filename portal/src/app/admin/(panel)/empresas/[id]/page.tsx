import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { ArrowLeftIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { EmpresaDetalle } from "@/components/admin/empresa-detalle";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { esIdDeEmpresa, hrefDetalleEmpresa, obtenerEmpresaAdmin } from "@/lib/api/admin-empresa-detalle";
import { ApiError } from "@/lib/api/types";

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
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <Link href="/admin/empresas" className="inline-flex w-fit items-center gap-1 text-etiqueta text-primary hover:underline">
          <ArrowLeftIcon weight={PESO_ICONO} className="size-3" />
          {t.volver}
        </Link>
        <h1 className="font-heading text-titulo-pagina">{resultado.empresa?.razon_social ?? messages.admin.empresas.titulo}</h1>
      </div>

      {resultado.empresa ? (
        <EmpresaDetalle empresa={resultado.empresa} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive-suave px-4 py-3 text-cuerpo text-destructive-texto">
          <span>{t.error}</span>
          <Link href={hrefDetalleEmpresa(id)} className="font-medium underline">
            {messages.admin.empresas.reintentar}
          </Link>
        </div>
      )}
    </div>
  );
}
