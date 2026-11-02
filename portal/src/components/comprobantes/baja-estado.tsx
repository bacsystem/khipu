"use client";

import { ArrowClockwiseIcon, ProhibitIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { buttonVariants } from "khipu-design-system/ui/button";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest } from "@/lib/api/browser";
import type { Baja } from "@/lib/api/facturas";
import { avisoAltaFueraDePlazo, bajaVencida, etiquetaDeBaja } from "@/lib/baja-etiqueta";
import { TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/**
 * Estado de la comunicación de baja en la ficha. Mientras está en curso (ENVIADA/ERROR_ENVIO/GENERADA) se puede reconsultar
 * a SUNAT (`GET /v1/bajas/{id}` continúa el trámite en el acto) y se refresca sola cada 30 s: antes la ficha decía «SUNAT la
 * está procesando» para siempre y nada en el portal volvía a preguntar.
 */
export function BajaEstado({ baja }: { baja: Baja }) {
  const messages = useMessages();
  const t = messages.comprobantes.estadoBaja;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [consultando, setConsultando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pendiente = baja.estado === "ENVIADA" || baja.estado === "ERROR_ENVIO" || baja.estado === "GENERADA";
  const alta = baja.condicion === "ALTA";
  // #20/#294: la boleta y sus notas van en un resumen diario (RC-…), lo demás en una comunicación de baja (RA-…). El
  // identificador lo dice para las dos: una nota sobre boleta es 07/08, no 03.
  const resumenDiario = baja.identificador.startsWith("RC-");
  const que = baja.tipo_comprobante === "03" ? "boleta" : "nota";
  const vencida = bajaVencida(baja);
  const etiqueta = etiquetaDeBaja(baja, que, messages);

  const actualizar = useCallback(async () => {
    setConsultando(true);
    setError(null);
    try {
      const res = await apiRequest<Baja>(`/api/proxy/bajas/${baja.id}`, { method: "GET" });
      if (res.estado !== "exito") setError(res.mensaje ?? mensajeError(res.codigo));
    } catch {
      setError(t.sinConexion);
    } finally {
      setConsultando(false);
      router.refresh();
    }
  }, [baja.id, router, mensajeError, t.sinConexion]);

  useEffect(() => {
    if (!pendiente) return;
    const t = setInterval(actualizar, 30_000);
    return () => clearInterval(t);
  }, [pendiente, actualizar]);

  // Una baja aceptada (el comprobante quedó anulado) o rechazada dice su estado en una `Alerta` del DS; la tarjeta ya no se tiñe.
  const tono = baja.estado === "ACEPTADA" && !alta ? "error" : baja.estado === "RECHAZADA" ? "aviso" : null;
  const estado = (
    <span className="font-semibold" role="status">
      {etiqueta}
    </span>
  );

  return (
    <section className={cn(TARJETA, "p-5 shadow-xs")} data-testid="baja">
      <div className={cn(TITULO_SECCION, "mb-3 justify-between")}>
        <span className="flex items-center gap-1.5">
          <ProhibitIcon className="size-4" />
          {resumenDiario ? t.resumenDiario : t.comunicacion} {baja.identificador}
          {alta ? t.alta : null}
        </span>
        {pendiente ? (
          <button type="button" onClick={actualizar} disabled={consultando} className={cn(buttonVariants({ variant: "outline", size: "sm" }), "normal-case tracking-normal")}>
            <ArrowClockwiseIcon weight="bold" className={cn("size-3.5", consultando ? "animate-spin" : "")} />
            {consultando ? messages.comprobantes.acciones.consultandoSunat : t.actualizar}
          </button>
        ) : null}
      </div>
      {tono ? (
        <Alerta tono={tono} className="mb-4">
          {estado}
        </Alerta>
      ) : null}
      <div className={cn("grid grid-cols-1 gap-4 text-etiqueta", tono ? "md:grid-cols-3" : "md:grid-cols-4")}>
        {tono ? null : (
          <div className="flex flex-col gap-1">
            <span className="text-etiqueta text-muted-foreground">{t.estado}</span>
            <span className="text-foreground">{estado}</span>
          </div>
        )}
        <div className="flex flex-col gap-1">
          <span className="text-etiqueta text-muted-foreground">{t.motivo}</span>
          <p className="leading-snug text-foreground/80">{baja.motivo}</p>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-etiqueta text-muted-foreground">{t.ticket}</span>
          <span className="font-mono text-foreground/80">{baja.ticket ?? "—"}</span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-etiqueta text-muted-foreground">{vencida ? t.detalle : baja.cdr ? t.cdr : t.ultimoIntento}</span>
          <span className="font-mono text-foreground/80">{baja.cdr ? `${baja.cdr.codigo} · ${baja.cdr.descripcion}` : (baja.ultimo_error ?? "—")}{!baja.cdr && baja.intentos > 1 ? interpolar(t.intentos, { n: baja.intentos }) : ""}</span>
        </div>
      </div>
      {alta && baja.fuera_de_plazo ? (
        <p className="mt-3 text-control text-warning-foreground" data-testid="aviso-fuera-de-plazo">
          {avisoAltaFueraDePlazo(messages)}
        </p>
      ) : null}
      {pendiente ? (
        <p className="mt-3 text-control text-muted-foreground">
          {alta ? interpolar(t.pendienteAlta, { que: t.que[que] }) : t.pendiente}
        </p>
      ) : null}
      {error ? <p className="mt-2 text-cuerpo text-destructive" role="alert">{error}</p> : null}
    </section>
  );
}
