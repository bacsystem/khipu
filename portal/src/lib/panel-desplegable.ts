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
    // La lista de un combo del panel (`SelectCampo`, #555) se dibuja en un portal, fuera de `ref`: un clic en ella o el Esc que la cierra no cierran el panel.
    // Cerrada, base-ui deja la lista en el DOM dentro de un contenedor `hidden`: solo cuenta una visible.
    const hayListaAbierta = () => [...document.querySelectorAll('[role="listbox"]')].some((l) => l.closest("[hidden]") === null);
    const tecla = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !hayListaAbierta()) setAbierto(false);
    };
    const clic = (e: MouseEvent) => {
      const destino = e.target as Element;
      if (destino instanceof Element && destino.closest('[role="listbox"]')) return;
      if (ref.current && !ref.current.contains(destino)) setAbierto(false);
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
