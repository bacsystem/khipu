import Link from "next/link";
import { messages } from "@/lib/messages";

export function DevelopersHeader({ autenticado }: { autenticado: boolean }) {
  return (
    <header className="sticky top-0 z-30 flex h-12 items-center justify-between border-b border-border bg-background px-4 text-sm">
      <Link href="/" className="font-heading text-base">
        {messages.app.nombre}
      </Link>
      <nav className="flex items-center gap-4 text-muted-foreground">
        <Link href="/#precios" className="hover:text-foreground">
          Precios
        </Link>
        <Link href={autenticado ? "/comprobantes" : "/login"} className="hover:text-foreground">
          {autenticado ? "Ir al panel" : "Iniciar sesión"}
        </Link>
      </nav>
    </header>
  );
}
