import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import { redirect } from "next/navigation";
import { PlanesTabla } from "@/components/admin/planes-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { listarPlanesAdmin } from "@/lib/api/admin-planes";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.planes };
}

/**
 * Planes (#190): lo que se vende, con su precio, sus límites y cuántas cuentas lo tienen. No captura el 401 por separado: el layout del panel valida la sesión
 * del administrador en cada render, así que un token vencido ya redirigió a /admin/login.
 */
export default async function AdminPlanesPage() {
  const messages = await getMessages();
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const planes = await listarPlanesAdmin(access).catch(() => null);

  const t = messages.admin.planes;
  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={t.titulo} descripcion={t.descripcion} />

      {planes ? (
        <PlanesTabla planes={planes} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href="/admin/planes" className="font-medium underline">
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
