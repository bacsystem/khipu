import Link from "next/link";
import { Fragment, type ReactNode } from "react";
import { Codigo } from "./prosa";

const MARCAS = /(`[^`]+`|\*\*[^*]+\*\*|\*[^*\s][^*]*\*|\[[^\]]+\]\([^)\s]+\))/g;
const ENLACE = /^\[([^\]]+)\]\(([^)\s]+)\)$/;

type Clases = { negrita: string; enlace: string };

function piezas(texto: string, clases: Clases): ReactNode[] {
  return texto
    .split(MARCAS)
    .filter(Boolean)
    .map((p, i) => {
      if (p.startsWith("`")) return <Codigo key={i}>{p.slice(1, -1)}</Codigo>;
      if (p.startsWith("**"))
        return (
          <strong key={i} className={clases.negrita || undefined}>
            {piezas(p.slice(2, -2), clases)}
          </strong>
        );
      if (p.startsWith("*") && p.length > 2) return <em key={i}>{piezas(p.slice(1, -1), clases)}</em>;
      const enlace = p.startsWith("[") ? ENLACE.exec(p) : null;
      if (enlace) {
        const [, etiqueta, href] = enlace;
        // Un ancla de la misma página no pasa por el router.
        return href.startsWith("#") ? (
          <a key={i} className={clases.enlace} href={href}>
            {piezas(etiqueta, clases)}
          </a>
        ) : (
          <Link key={i} className={clases.enlace} href={href}>
            {piezas(etiqueta, clases)}
          </Link>
        );
      }
      return <Fragment key={i}>{p}</Fragment>;
    });
}

/**
 * Texto con un subconjunto mínimo de Markdown en línea: `código`, **negrita**, *cursiva* y [enlace](/ruta). Así se escriben en `messages`
 * las notas y los párrafos de la documentación para developers (#25): el texto se traduce entero y el formato viaja con él. `negrita` y
 * `enlace` son las clases de esos elementos, para respetar el estilo de cada página (`negrita=""` deja el `<strong>` sin clase).
 */
export function Rico({
  texto,
  negrita = "font-semibold text-foreground",
  enlace = "text-primary hover:underline",
}: {
  texto: string;
  negrita?: string;
  enlace?: string;
}) {
  return <>{piezas(texto, { negrita, enlace })}</>;
}
