import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { CaretRightIcon, SquaresFourIcon } from "@phosphor-icons/react/dist/ssr";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { seccionesAdmin } from "@/lib/admin-secciones";
import { CONTENEDOR_PAGINA, TARJETA } from "@/lib/estilos";
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
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={t.titulo} descripcion={t.descripcion} />
      <div className="grid gap-3 md:grid-cols-2">
        {seccionesAdmin(messages).map((seccion) => (
          <section key={seccion.titulo} aria-labelledby={`inicio-${seccion.titulo}`} className={cn(TARJETA, "overflow-hidden")}>
            <CabeceraSeccion icon={SquaresFourIcon} titulo={<span id={`inicio-${seccion.titulo}`}>{seccion.titulo}</span>} />
            <ul className="divide-y divide-border/60">
              {seccion.items.map(({ href, etiqueta, icono: Icono, resumen }) => (
                <li key={href}>
                  <Link href={href} className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted">
                    <Icono aria-hidden weight={PESO_ICONO} className="size-4 shrink-0 text-acento-2-texto dark:text-acento-2" />
                    <span className="grid min-w-0 flex-1 gap-0.5">
                      <span className="text-control font-medium text-foreground">{etiqueta}</span>
                      <span className="text-control text-muted-foreground">{resumen}</span>
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
