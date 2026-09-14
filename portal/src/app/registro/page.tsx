import Link from "next/link";
import { AuthShell } from "@/components/auth/auth-shell";
import { RegistroForm } from "@/components/auth/registro-form";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.registro.titulo} · ${messages.app.nombre}` };

export default function RegistroPage() {
  return (
    <AuthShell
      title={messages.auth.registro.titulo}
      subtitle={messages.auth.registro.subtitulo}
      footer={
        <>
          {messages.auth.registro.yaTienesCuenta}{" "}
          <Link href="/login" className="font-medium text-foreground hover:underline">
            {messages.auth.registro.iniciarSesion}
          </Link>
        </>
      }
    >
      <RegistroForm />
    </AuthShell>
  );
}
