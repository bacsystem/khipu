"use client";

import { PauseCircleIcon, PlayCircleIcon, TriangleAlertIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { type EstadoCuentaAdmin, type EstadoDeCuentaAdmin, MOTIVO_MAX } from "@/lib/api/admin-suspension";
import { apiRequest } from "@/lib/api/browser";
import { AYUDA_CAMPO, BOTON_DESTRUCTIVO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { messages, mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.suspension;

function empresasAfectadas(n: number): string {
  if (n === 0) return t.sinEmpresasAfectadas;
  return n === 1 ? t.unaEmpresaAfectada : t.empresasAfectadas.replace("{n}", String(n));
}

/**
 * Suspender o reactivar una cuenta (#182). Pide confirmación en un modal que dice el efecto antes de enviar (el mismo `Dialog` y el mismo patrón
 * que la baja de comprobantes). Suspender corta el servicio de inmediato, así que el modal lo dice sin rodeos y a cuántas empresas alcanza; lo que
 * NO hace (borrar nada, cortar los envíos a SUNAT de lo ya emitido) también. Según el estado que llega de la página se ofrece una sola acción.
 */
export function AccionesDeCuenta({ id, nombre, estado, empresas }: { id: string; nombre: string; estado: EstadoCuentaAdmin; empresas: number }) {
  const router = useRouter();
  const suspendida = estado === "SUSPENDIDA";
  const textos = suspendida ? t.reactivar : t.suspender;
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
    if (enviando) return; // no se cierra mientras se envía: el usuario debe ver el resultado
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
    const res = await apiRequest<EstadoDeCuentaAdmin>(`/api/admin/cuentas/${id}/${suspendida ? "reactivar" : "suspender"}`, {
      method: "POST",
      body: suspendida ? undefined : { motivo: motivo.trim() },
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
      if (res.codigo === "CUENTA_YA_SUSPENDIDA" || res.codigo === "CUENTA_NO_SUSPENDIDA") router.refresh();
      return;
    }
    setAbierto(false);
    setMotivo("");
    router.refresh();
  }

  const Icono = suspendida ? PlayCircleIcon : PauseCircleIcon;

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger
        className={cn(suspendida ? BOTON_PRIMARIO : BOTON_SECUNDARIO, "h-8 text-xs", !suspendida && "text-destructive hover:text-destructive")}
        data-testid={suspendida ? "reactivar-cuenta" : "suspender-cuenta"}
      >
        <Icono className="size-4" />
        {textos.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="suspension-confirmacion" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg", suspendida ? "bg-success text-success-foreground" : "bg-destructive/10 text-destructive")}>
              <Icono className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{textos.titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{textos.descripcion.replace("{nombre}", nombre)}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className={cn("flex items-start gap-2.5 rounded-lg border px-3 py-2.5 text-[12px] leading-relaxed text-foreground", suspendida ? "border-border bg-muted/50" : "border-destructive/40 bg-destructive/5")}>
            {suspendida ? null : <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-destructive" />}
            <div className="grid gap-1.5">
              <p className="font-medium" data-testid="suspension-alcance">
                {empresasAfectadas(empresas)}
              </p>
              <ul className="grid list-disc gap-1 pl-4">
                {textos.efectos.map((efecto) => (
                  <li key={efecto}>{efecto}</li>
                ))}
              </ul>
            </div>
          </div>

          {suspendida ? null : (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="suspension-motivo" className={ETIQUETA_CAMPO}>
                {t.suspender.motivo}
              </label>
              <input
                id="suspension-motivo"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                maxLength={MOTIVO_MAX}
                placeholder={t.suspender.motivoEjemplo}
                className={cn(CAMPO, "h-9")}
                autoFocus
              />
              <span className={AYUDA_CAMPO}>
                {t.suspender.motivoAyuda}
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
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")}>
            {textos.cancelar}
          </button>
          <button
            type="button"
            disabled={enviando}
            onClick={confirmar}
            data-testid="suspension-confirmar"
            className={cn(suspendida ? BOTON_PRIMARIO : BOTON_DESTRUCTIVO, "h-9 px-3.5 text-[13px]")}
          >
            <Icono className="size-4" />
            {enviando ? textos.enviando : textos.confirmar}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
