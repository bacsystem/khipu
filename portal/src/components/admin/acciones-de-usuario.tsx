"use client";

import { EyeIcon, KeyRoundIcon, MailCheckIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import type { DestinatarioAdmin } from "@/lib/api/admin-acceso";
import { apiRequest } from "@/lib/api/browser";
import { BOTON_PRIMARIO_PIE, BOTON_SECUNDARIO_PIE } from "@/lib/estilos";
import { messages, mensajeError } from "@/lib/messages";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const t = messages.admin.detalle.acceso;

type Tipo = "restablecer" | "verificar";

const RUTA: Record<Tipo, string> = { restablecer: "restablecimiento", verificar: "verificacion" };
const ICONO = { restablecer: KeyRoundIcon, verificar: MailCheckIcon } as const;

/**
 * Un correo de acceso para un usuario (#183): restablecer su contraseña o reenviarle la verificación. Pide confirmación en un modal que dice
 * a quién llega el correo y que el administrador nunca ve ni define la contraseña. El resultado (enviado, o por qué no) se queda en el modal
 * hasta que el administrador lo cierre: un correo que no salió no puede pasar desapercibido.
 */
function AccionDeAcceso({ tipo, cuentaId, usuarioId, correo }: { tipo: Tipo; cuentaId: string; usuarioId: string; correo: string }) {
  const router = useRouter();
  const textos = t[tipo];
  const Icono = ICONO[tipo];
  const [abierto, setAbierto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviadoA, setEnviadoA] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure y mandarían dos correos.
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error || enviadoA) alertaRef.current?.focus();
  }, [error, enviadoA]);

  function cambiarAbierto(valor: boolean) {
    if (enviando) return;
    setAbierto(valor);
    if (!valor) {
      setError(null);
      setEnviadoA(null);
    }
  }

  async function confirmar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<DestinatarioAdmin>(`/api/admin/cuentas/${cuentaId}/usuarios/${usuarioId}/${RUTA[tipo]}`, { method: "POST" });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el correo salió: se dice, y no se reintenta solo (reintentar manda otro correo).
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Revisa la bitácora de la cuenta antes de volver a intentarlo.`);
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setEnviadoA(res.datos.correo);
    // La bitácora de la página registra el envío: se refresca para que aparezca.
    router.refresh();
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger className={cn(buttonVariants({ variant: "outline", size: "sm" }))} data-testid={`${tipo}-usuario`} aria-label={`${textos.boton}: ${correo}`}>
        <Icono className="size-3.5" />
        {textos.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="acceso-confirmacion" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground">
              <Icono className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{textos.titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{textos.descripcion.replace("{correo}", correo)}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          {enviadoA ? (
            <p ref={alertaRef} tabIndex={-1} className="text-sm text-success outline-none" role="status" data-testid="acceso-hecho">
              {textos.hecho.replace("{correo}", enviadoA)}
            </p>
          ) : (
            <ul className="grid list-disc gap-1 rounded-lg border border-border bg-muted/50 py-2.5 pr-3 pl-7 text-[12px] leading-relaxed text-foreground">
              {textos.efectos.map((efecto) => (
                <li key={efecto}>{efecto}</li>
              ))}
            </ul>
          )}
          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO_PIE}>
            {enviadoA ? t.cerrar : textos.cancelar}
          </button>
          {enviadoA ? null : (
            <button type="button" disabled={enviando} onClick={confirmar} data-testid="acceso-confirmar" className={BOTON_PRIMARIO_PIE}>
              <Icono className="size-4" />
              {enviando ? textos.enviando : textos.confirmar}
            </button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Entrar al portal como este usuario (#184). La función más sensible del backoffice, y el diálogo lo dice: dura 15 minutos y no se renueva, **solo se puede
 * mirar** (ni contraseña, ni credenciales SOL, ni API keys, ni emitir), queda en la bitácora a nombre del administrador y el cliente lo ve en su historial.
 * También avisa que reemplaza la sesión de cliente que hubiera en este navegador. Al abrirse la sesión se navega al portal del cliente, donde un aviso
 * permanente dice que se está actuando como él. `irA` es para las pruebas: por defecto navega de verdad.
 */
function Impersonar({ cuentaId, usuarioId, correo, irA }: { cuentaId: string; usuarioId: string; correo: string; irA: (url: string) => void }) {
  const i = t.impersonar;
  return (
    <DialogoDeAccion
      testId="impersonar-usuario"
      boton={i.boton}
      icono={EyeIcon}
      chico
      titulo={i.titulo}
      descripcion={i.descripcion.replace("{correo}", correo)}
      efectos={i.efectos}
      advertencia
      confirmar={i.confirmar}
      enviando={i.enviando}
      cancelar={t.restablecer.cancelar}
      ruta={`/api/admin/cuentas/${cuentaId}/usuarios/${usuarioId}/impersonar`}
      alExito={() => irA("/comprobantes")}
    />
  );
}

/**
 * Las acciones que corresponden a un usuario: un usuario inactivo no recibe ninguna (el backend responde 409 `USUARIO_INACTIVO`), la verificación solo se
 * ofrece a quien todavía no verificó su correo, y entrar como el usuario (#184) a cualquiera que esté activo.
 */
export function AccionesDeUsuario({
  cuentaId,
  usuarioId,
  correo,
  activo,
  verificado,
  irA = (url) => window.location.assign(url),
}: {
  cuentaId: string;
  usuarioId: string;
  correo: string;
  activo: boolean;
  verificado: boolean;
  irA?: (url: string) => void;
}) {
  if (!activo) return null;
  return (
    <div className="flex flex-wrap justify-end gap-1.5">
      <AccionDeAcceso tipo="restablecer" cuentaId={cuentaId} usuarioId={usuarioId} correo={correo} />
      {verificado ? null : <AccionDeAcceso tipo="verificar" cuentaId={cuentaId} usuarioId={usuarioId} correo={correo} />}
      <Impersonar cuentaId={cuentaId} usuarioId={usuarioId} correo={correo} irA={irA} />
    </div>
  );
}
