import type { Usuario } from "@/lib/api/auth";
import type { MiCuenta } from "@/lib/api/cuenta";
import type { Empresa } from "@/lib/api/empresas";
import { EmpresaSelector } from "./empresa-selector";
import { LogoMarca } from "./logo";
import { PerfilUsuario } from "./perfil-usuario";
import { PlanYConsumo } from "./plan-y-consumo";
import { SidebarNav } from "./sidebar-nav";

export function SidebarContent({
  usuario,
  cuenta,
  empresas,
  activaId,
}: {
  usuario: Usuario;
  /** La cuenta con su plan y su consumo (C1); `null` si no se pudo leer: el panel carga igual. */
  cuenta: MiCuenta | null;
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
        <PlanYConsumo cuenta={cuenta} />
        {/* El menú de usuario incluye el tema (en móvil el TopBar lo oculta), el cambio de contraseña y el cierre de sesión. */}
        <PerfilUsuario usuario={usuario} nombreCuenta={cuenta?.nombre ?? null} />
      </div>
    </div>
  );
}
