import { Suspense } from "react";
import { AdminLoginForm } from "@/components/admin/admin-login-form";
import { AuthShell } from "@/components/auth/auth-shell";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.admin.login.titulo} · ${messages.app.nombre}` };

export default function AdminLoginPage() {
  return (
    <AuthShell title={messages.admin.login.titulo} subtitle={messages.admin.login.subtitulo}>
      <Suspense>
        <AdminLoginForm />
      </Suspense>
    </AuthShell>
  );
}
