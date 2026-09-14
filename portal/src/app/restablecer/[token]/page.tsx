import { AuthShell } from "@/components/auth/auth-shell";
import { RestablecerForm } from "@/components/auth/restablecer-form";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.restablecer.titulo} · ${messages.app.nombre}` };

export default async function RestablecerPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <AuthShell title={messages.auth.restablecer.titulo} subtitle={messages.auth.restablecer.subtitulo}>
      <RestablecerForm token={token} />
    </AuthShell>
  );
}
