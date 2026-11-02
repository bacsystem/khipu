import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";
import { Suspense } from "react";
import { AdminLoginForm } from "@/components/admin/admin-login-form";
import { AuthShell } from "@/components/auth/auth-shell";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.admin.login.titulo} · ${messages.app.nombre}` };
}

export default function AdminLoginPage() {
  const messages = useMessages();
  return (
    <AuthShell title={messages.admin.login.titulo} subtitle={messages.admin.login.subtitulo}>
      <Suspense>
        <AdminLoginForm />
      </Suspense>
    </AuthShell>
  );
}
