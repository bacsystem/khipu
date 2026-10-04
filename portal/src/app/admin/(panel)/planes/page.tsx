import Link from "next/link";
import { redirect } from "next/navigation";
import { PlanesTabla } from "@/components/admin/planes-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { listarPlanesAdmin } from "@/lib/api/admin-planes";
import { messages } from "@/lib/messages";

export const metadata = { title: "Planes · Backoffice" };

/**
 * Planes (#190): lo que se vende, con su precio, sus límites y cuántas cuentas lo tienen. No captura el 401 por separado: el layout del panel valida la sesión
 * del administrador en cada render, así que un token vencido ya redirigió a /admin/login.
 */
export default async function AdminPlanesPage() {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const planes = await listarPlanesAdmin(access).catch(() => null);

  const t = messages.admin.planes;
  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{t.titulo}</h1>
        <p className="max-w-3xl text-sm text-muted-foreground">{t.descripcion}</p>
      </div>

      {planes ? (
        <PlanesTabla planes={planes} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{t.error}</span>
          <Link href="/admin/planes" className="font-medium underline">
            {t.reintentar}
          </Link>
        </div>
      )}
    </div>
  );
}
