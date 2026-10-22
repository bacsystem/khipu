import { AuthShell } from "@/components/auth/auth-shell";
import { VerificarCorreoForm } from "@/components/auth/verificar-correo-form";
import { Alerta } from "khipu-design-system/feedback/alerta";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { tokenDeEnlace } from "@/lib/token-de-enlace";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.auth.verificar.titulo} · ${messages.app.nombre}` };
}

/** Enlace del correo de verificación (#22): pública, porque puede abrirse en otro dispositivo sin sesión. */
export default async function VerificarPage({ params }: { params: Promise<{ token: string }> }) {
  // El segmento llega codificado tal como vino en el enlace; un cliente de correo puede haberlo reescrito así. Mal codificado (un `%` suelto), es un enlace inválido.
  const token = tokenDeEnlace((await params).token);
  const messages = await getMessages();
  return (
    <AuthShell title={messages.auth.verificar.titulo} subtitle={messages.auth.verificar.subtitulo}>
      {token === null ? (
        <Alerta tono="error">{messages.auth.verificar.enlaceInvalido}</Alerta>
      ) : (
        <VerificarCorreoForm token={token} />
      )}
    </AuthShell>
  );
}
