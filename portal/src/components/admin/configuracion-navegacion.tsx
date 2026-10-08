import Link from "next/link";
import { hrefConfiguracion, SECCIONES_DE_CONFIGURACION, type PlantillaDeCorreo, type SeccionDeConfiguracion } from "@/lib/api/admin-configuracion";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { SEGMENTADO, SEGMENTO } from "@/lib/estilos";

const t = messages.admin.configuracion;

/** Las tres cosas que se configuran: enlaces (la sección vive en la URL, así que se puede compartir y el servidor solo pide lo que se ve). */
export function SeccionesDeConfiguracion({ actual }: { actual: SeccionDeConfiguracion }) {
  return (
    <nav aria-label={t.secciones} className={SEGMENTADO}>
      {SECCIONES_DE_CONFIGURACION.map((s) => (
        <Link
          key={s}
          href={hrefConfiguracion({ seccion: s })}
          aria-current={actual === s ? "page" : undefined}
          className={SEGMENTO}
        >
          {t.filtros[s]}
        </Link>
      ))}
    </nav>
  );
}

/** Los correos que la plataforma manda, con una marca en los que alguien editó. El elegido va en la URL. */
export function ListaDePlantillas({ plantillas, actual }: { plantillas: PlantillaDeCorreo[]; actual: string }) {
  return (
    <nav aria-label={t.plantillas.lista} className="grid content-start gap-0.5 rounded-xl border border-border bg-card p-1.5 shadow-2xs">
      {plantillas.map((p) => (
        <Link
          key={p.tipo}
          href={hrefConfiguracion({ seccion: "plantillas", plantilla: p.tipo })}
          aria-current={actual === p.tipo ? "page" : undefined}
          data-testid="plantilla-enlace"
          data-tipo={p.tipo}
          className={cn(
            "flex items-center justify-between gap-2 rounded-md px-2.5 py-2 text-[13px] transition-colors",
            actual === p.tipo ? "bg-accent/70 font-medium text-accent-foreground" : "text-foreground/80 hover:bg-muted hover:text-foreground",
          )}
        >
          <span className="min-w-0">{p.etiqueta}</span>
          {p.personalizada ? <span className="shrink-0 rounded bg-secondary px-1.5 py-0.5 text-[10px] font-medium text-foreground/80">{t.plantillas.personalizada}</span> : null}
        </Link>
      ))}
    </nav>
  );
}
