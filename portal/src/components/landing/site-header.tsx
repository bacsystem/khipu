import Link from "next/link";
import { useMessages } from "next-intl";
import { LogoIcono, LogoMarca } from "@/components/nav/logo";
import { Button } from "khipu-design-system/ui/button";
import { contactoUrl, registroAbierto } from "@/lib/acceso";

export function SiteHeader() {
  // Mientras el autoservicio esté cerrado (beta por invitación) el header no ofrece crear cuenta: ver `lib/acceso`.
  const abierto = registroAbierto();
  const contacto = contactoUrl();
  const t = useMessages().landing.acciones;
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-4 sm:px-6">
        {/* En el teléfono solo el ícono: con el nombre no entraban el enlace al login y el botón (#525). */}
        <div className="sm:hidden">
          <LogoIcono href="/" />
        </div>
        <div className="hidden min-w-0 sm:block">
          <LogoMarca href="/" />
        </div>
        <nav className="hidden items-center gap-7 text-sm text-muted-foreground md:flex">
          <a href="#precios" className="transition-colors hover:text-foreground">
            {t.precios}
          </a>
          <Link href="/developers" className="transition-colors hover:text-foreground">
            {t.desarrolladores}
          </Link>
          <Link href="/login" className="transition-colors hover:text-foreground">
            {t.iniciarSesion}
          </Link>
        </nav>
        {abierto || contacto ? (
          <div className="flex shrink-0 items-center gap-3 sm:gap-4">
            {/* En el teléfono el menú de arriba se oculta: sin este enlace, el único camino al login era el pie de la página. En una línea (#525). */}
            <Link href="/login" className="text-sm whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground md:hidden">
              {t.iniciarSesion}
            </Link>
            {abierto ? (
              <Button render={<Link href="/registro" />} nativeButton={false} className="h-control px-4 whitespace-nowrap">
                {t.crearCuentaGratis}
              </Button>
            ) : (
              <Button render={<a href={contacto ?? undefined} />} nativeButton={false} className="h-control px-4 whitespace-nowrap">
                {t.solicitarAcceso}
              </Button>
            )}
          </div>
        ) : (
          <Button render={<Link href="/login" />} nativeButton={false} variant="outline" className="h-control px-4">
            {t.iniciarSesion}
          </Button>
        )}
      </div>
    </header>
  );
}
