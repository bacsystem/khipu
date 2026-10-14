import Link from "next/link";
import type { ReactNode } from "react";
import { AyudaDeSoporte } from "@/components/soporte/ayuda-de-soporte";
import { LogoMarca } from "@/components/nav/logo";
import { messages } from "@/lib/messages";

/**
 * Marco de las pantallas públicas de acceso (login, registro, recuperar, restablecer, verificar, cuenta suspendida): la marca a un lado y el formulario al otro.
 * Usa el `LogoMarca` y la escala de texto del panel privado (título de 18 px, textos de 13 px), para que no se noten dos estilos entre salir y entrar.
 */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="grid min-h-screen md:grid-cols-2">
      <div className="relative hidden flex-col justify-between overflow-hidden bg-sidebar px-12 py-12 text-sidebar-foreground md:flex">
        <LogoMarca href="/" />
        <div className="max-w-sm">
          <h1 className="font-heading text-3xl leading-tight text-balance">{messages.auth.marca.titulo}</h1>
          <p className="mt-4 text-[13px] leading-relaxed text-sidebar-foreground/70">{messages.auth.marca.subtitulo}</p>
          <div className="mt-8 h-px w-16 bg-sidebar-primary" />
        </div>
        <p className="text-[12px] text-sidebar-foreground/50">{messages.auth.marca.pie}</p>
      </div>

      <div className="flex flex-col items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex justify-center md:hidden">
            <LogoMarca href="/" />
          </div>
          <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
          <p className="mt-1 text-[13px] text-muted-foreground">{subtitle}</p>
          <div className="mt-6">{children}</div>
          {footer ? <div className="mt-6 text-center text-[13px] text-muted-foreground">{footer}</div> : null}
          {/* #250: quien no puede entrar (o tiene la cuenta suspendida) también necesita saber a quién escribir. */}
          <AyudaDeSoporte className="mt-8 justify-center" />
          {/* #342: los textos legales a mano desde el login, el registro y la pantalla que pide aceptarlos. */}
          <p className="mt-6 flex justify-center gap-4 text-xs text-muted-foreground">
            <Link href="/terminos" className="hover:text-foreground">
              {messages.legal.terminos}
            </Link>
            <Link href="/privacidad" className="hover:text-foreground">
              {messages.legal.privacidad}
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
