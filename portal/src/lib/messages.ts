import es from "@/messages/es.json";

export const messages = es;

export function mensajeError(codigo: string | null | undefined): string {
  if (codigo && codigo in messages.errores) {
    return messages.errores[codigo as keyof typeof messages.errores];
  }
  return messages.errores.generico;
}
