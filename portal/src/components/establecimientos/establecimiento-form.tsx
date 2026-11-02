"use client";

import { FloppyDiskIcon, MapPinIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { UbigeoSelector } from "@/components/empresa/ubigeo-selector";
import { apiRequest } from "@/lib/api/browser";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

/**
 * Alta (`POST`) o edición (`PUT`, código fijo) de un establecimiento anexo: código de la ficha RUC, nombre y domicilio
 * con el mismo selector de ubigeo que los datos fiscales. El `0000` no pasa por aquí: se edita en Empresa.
 */
export function EstablecimientoForm({ existente, onGuardado, onCancelar }: { existente?: Establecimiento | null; onGuardado?: () => void; onCancelar?: () => void }) {
  const messages = useMessages();
  const t = messages.establecimientos.form;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [codigo, setCodigo] = useState(existente?.codigo ?? "");
  const [nombre, setNombre] = useState(existente?.nombre ?? "");
  const [ubigeo, setUbigeo] = useState(existente?.domicilio.ubigeo ?? "");
  const [direccion, setDireccion] = useState(existente?.domicilio.direccion ?? "");
  const [urbanizacion, setUrbanizacion] = useState(existente?.domicilio.urbanizacion ?? "");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!ubigeo) {
      setError(t.errorUbigeo);
      return;
    }
    setEnviando(true);
    setError(null);
    const body = { codigo, nombre: nombre.trim(), domicilio: { ubigeo, direccion: direccion.trim(), urbanizacion: urbanizacion.trim() || null } };
    const res = await apiRequest<Establecimiento>(existente ? `/api/proxy/empresa/establecimientos/${existente.codigo}` : "/api/proxy/empresa/establecimientos", {
      method: existente ? "PUT" : "POST",
      body,
    });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    router.refresh();
    onGuardado?.();
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" data-testid="establecimiento-form">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="est-codigo" className={ETIQUETA_CAMPO}>
            {t.codigo}
          </label>
          <input
            id="est-codigo"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value.replace(/\D/g, "").slice(0, 4))}
            placeholder="0002"
            inputMode="numeric"
            pattern="\d{4}"
            required
            disabled={Boolean(existente)}
            autoFocus={!existente}
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA_CAMPO}>{t.codigoAyuda}</span>
        </div>
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="est-nombre" className={ETIQUETA_CAMPO}>
            {t.nombre}
          </label>
          <input id="est-nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder={t.nombrePlaceholder} maxLength={100} required autoFocus={Boolean(existente)} className={CAMPO} />
          <span className={AYUDA_CAMPO}>{t.nombreAyuda}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <UbigeoSelector value={ubigeo} onChange={setUbigeo} inicial={existente?.domicilio} idPrefijo="est" />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-6">
        <div className="flex flex-col gap-1.5 sm:col-span-4">
          <label htmlFor="est-direccion" className={ETIQUETA_CAMPO}>
            {t.direccion}
          </label>
          <div className="relative">
            <MapPinIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              id="est-direccion"
              value={direccion}
              onChange={(e) => setDireccion(e.target.value)}
              placeholder={t.direccionPlaceholder}
              required
              minLength={3}
              maxLength={200}
              className={cn(CAMPO, "pl-9")}
            />
          </div>
          <span className={AYUDA_CAMPO}>{t.direccionAyuda}</span>
        </div>
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="est-urbanizacion" className={ETIQUETA_CAMPO}>
            {t.urbanizacion} <span className="font-normal text-muted-foreground">{t.opcional}</span>
          </label>
          <input id="est-urbanizacion" value={urbanizacion} onChange={(e) => setUrbanizacion(e.target.value)} maxLength={25} className={CAMPO} />
        </div>
      </div>

      {error ? (
        <p className="text-cuerpo text-destructive" role="alert">
          {error}
        </p>
      ) : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            {messages.comun.cancelar}
          </button>
        ) : null}
        <button type="submit" disabled={enviando} className={BOTON_PRIMARIO}>
          <FloppyDiskIcon className="size-4" />
          {enviando ? t.guardando : existente ? t.guardarCambios : t.registrar}
        </button>
      </div>
    </form>
  );
}
