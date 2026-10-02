"use client";

import { PlusIcon } from "lucide-react";
import { usePathname } from "next/navigation";
import type { Administrador } from "@/lib/api/admin-auth";
import { migaAdmin } from "@/lib/admin-migas";
import { messages } from "@/lib/messages";
import { AdminMobileNav } from "./admin-mobile-nav";

const ACCION_PRINCIPAL_DESHABILITADA =
  "flex h-8 items-center gap-1.5 rounded-lg bg-foreground px-3 text-[12px] font-medium whitespace-nowrap text-background shadow-xs disabled:cursor-not-allowed disabled:opacity-60";

/**
 * Cabecera del backoffice, con el mismo esqueleto que la del portal de clientes: menú en móvil, miga de ubicación y la acción
 * principal de la página. La miga NO es un encabezado: cada página del backoffice ya trae su propio `h1`.
 */
export function AdminTopBar({ administrador }: { administrador: Administrador }) {
  const pathname = usePathname();
  const miga = migaAdmin(pathname);
  const t = messages.admin.topbar;

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center justify-between gap-3 border-b border-border/80 bg-card/80 px-4 backdrop-blur md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <div className="md:hidden">
          <AdminMobileNav administrador={administrador} />
        </div>

        {miga ? (
          <nav aria-label={t.ubicacion} className="flex min-w-0 shrink items-center gap-1.5 overflow-hidden text-[13px] whitespace-nowrap">
            <span className="text-muted-foreground/80">{miga.seccion}</span>
            <span aria-hidden="true" className="text-muted-foreground/40">
              /
            </span>
            <span aria-current="page" className="truncate font-heading font-semibold text-foreground">
              {miga.pagina}
            </span>
          </nav>
        ) : null}
      </div>

      <div className="flex shrink-0 items-center gap-2.5">
        {miga?.accion === "nuevaCuenta" ? (
          <button disabled title={t.nuevaCuentaProximamente} className={ACCION_PRINCIPAL_DESHABILITADA}>
            <PlusIcon className="size-4" />
            {t.nuevaCuenta}
          </button>
        ) : null}
      </div>
    </header>
  );
}
