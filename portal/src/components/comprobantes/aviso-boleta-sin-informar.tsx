import { Alerta } from "khipu-design-system/feedback/alerta";
import { useLocale, useMessages } from "next-intl";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { AvisoDePlazo } from "@/lib/comprobantes/plazo";
import { formatearFecha } from "@/lib/formato";

/** «queda 1 día» / «quedan 3 días». */
function cuantoQueda(dias: number, t: Mensajes["comprobantes"]["avisoBoleta"]): string {
  return dias === 1 ? t.quedaUno : interpolar(t.quedanVarios, { n: dias });
}

/**
 * La boleta (o la nota que la modifica) que sigue sin informarse a SUNAT desde el 5.º día (#356). No falló nada, pero el envío individual ya vence o venció y el resumen diario cierra
 * el 7.º: sin este aviso la única pista era una línea chica de fecha, y el cliente que dejó de enviar solas sus boletas pasaba el plazo sin enterarse. Cada fase dice lo que le queda;
 * las dos últimas se marcan como urgentes. El botón para enviarla está arriba, junto a las demás acciones de la ficha.
 */
export function AvisoBoletaSinInformar({ aviso, que }: { aviso: AvisoDePlazo; que: "boleta" | "nota" }) {
  const messages = useMessages();
  const t = messages.comprobantes.avisoBoleta;
  const urgente = aviso.fase === "ultimo-dia-resumen" || aviso.fase === "fuera-de-plazo";
  const limite = formatearFecha(aviso.limite, useLocale());
  return (
    // La fase va en el envoltorio: la `Alerta` del DS no lleva atributos propios (y pone el rol: `status`, o `alert` si es urgente).
    <section data-testid="aviso-boleta-sin-informar" data-fase={aviso.fase} data-urgente={urgente}>
      <Alerta tono={urgente ? "error" : "aviso"} titulo={interpolar(t.titulo, { que: messages.comprobantes.estadoBaja.que[que] })}>
        {aviso.fase === "ultimo-dia-individual" ? interpolar(t.ultimoDiaIndividual, { limite }) : null}
        {aviso.fase === "solo-resumen" ? interpolar(t.soloResumen, { limite, queda: cuantoQueda(aviso.diasRestantes, t) }) : null}
        {aviso.fase === "ultimo-dia-resumen" ? interpolar(t.ultimoDiaResumen, { limite }) : null}
        {aviso.fase === "fuera-de-plazo" ? interpolar(t.fueraDePlazo, { limite }) : null}{" "}
        {t.enviala}
      </Alerta>
    </section>
  );
}
