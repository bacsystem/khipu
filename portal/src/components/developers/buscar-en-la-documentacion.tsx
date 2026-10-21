"use client";

import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { useCallback, useMemo } from "react";
import { BuscadorConLista, type ResultadoDeLaBusqueda } from "@/components/formularios/buscador-con-lista";
import { buscarEnLaDocumentacion, type EntradaDeBusqueda, indexar } from "@/lib/developers/indice";

/**
 * El buscador del developer portal (#26): estados, códigos de error, casos de emisión, límites y secciones de todas las páginas; elegir uno lleva a su página y su ancla. Las entradas
 * las arma el servidor y llegan como propiedad (el cliente no carga los datos de las páginas); aquí solo se indexan una vez.
 */
export function BuscarEnLaDocumentacion({ entradas }: { entradas: readonly EntradaDeBusqueda[] }) {
  const router = useRouter();
  const t = useMessages().developers.buscador;
  const indice = useMemo(() => indexar(entradas), [entradas]);
  // Estable mientras no cambien las entradas: el buscador genérico vuelve a buscar cuando cambia `buscar`, y una función nueva en cada render lo haría en bucle.
  const buscar = useCallback(async (texto: string): Promise<ResultadoDeLaBusqueda<EntradaDeBusqueda>> => ({ estado: "exito", items: buscarEnLaDocumentacion(texto, indice) }), [indice]);
  return (
    <BuscadorConLista<EntradaDeBusqueda>
      etiqueta={t.etiqueta}
      placeholder={t.placeholder}
      testId="buscador-de-la-documentacion"
      buscar={buscar}
      fila={(e) => (
        <span className="flex min-w-0 flex-col">
          <span className="truncate font-medium">{e.titulo}</span>
          <span className="truncate text-[11px] text-muted-foreground">{e.seccion}</span>
        </span>
      )}
      sinResultados={t.sinResultados}
      mensajeDeFallo={t.fallo}
      encontrados={[t.encontrados[0], t.encontrados[1]]}
      onElegir={(e) => router.push(e.href)}
    />
  );
}
