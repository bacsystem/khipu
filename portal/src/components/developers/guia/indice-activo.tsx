"use client";

import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";

/** A partir de qué altura de la ventana se considera que una sección es «la que se está leyendo». */
const UMBRAL_PX = 120;

/**
 * Índice lateral que marca la sección que se está leyendo.
 *
 * <p>Sin esto, en una guía de veinte secciones el índice no dice dónde estás: hay que leer los títulos y ubicarse
 * a mano en la lista.
 *
 * <p>Se calcula por posición y no con `IntersectionObserver`. El observador parecía lo indicado, pero la lista de
 * items llega como un array nuevo en cada render, así que el efecto se rehacía y el estado de «qué está visible»
 * —que vive entre callbacks— se perdía: el índice quedaba clavado en la primera sección que hubiera marcado. Mirar
 * la posición no guarda estado entre eventos, así que no tiene ese problema.
 *
 * <p>Activa es la última sección cuyo comienzo ya pasó el umbral: la de más arriba de las que estás leyendo, no la
 * que ocupa más pantalla. Y si el documento llegó al final, la última, que si no nunca se marcaría.
 */
export function IndiceActivo({ items }: { items: Array<{ id: string; label: string }> }) {
  const [activo, setActivo] = useState<string | null>(null);
  const ids = items.map((i) => i.id).join(",");

  useEffect(() => {
    const lista = ids.split(",");

    function recalcular() {
      const fin = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;
      if (fin) return setActivo(lista[lista.length - 1]);

      let visible = lista[0];
      for (const id of lista) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top <= UMBRAL_PX) visible = id;
      }
      setActivo(visible);
    }

    let pendiente = false;
    function alDesplazar() {
      if (pendiente) return;
      pendiente = true;
      requestAnimationFrame(() => {
        pendiente = false;
        recalcular();
      });
    }

    recalcular();
    window.addEventListener("scroll", alDesplazar, { passive: true });
    window.addEventListener("resize", alDesplazar);
    return () => {
      window.removeEventListener("scroll", alDesplazar);
      window.removeEventListener("resize", alDesplazar);
    };
  }, [ids]);

  return (
    <nav aria-label="Contenido" className="sticky top-16 hidden max-h-[calc(100vh-5rem)] overflow-y-auto lg:block">
      <span className="mb-2 block text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">En esta página</span>
      <ul className="space-y-1 border-l border-border/60">
        {items.map((i) => {
          const esActivo = i.id === activo;
          return (
            <li key={i.id}>
              <a
                href={`#${i.id}`}
                aria-current={esActivo ? "location" : undefined}
                className={cn(
                  "-ml-px block border-l py-1 pl-3 text-[12px] transition-colors",
                  esActivo
                    ? "border-primary font-medium text-foreground"
                    : "border-transparent text-muted-foreground hover:border-primary/40 hover:text-foreground",
                )}
              >
                {i.label}
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
