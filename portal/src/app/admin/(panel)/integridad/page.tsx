import { redirect } from "next/navigation";
import { VerificarIntegridad } from "@/components/admin/verificar-integridad";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hoyLima } from "@/lib/formato";
import { messages } from "@/lib/messages";

export const metadata = { title: "Integridad · Backoffice" };

/**
 * Verificación de integridad del almacenamiento (#198). La página no carga nada: el barrido lo pide el administrador desde el formulario, por el BFF. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminIntegridadPage() {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{messages.admin.integridad.titulo}</h1>
        <p className="max-w-3xl text-sm text-muted-foreground">{messages.admin.integridad.descripcion}</p>
      </div>
      <VerificarIntegridad hoy={hoyLima()} />
    </div>
  );
}
