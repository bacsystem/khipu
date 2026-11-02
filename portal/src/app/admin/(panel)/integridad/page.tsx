import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import { redirect } from "next/navigation";
import { VerificarIntegridad } from "@/components/admin/verificar-integridad";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hoyLima } from "@/lib/formato";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.integridad };
}

/**
 * Verificación de integridad del almacenamiento (#198). La página no carga nada: el barrido lo pide el administrador desde el formulario, por el BFF. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminIntegridadPage() {
  const messages = await getMessages();
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={messages.admin.integridad.titulo} descripcion={messages.admin.integridad.descripcion} />
      <VerificarIntegridad hoy={hoyLima()} />
    </div>
  );
}
