"use client";

import { ApiReferenceReact } from "@scalar/api-reference-react";
import "@scalar/api-reference-react/style.css";
import { useTheme } from "next-themes";
import { type ComponentProps, useEffect } from "react";

// "agent" (botón "Ask AI") no está en los tipos de @scalar/types@0.19.0, pero @scalar/api-reference@1.68.0
// (la versión real que trae api-reference-react@0.9.67) sí lo lee en tiempo de ejecución: revisamos el
// bundle instalado y `agent?.disabled` apaga el botón incluso en localhost, antes que cualquier otra
// condición. Se extiende el tipo público en vez de castear todo el objeto a `any`.
type ScalarConfiguration = ComponentProps<typeof ApiReferenceReact>["configuration"] & {
  agent?: { disabled?: boolean };
};

const CUSTOM_CSS = `
:root {
  --scalar-font: var(--font-sans);
  --scalar-font-code: var(--font-mono);
  --scalar-radius: 8px;
}
/* Scalar pinta con sus propias variables; se mapean a los tokens del tema (globals.css)
   para que la referencia siga el mismo design system y el modo oscuro del portal. */
.light-mode,
.dark-mode {
  --scalar-background-1: var(--card);
  --scalar-background-2: var(--muted);
  --scalar-background-3: var(--secondary);
  --scalar-color-1: var(--foreground);
  --scalar-color-2: var(--muted-foreground);
  --scalar-color-3: var(--muted-foreground);
  --scalar-color-accent: var(--primary);
  --scalar-border-color: var(--border);
  --scalar-button-1: var(--primary);
  --scalar-button-1-hover: var(--primary-light);
  --scalar-button-1-color: var(--primary-foreground);
  --scalar-link-color: var(--primary);
}
/* Scalar no tiene una opción de config para ocultar su atribución "Powered by Scalar":
   es contenido por defecto del slot "description" de ScalarSidebarFooter, un <a> normal
   apuntando a https://www.scalar.com. Se apunta por href porque es estable entre versiones
   (las clases de Tailwind del bundle no lo son). */
a[href="https://www.scalar.com"] {
  display: none;
}
`;

export function ApiReference({ spec, baseServerURL, apiKey }: { spec: Record<string, unknown>; baseServerURL: string; apiKey?: string }) {
  // Scalar aplica "light-mode"/"dark-mode" a document.body (no a un contenedor propio) y su
  // CSS global (import de arriba) estiliza el body directo con esas clases. Sin este cleanup,
  // la clase queda pegada en el body al navegar a otra ruta con el router de Next (sin recarga
  // completa) y el fondo negro de Scalar se filtra al resto del portal.
  useEffect(() => {
    return () => {
      document.body.classList.remove("light-mode", "dark-mode");
    };
  }, []);

  // Scalar tiene su propio modo oscuro; se sincroniza con el tema del portal y se oculta su toggle.
  const { resolvedTheme } = useTheme();

  const configuration: ScalarConfiguration = {
    content: spec,
    theme: "default",
    forceDarkModeState: resolvedTheme === "dark" ? "dark" : "light",
    hideDarkModeToggle: true,
    customCss: CUSTOM_CSS,
    baseServerURL,
    showDeveloperTools: "never",
    mcp: { disabled: true },
    agent: { disabled: true },
    hideClientButton: true,
    authentication: apiKey ? { securitySchemes: { ApiKey: { value: apiKey } } } : undefined,
  };

  return <ApiReferenceReact configuration={configuration} />;
}
