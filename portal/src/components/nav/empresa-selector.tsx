"use client";

import { seleccionarEmpresa } from "@/app/actions";
import type { Empresa } from "@/lib/api/empresas";

export function EmpresaSelector({ empresas, activaId }: { empresas: Empresa[]; activaId?: string }) {
  return (
    <form action={seleccionarEmpresa}>
      <select
        name="empresaId"
        defaultValue={activaId ?? empresas[0]?.id}
        onChange={(e) => e.currentTarget.form?.requestSubmit()}
        className="w-full rounded-md border border-sidebar-border bg-sidebar-accent px-2.5 py-1.5 text-sm text-sidebar-foreground outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
      >
        {empresas.map((empresa) => (
          <option key={empresa.id} value={empresa.id} className="text-foreground">
            {empresa.razon_social}
          </option>
        ))}
      </select>
    </form>
  );
}
