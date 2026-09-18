import Link from "next/link";
import { LogoMarca } from "@/components/nav/logo";
import { DevelopersNav } from "./developers-nav";

/** Cabecera del developer portal: marca, secciones (referencia, guía, catálogos, errores) y acceso al panel. */
export function DevelopersHeader({ autenticado }: { autenticado: boolean }) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-card/80 backdrop-blur">
      <div className="flex h-12 items-center justify-between gap-4 px-4 md:px-6">
        <div className="flex min-w-0 items-center gap-6">
          <LogoMarca />
          <DevelopersNav />
        </div>
        <nav className="flex shrink-0 items-center gap-4 text-[12px] text-muted-foreground">
          <Link href="/#precios" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
            Precios
          </Link>
          <Link href={autenticado ? "/comprobantes" : "/login"} target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
            {autenticado ? "Ir al panel" : "Iniciar sesión"}
          </Link>
        </nav>
      </div>
    </header>
  );
}
