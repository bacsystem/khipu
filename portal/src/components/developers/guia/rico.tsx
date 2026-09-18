import { Fragment } from "react";
import { Codigo } from "./prosa";

/** Texto con `código` y **negrita** en línea (subconjunto mínimo de Markdown para las notas de la guía). */
export function Rico({ texto }: { texto: string }) {
  const partes = texto.split(/(`[^`]+`|\*\*[^*]+\*\*)/g).filter(Boolean);
  return (
    <>
      {partes.map((p, i) => {
        if (p.startsWith("`")) return <Codigo key={i}>{p.slice(1, -1)}</Codigo>;
        if (p.startsWith("**"))
          return (
            <strong key={i} className="font-semibold text-foreground">
              {p.slice(2, -2)}
            </strong>
          );
        return <Fragment key={i}>{p}</Fragment>;
      })}
    </>
  );
}
