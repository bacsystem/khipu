import type { Usuario } from "@/lib/api/auth";
import type { Empresa } from "@/lib/api/empresas";
import { messages } from "@/lib/messages";
import { EmpresaSelector } from "./empresa-selector";
import { LogoutButton } from "./logout-button";
import { SidebarNav } from "./sidebar-nav";

export function SidebarContent({
  usuario,
  empresas,
  activaId,
}: {
  usuario: Usuario;
  empresas: Empresa[];
  activaId?: string;
}) {
  return (
    <div className="flex h-full flex-col justify-between">
      <div className="grid gap-6">
        <span className="font-heading text-lg">{messages.app.nombre}</span>
        {empresas.length > 0 ? <EmpresaSelector empresas={empresas} activaId={activaId} /> : null}
        <SidebarNav />
      </div>
      <div className="grid gap-2 border-t border-sidebar-border pt-4">
        <span className="truncate text-sm text-sidebar-foreground/70">{usuario.email}</span>
        <LogoutButton />
      </div>
    </div>
  );
}
