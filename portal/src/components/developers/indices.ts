import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { casos } from "./guia/casos-emision";

/**
 * Los índices laterales de la guía y de la página de errores, aparte de sus páginas (que no pueden exportar nada más que su componente) para que el buscador de la documentación
 * (#26) lleve a las mismas anclas que ellas muestran: una prueba renderiza las páginas y comprueba que cada ancla exista. Los nombres están en `messages.developers` (#25).
 */
export type EntradaDeIndice = { id: string; label: string; esCaso?: boolean };

export function indiceGuia(messages: Mensajes): EntradaDeIndice[] {
  const t = messages.developers.guia.indice;
  return [
    { id: "inicio-rapido", label: t.inicioRapido },
    { id: "anatomia", label: t.anatomia },
    { id: "casos", label: t.casos },
    ...casos(messages).map((c) => ({ id: c.id, label: interpolar(t.caso, { titulo: c.titulo }), esCaso: true })),
    { id: "respuesta", label: t.respuesta },
    { id: "errores", label: t.errores },
    { id: "webhooks", label: t.webhooks },
    { id: "sire", label: t.sire },
    { id: "buenas-practicas", label: t.buenasPracticas },
  ];
}

export function indiceErrores(messages: Mensajes): EntradaDeIndice[] {
  const t = messages.developers.errores.indice;
  return [
    { id: "estados", label: t.estados },
    { id: "http", label: t.http },
    { id: "codigos", label: t.codigos },
    { id: "sunat", label: t.sunat },
  ];
}
