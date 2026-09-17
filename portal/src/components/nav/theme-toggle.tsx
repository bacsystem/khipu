"use client";

import { MonitorIcon, MoonIcon, SunIcon } from "lucide-react";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";

const OPCIONES = [
  { valor: "light", etiqueta: "Claro", Icono: SunIcon },
  { valor: "dark", etiqueta: "Oscuro", Icono: MoonIcon },
  { valor: "system", etiqueta: "Sistema", Icono: MonitorIcon },
] as const;

/** `conTexto`: variante ancha con etiqueta visible en cada opción (p. ej. dentro del menú de usuario). */
export function ThemeToggle({ className, conTexto = false }: { className?: string; conTexto?: boolean }) {
  const { theme, setTheme } = useTheme();
  // El tema real solo se conoce en el cliente (localStorage / prefers-color-scheme);
  // hasta montar se pinta sin selección para no desajustar la hidratación.
  const [montado, setMontado] = useState(false);
  useEffect(() => setMontado(true), []);
  const actual = montado ? theme : undefined;

  return (
    <div
      role="radiogroup"
      aria-label="Tema de la interfaz"
      className={cn("inline-flex h-8 items-center rounded-lg border border-border bg-muted p-0.5", conTexto && "w-full", className)}
    >
      {OPCIONES.map(({ valor, etiqueta, Icono }) => {
        const activo = actual === valor;
        return (
          <button
            key={valor}
            type="button"
            role="radio"
            aria-checked={activo}
            title={etiqueta}
            onClick={() => setTheme(valor)}
            className={cn(
              "flex h-7 items-center justify-center gap-1.5 rounded-md transition-colors",
              conTexto ? "min-w-0 flex-1 basis-0 gap-1 px-0 text-[12px] font-medium" : "size-7",
              activo ? "bg-card text-foreground shadow-2xs" : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Icono className={cn("shrink-0", conTexto ? "size-3" : "size-4")} />
            <span className={conTexto ? "truncate" : "sr-only"}>{etiqueta}</span>
          </button>
        );
      })}
    </div>
  );
}
