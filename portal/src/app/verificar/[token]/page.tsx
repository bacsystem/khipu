import { AuthShell } from "@/components/auth/auth-shell";
import { VerificarCorreoForm } from "@/components/auth/verificar-correo-form";
import { messages } from "@/lib/messages";
import { tokenDeEnlace } from "@/lib/token-de-enlace";

export const metadata = { title: `${messages.auth.verificar.titulo} · ${messages.app.nombre}` };

/** Enlace del correo de verificación (#22): pública, porque puede abrirse en otro dispositivo sin sesión. */
export default async function VerificarPage({ params }: { params: Promise<{ token: string }> }) {
  // El segmento llega codificado tal como vino en el enlace; un cliente de correo puede haberlo reescrito así. Mal codificado (un `%` suelto), es un enlace inválido.
  const token = tokenDeEnlace((await params).token);
  return (
    <AuthShell title={messages.auth.verificar.titulo} subtitle={messages.auth.verificar.subtitulo}>
      {token === null ? (
        <p role="alert" className="text-sm text-destructive">{messages.auth.verificar.enlaceInvalido}</p>
      ) : (
        <VerificarCorreoForm token={token} />
      )}
    </AuthShell>
  );
}
