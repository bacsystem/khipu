import { MailCheckIcon, MailIcon, MailWarningIcon, MailXIcon } from "lucide-react";
import { useLocale, useMessages } from "next-intl";
import { interpolar } from "@/i18n/mensajes";
import type { EnvioAlCliente } from "@/lib/api/facturas";
import { formatearFechaHora } from "@/lib/formato";
import { TITULO_SECCION } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** El tono y el icono de cada estado; el título está en `messages.comprobantes.envioAlCliente.titulos` (#25). */
const PRESENTACION: Record<EnvioAlCliente["estado"], { tono: string; Icono: typeof MailIcon }> = {
  PENDIENTE: { tono: "text-warning-foreground", Icono: MailIcon },
  ENVIADO: { tono: "text-success-foreground", Icono: MailCheckIcon },
  FALLIDO: { tono: "text-destructive", Icono: MailXIcon },
  OMITIDO: { tono: "text-muted-foreground", Icono: MailWarningIcon },
};

/** Lo que pasó con el envío automático del comprobante a su cliente (#325): a quién, si salió y, si no, por qué y qué hacer. */
export function EnvioAlClienteEstado({ envio }: { envio: EnvioAlCliente }) {
  const t = useMessages().comprobantes.envioAlCliente;
  const locale = useLocale();
  const { tono, Icono } = PRESENTACION[envio.estado];
  const titulo = t.titulos[envio.estado];
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-2xs" data-testid="envio-al-cliente" data-estado={envio.estado}>
      <div className={cn(TITULO_SECCION, "mb-2", tono)}>
        <Icono className="size-4" />
        {titulo}
      </div>
      <p className="text-[13px] text-foreground/90">
        {envio.estado === "ENVIADO" ? t.salieronA : t.destinatario}
        <span className="font-mono">{envio.destinatario}</span>
        {envio.estado === "ENVIADO" && envio.enviado_en ? interpolar(t.el, { fecha: formatearFechaHora(envio.enviado_en, locale) }) : null}.
      </p>
      {envio.estado === "PENDIENTE" ? (
        <p className="mt-1 text-[12px] text-muted-foreground">
          {envio.intentos > 0
            ? interpolar(envio.intentos === 1 ? t.falloUno : t.falloVarios, {
                n: envio.intentos,
                cuando: envio.siguiente_intento ? interpolar(t.el, { fecha: formatearFechaHora(envio.siguiente_intento, locale) }) : "",
              })
            : t.enSegundos}
        </p>
      ) : null}
      {envio.estado === "FALLIDO" || envio.estado === "OMITIDO" ? (
        <p className="mt-1 text-[12px] text-muted-foreground">{t.aMano}</p>
      ) : null}
      {envio.ultimo_error && envio.estado !== "ENVIADO" ? (
        <p className="mt-1 font-mono text-[11px] leading-snug text-foreground/80" data-testid="envio-al-cliente-error">
          {envio.ultimo_error}
        </p>
      ) : null}
    </section>
  );
}
