import { notFound } from "next/navigation";
import Link from "next/link";
import { AuthShell } from "@/components/auth/auth-shell";
import { RegistroForm } from "@/components/auth/registro-form";
import { registroAbierto } from "@/lib/acceso";
import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.auth.registro.titulo} · ${messages.app.nombre}` };
}

// Se renderiza en cada request, no en el build: `REGISTRO_ABIERTO` y `CONTACTO_URL` son variables del entorno de despliegue
// (Railway las inyecta en runtime) y prerenderizar dejaría el flag congelado con lo que hubiera al construir la imagen.
export const dynamic = "force-dynamic";

export default function RegistroPage() {
  // Con el autoservicio cerrado la página no existe: el landing ofrece «Solicitar acceso» y las cuentas se dan de alta a mano.
  if (!registroAbierto()) notFound();
  const messages = useMessages();
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
