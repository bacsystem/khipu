"use client";

import { ChevronDownIcon, SaveIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
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

const TIPOS = [
  { codigo: "01", etiqueta: "01 · Factura electrónica (F###)" },
  { codigo: "03", etiqueta: "03 · Boleta de venta electrónica (B###)" },
  { codigo: "07", etiqueta: "07 · Nota de crédito (FC## / BC##)" },
  { codigo: "08", etiqueta: "08 · Nota de débito (FD## / BD##)" },
];

export function NuevaSerieForm({ onGuardado, onCancelar }: { onGuardado?: () => void; onCancelar?: () => void }) {
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
      setError("El último número va de 0 a 99 999 999: SUNAT admite 8 dígitos de correlativo (regla 1001).");
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
          Tipo de comprobante
        </label>
        <div className="relative">
          <select id="tipo-serie" value={tipo} onChange={(e) => setTipo(e.target.value)} className={cn(CAMPO, "appearance-none pr-8")}>
            {TIPOS.map((t) => (
              <option key={t.codigo} value={t.codigo}>
                {t.etiqueta}
              </option>
            ))}
          </select>
          <ChevronDownIcon className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
        </div>
        <span className={AYUDA_CAMPO}>Catálogo SUNAT N.º 01</span>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="serie" className={ETIQUETA_CAMPO}>
            Código de serie
          </label>
          <input
            id="serie"
            value={serie}
            onChange={(e) => setSerie(e.target.value.toUpperCase())}
            placeholder="Ej. F002"
            maxLength={4}
            required
            autoFocus
            className={cn(CAMPO, "font-mono uppercase")}
          />
          <span className={AYUDA_CAMPO}>4 caracteres alfanuméricos</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="correlativo" className={ETIQUETA_CAMPO}>
            Último número
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
          <span className={AYUDA_CAMPO}>Base inicial (0 = nueva). Hasta 8 dígitos.</span>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="serie-establecimiento" className={ETIQUETA_CAMPO}>
          Establecimiento
        </label>
        <div className="relative">
          <select
            id="serie-establecimiento"
            value={establecimiento}
            onChange={(e) => setEstablecimiento(e.target.value)}
            className={cn(CAMPO, "appearance-none pr-8")}
            data-testid="serie-establecimiento"
          >
            <option value="0000">0000 · Domicilio fiscal</option>
            {(establecimientos ?? [])
              .filter((e) => !e.principal)
              .map((e) => (
                <option key={e.codigo} value={e.codigo}>
                  {e.codigo} · {e.nombre}
                </option>
              ))}
          </select>
          <ChevronDownIcon className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
        </div>
        <span className={AYUDA_CAMPO}>Los comprobantes de la serie salen con la dirección de este punto (AddressTypeCode, regla 3030)</span>
      </div>

      <label
        className="inline-flex cursor-not-allowed items-center gap-2 self-start select-none"
        title="Las series nuevas se crean activas; activar/desactivar: próximamente"
      >
        <input type="checkbox" checked disabled readOnly className="size-4 rounded border-input" />
        <span className={ETIQUETA_CAMPO}>Activa</span>
      </label>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button
            type="button"
            onClick={onCancelar}
            className={BOTON_SECUNDARIO}
          >
            Cancelar
          </button>
        ) : null}
        <button
          type="submit"
          disabled={enviando}
          className={BOTON_PRIMARIO}
        >
          <SaveIcon className="size-4" />
          {enviando ? "Guardando…" : "Guardar serie"}
        </button>
      </div>
    </form>
  );
}
