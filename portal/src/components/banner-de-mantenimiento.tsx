import { MegaphoneIcon } from "@phosphor-icons/react/dist/ssr";
import { Banner } from "khipu-design-system/feedback/banner";
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
    // El `Banner` del DS pone el rol (`status`) y el tinte; el `data-testid` va en el envoltorio.
    <div data-testid="banner-de-mantenimiento">
      <Banner tono="aviso" icon={MegaphoneIcon}>
        <span data-testid="banner-de-mantenimiento-texto">{texto}</span>{" "}
        <span className="opacity-80">{interpolar(messages.banner.hasta, { fecha: formatearFechaHora(hasta, locale) })}</span>
      </Banner>
    </div>
  );
}
