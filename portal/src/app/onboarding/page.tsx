import { OnboardingWizard } from "@/components/onboarding/onboarding-wizard";
import { messages } from "@/lib/messages";

export const metadata = { title: `Configura tu empresa · ${messages.app.nombre}` };

export default function OnboardingPage() {
  return (
    <div className="min-h-screen px-6 py-16">
      <div className="mx-auto mb-10 max-w-lg text-center">
        <span className="font-heading text-lg">{messages.app.nombre}</span>
        <h1 className="mt-4 font-heading text-2xl">Configura tu empresa</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Necesitamos estos datos para poder emitir comprobantes a nombre tuyo.
        </p>
      </div>
      <OnboardingWizard />
    </div>
  );
}
