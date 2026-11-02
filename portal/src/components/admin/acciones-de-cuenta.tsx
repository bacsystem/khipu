"use client";

import { PauseCircleIcon, PlayCircleIcon, WarningIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { type EstadoDeCuentaAdmin, MOTIVO_MAX } from "@/lib/api/admin-suspension";
import { apiRequest } from "@/lib/api/browser";
import { noSeSabeSiLlego } from "@/lib/api/corte-de-red";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA, BOTON_DESTRUCTIVO, BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";
import { Aviso, CabeceraDialogo, PieDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

function empresasAfectadas(n: number, t: Mensajes["admin"]["suspension"]): string {
  if (n === 0) return t.sinEmpresasAfectadas;
  return n === 1 ? t.unaEmpresaAfectada : t.empresasAfectadas.replace("{n}", String(n));
}

/**
 * Suspender o reactivar una cuenta (#182). Pide confirmación en un modal que dice el efecto antes de enviar (el mismo `Dialog` y el mismo patrón
 * que la baja de comprobantes). Suspender corta el servicio de inmediato, así que el modal lo dice sin rodeos y a cuántas empresas alcanza; lo que
 * NO hace (borrar nada, cortar los envíos a SUNAT de lo ya emitido) también. Según `suspendida` se ofrece una sola acción. Va por la fecha de suspensión y no
 * por el estado: una cuenta de baja puede estar suspendida o no, y la baja no corta el acceso, así que suspenderla es justo lo que corta el servicio (H16).
 */
export function AccionesDeCuenta({ id, nombre, suspendida, empresas }: { id: string; nombre: string; suspendida: boolean; empresas: number }) {
  const messages = useMessages();
  const mensajeError = useMensajeError();
  const t = messages.admin.suspension;
  const router = useRouter();
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
    if (noSeSabeSiLlego(res)) {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas (el backend responde 409 si ya estaba hecho).
      setError(interpolar(messages.admin.recargar.estadoDeLaCuenta, { error: res.mensaje ?? mensajeError(res.codigo) }));
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
  const alcance = (
    <div className="grid gap-1.5">
      <p className="font-medium" data-testid="suspension-alcance">
        {empresasAfectadas(empresas, t)}
      </p>
      <ul className="grid list-disc gap-1 pl-4">
        {textos.efectos.map((efecto) => (
          <li key={efecto}>{efecto}</li>
        ))}
      </ul>
    </div>
  );

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger
        className={cn(suspendida ? ACCION_PRINCIPAL : ACCION_SECUNDARIA, !suspendida && "text-destructive hover:text-destructive")}
        data-testid={suspendida ? "reactivar-cuenta" : "suspender-cuenta"}
      >
        <Icono className="size-4" />
        {textos.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="suspension-confirmacion" showCloseButton={!enviando}>
        <CabeceraDialogo icon={Icono} tono={suspendida ? "ok" : "peligro"} titulo={textos.titulo} descripcion={textos.descripcion.replace("{nombre}", nombre)} />

        <div className="grid gap-4 px-5 py-4">
          {/* Suspender corta el servicio: sus efectos van en el `Aviso` de peligro del DS. Reactivar no pesa: `Aviso` neutro. */}
          <Aviso tono={suspendida ? "neutro" : "peligro"} icon={suspendida ? PlayCircleIcon : WarningIcon}>
            {alcance}
          </Aviso>

          {suspendida ? null : (
            <Campo
              id="suspension-motivo"
              etiqueta={t.suspender.motivo}
              ayuda={
                <>
                  {t.suspender.motivoAyuda}
                  {motivo.length >= MOTIVO_MAX ? interpolar(messages.admin.motivoAlMaximo, { max: MOTIVO_MAX }) : ""}
                </>
              }
            >
              <Entrada
                id="suspension-motivo"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                maxLength={MOTIVO_MAX}
                placeholder={t.suspender.motivoEjemplo}
                aria-describedby="suspension-motivo-ayuda"
                autoFocus
              />
            </Campo>
          )}
          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-cuerpo text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <PieDialogo>
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
            {textos.cancelar}
          </button>
          <BotonAsync
            pendiente={enviando}
            onClick={confirmar}
            data-testid="suspension-confirmar"
            icon={<Icono className="size-4" />}
            textoPendiente={textos.enviando}
            className={suspendida ? BOTON_PRIMARIO : BOTON_DESTRUCTIVO}
          >
            {textos.confirmar}
          </BotonAsync>
        </PieDialogo>
      </DialogContent>
    </Dialog>
  );
}
