"use client";

import { SearchIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { CONTROL_FILTRO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** Cuánto se espera sin teclear antes de filtrar: cada cambio de la URL pide el listado de nuevo. */
export const ESPERA_DE_BUSQUEDA_MS = 400;

/**
 * Campo de búsqueda del listado (#326). Escribe en su propio estado y, pasada una pausa, cambia el filtro (que vive en la URL): así no se pide el listado por cada
 * tecla ni el campo se reescribe mientras se teclea. Si el valor de la URL cambia desde fuera (otro filtro, «atrás»), el campo lo sigue.
 */
export function BusquedaDelListado({
  id,
  etiqueta,
  ayuda,
  placeholder,
  valor,
  onCambio,
  ancho,
  maxLength,
  soloDigitos = false,
}: {
  id: string;
  etiqueta: string;
  ayuda: string;
  placeholder: string;
  /** El valor del filtro en la URL. */
  valor: string;
  onCambio: (v: string) => void;
  ancho: string;
  maxLength: number;
  soloDigitos?: boolean;
}) {
  const [texto, setTexto] = useState(valor);
  const ultimoDeLaUrl = useRef(valor);
  // El que llama pasa una función nueva en cada render; guardarla aparte evita que cada render reinicie la pausa.
  const alCambiar = useRef(onCambio);
  useEffect(() => {
    alCambiar.current = onCambio;
  });
  // La URL cambió por otro camino: el campo la sigue, pero no pisa lo que se está tecleando si es lo mismo que ya se pidió.
  useEffect(() => {
    if (valor !== ultimoDeLaUrl.current) {
      ultimoDeLaUrl.current = valor;
      // La página quita los espacios de los extremos: si es solo eso, lo tecleado se queda como está.
      setTexto((actual) => (actual.trim() === valor ? actual : valor));
    }
  }, [valor]);
  useEffect(() => {
    if (texto === ultimoDeLaUrl.current) return;
    const espera = setTimeout(() => {
      ultimoDeLaUrl.current = texto;
      alCambiar.current(texto);
    }, ESPERA_DE_BUSQUEDA_MS);
    return () => clearTimeout(espera);
  }, [texto]);

  return (
    <div className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3", ancho)} title={ayuda}>
      <SearchIcon className="size-4 shrink-0 text-muted-foreground/70" />
      <label className="sr-only" htmlFor={id}>
        {etiqueta}
      </label>
      <input
        id={id}
        value={texto}
        onChange={(e) => setTexto(soloDigitos ? e.target.value.replace(/\D/g, "") : e.target.value)}
        inputMode={soloDigitos ? "numeric" : "search"}
        placeholder={placeholder}
        maxLength={maxLength}
        autoComplete="off"
        className="min-w-0 flex-1 bg-transparent text-[12px] text-foreground outline-none placeholder:text-muted-foreground/60"
      />
    </div>
  );
}
