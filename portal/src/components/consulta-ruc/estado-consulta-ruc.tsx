import { useMessages } from "next-intl";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { interpolar } from "@/i18n/mensajes";
import { avisosDeEmisor, observacionesDeSunat, type ResultadoConsultaRuc } from "@/lib/api/consulta-ruc";
import { AYUDA_CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const ACCION = "text-control font-medium text-primary hover:underline";

/**
 * De quién es el RUC, que decide qué avisos importan (#575):
 * - `comprobante`: el receptor de un comprobante (el modal de emisión, un cliente). Un RUC que no está activo o no está habido hace que SUNAT observe el
 *   comprobante (4013, 4014), y uno que no existe lo hace rechazar.
 * - `emisor`: el de la propia empresa (onboarding, empresa nueva, alta asistida). SUNAT puede rechazar lo que emita un RUC que no está activo o no está habido.
 * - `dato`: un tercero sin esas consecuencias (el destinatario o el transportista de una guía): solo el estado.
 */
export type ContextoConsultaRuc = "comprobante" | "emisor" | "dato";

/**
 * Qué dijo el padrón de SUNAT del RUC digitado (#379), debajo del campo. En ningún caso bloquea: el nombre y la dirección se pueden digitar a mano, que es lo
 * que hace falta con un RUC recién inscrito, un padrón sin cargar o una consulta que no llegó.
 */
export function EstadoConsultaRuc({
  consultando,
  resultado,
  contexto = "comprobante",
  nombreActual,
  onReintentar,
  onUsarNombre,
  className,
}: {
  consultando: boolean;
  resultado: ResultadoConsultaRuc | null;
  contexto?: ContextoConsultaRuc;
  /** Lo que hay escrito en el nombre o la razón social: si difiere del de SUNAT se ofrece cambiarlo, sin pisarlo. Sin campo de nombre, no se pasa. */
  nombreActual?: string;
  onReintentar: () => void;
  onUsarNombre?: (nombre: string) => void;
  className?: string;
}) {
  const messages = useMessages();
  const t = messages.consultaRuc;
  if (consultando) {
    return (
      <p role="status" data-testid="consulta-ruc" data-estado="consultando" className={cn(AYUDA_CAMPO, className)}>
        {t.consultando}
      </p>
    );
  }
  if (!resultado) return null;

  switch (resultado.tipo) {
    case "encontrado": {
      const c = resultado.contribuyente;
      const avisos =
        contexto === "comprobante"
          ? observacionesDeSunat(c, messages).map((o) => ({ clave: o.codigo, texto: o.texto }))
          : contexto === "emisor"
            ? avisosDeEmisor(c, messages).map((texto, i) => ({ clave: `emisor-${i}`, texto }))
            : [];
      const nombre = nombreActual?.trim() ?? "";
      const otroNombre = onUsarNombre && nombre !== "" && nombre !== c.razon_social;
      return (
        <div className={cn("grid gap-2", className)} data-testid="consulta-ruc" data-estado="encontrado">
          <p role="status" className={AYUDA_CAMPO}>
            {interpolar(t.encontrado, { estado: c.estado, condicion: c.condicion })}
          </p>
          {otroNombre ? (
            <p className="text-control text-muted-foreground">
              {t.registraComo} <strong className="font-medium text-foreground">{c.razon_social}</strong>.{" "}
              <button type="button" className={ACCION} onClick={() => onUsarNombre(c.razon_social)}>
                {t.usarNombre}
              </button>
            </p>
          ) : null}
          {avisos.map((a) => (
            <Alerta key={a.clave} tono="aviso" className="px-3 py-2">
              <span data-testid={`aviso-ruc-${a.clave}`}>{a.texto}</span>
            </Alerta>
          ))}
        </div>
      );
    }
    case "no-encontrado":
      return (
        <Alerta tono="aviso" className={cn("px-3 py-2", className)}>
          <span data-testid="consulta-ruc" data-estado="no-encontrado">
            {contexto === "comprobante" ? t.noEncontrado : t.noEncontradoGeneral}
          </span>
        </Alerta>
      );
    case "sin-padron":
      return (
        <p role="status" data-testid="consulta-ruc" data-estado="sin-padron" className={cn(AYUDA_CAMPO, className)}>
          {t.sinPadron}
        </p>
      );
    case "cupo":
      return (
        <Alerta tono="aviso" className={cn("px-3 py-2", className)} accion={<ReintentarBoton onReintentar={onReintentar} />}>
          <span data-testid="consulta-ruc" data-estado="cupo">
            {t.cupo}
          </span>
        </Alerta>
      );
    case "error":
      return (
        <Alerta tono="aviso" className={cn("px-3 py-2", className)} accion={<ReintentarBoton onReintentar={onReintentar} />}>
          <span data-testid="consulta-ruc" data-estado="error">
            {t.error}
          </span>
        </Alerta>
      );
  }
}

function ReintentarBoton({ onReintentar }: { onReintentar: () => void }) {
  const t = useMessages().consultaRuc;
  return (
    <button type="button" className={ACCION} onClick={onReintentar}>
      {t.reintentar}
    </button>
  );
}
