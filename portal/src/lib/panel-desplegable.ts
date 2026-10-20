"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const EVENTO = "khipu:panel-desplegable";

/**
 * Un panel que se despliega bajo su botón (la descarga en ZIP, la exportación, #321): se cierra con Esc y con un clic afuera, y abrir uno cierra el otro —dos paneles abiertos a la
 * vez se tapaban—. `ref` va en el contenedor del botón y el panel: un clic adentro no lo cierra.
 */
export function usePanelDesplegable(id: string) {
  const [abierto, setAbierto] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Otro panel se abrió: este se cierra.
  useEffect(() => {
    const otroSeAbrio = (e: Event) => {
      if ((e as CustomEvent<string>).detail !== id) setAbierto(false);
    };
    window.addEventListener(EVENTO, otroSeAbrio);
    return () => window.removeEventListener(EVENTO, otroSeAbrio);
  }, [id]);

  useEffect(() => {
    if (!abierto) return;
    const tecla = (e: KeyboardEvent) => {
      if (e.key === "Escape") setAbierto(false);
    };
    const clic = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setAbierto(false);
    };
    document.addEventListener("keydown", tecla);
    document.addEventListener("mousedown", clic);
    return () => {
      document.removeEventListener("keydown", tecla);
      document.removeEventListener("mousedown", clic);
    };
  }, [abierto]);

  const alternar = useCallback(() => {
    if (!abierto) window.dispatchEvent(new CustomEvent(EVENTO, { detail: id }));
    setAbierto(!abierto);
  }, [abierto, id]);
  const cerrar = useCallback(() => setAbierto(false), []);

  return { abierto, alternar, cerrar, ref };
}
