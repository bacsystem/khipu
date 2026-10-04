import { redirect } from "next/navigation";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { OnboardingWizard } from "@/components/onboarding/onboarding-wizard";
import { me } from "@/lib/api/auth";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export const metadata = { title: `Configura tu empresa · ${messages.app.nombre}` };

export default async function OnboardingPage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");
  const usuario = await me(access);

  return (
    <div className="min-h-screen px-6 py-16">
      <div className="mx-auto mb-10 max-w-lg text-center">
        <span className="font-heading text-lg">{messages.app.nombre}</span>
        <h1 className="mt-4 font-heading text-2xl">Configura tu empresa</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Necesitamos estos datos para poder emitir comprobantes a nombre tuyo.
        </p>
      </div>
      {/* Sin verificar el correo (#22) el backend rechaza crear la empresa: se explica antes de que llene el formulario. */}
      {usuario.correo_verificado ? <OnboardingWizard /> : <RevisaTuCorreo email={usuario.email} />}
    </div>
  );
}
