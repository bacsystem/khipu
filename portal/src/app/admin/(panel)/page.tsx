import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { CaretRightIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { seccionesAdmin } from "@/lib/admin-secciones";
import { TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { cn } from "@/lib/utils";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.inicio };
}

/** El inicio del backoffice: cada sección del menú con sus páginas y para qué sirve cada una. */
export default async function AdminInicioPage() {
  const messages = await getMessages();
  const t = messages.admin.inicio;
  return (
    <div className="grid gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descripcion}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {seccionesAdmin(messages).map((seccion) => (
          <section key={seccion.titulo} aria-labelledby={`inicio-${seccion.titulo}`} className={cn(TARJETA, "overflow-hidden")}>
            <h2 id={`inicio-${seccion.titulo}`} className={cn(TITULO_SECCION, "border-b border-border/60 px-4 py-3")}>
              {seccion.titulo}
            </h2>
            <ul className="divide-y divide-border/60">
              {seccion.items.map(({ href, etiqueta, icono: Icono, resumen }) => (
                <li key={href}>
                  <Link href={href} className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
                      <Icono weight={PESO_ICONO} className="size-4" />
                    </span>
                    <span className="grid min-w-0 flex-1 gap-0.5">
                      <span className="text-[13px] font-medium text-foreground">{etiqueta}</span>
                      <span className="text-[12px] text-muted-foreground">{resumen}</span>
                    </span>
                    <CaretRightIcon weight="bold" className="size-4 shrink-0 text-muted-foreground/60 transition-colors group-hover:text-foreground" />
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
