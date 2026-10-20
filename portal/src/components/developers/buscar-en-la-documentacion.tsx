"use client";

import { useRouter } from "next/navigation";
import { BuscadorConLista, type ResultadoDeLaBusqueda } from "@/components/formularios/buscador-con-lista";
import { buscarEnLaDocumentacion, type EntradaDeBusqueda } from "@/lib/developers/indice";

// Una función de módulo y no una flecha en el render: el buscador genérico vuelve a buscar cuando cambia `buscar`, y una nueva en cada render lo haría en bucle.
async function buscar(texto: string): Promise<ResultadoDeLaBusqueda<EntradaDeBusqueda>> {
  return { estado: "exito", items: buscarEnLaDocumentacion(texto) };
}

/** El buscador de la cabecera del developer portal (#26): estados, códigos de error, casos de emisión y secciones de todas las páginas; elegir uno lleva a su página y su ancla. */
export function BuscarEnLaDocumentacion() {
  const router = useRouter();
  return (
    <BuscadorConLista<EntradaDeBusqueda>
      etiqueta="Buscar en la documentación"
      placeholder="Buscar: ERROR_ENVIO, 2108, boleta…"
      testId="buscador-de-la-documentacion"
      buscar={buscar}
      fila={(e) => (
        <>
          <span className="min-w-0 truncate font-medium">{e.titulo}</span>
          <span className="shrink-0 text-[11px] text-muted-foreground">{e.seccion}</span>
        </>
      )}
      sinResultados="Nada coincide. Pruebe con un código de error, un estado o el nombre de un caso."
      mensajeDeFallo="No se pudo buscar."
      encontrados={["resultado", "resultados"]}
      onElegir={(e) => router.push(e.href)}
    />
  );
}
