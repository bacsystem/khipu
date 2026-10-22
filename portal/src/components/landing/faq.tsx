import { Plus } from "lucide-react";
import { useMessages } from "next-intl";

/** El orden de las preguntas; el texto de cada una está en `landing.faq.<clave>` de messages. */
const PREGUNTAS = ["sunatNoResponde", "instalar", "certificado", "planGratis", "boletas"] as const;

export function Faq() {
  const t = useMessages().landing.faq;
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <h2 className="font-heading text-3xl">{t.titulo}</h2>
      <div className="mt-8 divide-y divide-border border-t border-border">
        {PREGUNTAS.map((clave) => (
          <details key={clave} className="group py-5">
            <summary className="flex cursor-pointer list-none items-start justify-between gap-4 font-heading text-base leading-snug text-foreground marker:content-none">
              {t[clave].pregunta}
              <Plus className="mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform duration-200 group-open:rotate-45" />
            </summary>
            <p className="mt-3 max-w-xl text-sm text-muted-foreground">{t[clave].respuesta}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
