import { DocumentoLegalVista } from "@/components/legal/documento-legal";
import { PRIVACIDAD } from "@/content/legal/privacidad";
import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.legal.privacidad} · ${messages.app.nombre}` };
}

/** Pública (#342): se lee antes de registrarse, y está enlazada desde el pie del landing, el registro y la pantalla que pide aceptarla. */
export default function PrivacidadPage() {
  const messages = useMessages();
  return <DocumentoLegalVista documento={PRIVACIDAD} otro={{ href: "/terminos", titulo: messages.legal.terminos }} />;
}
