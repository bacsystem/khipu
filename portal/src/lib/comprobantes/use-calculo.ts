"use client";

import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Comprobante } from "@/lib/api/facturas";

/**
 * Lo que el backend calcularía para el cuerpo del formulario (`POST /v1/facturas/calcular`, #405): los mismos importes que saldrán al emitir, con las mismas
 * reglas de dominio. El formulario no los recalcula en TypeScript —descuentos, cargos, ISC, anticipos, redondeo—: se los pide al backend para que el total que el
 * usuario revisa no pueda desalinearse del que sale.
 *
 * - `listo`: los totales del servidor.
 * - `rechazado`: el dominio rechazaría ese cuerpo (el mismo `422` que daría la emisión); `mensaje` es el motivo.
 * - `sin-servicio`: no se pudo preguntar (red, respuesta ilegible): el formulario cae a su cálculo local.
 */
export type Calculo =
  | { estado: "inactivo" }
  | { estado: "calculando" }
  | { estado: "listo"; totales: Comprobante["totales"]; totalEnSoles: number | null; comprobante: Comprobante }
  | { estado: "rechazado"; mensaje: string }
  | { estado: "sin-servicio" };

const CODIGOS_SIN_SERVICIO = new Set(["RED", "RESPUESTA_INVALIDA"]);

/**
 * Pide el cálculo cada vez que cambia el cuerpo (con una pausa, para no consultar en cada tecla) y descarta la respuesta de un cuerpo que ya no es el actual.
 * Con `cuerpo` nulo no pregunta: no hay nada completo que calcular.
 */
export function useCalculo(cuerpo: object | null, esperaMs = 350): Calculo {
  const clave = cuerpo === null ? null : JSON.stringify(cuerpo);
  const [resultado, setResultado] = useState<{ clave: string; calculo: Calculo } | null>(null);

  useEffect(() => {
    if (clave === null) return;
    let vigente = true;
    const temporizador = setTimeout(() => {
      apiRequest<Comprobante>("/api/proxy/facturas/calcular", { method: "POST", body: JSON.parse(clave) }).then((res) => {
        if (!vigente) return;
        if (res.estado === "exito" && res.datos?.totales) setResultado({ clave, calculo: { estado: "listo", totales: res.datos.totales, totalEnSoles: res.datos.total_en_soles ?? null, comprobante: res.datos } });
        else if (res.codigo && CODIGOS_SIN_SERVICIO.has(res.codigo)) setResultado({ clave, calculo: { estado: "sin-servicio" } });
        else setResultado({ clave, calculo: res.estado === "error" && res.mensaje ? { estado: "rechazado", mensaje: res.mensaje } : { estado: "sin-servicio" } });
      });
    }, esperaMs);
    return () => {
      vigente = false;
      clearTimeout(temporizador);
    };
  }, [clave, esperaMs]);

  if (clave === null) return { estado: "inactivo" };
  // El resultado de un cuerpo anterior no se muestra: mientras llega el del actual, se dice que se está calculando.
  return resultado?.clave === clave ? resultado.calculo : { estado: "calculando" };
}
