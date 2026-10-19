"use client";

import { BuscadorConLista, type ResultadoDeLaBusqueda } from "@/components/formularios/buscador-con-lista";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { buscarClientes } from "@/lib/api/clientes-browser";

async function buscar(q: string, senal: AbortSignal): Promise<ResultadoDeLaBusqueda<ClienteGuardado>> {
  const res = await buscarClientes({ q, porPagina: 6, senal });
  return res.estado === "exito" ? { estado: "exito", items: res.datos.clientes } : { estado: "error" };
}

/** Autocompletado de los clientes guardados (#319): se teclea parte del documento o del nombre y se elige de la lista. */
export function BuscadorDeClientes({ onElegir, deshabilitado }: { onElegir: (c: ClienteGuardado) => void; deshabilitado?: boolean }) {
  return (
    <BuscadorConLista
      etiqueta="Buscar un cliente guardado"
      placeholder="Buscar un cliente guardado por documento o nombre"
      testId="buscador-de-clientes"
      buscar={buscar}
      fila={(c) => (
        <>
          <span className="min-w-0 truncate font-medium">{c.razon_social}</span>
          <span className="shrink-0 font-mono text-[12px] text-muted-foreground tabular-nums">{c.num_doc}</span>
        </>
      )}
      sinResultados="Ningún cliente guardado coincide. Escríbelo abajo y márcalo para guardarlo."
      mensajeDeFallo="No se pudieron buscar tus clientes; escríbelo a mano."
      encontrados={["cliente encontrado", "clientes encontrados"]}
      onElegir={onElegir}
      deshabilitado={deshabilitado}
    />
  );
}
