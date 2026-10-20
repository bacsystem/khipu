import Link from "next/link";
import { LogoMarca } from "@/components/nav/logo";
import { ENTRADAS } from "@/lib/developers/entradas";
import { BuscarEnLaDocumentacion } from "./buscar-en-la-documentacion";
import { DevelopersNav } from "./developers-nav";

/** Cabecera del developer portal: marca, secciones (referencia, guía, catálogos, errores, entornos), buscador y acceso al panel. */
export function DevelopersHeader({ autenticado }: { autenticado: boolean }) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-card/80 backdrop-blur">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2 md:px-6 lg:h-12 lg:flex-nowrap lg:py-0">
        <div className="flex min-w-0 flex-1 items-center gap-6">
          <LogoMarca />
          <DevelopersNav />
        </div>
        <div className="order-last w-full lg:order-none lg:w-64 lg:shrink-0">
          <BuscarEnLaDocumentacion entradas={ENTRADAS} />
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
