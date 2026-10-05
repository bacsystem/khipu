import { MegaphoneIcon } from "lucide-react";
import { formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";

/**
 * El aviso de mantenimiento que el administrador publica (#199), como lo ven los clientes arriba de su portal y de su inicio de sesión: el texto (plano, nunca HTML) y hasta
 * cuándo. Lo usa también el backoffice para la vista previa.
 */
export function BannerDeMantenimiento({ texto, hasta }: { texto: string; hasta: string }) {
  return (
    <div
      role="status"
      data-testid="banner-de-mantenimiento"
      className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 border-b border-warning-border bg-warning px-4 py-2 text-center text-[13px] text-warning-foreground"
    >
      <MegaphoneIcon className="size-4 shrink-0" aria-hidden="true" />
      <span data-testid="banner-de-mantenimiento-texto">{texto}</span>
      <span className="text-[12px] opacity-80">{messages.banner.hasta.replace("{fecha}", formatearFechaHora(hasta))}</span>
    </div>
  );
}
