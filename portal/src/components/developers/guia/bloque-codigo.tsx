import { BotonCopiar } from "@/components/ui/boton-copiar";

/** Bloque de código con etiqueta y botón de copiar; mismo aspecto que el diálogo "Prueba de emisión". */
export function BloqueCodigo({ titulo, codigo, lenguaje = "json" }: { titulo?: string; codigo: string; lenguaje?: string }) {
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <div className="flex items-center justify-between border-b border-border/60 bg-muted px-3 py-1.5">
        <span className="font-mono text-[11px] text-muted-foreground">{titulo ?? lenguaje}</span>
        <BotonCopiar texto={codigo} etiqueta className="h-6 px-1.5 text-[11px]" />
      </div>
      <pre className="overflow-x-auto bg-code p-3 font-mono text-[12px] leading-relaxed text-code-foreground">{codigo}</pre>
    </div>
  );
}
