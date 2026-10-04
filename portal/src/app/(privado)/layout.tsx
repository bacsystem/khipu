import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { SidebarContent } from "@/components/nav/sidebar-content";
import { TopBar } from "@/components/nav/top-bar";
import { me } from "@/lib/api/auth";
import { apiPublicUrl } from "@/lib/api/client";
import { esCuentaSuspendida, RUTA_CUENTA_SUSPENDIDA } from "@/lib/api/cuenta-suspendida";
import { listarEmpresas } from "@/lib/api/empresas";
import { getServerSession } from "@/lib/session-server";

export default async function PrivadoLayout({ children }: { children: ReactNode }) {
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");

  // Una cuenta suspendida (#182) conserva su sesión pero el backend le niega todo: se le explica en vez de dejar que la página falle sin decir por qué.
  // El `redirect` va fuera del `.then`, porque lanza y no debe confundirse con un fallo del backend.
  const cargado = await Promise.all([me(access), listarEmpresas(access)]).then(
    (datos) => ({ datos }),
    (error: unknown) => ({ error }),
  );
  if ("error" in cargado) {
    if (esCuentaSuspendida(cargado.error)) redirect(RUTA_CUENTA_SUSPENDIDA);
    throw cargado.error;
  }
  const [usuario, empresas] = cargado.datos;
  if (empresas.length === 0) redirect("/onboarding");
  const activa = empresas.find((empresa) => empresa.id === empresaId) ?? empresas[0];

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 overflow-x-hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:sticky md:top-0 md:block md:h-screen md:overflow-y-auto">
        <SidebarContent usuario={usuario} empresas={empresas} activaId={activa?.id} />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar entorno={activa.entorno} usuario={usuario} empresas={empresas} activaId={activa?.id} apiBaseUrl={apiPublicUrl()} />
        <main className="min-w-0 flex-1 overflow-x-auto p-4 md:p-6">
          {/* Con el correo sin verificar (#22) el backend rechaza toda escritura: se dice antes de que algo falle sin explicación. */}
          {usuario.correo_verificado ? null : (
            <div className="mb-4">
              <RevisaTuCorreo email={usuario.email} />
            </div>
          )}
          {children}
        </main>
      </div>
    </div>
  );
}
