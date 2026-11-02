import type { KeyboardEvent } from "react";

/**
 * Anula el envío implícito del navegador: Enter solo emite con el foco en un botón.
 *
 * En un formulario que produce un documento tributario irreversible, el reflejo de «Enter para pasar al siguiente
 * campo» no puede consumir un correlativo. Cubre TODO lo que no sea botón, no solo los inputs: Chromium hace envío
 * implícito también desde un `<select>` cerrado (la primera versión los exceptuaba y la recertificación lo
 * reprodujo). El popup de un select abierto consume sus propias teclas, así que elegir con teclado no cambia.
 *
 * Nació en el formulario de emisión (#112, #116); las notas de crédito/débito lo heredaron sin la guarda y la
 * auditoría emitió cinco notas reales con cinco Enter. Por eso vive acá y no en cada formulario.
 */
export function sinEnvioImplicito(e: KeyboardEvent<HTMLFormElement>) {
  if (e.key !== "Enter") return;
  const tag = (e.target as HTMLElement).tagName;
  // Botones y enlaces conservan su Enter: un `<Link>` («Cancelar», «Cree una») dentro del form navega con Enter
  // por su propia acción por defecto, no por el envío implícito. La primera versión solo exceptuaba BUTTON y un
  // usuario de teclado no podía cancelar — regresión que la recertificación de notas reprodujo.
  // Un `<textarea>` también: su Enter es un salto de línea (las observaciones los admiten) y nunca envía el formulario.
  if (tag === "BUTTON" || tag === "A" || tag === "TEXTAREA") return;
  e.preventDefault();
  // #117: sin esto, Enter sobre un campo incompleto ya no hacía nada (antes el envío implícito al menos disparaba la burbuja nativa «Completa este campo»). Si el
  // formulario no vale se muestra qué falta; si vale, no se emite (eso es del botón) y el foco pasa a él para que el siguiente Enter sea una decisión.
  const form = e.currentTarget;
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  form.querySelector<HTMLButtonElement>('button[type="submit"]')?.focus();
}
