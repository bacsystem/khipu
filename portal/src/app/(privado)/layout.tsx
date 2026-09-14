import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { MobileNav } from "@/components/nav/mobile-nav";
import { SidebarContent } from "@/components/nav/sidebar-content";
import { me } from "@/lib/api/auth";
import { listarEmpresas } from "@/lib/api/empresas";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export default async function PrivadoLayout({ children }: { children: ReactNode }) {
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");

  const [usuario, empresas] = await Promise.all([me(access), listarEmpresas(access)]);
  const activa = empresas.find((empresa) => empresa.id === empresaId) ?? empresas[0];

  return (
    <div className="grid min-h-screen md:grid-cols-[240px_1fr]">
      <aside className="hidden bg-sidebar px-4 py-6 text-sidebar-foreground md:block">
        <SidebarContent usuario={usuario} empresas={empresas} activaId={activa?.id} />
      </aside>

      <div className="flex flex-col">
        <header className="flex items-center justify-between border-b border-border bg-card px-4 py-3 md:hidden">
          <span className="font-heading text-lg">{messages.app.nombre}</span>
          <MobileNav usuario={usuario} empresas={empresas} activaId={activa?.id} />
        </header>

        {activa?.entorno === "BETA" ? (
          <div className="border-b border-warning bg-warning px-4 py-2 text-center text-sm text-warning-foreground md:px-8">
            Entorno BETA de SUNAT: los comprobantes emitidos aquí no tienen validez tributaria.
          </div>
        ) : null}

        <main className="flex-1 px-4 py-6 md:px-8 md:py-10">{children}</main>
      </div>
    </div>
  );
}
