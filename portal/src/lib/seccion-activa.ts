"use client";

import { useEffect, useState } from "react";

/** A partir de qué altura de la ventana se considera que una sección es «la que se está leyendo». */
const UMBRAL_PX = 120;

/**
 * Cuál de las secciones se está leyendo, para que el índice lateral lo marque.
 *
 * <p>Se calcula por posición y no con `IntersectionObserver`. El observador parecía lo indicado, pero la lista de ids
 * llega como un array nuevo en cada render, así que el efecto se rehacía y el estado de «qué está visible» —que vive
 * entre callbacks— se perdía: el índice quedaba clavado en la primera sección que hubiera marcado. Mirar la posición
 * no guarda estado entre eventos, así que no tiene ese problema.
 *
 * <p>Activa es la última sección cuyo comienzo ya pasó el umbral: la de más arriba de las que se están leyendo, no la
 * que ocupa más pantalla. Y si el documento llegó al final, la última, que si no nunca se marcaría.
 *
 * @param ids en el orden en que aparecen en el documento.
 */
export function useSeccionActiva(ids: string[]): string | null {
  const [activa, setActiva] = useState<string | null>(null);
  // La clave del efecto es el contenido, no la identidad del array: si no, se rehace en cada render.
  const clave = ids.join(",");

  useEffect(() => {
    const lista = clave ? clave.split(",") : [];
    if (lista.length === 0) return setActiva(null);

    function recalcular() {
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) {
        return setActiva(lista[lista.length - 1]);
      }
      let visible = lista[0];
      for (const id of lista) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top <= UMBRAL_PX) visible = id;
      }
      setActiva(visible);
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
  }, [clave]);

  return activa;
}
