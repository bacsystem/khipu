import { MailCheckIcon, MailIcon, MailWarningIcon, MailXIcon } from "lucide-react";
import type { EnvioAlCliente } from "@/lib/api/facturas";
import { formatearFechaHora } from "@/lib/formato";
import { TITULO_SECCION } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const PRESENTACION: Record<EnvioAlCliente["estado"], { titulo: string; tono: string; Icono: typeof MailIcon }> = {
  PENDIENTE: { titulo: "Envío al cliente pendiente", tono: "text-warning-foreground", Icono: MailIcon },
  ENVIADO: { titulo: "Enviado al cliente", tono: "text-success-foreground", Icono: MailCheckIcon },
  FALLIDO: { titulo: "No se pudo enviar al cliente", tono: "text-destructive", Icono: MailXIcon },
  OMITIDO: { titulo: "No se envió al cliente", tono: "text-muted-foreground", Icono: MailWarningIcon },
};

/** Lo que pasó con el envío automático del comprobante a su cliente (#325): a quién, si salió y, si no, por qué y qué hacer. */
export function EnvioAlClienteEstado({ envio }: { envio: EnvioAlCliente }) {
  const { titulo, tono, Icono } = PRESENTACION[envio.estado];
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-2xs" data-testid="envio-al-cliente" data-estado={envio.estado}>
      <div className={cn(TITULO_SECCION, "mb-2", tono)}>
        <Icono className="size-4" />
        {titulo}
      </div>
      <p className="text-[13px] text-foreground/90">
        {envio.estado === "ENVIADO" ? "El PDF, el XML y la constancia de SUNAT salieron a " : "Destinatario: "}
        <span className="font-mono">{envio.destinatario}</span>
        {envio.estado === "ENVIADO" && envio.enviado_en ? <> el {formatearFechaHora(envio.enviado_en)}</> : null}.
      </p>
      {envio.estado === "PENDIENTE" ? (
        <p className="mt-1 text-[12px] text-muted-foreground">
          {envio.intentos > 0
            ? `Falló ${envio.intentos === 1 ? "1 intento" : `${envio.intentos} intentos`}; se reintenta solo${envio.siguiente_intento ? ` el ${formatearFechaHora(envio.siguiente_intento)}` : ""}.`
            : "Sale en unos segundos."}
        </p>
      ) : null}
      {envio.estado === "FALLIDO" || envio.estado === "OMITIDO" ? (
        <p className="mt-1 text-[12px] text-muted-foreground">Puedes enviarlo a mano con «Enviar por correo» o descargar el PDF.</p>
      ) : null}
      {envio.ultimo_error && envio.estado !== "ENVIADO" ? (
        <p className="mt-1 font-mono text-[11px] leading-snug text-foreground/80" data-testid="envio-al-cliente-error">
          {envio.ultimo_error}
        </p>
      ) : null}
    </section>
  );
}
