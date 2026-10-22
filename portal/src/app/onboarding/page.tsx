import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getMessages } from "next-intl/server";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { debeAceptarTerminos, PantallaDeTerminos } from "@/components/legal/pantalla-de-terminos";
import { OnboardingEmpresaForm } from "@/components/onboarding/onboarding-empresa-form";
import { me } from "@/lib/api/auth";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.onboarding.titulo} · ${messages.app.nombre}` };
}

export default async function OnboardingPage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");
  const usuario = await me(access);
  const messages = await getMessages();
  // #342: lo mismo que el panel: sin aceptar la versión vigente de los términos no se configura nada.
  if (debeAceptarTerminos(usuario)) return <PantallaDeTerminos />;

  return (
    <div className="min-h-screen px-6 py-16">
      <div className="mx-auto mb-10 max-w-lg text-center">
        <span className="font-heading text-lg">{messages.app.nombre}</span>
        <h1 className="mt-4 font-heading text-2xl">{messages.onboarding.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{messages.onboarding.subtitulo}</p>
      </div>
      {/* Sin verificar el correo (#22) el backend rechaza crear la empresa: se explica antes de que llene el formulario. */}
      {usuario.correo_verificado ? <OnboardingEmpresaForm /> : <RevisaTuCorreo email={usuario.email} />}
    </div>
  );
}
