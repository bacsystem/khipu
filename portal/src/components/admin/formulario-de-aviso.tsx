"use client";

import { MegaphoneIcon, XIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { EntradaFecha } from "khipu-design-system/formularios/entrada-fecha";
import { BannerDeMantenimiento } from "@/components/banner-de-mantenimiento";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { BannerConfigurado } from "@/lib/api/admin-configuracion";
import { apiRequest } from "@/lib/api/browser";
import { noSeSabeSiLlego } from "@/lib/api/corte-de-red";
import {
  cuerpoDeAviso,
  DIAS_MAX_DE_AVISO,
  estadoDelAviso,
  limaAInstante,
  partirFechaHora,
  TEXTO_DE_AVISO_MAX,
  unirFechaHora,
  valoresDeAviso,
  valoresNuevoAviso,
  type ErroresDeAviso,
  type ValoresDeAviso,
} from "@/lib/configuracion-formulario";
import { AYUDA_CAMPO, BOTON_PRIMARIO, CAMPO, ETIQUETA_CAMPO, TARJETA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

/**
 * El aviso de mantenimiento (#199): su texto y su vigencia, en hora de Lima, y cómo lo verán los clientes. Hay uno solo, y publicar otro reemplaza al anterior. Siempre tiene fin.
 * Acá solo se comprueba que haya texto y fechas; los límites (largo, orden, duración) los pone el backend, una sola vez, y su mensaje se muestra tal cual.
 * {@code ahora} lo da el servidor, para que el valor inicial sea el mismo al renderizar y al hidratar. Que quedó publicado lo dice quien lo contiene ({@code alResultado}): al publicar, la
 * página se recarga y este formulario se vuelve a montar con lo guardado, y el mensaje no puede morir con él.
 */
export function FormularioDeAviso({ banner, ahora, alResultado }: { banner: BannerConfigurado | null; ahora: string; alResultado: (mensaje: string | null) => void }) {
  const messages = useMessages();
  const locale = useLocale();
  const mensajeError = useMensajeError();
  const t = messages.admin.configuracion.aviso;
  const router = useRouter();
  const [valores, setValores] = useState<ValoresDeAviso>(banner ? valoresDeAviso(banner) : valoresNuevoAviso(new Date(ahora)));
  const [errores, setErrores] = useState<ErroresDeAviso>({});
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  function poner<K extends keyof ValoresDeAviso>(campo: K, valor: ValoresDeAviso[K]) {
    setValores((v) => ({ ...v, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));
    alResultado(null);
  }

  async function publicar() {
    if (enviandoRef.current) return;
    const r = cuerpoDeAviso(valores, messages);
    if ("errores" in r) {
      setErrores(r.errores);
      return;
    }
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    alResultado(null);
    const res = await apiRequest<BannerConfigurado>("/api/admin/configuracion/banner", { method: "PUT", body: r.cuerpo });
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
    alResultado(t.publicado);
    router.refresh();
  }

  const hasta = limaAInstante(valores.hasta);
  const estado = banner ? estadoDelAviso(banner, new Date(ahora)) : null;

  return (
    <section className={cn(TARJETA, "grid gap-4 p-5")} aria-labelledby="aviso-titulo">
      <div className="grid gap-1">
        <h2 id="aviso-titulo" className="font-heading text-titulo">
          {t.titulo}
        </h2>
        <p className="max-w-3xl text-control text-muted-foreground">{t.ayuda}</p>
      </div>

      <div className="grid gap-1 rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-control" data-testid="aviso-actual">
        {banner && estado ? (
          <>
            <p data-testid="aviso-estado" data-estado={estado} className="font-medium">
              {t.estado[estado]}
            </p>
            <p data-testid="aviso-publicado-texto">{banner.texto}</p>
            <p className="text-control text-muted-foreground">{t.vigencia.replace("{desde}", formatearFechaHora(banner.desde, locale)).replace("{hasta}", formatearFechaHora(banner.hasta, locale))}</p>
          </>
        ) : (
          <p data-testid="aviso-ninguno" className="text-muted-foreground">
            {t.ninguno}
          </p>
        )}
      </div>

      <div className="grid max-w-xl gap-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="aviso-texto" className={ETIQUETA_CAMPO}>
            {t.texto}
          </label>
          <input
            id="aviso-texto"
            data-testid="aviso-texto"
            value={valores.texto}
            maxLength={TEXTO_DE_AVISO_MAX}
            onChange={(e) => poner("texto", e.target.value)}
            autoComplete="off"
            aria-invalid={errores.texto ? true : undefined}
            aria-describedby={errores.texto ? "aviso-texto-error" : undefined}
            className={CAMPO}
          />
          {errores.texto ? (
            <span id="aviso-texto-error" className="text-control text-destructive">
              {errores.texto}
            </span>
          ) : (
            <span className={AYUDA_CAMPO}>{t.textoAyuda.replace("{max}", String(TEXTO_DE_AVISO_MAX))}</span>
          )}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <FechaYHora id="aviso-desde" etiqueta={t.desde} etiquetaHora={t.horaDesde} valor={valores.desde} onCambio={(v) => poner("desde", v)} error={errores.desde} />
          <FechaYHora
            id="aviso-hasta"
            etiqueta={t.hasta}
            etiquetaHora={t.horaHasta}
            valor={valores.hasta}
            onCambio={(v) => poner("hasta", v)}
            error={errores.hasta}
            ayuda={t.hastaAyuda.replace("{dias}", String(DIAS_MAX_DE_AVISO))}
          />
        </div>
      </div>

      {valores.texto.trim() !== "" && hasta ? (
        <div className="grid gap-1.5" data-testid="aviso-vista-previa">
          <h3 className="text-control font-semibold text-foreground">{t.vistaPrevia}</h3>
          <div className="overflow-hidden rounded-lg border border-border">
            <BannerDeMantenimiento texto={valores.texto.trim()} hasta={hasta} />
          </div>
        </div>
      ) : null}

      {error ? (
        <p ref={alertaRef} tabIndex={-1} data-testid="aviso-error" className="text-cuerpo text-destructive outline-none" role="alert">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button type="button" disabled={enviando} onClick={publicar} data-testid="aviso-publicar" className={BOTON_PRIMARIO}>
          <MegaphoneIcon className="size-4" />
          {enviando ? t.publicando : banner ? t.reemplazar : t.publicar}
        </button>
        {banner ? (
          <DialogoDeAccion
            testId="aviso-retirar"
            boton={t.retirar.boton}
            icono={XIcon}
            titulo={t.retirar.titulo}
            descripcion={t.retirar.descripcion}
            efectos={t.retirar.efectos}
            confirmar={t.retirar.confirmar}
            enviando={t.retirar.enviando}
            cancelar={t.retirar.cancelar}
            ruta="/api/admin/configuracion/banner"
            metodo="DELETE"
            estadoViejo={["NO_ENCONTRADO"]}
            alExito={() => alResultado(t.retirado)}
          />
        ) : null}
      </div>
    </section>
  );
}

/**
 * Una fecha y hora de Lima del aviso: la fecha del design system (su calendario) y la hora al lado. El design system no tiene fecha y hora en un solo campo, y el
 * `datetime-local` del navegador no se puede estilizar. El valor sigue siendo el texto «2026-10-15T22:00» de siempre ({@link unirFechaHora}): lo que se envía no cambia.
 */
function FechaYHora({
  id,
  etiqueta,
  etiquetaHora,
  valor,
  onCambio,
  error,
  ayuda,
}: {
  id: string;
  etiqueta: string;
  etiquetaHora: string;
  valor: string;
  onCambio: (valor: string) => void;
  error?: string;
  ayuda?: string;
}) {
  const messages = useMessages();
  const { fecha, hora } = partirFechaHora(valor);
  const idError = `${id}-error`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={ETIQUETA_CAMPO}>
        {etiqueta}
      </label>
      <div className="flex gap-2">
        <EntradaFecha
          id={id}
          valor={fecha}
          onCambio={(f) => onCambio(unirFechaHora(f, hora))}
          placeholder={messages.comun.elegirFecha}
          invalido={Boolean(error)}
          describidoPor={error ? idError : undefined}
          className="flex-1"
        />
        <input
          id={`${id}-hora`}
          data-testid={`${id}-hora`}
          type="time"
          value={hora}
          onChange={(e) => onCambio(unirFechaHora(fecha, e.target.value))}
          aria-label={etiquetaHora}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? idError : undefined}
          className={cn(CAMPO, "w-28 tabular-nums")}
        />
      </div>
      {error ? (
        <span id={idError} className="text-control text-destructive">
          {error}
        </span>
      ) : ayuda ? (
        <span className={AYUDA_CAMPO}>{ayuda}</span>
      ) : null}
    </div>
  );
}
