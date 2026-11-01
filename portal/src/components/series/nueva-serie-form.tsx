"use client";

import { CaretDownIcon, FloppyDiskIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

/**
 * Ocho dígitos: la regla 1001 de SUNAT define el ID del comprobante como `[FB][A-Z0-9]{3}-[0-9]{1,8}`. Pasado ese
 * techo el comprobante se firma igual y SUNAT lo rechaza con el correlativo ya consumido, y la serie no se puede
 * editar. El backend lo rechaza también; acá se evita el viaje.
 */
export const CORRELATIVO_MAXIMO = 99_999_999;

/**
 * El `value` de un `type=number` es texto libre: «1e30», «3.5», «-1» y «» llegan acá si el navegador no los filtra.
 * Devuelve `null` cuando no es un correlativo usable, para avisar en vez de recortar: recortar al tope crearía una
 * serie agotada de entrada, que tampoco sirve para emitir.
 */
export function correlativoValido(valor: string): number | null {
  if (!/^\d+$/.test(valor.trim())) return null;
  const n = Number(valor);
  return n <= CORRELATIVO_MAXIMO ? n : null;
}

// #39: la guía de remisión (09, 31) se emite por la API (POST /v1/guias-remision); su serie se puede crear aquí. Las etiquetas, en `series.form.tipos`.
const TIPOS = ["01", "03", "07", "08", "09", "31"] as const;

export function NuevaSerieForm({ onGuardado, onCancelar }: { onGuardado?: () => void; onCancelar?: () => void }) {
  const messages = useMessages();
  const t = messages.series.form;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [tipo, setTipo] = useState("01");
  const [serie, setSerie] = useState("");
  const [correlativo, setCorrelativo] = useState("0");
  const [establecimiento, setEstablecimiento] = useState("0000");
  const [establecimientos, setEstablecimientos] = useState<Establecimiento[] | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // `enviando` es estado: React lo aplica al final del tick, así que dos clics en el mismo tick pasan los dos por
  // `onSubmit` y crean la serie dos veces (la segunda choca con el 409 y confunde). El ref corta en el acto.
  const enviandoRef = useRef(false);

  // Puntos de emisión de la empresa (#80): el 0000 siempre existe; los anexos dados de baja no se ofrecen.
  useEffect(() => {
    let vigente = true;
    apiRequest<Establecimiento[]>("/api/proxy/empresa/establecimientos", { method: "GET" })
      .then((res) => {
        if (vigente) setEstablecimientos(res.estado === "exito" && res.datos ? res.datos.filter((e) => e.activo) : []);
      })
      .catch(() => {
        if (vigente) setEstablecimientos([]);
      });
    return () => {
      vigente = false;
    };
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (enviandoRef.current) return;
    setError(null);
    const correlativoInicial = correlativoValido(correlativo);
    if (correlativoInicial === null) {
      setError(t.errorCorrelativo);
      return;
    }
    enviandoRef.current = true;
    setEnviando(true);
    const res = await apiRequest("/api/proxy/series", {
      method: "POST",
      body: { tipo, serie: serie.toUpperCase(), correlativo_inicial: correlativoInicial, establecimiento },
    });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setSerie("");
    setCorrelativo("0");
    router.refresh();
    onGuardado?.();
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="tipo-serie" className={ETIQUETA_CAMPO}>
          {t.tipoComprobante}
        </label>
        <div className="relative">
          <select id="tipo-serie" value={tipo} onChange={(e) => setTipo(e.target.value)} className={cn(CAMPO, "appearance-none pr-8")}>
            {TIPOS.map((codigo) => (
              <option key={codigo} value={codigo}>
                {t.tipos[codigo]}
              </option>
            ))}
          </select>
          <CaretDownIcon weight="bold" className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
        </div>
        <span className={AYUDA_CAMPO}>{t.catalogo}</span>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="serie" className={ETIQUETA_CAMPO}>
            {t.codigoSerie}
          </label>
          <input
            id="serie"
            value={serie}
            onChange={(e) => setSerie(e.target.value.toUpperCase())}
            placeholder={t.codigoPlaceholder}
            maxLength={4}
            required
            autoFocus
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA_CAMPO}>{t.codigoAyuda}</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="correlativo" className={ETIQUETA_CAMPO}>
            {t.ultimoNumero}
          </label>
          <input
            id="correlativo"
            type="number"
            min={0}
            max={CORRELATIVO_MAXIMO}
            step={1}
            value={correlativo}
            onChange={(e) => setCorrelativo(e.target.value)}
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA_CAMPO}>{t.ultimoNumeroAyuda}</span>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="serie-establecimiento" className={ETIQUETA_CAMPO}>
          {t.establecimiento}
        </label>
        <div className="relative">
          <select
            id="serie-establecimiento"
            value={establecimiento}
            onChange={(e) => setEstablecimiento(e.target.value)}
            className={cn(CAMPO, "appearance-none pr-8")}
            data-testid="serie-establecimiento"
          >
            <option value="0000">{t.domicilioFiscal}</option>
            {(establecimientos ?? [])
              .filter((e) => !e.principal)
              .map((e) => (
                <option key={e.codigo} value={e.codigo}>
                  {e.codigo} · {e.nombre}
                </option>
              ))}
          </select>
          <CaretDownIcon weight="bold" className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
        </div>
        <span className={AYUDA_CAMPO}>{t.establecimientoAyuda}</span>
      </div>

      {/* Una casilla «Activa» fija y deshabilitada no decidía nada (C6): toda serie nueva nace activa, y se dice así. */}
      <p className={AYUDA_CAMPO}>{t.seCreaActiva}</p>

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button
            type="button"
            onClick={onCancelar}
            className={BOTON_SECUNDARIO}
          >
            {messages.comun.cancelar}
          </button>
        ) : null}
        <button
          type="submit"
          disabled={enviando}
          className={BOTON_PRIMARIO}
        >
          <FloppyDiskIcon className="size-4" />
          {enviando ? t.guardando : t.guardar}
        </button>
      </div>
    </form>
  );
}
