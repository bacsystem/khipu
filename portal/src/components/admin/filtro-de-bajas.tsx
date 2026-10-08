"use client";

import { bajasDesdeUrl, VISIBILIDADES_DE_BAJAS, type VisibilidadDeBajasAdmin } from "@/lib/api/admin-baja";
import { CAMPO_FILTRO } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { messages } from "@/lib/messages";

const t = messages.admin.bajas;

const TEXTO: Record<VisibilidadDeBajasAdmin, string> = { INCLUIDAS: t.incluidas, SOLO: t.solo };

/**
 * Qué hacer con las cuentas dadas de baja (#201) en un listado: ocultarlas (lo que pasa sin pedir nada), mezclarlas o verlas solas. Es el mismo
 * selector en el listado de cuentas y en el de empresas, así los dos dicen lo mismo. «Ocultar» no es un valor de la URL: es no tener filtro.
 */
export function FiltroDeBajas({ valor, onCambio }: { valor?: VisibilidadDeBajasAdmin; onCambio: (valor: VisibilidadDeBajasAdmin | undefined) => void }) {
  return (
    <div className="grid gap-1">
      <label htmlFor="filtro-bajas" className="text-[11px] font-medium text-muted-foreground">
        {t.etiqueta}
      </label>
      <select id="filtro-bajas" value={valor ?? ""} onChange={(e) => onCambio(bajasDesdeUrl(e.target.value))} className={cn(CAMPO_FILTRO, "w-auto")}>
        <option value="">{t.ocultas}</option>
        {VISIBILIDADES_DE_BAJAS.map((v) => (
          <option key={v} value={v}>
            {TEXTO[v]}
          </option>
        ))}
      </select>
    </div>
  );
}
