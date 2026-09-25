"use client";

import { ChevronsUpDownIcon, LogOutIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Menu, MenuContent, MenuItem, MenuSeparator, MenuTrigger } from "@/components/ui/menu";
import { ThemeToggle } from "@/components/nav/theme-toggle";
import type { Administrador } from "@/lib/api/admin-auth";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

function iniciales(email: string): string {
  const local = email.split("@")[0] ?? email;
  return local.slice(0, 2).toUpperCase();
}

/** Menú del administrador en el sidebar: solo tema y cierre de sesión — sin cambio de contraseña ni 2FA todavía (#177). */
export function AdminPerfil({ administrador }: { administrador: Administrador }) {
  const router = useRouter();
  const [saliendo, setSaliendo] = useState(false);

  async function salir() {
    setSaliendo(true);
    await fetch("/api/admin/auth/logout", { method: "POST" });
    router.push("/admin/login");
    router.refresh();
  }

  return (
    <Menu>
      <MenuTrigger
        aria-label="Menú de administrador"
        className={cn(
          "flex w-full min-w-0 cursor-pointer items-center justify-between gap-2 overflow-hidden rounded-lg border border-border/60 bg-muted/80 p-2 text-left transition-colors outline-none select-none",
          "hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring/50 data-popup-open:bg-secondary",
        )}
      >
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground shadow-xs">
            {iniciales(administrador.email)}
          </div>
          <div className="min-w-0 overflow-hidden">
            <p className="truncate text-[12px] leading-tight font-medium text-foreground">{administrador.email.split("@")[0]}</p>
            <p className="truncate font-mono text-[10px] leading-tight text-muted-foreground">{administrador.email}</p>
          </div>
        </div>
        <ChevronsUpDownIcon className="size-4 shrink-0 text-muted-foreground/70" />
      </MenuTrigger>

      <MenuContent side="top" align="start" className="w-(--anchor-width) min-w-0">
        <div className="px-2 pt-1.5 pb-1 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">Tema</div>
        <div className="px-0.5 pb-1.5">
          <ThemeToggle conTexto className="h-8" />
        </div>
        <MenuSeparator />
        <MenuItem variant="destructive" disabled={saliendo} onClick={salir}>
          <LogOutIcon />
          {messages.comun.cerrarSesion}
        </MenuItem>
      </MenuContent>
    </Menu>
  );
}
