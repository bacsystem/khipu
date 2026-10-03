import { redirect } from "next/navigation";
import { AltaAsistidaForm } from "@/components/admin/alta-asistida-form";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.admin.alta.titulo} · Backoffice` };

/**
 * Alta asistida de un cliente (#188). Como el listado de cuentas, no captura el 401 por separado: el layout del panel valida la
 * sesión del administrador (`/me`) en cada render, y la ruta del BFF que recibe el formulario vuelve a exigirla.
 */
export default async function AdminNuevaCuentaPage() {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <h1 className="font-heading text-2xl">{messages.admin.alta.titulo}</h1>
      <AltaAsistidaForm />
    </div>
  );
}
