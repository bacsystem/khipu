import Link from "next/link";
import { Suspense } from "react";
import { AuthShell } from "@/components/auth/auth-shell";
import { LoginForm } from "@/components/auth/login-form";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.login.titulo} · ${messages.app.nombre}` };

export default function LoginPage() {
  return (
    <AuthShell
      title={messages.auth.login.titulo}
      subtitle={messages.auth.login.subtitulo}
      footer={
        <>
          {messages.auth.login.sinCuenta}{" "}
          <Link href="/registro" className="font-medium text-foreground hover:underline">
            {messages.auth.login.crearCuenta}
          </Link>
        </>
      }
    >
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthShell>
  );
}
