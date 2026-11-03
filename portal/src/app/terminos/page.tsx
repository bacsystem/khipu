import { DocumentoLegalVista } from "@/components/legal/documento-legal";
import { TERMINOS } from "@/content/legal/terminos";
import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";

/** Dinámica a propósito: el aviso de borrador depende de `REVISADO_POR_LEGAL`, que se lee al pintar, no al construir la imagen (#580). */
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.legal.terminos} · ${messages.app.nombre}` };
}

/** Pública (#342): se lee antes de registrarse, y está enlazada desde el pie del landing, el registro y la pantalla que pide aceptarla. */
export default function TerminosPage() {
  const messages = useMessages();
  return <DocumentoLegalVista documento={TERMINOS} otro={{ href: "/privacidad", titulo: messages.legal.privacidad }} />;
}
