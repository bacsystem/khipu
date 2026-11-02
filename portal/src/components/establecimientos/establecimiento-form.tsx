"use client";

import { FloppyDiskIcon, MapPinIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { UbigeoSelector } from "@/components/empresa/ubigeo-selector";
import { apiRequest } from "@/lib/api/browser";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { useMensajeError } from "@/i18n/use-mensaje-error";

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
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-3">
        <Campo id="est-codigo" etiqueta={t.codigo} ayuda={t.codigoAyuda}>
          <Entrada
            id="est-codigo"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value.replace(/\D/g, "").slice(0, 4))}
            placeholder="0002"
            inputMode="numeric"
            pattern="\d{4}"
            required
            disabled={Boolean(existente)}
            autoFocus={!existente}
            mono
            aria-describedby="est-codigo-ayuda"
          />
        </Campo>
        <Campo id="est-nombre" etiqueta={t.nombre} ayuda={t.nombreAyuda} className="sm:col-span-2">
          <Entrada id="est-nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder={t.nombrePlaceholder} maxLength={100} required autoFocus={Boolean(existente)} aria-describedby="est-nombre-ayuda" />
        </Campo>
      </div>

      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-3">
        <UbigeoSelector value={ubigeo} onChange={setUbigeo} inicial={existente?.domicilio} idPrefijo="est" />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-6">
        <Campo id="est-direccion" etiqueta={t.direccion} ayuda={t.direccionAyuda} className="sm:col-span-4">
          <div className="relative">
            <MapPinIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <Entrada
              id="est-direccion"
              value={direccion}
              onChange={(e) => setDireccion(e.target.value)}
              placeholder={t.direccionPlaceholder}
              required
              minLength={3}
              maxLength={200}
              aria-describedby="est-direccion-ayuda"
              className="pl-9"
            />
          </div>
        </Campo>
        {/* «(opcional)» en la etiqueta: la prop `opcional` de `Campo` lo escribe fijo en castellano. */}
        <Campo id="est-urbanizacion" etiqueta={<>{t.urbanizacion} <span className="font-normal text-muted-foreground">{t.opcional}</span></>} className="sm:col-span-2">
          <Entrada id="est-urbanizacion" value={urbanizacion} onChange={(e) => setUrbanizacion(e.target.value)} maxLength={25} />
        </Campo>
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
        <BotonAsync type="submit" pendiente={enviando} icon={<FloppyDiskIcon className="size-4" />} textoPendiente={t.guardando} className={BOTON_PRIMARIO}>
          {existente ? t.guardarCambios : t.registrar}
        </BotonAsync>
      </div>
    </form>
  );
}
