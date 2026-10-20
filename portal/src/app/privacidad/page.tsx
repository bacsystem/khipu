import { DocumentoLegalVista } from "@/components/legal/documento-legal";
import { PRIVACIDAD } from "@/content/legal/privacidad";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.legal.privacidad} · ${messages.app.nombre}` };

/** Pública (#342): se lee antes de registrarse, y está enlazada desde el pie del landing, el registro y la pantalla que pide aceptarla. */
export default function PrivacidadPage() {
  return <DocumentoLegalVista documento={PRIVACIDAD} otro={{ href: "/terminos", titulo: messages.legal.terminos }} />;
}
