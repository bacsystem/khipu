import { CheckCircle2Icon, CircleIcon } from "lucide-react";
import Link from "next/link";
import { hayPasosPendientes, type PasoGuia } from "@/lib/empresa/guia-emision";
import { ACCION_SECUNDARIA, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/**
 * «Para empezar a emitir» (#375): lo que le falta a la empresa, en un solo lugar y con un enlace a cada paso. No se muestra cuando
 * está todo hecho. Es un Server Component: al guardar un paso el formulario hace `router.refresh()` y la guía se vuelve a calcular.
 */
export function GuiaParaEmitir({ pasos }: { pasos: PasoGuia[] }) {
  if (!hayPasosPendientes(pasos)) return null;
  const listos = pasos.filter((p) => p.listo).length;

  return (
    <section aria-labelledby="guia-emitir-titulo" data-testid="guia-emitir" className={cn(TARJETA, "p-5")}>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <h2 id="guia-emitir-titulo" className={TITULO_SECCION}>
          Para empezar a emitir
        </h2>
        <span className="font-mono text-[11px] text-muted-foreground" data-testid="guia-emitir-avance">
          {listos} de {pasos.length} listos
        </span>
      </div>
      <ol className="mt-3 divide-y divide-border/60">
        {pasos.map((paso) => (
          <li key={paso.id} data-paso={paso.id} data-listo={paso.listo} className="flex flex-wrap items-center gap-3 py-2.5">
            {paso.listo ? (
              <CheckCircle2Icon className="size-4 shrink-0 text-success-solid" aria-hidden />
            ) : (
              <CircleIcon className="size-4 shrink-0 text-muted-foreground/50" aria-hidden />
            )}
            <div className="min-w-0 flex-1 basis-56">
              <p className="text-[13px] font-medium text-foreground">
                {paso.titulo}
                <span className="sr-only">{paso.listo ? ", listo" : ", pendiente"}</span>
                {paso.recomendado && !paso.listo ? <span className="ml-2 font-mono text-[11px] font-normal text-muted-foreground">recomendado</span> : null}
              </p>
              <p className="text-[12px] leading-relaxed text-muted-foreground">{paso.detalle}</p>
            </div>
            {paso.listo ? null : (
              <Link href={paso.enlace} className={ACCION_SECUNDARIA}>
                {paso.accion}
              </Link>
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}
