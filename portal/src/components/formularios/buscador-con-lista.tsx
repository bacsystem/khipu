"use client";

import { SearchIcon } from "lucide-react";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import { CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const ESPERA_MS = 250;

export type ResultadoDeLaBusqueda<T> = { estado: "exito"; items: T[] } | { estado: "error" };

/**
 * Autocompletado con lista (clientes #319, productos #320): se teclea parte del texto y se elige de la lista (flechas y Enter, o clic). No manda nada al servidor mientras no haya texto, espera
 * 250 ms a la última tecla, descarta la búsqueda anterior al teclear otra letra y, si falla, lo dice sin bloquear el formulario (lo buscado se puede teclear a mano). Escape cierra la lista y no
 * el diálogo que contiene al buscador.
 */
export function BuscadorConLista<T extends { id: string }>({
  etiqueta,
  placeholder,
  testId,
  buscar,
  fila,
  sinResultados,
  mensajeDeFallo,
  encontrados,
  onElegir,
  deshabilitado,
}: {
  /** El nombre accesible del campo (no se ve: el `placeholder` ya dice qué se busca). */
  etiqueta: string;
  placeholder: string;
  testId: string;
  buscar: (texto: string, senal: AbortSignal) => Promise<ResultadoDeLaBusqueda<T>>;
  /** Lo que se ve de cada resultado; el nombre accesible de la opción es su texto. */
  fila: (item: T) => ReactNode;
  sinResultados: string;
  mensajeDeFallo: string;
  /** El anuncio para lectores de pantalla, en singular y plural: «1 cliente encontrado», «3 clientes encontrados». */
  encontrados: readonly [singular: string, plural: string];
  onElegir: (item: T) => void;
  deshabilitado?: boolean;
}) {
  const idLista = useId();
  const [texto, setTexto] = useState("");
  const [resultados, setResultados] = useState<T[] | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const contenedor = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const buscado = texto.trim();
    if (buscado === "") return;
    const control = new AbortController();
    const id = window.setTimeout(async () => {
      const res = await buscar(buscado, control.signal);
      if (control.signal.aborted) return;
      if (res.estado === "exito") {
        setResultados(res.items);
        setActivo(0);
        setError(null);
        setAbierto(true);
      } else {
        setError(mensajeDeFallo);
        setResultados(null);
      }
    }, ESPERA_MS);
    return () => {
      window.clearTimeout(id);
      control.abort();
    };
  }, [texto, buscar, mensajeDeFallo]);

  // Un clic fuera cierra la lista.
  useEffect(() => {
    function fuera(e: MouseEvent) {
      if (contenedor.current && !contenedor.current.contains(e.target as Node)) setAbierto(false);
    }
    document.addEventListener("mousedown", fuera);
    return () => document.removeEventListener("mousedown", fuera);
  }, []);

  function elegir(item: T) {
    onElegir(item);
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
      // Cierra la lista, no el diálogo que la contiene (ni borra el texto, que es lo que hace un campo de búsqueda).
      e.preventDefault();
      e.stopPropagation();
      setAbierto(false);
    }
  }

  const hayTexto = texto.trim() !== "";
  const listaVisible = abierto && hayTexto && resultados !== null;
  return (
    <div ref={contenedor} className="relative" data-testid={testId}>
      <label htmlFor={`${idLista}-buscar`} className="sr-only">
        {etiqueta}
      </label>
      <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
      <input
        id={`${idLista}-buscar`}
        type="search"
        role="combobox"
        aria-expanded={abierto}
        aria-controls={listaVisible ? idLista : undefined}
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
        placeholder={placeholder}
        className={cn(CAMPO, "pl-9 text-[13px]")}
      />
      <p role="status" className="sr-only">
        {listaVisible ? (resultados.length === 0 ? sinResultados : `${resultados.length} ${resultados.length === 1 ? encontrados[0] : encontrados[1]}`) : ""}
      </p>
      {error ? (
        <p role="status" className="mt-1 text-[12px] text-muted-foreground">
          {error}
        </p>
      ) : null}
      {listaVisible ? (
        <ul id={idLista} role="listbox" className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-border bg-popover py-1 shadow-md">
          {resultados.length === 0 ? (
            <li role="option" aria-selected={false} aria-disabled className="px-3 py-2 text-[13px] text-muted-foreground">
              {sinResultados}
            </li>
          ) : (
            resultados.map((item, i) => (
              <li
                key={item.id}
                id={`${idLista}-${i}`}
                role="option"
                aria-selected={i === activo}
                onMouseEnter={() => setActivo(i)}
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir(item);
                }}
                className={cn("flex cursor-pointer items-baseline justify-between gap-3 px-3 py-2 text-[13px]", i === activo && "bg-muted")}
              >
                {fila(item)}
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );
}
