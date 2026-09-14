import Link from "next/link";
import { AuthShell } from "@/components/auth/auth-shell";
import { RecuperarForm } from "@/components/auth/recuperar-form";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.recuperar.titulo} · ${messages.app.nombre}` };

export default function RecuperarPage() {
  return (
    <AuthShell
      title={messages.auth.recuperar.titulo}
      subtitle={messages.auth.recuperar.subtitulo}
      footer={
        <Link href="/login" className="font-medium text-foreground hover:underline">
          {messages.auth.recuperar.volver}
        </Link>
      }
    >
      <RecuperarForm />
    </AuthShell>
  );
}
