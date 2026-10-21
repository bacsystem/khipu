"use client";

import { useMessages } from "next-intl";
import { BuscadorConLista, type ResultadoDeLaBusqueda } from "@/components/formularios/buscador-con-lista";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { buscarClientes } from "@/lib/api/clientes-browser";

async function buscar(q: string, senal: AbortSignal): Promise<ResultadoDeLaBusqueda<ClienteGuardado>> {
  const res = await buscarClientes({ q, porPagina: 6, senal });
  return res.estado === "exito" ? { estado: "exito", items: res.datos.clientes } : { estado: "error" };
}

/** Autocompletado de los clientes guardados (#319): se teclea parte del documento o del nombre y se elige de la lista. */
export function BuscadorDeClientes({ onElegir, deshabilitado }: { onElegir: (c: ClienteGuardado) => void; deshabilitado?: boolean }) {
  const t = useMessages().comprobantes.buscador.clientes;
  return (
    <BuscadorConLista
      etiqueta={t.etiqueta}
      placeholder={t.placeholder}
      testId="buscador-de-clientes"
      buscar={buscar}
      fila={(c) => (
        <>
          <span className="min-w-0 truncate font-medium">{c.razon_social}</span>
          <span className="shrink-0 font-mono text-[12px] text-muted-foreground tabular-nums">{c.num_doc}</span>
        </>
      )}
      sinResultados={t.sinResultados}
      mensajeDeFallo={t.fallo}
      encontrados={[t.encontradoUno, t.encontradoVarios]}
      onElegir={onElegir}
      deshabilitado={deshabilitado}
    />
  );
}
