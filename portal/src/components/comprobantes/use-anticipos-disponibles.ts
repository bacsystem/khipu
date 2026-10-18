"use client";

import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Comprobante } from "@/lib/api/facturas";
import { type Candidato, candidatosDeAnticipo } from "@/lib/comprobantes/anticipos";

export type AnticiposDisponibles = { estado: "inactivo" | "cargando" | "listo" | "error"; candidatos: Candidato[] };

/**
 * Los comprobantes aceptados del mismo cliente y moneda que se pueden descontar (#312). Pide la primera página del listado (los 100 más recientes) cuando se activa, una
 * sola vez, y filtra por cliente en el navegador: la API no filtra por comprador. Lo que la lista no alcance a ver, el backend lo valida al emitir (3218).
 */
export function useAnticiposDisponibles(activo: boolean, numDoc: string, moneda: string): AnticiposDisponibles {
  const [lista, setLista] = useState<Comprobante[] | null>(null);
  const [fallo, setFallo] = useState(false);
  useEffect(() => {
    if (!activo || lista !== null) return;
    let vigente = true;
    apiRequest<Comprobante[]>("/api/proxy/facturas?por_pagina=100", { method: "GET" })
      .then((res) => {
        if (!vigente) return;
        if (res.estado === "exito" && res.datos) setLista(res.datos);
        else setFallo(true);
      })
      .catch(() => vigente && setFallo(true));
    return () => {
      vigente = false;
    };
  }, [activo, lista]);
  const candidatos = useMemo(() => (lista ? candidatosDeAnticipo(lista, { numDoc }, moneda) : []), [lista, numDoc, moneda]);
  if (!activo) return { estado: "inactivo", candidatos: [] };
  if (fallo) return { estado: "error", candidatos: [] };
  return { estado: lista === null ? "cargando" : "listo", candidatos };
}
