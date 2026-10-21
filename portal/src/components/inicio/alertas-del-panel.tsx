import { CheckCircle2Icon } from "lucide-react";
import Link from "next/link";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { ACCION_SECUNDARIA } from "@/lib/estilos";
import type { AlertaDelPanel } from "@/lib/inicio/panel";

/**
 * Los avisos del panel de inicio (#24): certificado, credenciales SOL y comprobantes del mes que piden algo, cada uno con un enlace a donde se resuelve. Sin avisos lo dice en
 * una línea; si algo no se pudo revisar (`incompleto`), lo dice también, para que «sin avisos» no se lea como «todo en orden». Con la guía «Para empezar a emitir» con pasos
 * pendientes (`guiaPendiente`) tampoco se dice que todo está en orden: lo que falta lo dice la guía.
 */
export function AlertasDelPanel({ alertas, incompleto, guiaPendiente = false }: { alertas: AlertaDelPanel[]; incompleto: boolean; guiaPendiente?: boolean }) {
  return (
    <section aria-labelledby="alertas-del-panel-titulo" data-testid="alertas-del-panel" className="grid gap-2">
      <h2 id="alertas-del-panel-titulo" className="sr-only">
        Avisos
      </h2>
      {alertas.map((a) => (
        <div key={a.id} data-testid="alerta-del-panel" data-alerta={a.id} data-tono={a.tono}>
          <Alerta
            tono={a.tono}
            titulo={a.titulo}
            accion={
              <Link href={a.enlace} className={ACCION_SECUNDARIA}>
                {a.accion}
              </Link>
            }
          >
            {a.detalle}
          </Alerta>
        </div>
      ))}
      {incompleto ? (
        <p data-testid="alertas-incompletas" className="text-[12px] text-muted-foreground">
          No se pudo revisar todo: puede haber avisos que no se ven aquí. Vuelve a cargar la página en un momento.
        </p>
      ) : alertas.length === 0 && !guiaPendiente ? (
        <p data-testid="alertas-sin-avisos" className="flex items-center gap-1.5 text-[12px] text-success-foreground">
          <CheckCircle2Icon className="size-3.5 shrink-0" aria-hidden />
          Sin avisos: certificado, credenciales SOL y comprobantes recientes en orden.
        </p>
      ) : null}
    </section>
  );
}
