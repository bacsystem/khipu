import Link from "next/link";
import { AuthShell } from "@/components/auth/auth-shell";
import { RecuperarForm } from "@/components/auth/recuperar-form";
import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.auth.recuperar.titulo} · ${messages.app.nombre}` };
}

export default function RecuperarPage() {
  const messages = useMessages();
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
