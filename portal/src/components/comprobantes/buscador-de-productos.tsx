"use client";

import { useLocale, useMessages } from "next-intl";
import { BuscadorConLista, type ResultadoDeLaBusqueda } from "@/components/formularios/buscador-con-lista";
import type { ProductoGuardado } from "@/lib/api/productos";
import { buscarProductos } from "@/lib/api/productos-browser";
import { formatearPrecioUnitario } from "@/lib/formato";

async function buscar(q: string, senal: AbortSignal): Promise<ResultadoDeLaBusqueda<ProductoGuardado>> {
  const res = await buscarProductos({ q, porPagina: 6, senal });
  return res.estado === "exito" ? { estado: "exito", items: res.datos.productos } : { estado: "error" };
}

/** Autocompletado de los productos y servicios guardados (#320): se teclea parte del código o de la descripción y se elige de la lista. */
export function BuscadorDeProductos({ onElegir, deshabilitado }: { onElegir: (p: ProductoGuardado) => void; deshabilitado?: boolean }) {
  const t = useMessages().comprobantes.buscador.productos;
  const locale = useLocale();
  return (
    <BuscadorConLista
      etiqueta={t.etiqueta}
      placeholder={t.placeholder}
      testId="buscador-de-productos"
      buscar={buscar}
      fila={(p) => (
        <>
          <span className="min-w-0 truncate font-medium">{p.descripcion}</span>
          <span className="shrink-0 font-mono text-[12px] text-muted-foreground tabular-nums">{p.codigo ? `${p.codigo} · ` : ""}{formatearPrecioUnitario(p.precio_unitario, locale)}</span>
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
