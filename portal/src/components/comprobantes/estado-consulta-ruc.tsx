import { useMessages } from "next-intl";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { interpolar } from "@/i18n/mensajes";
import { observacionesDeSunat, type ResultadoConsultaRuc } from "@/lib/api/consulta-ruc";
import { AYUDA_CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const ACCION = "text-[12px] font-medium text-primary hover:underline";

/**
 * Qué dijo el padrón de SUNAT del RUC digitado (#379), debajo de los campos del cliente. En ningún caso bloquea: el nombre y la dirección se pueden digitar
 * a mano, que es lo que hace falta con un RUC recién inscrito, un padrón sin cargar o una consulta que no llegó.
 */
export function EstadoConsultaRuc({
  consultando,
  resultado,
  nombreActual,
  onReintentar,
  onUsarNombre,
  className,
}: {
  consultando: boolean;
  resultado: ResultadoConsultaRuc | null;
  /** Lo que hay escrito en el nombre o la razón social: si difiere del de SUNAT se ofrece cambiarlo, sin pisarlo. */
  nombreActual: string;
  onReintentar: () => void;
  onUsarNombre: (nombre: string) => void;
  className?: string;
}) {
  const messages = useMessages();
  const t = messages.comprobantes.consultaRuc;
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
      const observaciones = observacionesDeSunat(c, messages);
      const otroNombre = nombreActual.trim() !== "" && nombreActual.trim() !== c.razon_social;
      return (
        <div className={cn("grid gap-2", className)} data-testid="consulta-ruc" data-estado="encontrado">
          <p role="status" className={AYUDA_CAMPO}>
            {interpolar(t.encontrado, { estado: c.estado, condicion: c.condicion })}
          </p>
          {otroNombre ? (
            <p className="text-[12px] text-muted-foreground">
              {t.registraComo} <strong className="font-medium text-foreground">{c.razon_social}</strong>.{" "}
              <button type="button" className={ACCION} onClick={() => onUsarNombre(c.razon_social)}>
                {t.usarNombre}
              </button>
            </p>
          ) : null}
          {observaciones.map((o) => (
            <Alerta key={o.codigo} tono="aviso" className="px-3 py-2">
              <span data-testid={`aviso-ruc-${o.codigo}`}>{o.texto}</span>
            </Alerta>
          ))}
        </div>
      );
    }
    case "no-encontrado":
      return (
        <Alerta tono="aviso" className={cn("px-3 py-2", className)}>
          <span data-testid="consulta-ruc" data-estado="no-encontrado">
            {t.noEncontrado}
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
  const t = useMessages().comprobantes.consultaRuc;
  return (
    <button type="button" className={ACCION} onClick={onReintentar}>
      {t.reintentar}
    </button>
  );
}
