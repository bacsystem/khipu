"use client";

import { Select as SelectPrimitive } from "@base-ui/react/select";
import { CheckIcon, ChevronsUpDownIcon } from "lucide-react";
import { useTransition } from "react";
import { seleccionarEmpresa } from "@/app/actions";
import { SelectContent } from "@/components/ui/select";
import type { Empresa } from "@/lib/api/empresas";
import { cn } from "@/lib/utils";

function Avatar({ empresa, className }: { empresa: Empresa; className?: string }) {
  return (
    <div
      className={cn(
        "flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground shadow-xs",
        className,
      )}
    >
      {empresa.razon_social.slice(0, 1).toUpperCase()}
    </div>
  );
}

export function EmpresaSelector({ empresas, activaId }: { empresas: Empresa[]; activaId?: string }) {
  const [cambiando, startTransition] = useTransition();
  const activa = empresas.find((e) => e.id === activaId) ?? empresas[0];
  const items = Object.fromEntries(empresas.map((e) => [e.id, e.razon_social]));

  function cambiar(id: string | null) {
    if (!id || id === activa?.id) return;
    const formData = new FormData();
    formData.set("empresaId", id);
    startTransition(() => seleccionarEmpresa(formData));
  }

  return (
    <SelectPrimitive.Root items={items} value={activa?.id ?? null} onValueChange={cambiar}>
      <SelectPrimitive.Trigger
        aria-label="Cambiar de empresa"
        disabled={cambiando}
        className={cn(
          "flex w-full min-w-0 cursor-pointer items-center justify-between gap-2 overflow-hidden rounded-lg border border-border/60 bg-muted/80 p-2 text-left transition-colors outline-none select-none",
          "hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring/50 data-[popup-open]:bg-secondary disabled:opacity-60",
        )}
      >
        <div className="flex min-w-0 items-center gap-2.5">
          {activa ? <Avatar empresa={activa} /> : null}
          <div className="min-w-0 overflow-hidden">
            <p className="truncate text-[12px] leading-tight font-medium text-foreground">{activa?.razon_social}</p>
            <p className="truncate font-mono text-[10px] leading-tight text-muted-foreground">RUC {activa?.ruc}</p>
          </div>
        </div>
        <ChevronsUpDownIcon className="size-4 shrink-0 text-muted-foreground/70" />
      </SelectPrimitive.Trigger>

      <SelectContent align="start" alignItemWithTrigger={false} sideOffset={6} className="w-(--anchor-width) min-w-56 p-1">
        <div className="px-2 pt-1.5 pb-1 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">
          Cambiar de empresa
        </div>
        {empresas.map((empresa) => (
          <SelectPrimitive.Item
            key={empresa.id}
            value={empresa.id}
            className="relative flex w-full cursor-default items-center gap-2.5 rounded-md py-1.5 pr-8 pl-2 outline-none select-none data-highlighted:bg-accent/70 data-highlighted:text-accent-foreground"
          >
            <Avatar empresa={empresa} className={cn(empresa.id !== activa?.id && "bg-secondary text-foreground/80 shadow-none")} />
            <div className="min-w-0 flex-1 overflow-hidden">
              <SelectPrimitive.ItemText className="block truncate text-[12px] leading-tight font-medium">
                {empresa.razon_social}
              </SelectPrimitive.ItemText>
              <span className="block truncate font-mono text-[10px] leading-tight text-muted-foreground">
                RUC {empresa.ruc}
                <span className="text-muted-foreground/50"> · </span>
                {empresa.entorno === "BETA" ? "Beta" : "Producción"}
              </span>
            </div>
            <SelectPrimitive.ItemIndicator className="absolute right-2 flex size-4 items-center justify-center text-primary">
              <CheckIcon className="size-4" />
            </SelectPrimitive.ItemIndicator>
          </SelectPrimitive.Item>
        ))}
      </SelectContent>
    </SelectPrimitive.Root>
  );
}
