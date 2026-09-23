import Link from "next/link";
import { LogoMarca } from "@/components/nav/logo";
import { Button } from "@/components/ui/button";
import { contactoUrl, registroAbierto } from "@/lib/acceso";

export function SiteHeader() {
  // Mientras el autoservicio esté cerrado (beta por invitación) el header no ofrece crear cuenta: ver `lib/acceso`.
  const abierto = registroAbierto();
  const contacto = contactoUrl();
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <LogoMarca href="/" />
        <nav className="hidden items-center gap-7 text-sm text-muted-foreground md:flex">
          <a href="#precios" className="transition-colors hover:text-foreground">
            Precios
          </a>
          <Link href="/developers" className="transition-colors hover:text-foreground">
            Desarrolladores
          </Link>
          <Link href="/login" className="transition-colors hover:text-foreground">
            Iniciar sesión
          </Link>
        </nav>
        {abierto ? (
          <Button render={<Link href="/registro" />} nativeButton={false} className="h-9 px-4">
            Crear cuenta gratis
          </Button>
        ) : contacto ? (
          <Button render={<a href={contacto} />} nativeButton={false} className="h-9 px-4">
            Solicitar acceso
          </Button>
        ) : (
          <Button render={<Link href="/login" />} nativeButton={false} variant="outline" className="h-9 px-4">
            Iniciar sesión
          </Button>
        )}
      </div>
    </header>
  );
}
