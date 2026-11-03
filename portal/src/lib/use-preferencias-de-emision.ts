"use client";

import { useEffect, useState } from "react";
import { leerPreferenciasDeEmision } from "@/lib/api/preferencias-emision";

/**
 * Si el modal de emisión debe ofrecer las opciones avanzadas (la preferencia «Mostrar opciones avanzadas al emitir» de la empresa). Mientras carga, y si no se pudo leer,
 * vale `false`: el formulario simple es el seguro (nada se pierde, solo no se ofrecen los «+» avanzados).
 */
export function usePreferenciasDeEmision(): { opcionesAvanzadas: boolean; cargando: boolean } {
  const [opcionesAvanzadas, setOpcionesAvanzadas] = useState(false);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    let vigente = true;
    void leerPreferenciasDeEmision().then((res) => {
      if (!vigente) return;
      setOpcionesAvanzadas(res.estado === "exito" && res.datos?.opciones_avanzadas === true);
      setCargando(false);
    });
    return () => {
      vigente = false;
    };
  }, []);

  return { opcionesAvanzadas, cargando };
}
