"use client";

import { TriangleAlertIcon, type LucideIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA, BOTON_DESTRUCTIVO, BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type DialogoDeAccionProps = {
  /** Base de los `data-testid`: el botón es `{testId}`, el diálogo `{testId}-dialogo` y el de confirmar `{testId}-confirmar`. */
  testId: string;
  boton: string;
  icono: LucideIcon;
  /** `peligro` para lo que corta o pesa mucho; `secundario` para lo corriente; `primario` para lo que devuelve algo a su sitio. */
  tono?: "primario" | "secundario" | "peligro";
  chico?: boolean;
  titulo: string;
  descripcion: string;
  /** Lo que hace y lo que NO hace, antes de confirmar. */
  efectos: string[];
  /** Con una advertencia (triángulo) cuando lo que sigue pesa; sin ella para lo reversible. */
  advertencia?: boolean;
  confirmar: string;
  enviando: string;
  cancelar: string;
  /** Ruta del BFF a la que se hace el pedido. */
  ruta: string;
  /** Por defecto POST; borrar un plan es un DELETE (#190). */
  metodo?: "POST" | "DELETE";
  /** Cuerpo del POST; una función porque puede depender de campos que el diálogo muestra. */
  cuerpo?: () => unknown;
  /**
   * Códigos de error que dicen que el estado de la página quedó viejo (otro administrador ya hizo lo mismo): se muestra el mensaje y se recarga para que
   * la página muestre el estado real.
   */
  estadoViejo?: string[];
  /**
   * Se llama con el mensaje del backend cuando el error es de los de {@link estadoViejo}, justo antes de recargar: si la recarga hace desaparecer lo que este diálogo
   * accionaba (la fila ya no está), el mensaje se perdería con él; quien llama puede guardarlo en un lugar que sobreviva a la recarga.
   */
  alEstadoViejo?: (mensaje: string) => void;
  /** Campos propios de la acción (un motivo, un selector…), dentro del diálogo. */
  children?: ReactNode;
  /** Se llama al cerrar el diálogo, para que la acción limpie sus campos. */
  alCerrar?: () => void;
  /** Lo que se hace con la respuesta si fue bien; por defecto se cierra y se recarga la página. */
  alExito?: (datos: unknown) => void;
};

/**
 * Un diálogo de confirmación para una acción del backoffice: dice el efecto antes de enviar (qué hace y qué NO hace), manda un POST (o DELETE) al BFF y muestra el
 * resultado sin cerrarse si falló. Reúne lo que ya aprendieron las acciones de suspender, dar de baja y los correos de acceso: la guardia contra el doble
 * clic es un ref (dos clics en el mismo tick leen el `enviando` viejo del closure), el diálogo no se cierra mientras envía (Escape incluido: el administrador
 * debe ver el resultado), y un corte de red no se reintenta a ciegas, porque no se sabe si el cambio llegó.
 */
export function DialogoDeAccion(p: DialogoDeAccionProps) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  function cambiarAbierto(valor: boolean) {
    if (enviando) return;
    setAbierto(valor);
    if (!valor) {
      setError(null);
      p.alCerrar?.();
    }
  }

  async function confirmar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<unknown>(p.ruta, { method: p.metodo ?? "POST", body: p.cuerpo?.() });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver el estado real.`);
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      if (res.codigo && p.estadoViejo?.includes(res.codigo)) {
        p.alEstadoViejo?.(res.mensaje ?? mensajeError(res.codigo));
        router.refresh();
      }
      return;
    }
    setAbierto(false);
    p.alCerrar?.();
    p.alExito?.(res.datos);
    router.refresh();
  }

  const Icono = p.icono;
  const tono = p.tono ?? "secundario";
  // Chico vive en una fila de tabla (h-7, como el resto de las acciones de fila); si no, en la ficha, junto a las demás acciones (h-8).
  const claseDelBoton = p.chico
    ? buttonVariants({ variant: tono === "primario" ? "default" : "outline", size: "sm" })
    : tono === "primario"
      ? ACCION_PRINCIPAL
      : ACCION_SECUNDARIA;

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger
        className={cn(claseDelBoton, tono === "peligro" && "text-destructive hover:text-destructive")}
        data-testid={p.testId}
      >
        <Icono className={p.chico ? "size-3.5" : "size-4"} />
        {p.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid={`${p.testId}-dialogo`} showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg", tono === "peligro" ? "bg-destructive/10 text-destructive" : "bg-muted text-foreground")}>
              <Icono className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{p.titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{p.descripcion}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className={cn("flex items-start gap-2.5 rounded-lg border px-3 py-2.5 text-[12px] leading-relaxed text-foreground", tono === "peligro" ? "border-destructive/40 bg-destructive/5" : "border-border bg-muted/50")}>
            {p.advertencia ? <TriangleAlertIcon className={cn("mt-0.5 size-4 shrink-0", tono === "peligro" ? "text-destructive" : "text-warning-foreground")} /> : null}
            <ul className="grid list-disc gap-1 pl-4">
              {p.efectos.map((efecto) => (
                <li key={efecto}>{efecto}</li>
              ))}
            </ul>
          </div>
          {p.children}
          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
            {p.cancelar}
          </button>
          <button
            type="button"
            disabled={enviando}
            onClick={confirmar}
            data-testid={`${p.testId}-confirmar`}
            className={tono === "peligro" ? BOTON_DESTRUCTIVO : BOTON_PRIMARIO}
          >
            <Icono className="size-4" />
            {enviando ? p.enviando : p.confirmar}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
