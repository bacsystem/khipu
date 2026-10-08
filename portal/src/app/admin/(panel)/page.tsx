import { ChevronRightIcon } from "lucide-react";
import Link from "next/link";
import { SECCIONES_ADMIN } from "@/lib/admin-secciones";
import { TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

export const metadata = { title: "Inicio · Backoffice" };

const t = messages.admin.inicio;

/** El inicio del backoffice: cada sección del menú con sus páginas y para qué sirve cada una. */
export default function AdminInicioPage() {
  return (
    <div className="grid gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descripcion}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {SECCIONES_ADMIN.map((seccion) => (
          <section key={seccion.titulo} aria-labelledby={`inicio-${seccion.titulo}`} className={cn(TARJETA, "overflow-hidden")}>
            <h2 id={`inicio-${seccion.titulo}`} className={cn(TITULO_SECCION, "border-b border-border/60 px-4 py-3")}>
              {seccion.titulo}
            </h2>
            <ul className="divide-y divide-border/60">
              {seccion.items.map(({ href, etiqueta, icono: Icono, resumen }) => (
                <li key={href}>
                  <Link href={href} className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
                      <Icono className="size-4" />
                    </span>
                    <span className="grid min-w-0 flex-1 gap-0.5">
                      <span className="text-[13px] font-medium text-foreground">{etiqueta}</span>
                      <span className="text-[12px] text-muted-foreground">{resumen}</span>
                    </span>
                    <ChevronRightIcon className="size-4 shrink-0 text-muted-foreground/60 transition-colors group-hover:text-foreground" />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </div>
  );
}
