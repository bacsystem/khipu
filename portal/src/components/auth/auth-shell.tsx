import type { ReactNode } from "react";
import { messages } from "@/lib/messages";

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
        <span className="font-heading text-lg">{messages.app.nombre}</span>
        <div className="max-w-sm">
          <h1 className="font-heading text-3xl leading-tight text-balance">{messages.auth.marca.titulo}</h1>
          <p className="mt-4 text-sidebar-foreground/70">{messages.auth.marca.subtitulo}</p>
          <div className="mt-8 h-px w-16 bg-sidebar-primary" />
        </div>
        <p className="text-sm text-sidebar-foreground/50">{messages.auth.marca.pie}</p>
      </div>

      <div className="flex flex-col items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <div className="mb-8 text-center md:hidden">
            <span className="font-heading text-lg">{messages.app.nombre}</span>
          </div>
          <h2 className="font-heading text-2xl">{title}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
          <div className="mt-6">{children}</div>
          {footer ? <div className="mt-6 text-center text-sm text-muted-foreground">{footer}</div> : null}
        </div>
      </div>
    </div>
  );
}
