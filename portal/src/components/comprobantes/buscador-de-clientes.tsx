"use client";

import { useCallback } from "react";
import { useMessages } from "next-intl";
import { BuscadorConLista, type ResultadoDeLaBusqueda } from "@/components/formularios/buscador-con-lista";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { buscarClientes } from "@/lib/api/clientes-browser";

/**
 * Autocompletado de los clientes guardados (#319): se teclea parte del documento o del nombre y se elige de la lista. En el comprobante es además el campo «Cliente»
 * (#557): quien lo usa controla el texto para tomar un RUC o un DNI completo como documento.
 */
export function BuscadorDeClientes({
  onElegir,
  deshabilitado,
  etiqueta,
  placeholder,
  texto,
  onTexto,
  onEnter,
  onBlur,
  autoFocus,
  describidoPor,
}: {
  onElegir: (c: ClienteGuardado) => void;
  deshabilitado?: boolean;
  /** El nombre accesible; por defecto, «Buscar un cliente guardado». */
  etiqueta?: string;
  placeholder?: string;
  texto?: string;
  onTexto?: (texto: string) => void;
  onEnter?: (texto: string) => void;
  onBlur?: (texto: string) => void;
  autoFocus?: boolean;
  describidoPor?: string;
}) {
  const messages = useMessages();
  const t = messages.comprobantes.buscador.clientes;
  const buscar = useCallback(
    async (q: string, senal: AbortSignal): Promise<ResultadoDeLaBusqueda<ClienteGuardado>> => {
      const res = await buscarClientes({ q, porPagina: 6, senal }, messages);
      return res.estado === "exito" ? { estado: "exito", items: res.datos.clientes } : { estado: "error" };
    },
    [messages],
  );
  return (
    <BuscadorConLista
      etiqueta={etiqueta ?? t.etiqueta}
      placeholder={placeholder ?? t.placeholder}
      testId="buscador-de-clientes"
      buscar={buscar}
      fila={(c) => (
        <>
          <span className="min-w-0 truncate font-medium">{c.razon_social}</span>
          <span className="shrink-0 font-mono text-control text-muted-foreground tabular-nums">{c.num_doc}</span>
        </>
      )}
      sinResultados={t.sinResultados}
      mensajeDeFallo={t.fallo}
      encontrados={[t.encontradoUno, t.encontradoVarios]}
      onElegir={onElegir}
      deshabilitado={deshabilitado}
      texto={texto}
      onTexto={onTexto}
      onEnter={onEnter}
      onBlur={onBlur}
      autoFocus={autoFocus}
      describidoPor={describidoPor}
    />
  );
}
