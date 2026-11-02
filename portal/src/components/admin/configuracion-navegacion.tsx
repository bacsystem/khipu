"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { useEffect, useState, type ReactNode } from "react";
import { Tabs } from "khipu-design-system/navegacion/tabs";
import { Chip } from "khipu-design-system/patrones/pill-estado";
import { hrefConfiguracion, SECCIONES_DE_CONFIGURACION, type PlantillaDeCorreo, type SeccionDeConfiguracion } from "@/lib/api/admin-configuracion";
import { cn } from "@/lib/utils";
import { TARJETA } from "@/lib/estilos";

/**
 * Las tres cosas que se configuran, en las pestañas del design system (como el detalle de una cuenta o la Empresa del cliente). La sección vive en la URL: se puede compartir
 * y el servidor solo pide lo que se ve. Por eso el panel llega armado (`children`, el de la sección actual) y cambiar de pestaña navega; la pestaña se marca al instante y su
 * panel aparece cuando responde el servidor.
 */
export function SeccionesDeConfiguracion({ actual, children }: { actual: SeccionDeConfiguracion; children: ReactNode }) {
  const t = useMessages().admin.configuracion;
  const router = useRouter();
  const [valor, setValor] = useState(actual);

  // La sección cambió por otro camino (atrás, un enlace): la pestaña la sigue.
  useEffect(() => setValor(actual), [actual]);

  return (
    <Tabs<SeccionDeConfiguracion>
      items={SECCIONES_DE_CONFIGURACION.map((s) => ({ id: s, etiqueta: t.filtros[s] }))}
      valor={valor}
      onCambio={(s) => {
        setValor(s);
        router.push(hrefConfiguracion({ seccion: s }));
      }}
      paneles={{ [actual]: children }}
    />
  );
}

/** Los correos que la plataforma manda, con una marca en los que alguien editó. El elegido va en la URL. */
export function ListaDePlantillas({ plantillas, actual }: { plantillas: PlantillaDeCorreo[]; actual: string }) {
  const t = useMessages().admin.configuracion;
  return (
    <nav aria-label={t.plantillas.lista} className={cn(TARJETA, "grid content-start gap-0.5 p-1.5 shadow-xs")}>
      {plantillas.map((p) => (
        <Link
          key={p.tipo}
          href={hrefConfiguracion({ seccion: "plantillas", plantilla: p.tipo })}
          aria-current={actual === p.tipo ? "page" : undefined}
          data-testid="plantilla-enlace"
          data-tipo={p.tipo}
          className={cn(
            "flex items-center justify-between gap-2 rounded-md px-2.5 py-2 text-control transition-colors",
            actual === p.tipo ? "bg-accent/70 font-medium text-accent-foreground" : "text-foreground/80 hover:bg-muted hover:text-foreground",
          )}
        >
          <span className="min-w-0">{p.etiqueta}</span>
          {p.personalizada ? <Chip className="shrink-0">{t.plantillas.personalizada}</Chip> : null}
        </Link>
      ))}
    </nav>
  );
}
