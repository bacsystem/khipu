import Link from "next/link";
import { Suspense } from "react";
import { AuthShell } from "@/components/auth/auth-shell";
import { LoginForm } from "@/components/auth/login-form";
import { BannerDeMantenimiento } from "@/components/banner-de-mantenimiento";
import { registroAbierto } from "@/lib/acceso";
import { obtenerBannerVigente } from "@/lib/api/banner";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.login.titulo} · ${messages.app.nombre}` };

// Se renderiza en cada request, no en el build: `REGISTRO_ABIERTO` y `CONTACTO_URL` son variables del entorno de despliegue
// (Railway las inyecta en runtime) y prerenderizar dejaría el flag congelado con lo que hubiera al construir la imagen.
export const dynamic = "force-dynamic";

export default async function LoginPage() {
  // El aviso de mantenimiento (#199) se ve también antes de iniciar sesión: es cuando un cliente se entera de que hoy no va a poder emitir.
  const banner = await obtenerBannerVigente();
  return (
    <>
      {banner ? <BannerDeMantenimiento texto={banner.texto} hasta={banner.hasta} /> : null}
      <AuthShell
        title={messages.auth.login.titulo}
        subtitle={messages.auth.login.subtitulo}
        footer={
          // Con el registro cerrado no se ofrece crear cuenta: el enlace llevaría a un 404.
          registroAbierto() ? (
            <>
              {messages.auth.login.sinCuenta}{" "}
              <Link href="/registro" className="font-medium text-foreground hover:underline">
                {messages.auth.login.crearCuenta}
              </Link>
            </>
          ) : (
            <>Acceso por invitación mientras dura la beta.</>
          )
        }
      >
        <Suspense>
          <LoginForm />
        </Suspense>
      </AuthShell>
    </>
  );
}
