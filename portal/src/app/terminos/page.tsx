import { DocumentoLegalVista } from "@/components/legal/documento-legal";
import { TERMINOS } from "@/content/legal/terminos";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.legal.terminos} · ${messages.app.nombre}` };

/** Pública (#342): se lee antes de registrarse, y está enlazada desde el pie del landing, el registro y la pantalla que pide aceptarla. */
export default function TerminosPage() {
  return <DocumentoLegalVista documento={TERMINOS} otro={{ href: "/privacidad", titulo: messages.legal.privacidad }} />;
}
