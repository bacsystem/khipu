"use client";

import { ArchiveIcon, ArchiveRestoreIcon, TriangleAlertIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import type { BajaDeCuentaAdmin } from "@/lib/api/admin-baja";
import { MOTIVO_MAX, type EstadoCuentaAdmin } from "@/lib/api/admin-suspension";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA, AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { messages, mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.baja;

/**
 * Dar de baja o reponer una cuenta (#201). Pide confirmación en un modal que dice el efecto antes de enviar (el mismo `Dialog` y el mismo patrón que
 * suspender, #182). La baja es para el cliente que se fue: lo saca de los listados y del cobro **sin borrar nada**, y el modal lo dice, igual que dice lo
 * que NO hace: no corta el acceso (para eso está suspender). Según el estado que llega de la página se ofrece una sola acción.
 */
export function AccionesDeBaja({ id, nombre, estado }: { id: string; nombre: string; estado: EstadoCuentaAdmin }) {
  const router = useRouter();
  const deBaja = estado === "BAJA";
  const textos = deBaja ? t.reponer : t.darDeBaja;
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure.
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  function cambiarAbierto(valor: boolean) {
    if (enviando) return; // no se cierra mientras se envía: el administrador debe ver el resultado
    setAbierto(valor);
    if (!valor) {
      setMotivo("");
      setError(null);
    }
  }

  async function confirmar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<BajaDeCuentaAdmin>(`/api/admin/cuentas/${id}/${deBaja ? "reponer" : "baja"}`, {
      method: "POST",
      body: deBaja ? undefined : { motivo: motivo.trim() },
    });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas (el backend responde 409 si ya estaba hecho).
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver el estado real de la cuenta.`);
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      // El estado cambió por otro lado (otro administrador): la página muestra el real, y el botón se ajusta solo.
      if (res.codigo === "CUENTA_YA_DE_BAJA" || res.codigo === "CUENTA_NO_DE_BAJA") router.refresh();
      return;
    }
    setAbierto(false);
    setMotivo("");
    router.refresh();
  }

  const Icono = deBaja ? ArchiveRestoreIcon : ArchiveIcon;

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger className={deBaja ? ACCION_PRINCIPAL : ACCION_SECUNDARIA} data-testid={deBaja ? "reponer-cuenta" : "dar-de-baja-cuenta"}>
        <Icono className="size-4" />
        {textos.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="baja-confirmacion" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg", deBaja ? "bg-success text-success-foreground" : "bg-muted text-foreground")}>
              <Icono className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{textos.titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{textos.descripcion.replace("{nombre}", nombre)}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="flex items-start gap-2.5 rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[12px] leading-relaxed text-foreground">
            {deBaja ? null : <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-warning-foreground" />}
            <ul className="grid list-disc gap-1 pl-4">
              {textos.efectos.map((efecto) => (
                <li key={efecto}>{efecto}</li>
              ))}
            </ul>
          </div>

          {deBaja ? null : (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="baja-motivo" className={ETIQUETA_CAMPO}>
                {t.darDeBaja.motivo}
              </label>
              <input
                id="baja-motivo"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                maxLength={MOTIVO_MAX}
                placeholder={t.darDeBaja.motivoEjemplo}
                className={CAMPO}
                autoFocus
              />
              <span className={AYUDA_CAMPO}>
                {t.darDeBaja.motivoAyuda}
                {motivo.length >= MOTIVO_MAX ? ` Llegaste al máximo de ${MOTIVO_MAX}.` : ""}
              </span>
            </div>
          )}
          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
            {textos.cancelar}
          </button>
          <button type="button" disabled={enviando} onClick={confirmar} data-testid="baja-confirmar" className={BOTON_PRIMARIO}>
            <Icono className="size-4" />
            {enviando ? textos.enviando : textos.confirmar}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
