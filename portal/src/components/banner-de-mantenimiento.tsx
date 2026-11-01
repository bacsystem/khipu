import { MegaphoneIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import { useLocale, useMessages } from "next-intl";
import { interpolar } from "@/i18n/mensajes";
import { formatearFechaHora } from "@/lib/formato";

/**
 * El aviso de mantenimiento que el administrador publica (#199), como lo ven los clientes arriba de su portal y de su inicio de sesión: el texto (plano, nunca HTML) y hasta
 * cuándo. Lo usa también el backoffice para la vista previa.
 */
export function BannerDeMantenimiento({ texto, hasta }: { texto: string; hasta: string }) {
  const messages = useMessages();
  const locale = useLocale();
  return (
    <div
      role="status"
      data-testid="banner-de-mantenimiento"
      className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 border-b border-warning-border bg-warning px-4 py-2 text-center text-[13px] text-warning-foreground"
    >
      <MegaphoneIcon weight={PESO_ICONO} className="size-4 shrink-0" aria-hidden="true" />
      <span data-testid="banner-de-mantenimiento-texto">{texto}</span>
      <span className="text-[12px] opacity-80">{interpolar(messages.banner.hasta, { fecha: formatearFechaHora(hasta, locale) })}</span>
    </div>
  );
}
