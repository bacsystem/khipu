"use client";

import { MegaphoneIcon, XIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { BannerDeMantenimiento } from "@/components/banner-de-mantenimiento";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { BannerConfigurado } from "@/lib/api/admin-configuracion";
import { apiRequest } from "@/lib/api/browser";
import {
  cuerpoDeAviso,
  DIAS_MAX_DE_AVISO,
  estadoDelAviso,
  limaAInstante,
  TEXTO_DE_AVISO_MAX,
  valoresDeAviso,
  valoresNuevoAviso,
  type ErroresDeAviso,
  type ValoresDeAviso,
} from "@/lib/configuracion-formulario";
import { AYUDA_CAMPO, BOTON_PRIMARIO, CAMPO, ETIQUETA_CAMPO, TARJETA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { messages, mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.configuracion.aviso;

/**
 * El aviso de mantenimiento (#199): su texto y su vigencia, en hora de Lima, y cómo lo verán los clientes. Hay uno solo, y publicar otro reemplaza al anterior. Siempre tiene fin.
 * Acá solo se comprueba que haya texto y fechas; los límites (largo, orden, duración) los pone el backend, una sola vez, y su mensaje se muestra tal cual.
 * {@code ahora} lo da el servidor, para que el valor inicial sea el mismo al renderizar y al hidratar. Que quedó publicado lo dice quien lo contiene ({@code alResultado}): al publicar, la
 * página se recarga y este formulario se vuelve a montar con lo guardado, y el mensaje no puede morir con él.
 */
export function FormularioDeAviso({ banner, ahora, alResultado }: { banner: BannerConfigurado | null; ahora: string; alResultado: (mensaje: string | null) => void }) {
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
    const r = cuerpoDeAviso(valores);
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
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas.
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver el estado real.`);
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
        <h2 id="aviso-titulo" className="font-heading text-lg">
          {t.titulo}
        </h2>
        <p className="max-w-3xl text-[13px] text-muted-foreground">{t.ayuda}</p>
      </div>

      <div className="grid gap-1 rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px]" data-testid="aviso-actual">
        {banner && estado ? (
          <>
            <p data-testid="aviso-estado" data-estado={estado} className="font-medium">
              {t.estado[estado]}
            </p>
            <p data-testid="aviso-publicado-texto">{banner.texto}</p>
            <p className="text-[12px] text-muted-foreground">{t.vigencia.replace("{desde}", formatearFechaHora(banner.desde)).replace("{hasta}", formatearFechaHora(banner.hasta))}</p>
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
            <span id="aviso-texto-error" className="text-[12px] text-destructive">
              {errores.texto}
            </span>
          ) : (
            <span className={AYUDA_CAMPO}>{t.textoAyuda.replace("{max}", String(TEXTO_DE_AVISO_MAX))}</span>
          )}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="aviso-desde" className={ETIQUETA_CAMPO}>
              {t.desde}
            </label>
            <input
              id="aviso-desde"
              data-testid="aviso-desde"
              type="datetime-local"
              value={valores.desde}
              onChange={(e) => poner("desde", e.target.value)}
              aria-invalid={errores.desde ? true : undefined}
              aria-describedby={errores.desde ? "aviso-desde-error" : undefined}
              className={CAMPO}
            />
            {errores.desde ? (
              <span id="aviso-desde-error" className="text-[12px] text-destructive">
                {errores.desde}
              </span>
            ) : null}
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="aviso-hasta" className={ETIQUETA_CAMPO}>
              {t.hasta}
            </label>
            <input
              id="aviso-hasta"
              data-testid="aviso-hasta"
              type="datetime-local"
              value={valores.hasta}
              onChange={(e) => poner("hasta", e.target.value)}
              aria-invalid={errores.hasta ? true : undefined}
              aria-describedby={errores.hasta ? "aviso-hasta-error" : undefined}
              className={CAMPO}
            />
            {errores.hasta ? (
              <span id="aviso-hasta-error" className="text-[12px] text-destructive">
                {errores.hasta}
              </span>
            ) : (
              <span className={AYUDA_CAMPO}>{t.hastaAyuda.replace("{dias}", String(DIAS_MAX_DE_AVISO))}</span>
            )}
          </div>
        </div>
      </div>

      {valores.texto.trim() !== "" && hasta ? (
        <div className="grid gap-1.5" data-testid="aviso-vista-previa">
          <h3 className="text-[12px] font-semibold text-foreground">{t.vistaPrevia}</h3>
          <div className="overflow-hidden rounded-lg border border-border">
            <BannerDeMantenimiento texto={valores.texto.trim()} hasta={hasta} />
          </div>
        </div>
      ) : null}

      {error ? (
        <p ref={alertaRef} tabIndex={-1} data-testid="aviso-error" className="text-sm text-destructive outline-none" role="alert">
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
          />
        ) : null}
      </div>
    </section>
  );
}
