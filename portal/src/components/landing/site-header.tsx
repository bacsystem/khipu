import Link from "next/link";
import { Button } from "@/components/ui/button";
import { messages } from "@/lib/messages";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link href="/" className="font-heading text-lg">
          {messages.app.nombre}
        </Link>
        <nav className="hidden items-center gap-6 text-sm text-muted-foreground md:flex">
          <a href="#precios" className="hover:text-foreground">
            Precios
          </a>
          <Link href="/developers" className="hover:text-foreground">
            Desarrolladores
          </Link>
          <Link href="/login" className="hover:text-foreground">
            Iniciar sesión
          </Link>
        </nav>
        <Button render={<Link href="/registro" />} nativeButton={false} className="h-9 px-4">
          Crear cuenta gratis
        </Button>
      </div>
    </header>
  );
}
