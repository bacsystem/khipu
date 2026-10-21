import { Alerta } from "khipu-design-system/feedback/alerta";
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
  if (consultando) {
    return (
      <p role="status" data-testid="consulta-ruc" data-estado="consultando" className={cn(AYUDA_CAMPO, className)}>
        Consultando SUNAT…
      </p>
    );
  }
  if (!resultado) return null;

  switch (resultado.tipo) {
    case "encontrado": {
      const c = resultado.contribuyente;
      const observaciones = observacionesDeSunat(c);
      const otroNombre = nombreActual.trim() !== "" && nombreActual.trim() !== c.razon_social;
      return (
        <div className={cn("grid gap-2", className)} data-testid="consulta-ruc" data-estado="encontrado">
          <p role="status" className={AYUDA_CAMPO}>
            Encontrado en SUNAT · {c.estado} · {c.condicion}
          </p>
          {otroNombre ? (
            <p className="text-[12px] text-muted-foreground">
              SUNAT lo registra como <strong className="font-medium text-foreground">{c.razon_social}</strong>.{" "}
              <button type="button" className={ACCION} onClick={() => onUsarNombre(c.razon_social)}>
                Usar este nombre
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
            Este RUC no figura en el padrón de SUNAT, que se actualiza cada día: si lo inscribieron hoy, puede tardar un día en aparecer. SUNAT rechaza los
            comprobantes a un RUC que no existe, así que confirma el número. Puedes digitar los datos a mano.
          </span>
        </Alerta>
      );
    case "sin-padron":
      return (
        <p role="status" data-testid="consulta-ruc" data-estado="sin-padron" className={cn(AYUDA_CAMPO, className)}>
          La consulta de RUC no está disponible por ahora: digita los datos a mano.
        </p>
      );
    case "cupo":
      return (
        <Alerta tono="aviso" className={cn("px-3 py-2", className)} accion={<ReintentarBoton onReintentar={onReintentar} />}>
          <span data-testid="consulta-ruc" data-estado="cupo">
            Hiciste muchas consultas seguidas. Espera un momento para consultar de nuevo, o digita los datos a mano.
          </span>
        </Alerta>
      );
    case "error":
      return (
        <Alerta tono="aviso" className={cn("px-3 py-2", className)} accion={<ReintentarBoton onReintentar={onReintentar} />}>
          <span data-testid="consulta-ruc" data-estado="error">
            No se pudo consultar SUNAT. Reintenta o digita los datos a mano.
          </span>
        </Alerta>
      );
  }
}

function ReintentarBoton({ onReintentar }: { onReintentar: () => void }) {
  return (
    <button type="button" className={ACCION} onClick={onReintentar}>
      Consultar de nuevo
    </button>
  );
}
