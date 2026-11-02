"use client";

import { ArrowUUpLeftIcon, EnvelopeSimpleIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { Direccion, RemitenteConfigurado } from "@/lib/api/admin-configuracion";
import { apiRequest } from "@/lib/api/browser";
import { noSeSabeSiLlego } from "@/lib/api/corte-de-red";
import { BOTON_PRIMARIO, TARJETA } from "@/lib/estilos";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

/** «khipu <avisos@khipu.pe>» o solo la dirección, como la verá el cliente. */
export function direccionEnPalabras(d: Direccion): string {
  return d.nombre ? `${d.nombre} <${d.email}>` : d.email;
}

/**
 * El remitente de los correos de la plataforma (#199): el nombre con que se muestra, el correo y adónde llegan las respuestas. Vale desde el siguiente correo. Las reglas de cada
 * dato las pone el backend, una sola vez: si algo no sirve, su mensaje se muestra tal cual. No se puede comprobar que el servidor de correo acepte esa dirección, y la pantalla lo dice.
 * Que quedó guardado lo dice quien lo contiene ({@code alResultado}): al guardar, la página se recarga y este formulario se vuelve a montar con lo guardado, y el mensaje no puede morir con él.
 */
export function FormularioDeRemitente({ remitente, alResultado }: { remitente: RemitenteConfigurado; alResultado: (mensaje: string | null) => void }) {
  const messages = useMessages();
  const locale = useLocale();
  const mensajeError = useMensajeError();
  const t = messages.admin.configuracion.correo;
  const router = useRouter();
  const [nombre, setNombre] = useState(remitente.vigente.nombre ?? "");
  const [email, setEmail] = useState(remitente.vigente.email);
  const [responderA, setResponderA] = useState(remitente.vigente.responder_a ?? "");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure.
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  function cambiar(poner: (v: string) => void, valor: string) {
    poner(valor);
    alResultado(null);
  }

  async function guardar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    alResultado(null);
    const res = await apiRequest<RemitenteConfigurado>("/api/admin/configuracion/correo", { method: "PUT", body: { nombre, email, responder_a: responderA } });
    enviandoRef.current = false;
    setEnviando(false);
    if (noSeSabeSiLlego(res)) {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas.
      setError(interpolar(messages.admin.recargar.estado, { error: res.mensaje ?? mensajeError(res.codigo) }));
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    alResultado(t.guardado);
    router.refresh();
  }

  return (
    <section className={cn(TARJETA, "grid gap-4 p-5")} aria-labelledby="correo-titulo">
      <div className="grid gap-1">
        <h2 id="correo-titulo" className="font-heading text-titulo">
          {t.titulo}
        </h2>
        <p className="max-w-3xl text-control text-muted-foreground">{t.ayuda}</p>
      </div>

      <div className="grid gap-1 rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-control">
        <p data-testid="correo-vigente" className="font-medium">
          {t.vigente.replace("{remitente}", direccionEnPalabras(remitente.vigente))}
        </p>
        <p data-testid="correo-origen" className="text-control text-muted-foreground">
          {remitente.personalizado && remitente.actualizado_en ? t.personalizado.replace("{quien}", remitente.actualizado_por ?? messages.admin.unAdministrador).replace("{fecha}", formatearFechaHora(remitente.actualizado_en, locale)) : t.delServidor}
        </p>
      </div>

      <div className="grid max-w-xl gap-4">
        <Campo id="correo-nombre" etiqueta={t.nombre} ayuda={t.nombreAyuda}>
          <Entrada id="correo-nombre" data-testid="correo-nombre" value={nombre} maxLength={100} onChange={(e) => cambiar(setNombre, e.target.value)} autoComplete="off" aria-describedby="correo-nombre-ayuda" />
        </Campo>
        <Campo id="correo-email" etiqueta={t.email} ayuda={t.emailAyuda}>
          <Entrada id="correo-email" data-testid="correo-email" type="email" value={email} onChange={(e) => cambiar(setEmail, e.target.value)} autoComplete="off" aria-describedby="correo-email-ayuda" />
        </Campo>
        <Campo id="correo-responder-a" etiqueta={t.responderA} ayuda={t.responderAyuda}>
          <Entrada
            id="correo-responder-a"
            data-testid="correo-responder-a"
            type="email"
            value={responderA}
            onChange={(e) => cambiar(setResponderA, e.target.value)}
            autoComplete="off"
            aria-describedby="correo-responder-a-ayuda"
          />
        </Campo>
      </div>

      {error ? (
        <p ref={alertaRef} tabIndex={-1} data-testid="correo-error" className="text-cuerpo text-destructive outline-none" role="alert">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <BotonAsync pendiente={enviando} onClick={guardar} data-testid="correo-guardar" icon={<EnvelopeSimpleIcon className="size-4" />} textoPendiente={t.guardando} className={BOTON_PRIMARIO}>
          {t.guardar}
        </BotonAsync>
        {remitente.personalizado ? (
          <DialogoDeAccion
            testId="correo-restablecer"
            boton={t.restablecer.boton}
            icono={ArrowUUpLeftIcon}
            titulo={t.restablecer.titulo}
            descripcion={t.restablecer.descripcion}
            efectos={t.restablecer.efectos.map((e) => e.replace("{remitente}", direccionEnPalabras(remitente.predeterminado)))}
            confirmar={t.restablecer.confirmar}
            enviando={t.restablecer.enviando}
            cancelar={t.restablecer.cancelar}
            ruta="/api/admin/configuracion/correo"
            metodo="DELETE"
          />
        ) : null}
      </div>
    </section>
  );
}
