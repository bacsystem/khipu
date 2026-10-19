"use client";

import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Comprobante } from "@/lib/api/facturas";
import { type Candidato, candidatosDeAnticipo } from "@/lib/comprobantes/anticipos";

export type AnticiposDisponibles = { estado: "inactivo" | "cargando" | "listo" | "error"; candidatos: Candidato[] };

/** Cuántos comprobantes recientes se miran: PAGINAS × POR_PAGINA. Más viejos que eso no se ofrecen (el backend sí los aceptaría si se piden por la API). */
export const POR_PAGINA = 100;
export const PAGINAS_MAXIMAS = 5;

/**
 * Los comprobantes aceptados del mismo cliente y moneda que se pueden descontar (#312). Pide el listado (hasta {@link PAGINAS_MAXIMAS} páginas de {@link POR_PAGINA}, los más
 * recientes, parando en la primera corta) cuando se activa, una sola vez, y filtra por cliente en el navegador: la API no filtra por el comprador del anticipo sino por el
 * del listado. Lo que la lista no alcance a ver, el backend lo valida al emitir (3218). Si falla, se puede reintentar cerrando y abriendo la sección.
 */
export function useAnticiposDisponibles(activo: boolean, numDoc: string, moneda: string): AnticiposDisponibles {
  const [lista, setLista] = useState<Comprobante[] | null>(null);
  const [fallo, setFallo] = useState(false);
  useEffect(() => {
    if (!activo || lista !== null) return;
    let vigente = true;
    setFallo(false);
    (async () => {
      const todos: Comprobante[] = [];
      for (let pagina = 1; pagina <= PAGINAS_MAXIMAS; pagina++) {
        const res = await apiRequest<Comprobante[]>(`/api/proxy/facturas?por_pagina=${POR_PAGINA}&pagina=${pagina}`, { method: "GET" });
        if (!vigente) return;
        if (res.estado !== "exito" || !res.datos) {
          setFallo(true);
          return;
        }
        todos.push(...res.datos);
        if (res.datos.length < POR_PAGINA) break;
      }
      setLista(todos);
    })().catch(() => vigente && setFallo(true));
    return () => {
      vigente = false;
    };
  }, [activo, lista]);
  const candidatos = useMemo(() => (lista ? candidatosDeAnticipo(lista, { numDoc }, moneda) : []), [lista, numDoc, moneda]);
  if (!activo) return { estado: "inactivo", candidatos: [] };
  if (fallo) return { estado: "error", candidatos: [] };
  return { estado: lista === null ? "cargando" : "listo", candidatos };
}
