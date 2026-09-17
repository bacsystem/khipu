import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { SidebarContent } from "@/components/nav/sidebar-content";
import { TopBar } from "@/components/nav/top-bar";
import { me } from "@/lib/api/auth";
import { apiBaseUrl } from "@/lib/api/client";
import { listarEmpresas } from "@/lib/api/empresas";
import { getServerSession } from "@/lib/session-server";

export default async function PrivadoLayout({ children }: { children: ReactNode }) {
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");

  const [usuario, empresas] = await Promise.all([me(access), listarEmpresas(access)]);
  if (empresas.length === 0) redirect("/onboarding");
  const activa = empresas.find((empresa) => empresa.id === empresaId) ?? empresas[0];

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 overflow-x-hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:sticky md:top-0 md:block md:h-screen md:overflow-y-auto">
        <SidebarContent usuario={usuario} empresas={empresas} activaId={activa?.id} />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar entorno={activa.entorno} usuario={usuario} empresas={empresas} activaId={activa?.id} apiBaseUrl={apiBaseUrl()} />
        <main className="min-w-0 flex-1 overflow-x-auto p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
