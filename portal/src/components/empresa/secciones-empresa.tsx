"use client";

import { useEffect, useState, type ReactNode } from "react";
import { Tabs, type TabItem } from "@/components/navegacion/tabs";
import type { Seccion } from "@/lib/empresa/secciones";

const ETIQUETAS: Record<Seccion, string> = {
  datos: "Datos fiscales",
  certificado: "Certificado",
  sol: "Credenciales SOL",
  pdf: "PDF",
  empresas: "Empresas",
};

/** Deja `?seccion=` en la URL sin navegar: un `router.refresh()` (lo hacen los formularios al guardar) vuelve a pedir la misma pestaña. */
function escribirEnLaUrl(seccion: Seccion) {
  const url = new URL(window.location.href);
  if (url.searchParams.get("seccion") === seccion) return;
  url.searchParams.set("seccion", seccion);
  window.history.replaceState(window.history.state, "", url);
}

/**
 * Las pestañas de «Fiscal & certificado» (#276). Los paneles llegan armados desde la página (Server Component). La pestaña vive en la URL
 * para que los enlaces del portal (`enlaceASeccion`) abran la que corresponde y recargar no la pierda.
 */
export function SeccionesEmpresa({
  inicial,
  pendientes,
  empresas,
  paneles,
}: {
  inicial: Seccion;
  pendientes: Seccion[];
  empresas: number;
  paneles: Record<Seccion, ReactNode>;
}) {
  const [valor, setValor] = useState(inicial);

  // Otra inicial llega cuando un enlace lleva a otra sección estando ya en la página: se abre esa.
  useEffect(() => {
    setValor(inicial);
    escribirEnLaUrl(inicial);
  }, [inicial]);

  const items: TabItem<Seccion>[] = (Object.keys(ETIQUETAS) as Seccion[]).map((id) => ({
    id,
    etiqueta: pendientes.includes(id) ? (
      <>
        {ETIQUETAS[id]}
        <span className="size-1.5 rounded-full bg-warning-solid" aria-hidden />
        <span className="sr-only">, pendiente</span>
      </>
    ) : (
      ETIQUETAS[id]
    ),
    contador: id === "empresas" ? empresas : undefined,
  }));

  return (
    <Tabs<Seccion>
      items={items}
      valor={valor}
      onCambio={(v) => {
        setValor(v);
        escribirEnLaUrl(v);
      }}
      paneles={paneles}
    />
  );
}
