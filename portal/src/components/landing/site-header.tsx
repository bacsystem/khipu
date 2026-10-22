import Link from "next/link";
import { useMessages } from "next-intl";
import { LogoMarca } from "@/components/nav/logo";
import { Button } from "khipu-design-system/ui/button";
import { contactoUrl, registroAbierto } from "@/lib/acceso";

export function SiteHeader() {
  // Mientras el autoservicio esté cerrado (beta por invitación) el header no ofrece crear cuenta: ver `lib/acceso`.
  const abierto = registroAbierto();
  const contacto = contactoUrl();
  const t = useMessages().landing.acciones;
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <LogoMarca href="/" />
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
        {abierto ? (
          <Button render={<Link href="/registro" />} nativeButton={false} className="h-9 px-4">
            {t.crearCuentaGratis}
          </Button>
        ) : contacto ? (
          <Button render={<a href={contacto} />} nativeButton={false} className="h-9 px-4">
            {t.solicitarAcceso}
          </Button>
        ) : (
          <Button render={<Link href="/login" />} nativeButton={false} variant="outline" className="h-9 px-4">
            {t.iniciarSesion}
          </Button>
        )}
      </div>
    </header>
  );
}
