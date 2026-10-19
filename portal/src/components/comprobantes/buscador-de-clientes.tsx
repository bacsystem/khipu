"use client";

import { SearchIcon } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { buscarClientes } from "@/lib/api/clientes-browser";
import { CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const ESPERA_MS = 250;

/**
 * Autocompletado de los clientes guardados (#319): se teclea parte del documento o del nombre y se elige de la lista (flechas y Enter, o clic). No manda nada al servidor mientras
 * no haya texto, descarta la búsqueda anterior al teclear otra letra y, si falla, lo dice sin bloquear el formulario (el cliente se puede teclear a mano).
 */
export function BuscadorDeClientes({ onElegir, deshabilitado }: { onElegir: (c: ClienteGuardado) => void; deshabilitado?: boolean }) {
  const idLista = useId();
  const [texto, setTexto] = useState("");
  const [resultados, setResultados] = useState<ClienteGuardado[] | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const contenedor = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const buscado = texto.trim();
    if (buscado === "") return;
    const control = new AbortController();
    const id = window.setTimeout(async () => {
      const res = await buscarClientes({ q: buscado, porPagina: 6, senal: control.signal });
      if (control.signal.aborted) return;
      if (res.estado === "exito") {
        setResultados(res.datos.clientes);
        setActivo(0);
        setError(null);
        setAbierto(true);
      } else {
        setError("No se pudieron buscar tus clientes; escríbelo a mano.");
        setResultados(null);
      }
    }, ESPERA_MS);
    return () => {
      window.clearTimeout(id);
      control.abort();
    };
  }, [texto]);

  // Un clic fuera cierra la lista.
  useEffect(() => {
    function fuera(e: MouseEvent) {
      if (contenedor.current && !contenedor.current.contains(e.target as Node)) setAbierto(false);
    }
    document.addEventListener("mousedown", fuera);
    return () => document.removeEventListener("mousedown", fuera);
  }, []);

  function elegir(c: ClienteGuardado) {
    onElegir(c);
    setTexto("");
    setResultados(null);
    setAbierto(false);
  }

  function teclas(e: React.KeyboardEvent<HTMLInputElement>) {
    if (!abierto || !resultados || resultados.length === 0) {
      // Con la lista cerrada Escape sigue su camino (cierra el diálogo que contiene al buscador).
      if (e.key === "Escape" && abierto) {
        e.preventDefault();
        e.stopPropagation();
        setAbierto(false);
      }
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActivo((a) => Math.min(resultados.length - 1, a + 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActivo((a) => Math.max(0, a - 1));
    } else if (e.key === "Enter") {
      // Enter elige de la lista: no es el envío del formulario.
      e.preventDefault();
      elegir(resultados[activo]);
    } else if (e.key === "Escape") {
      // Cierra la lista, no el diálogo de «Nuevo comprobante» que la contiene (ni borra el texto, que es lo que hace un campo de búsqueda).
      e.preventDefault();
      e.stopPropagation();
      setAbierto(false);
    }
  }

  const hayTexto = texto.trim() !== "";
  return (
    <div ref={contenedor} className="relative" data-testid="buscador-de-clientes">
      <label htmlFor={`${idLista}-buscar`} className="sr-only">
        Buscar un cliente guardado
      </label>
      <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
      <input
        id={`${idLista}-buscar`}
        type="search"
        role="combobox"
        aria-expanded={abierto}
        aria-controls={abierto && hayTexto && resultados ? idLista : undefined}
        aria-activedescendant={abierto && resultados && resultados.length > 0 ? `${idLista}-${activo}` : undefined}
        aria-autocomplete="list"
        autoComplete="off"
        disabled={deshabilitado}
        value={texto}
        onChange={(e) => {
          setTexto(e.target.value);
          if (e.target.value.trim() === "") {
            setResultados(null);
            setAbierto(false);
            setError(null);
          }
        }}
        onFocus={() => hayTexto && resultados && setAbierto(true)}
        onKeyDown={teclas}
        placeholder="Buscar un cliente guardado por documento o nombre"
        className={cn(CAMPO, "pl-9 text-[13px]")}
      />
      <p role="status" className="sr-only">
        {abierto && hayTexto && resultados ? (resultados.length === 0 ? "Ningún cliente coincide" : `${resultados.length} ${resultados.length === 1 ? "cliente encontrado" : "clientes encontrados"}`) : ""}
      </p>
      {error ? (
        <p role="status" className="mt-1 text-[12px] text-muted-foreground">
          {error}
        </p>
      ) : null}
      {abierto && hayTexto && resultados ? (
        <ul id={idLista} role="listbox" className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-border bg-popover py-1 shadow-md">
          {resultados.length === 0 ? (
            <li role="option" aria-selected={false} aria-disabled className="px-3 py-2 text-[13px] text-muted-foreground">
              Ningún cliente guardado coincide. Escríbelo abajo y márcalo para guardarlo.
            </li>
          ) : (
            resultados.map((c, i) => (
              <li
                key={c.id}
                id={`${idLista}-${i}`}
                role="option"
                aria-selected={i === activo}
                onMouseEnter={() => setActivo(i)}
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir(c);
                }}
                className={cn("flex cursor-pointer items-baseline justify-between gap-3 px-3 py-2 text-[13px]", i === activo && "bg-muted")}
              >
                <span className="min-w-0 truncate font-medium">{c.razon_social}</span>
                <span className="shrink-0 font-mono text-[12px] text-muted-foreground tabular-nums">{c.num_doc}</span>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );
}
