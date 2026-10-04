import { AuthShell } from "@/components/auth/auth-shell";
import { VerificarCorreoForm } from "@/components/auth/verificar-correo-form";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.verificar.titulo} · ${messages.app.nombre}` };

/** Enlace del correo de verificación (#22): pública, porque puede abrirse en otro dispositivo sin sesión. */
export default async function VerificarPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <AuthShell title={messages.auth.verificar.titulo} subtitle={messages.auth.verificar.subtitulo}>
      {/* El segmento llega codificado tal como vino en el enlace; un cliente de correo puede haberlo reescrito así. */}
      <VerificarCorreoForm token={decodeURIComponent(token)} />
    </AuthShell>
  );
}
