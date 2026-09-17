import type { Usuario } from "@/lib/api/auth";
import type { Empresa } from "@/lib/api/empresas";
import { EmpresaSelector } from "./empresa-selector";
import { LogoMarca } from "./logo";
import { PerfilUsuario } from "./perfil-usuario";
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
    <div className="flex h-full w-full min-w-0 flex-col justify-between overflow-hidden select-none">
      <div className="flex w-full min-w-0 flex-col">
        <div className="flex h-14 w-full min-w-0 shrink-0 items-center justify-between gap-2 overflow-hidden border-b border-border/60 px-4">
          <LogoMarca />
          <span className="inline-flex shrink-0 items-center rounded border border-border/60 bg-secondary px-1.5 py-0.5 font-mono text-[10px] font-medium whitespace-nowrap text-secondary-foreground/70">
            UBL 2.1
          </span>
        </div>

        {empresas.length > 0 ? (
          <div className="px-3 pt-3 pb-2">
            <EmpresaSelector empresas={empresas} activaId={activaId} />
          </div>
        ) : null}

        <SidebarNav />
      </div>

      <div className="flex w-full min-w-0 flex-col gap-2.5 border-t border-border/60 p-3">
        <div
          title="Plan y consumo mensual: próximamente"
          aria-disabled="true"
          className="grid w-full cursor-not-allowed grid-cols-1 gap-1.5 overflow-hidden rounded-lg border border-border/60 bg-muted/90 p-2.5 opacity-60"
        >
          <div className="flex items-center justify-between gap-2 text-[11px]">
            <span className="truncate font-medium text-foreground/80">Plan y consumo</span>
            <span className="shrink-0 font-mono text-[10px] whitespace-nowrap text-muted-foreground">— / —</span>
          </div>
          <div className="h-1 w-full overflow-hidden rounded-full bg-border">
            <div className="h-full w-0 rounded-full bg-primary" />
          </div>
        </div>

        {/* El menú de usuario incluye el tema (en móvil el TopBar lo oculta), el cambio de contraseña y el cierre de sesión. */}
        <PerfilUsuario usuario={usuario} />
      </div>
    </div>
  );
}
