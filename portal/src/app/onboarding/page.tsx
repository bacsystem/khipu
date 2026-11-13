import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getMessages } from "next-intl/server";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { debeAceptarTerminos, PantallaDeTerminos } from "@/components/legal/pantalla-de-terminos";
import { OnboardingEmpresaForm } from "@/components/onboarding/onboarding-empresa-form";
import { DialogoSesionTerminada } from "@/components/sesion/dialogo-sesion-terminada";
import { VigilanteDeInactividad } from "@/components/sesion/vigilante-de-inactividad";
import { me } from "@/lib/api/auth";
import { getServerSession } from "@/lib/session-server";
import { minutosDeInactividad } from "@/lib/sesion/inactividad";

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
  // #637: el onboarding es una sesión viva aunque no cuelgue de `(privado)`: lleva el mismo corte por inactividad, también sobre la pantalla de términos.
  // #654: «Tu sesión terminó» va aquí, hermano del vigilante y no dentro de él: el vigilante no renderiza nada sin `minutos`.
  const avisosDeSesion = (
    <>
      <VigilanteDeInactividad minutos={minutosDeInactividad(usuario)} />
      <DialogoSesionTerminada />
    </>
  );
  if (debeAceptarTerminos(usuario))
    return (
      <>
        {avisosDeSesion}
        <PantallaDeTerminos />
      </>
    );

  return (
    <div className="min-h-screen px-6 py-16">
      {avisosDeSesion}
      <div className="mx-auto mb-10 max-w-lg text-center">
        <span className="font-heading text-titulo">{messages.app.nombre}</span>
        <h1 className="mt-4 font-heading text-titulo-pagina">{messages.onboarding.titulo}</h1>
        <p className="mt-1 text-cuerpo text-muted-foreground">{messages.onboarding.subtitulo}</p>
      </div>
      {/* Sin verificar el correo (#22) el backend rechaza crear la empresa: se explica antes de que llene el formulario. */}
      {usuario.correo_verificado ? <OnboardingEmpresaForm /> : <RevisaTuCorreo email={usuario.email} />}
    </div>
  );
}
