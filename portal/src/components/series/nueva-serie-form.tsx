"use client";

import { ChevronDownIcon, SaveIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const TIPOS = [
  { codigo: "01", etiqueta: "01 · Factura electrónica (F###)" },
  { codigo: "03", etiqueta: "03 · Boleta de venta electrónica (B###)" },
  { codigo: "07", etiqueta: "07 · Nota de crédito (FC## / BC##)" },
  { codigo: "08", etiqueta: "08 · Nota de débito (FD## / BD##)" },
];

const CAMPO = "h-10 w-full rounded-lg border border-border bg-muted px-3 text-sm text-foreground transition-colors outline-none focus:border-ring focus:bg-card focus:ring-3 focus:ring-ring/30";
const ETIQUETA = "text-[12px] font-medium text-foreground";
const AYUDA = "font-mono text-[11px] text-muted-foreground";

export function NuevaSerieForm({ onGuardado, onCancelar }: { onGuardado?: () => void; onCancelar?: () => void }) {
  const router = useRouter();
  const [tipo, setTipo] = useState("01");
  const [serie, setSerie] = useState("");
  const [correlativo, setCorrelativo] = useState("0");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    const res = await apiRequest("/api/proxy/series", {
      method: "POST",
      body: { tipo, serie: serie.toUpperCase(), correlativo_inicial: Number(correlativo) || 0 },
    });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(mensajeError(res.codigo));
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
        <label htmlFor="tipo-serie" className={ETIQUETA}>
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
        <span className={AYUDA}>Catálogo SUNAT N.º 01</span>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="serie" className={ETIQUETA}>
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
          <span className={AYUDA}>4 caracteres alfanuméricos</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="correlativo" className={ETIQUETA}>
            Último número
          </label>
          <input
            id="correlativo"
            type="number"
            min={0}
            value={correlativo}
            onChange={(e) => setCorrelativo(e.target.value)}
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA}>Base inicial (0 = nueva)</span>
        </div>
      </div>

      <label
        className="inline-flex cursor-not-allowed items-center gap-2 self-start select-none"
        title="Las series nuevas se crean activas; activar/desactivar: próximamente"
      >
        <input type="checkbox" checked disabled readOnly className="size-4 rounded border-input" />
        <span className={ETIQUETA}>Activa</span>
      </label>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button
            type="button"
            onClick={onCancelar}
            className="inline-flex h-10 items-center rounded-lg border border-border bg-card px-3.5 text-sm font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground"
          >
            Cancelar
          </button>
        ) : null}
        <button
          type="submit"
          disabled={enviando}
          className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-primary px-3.5 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] disabled:opacity-60"
        >
          <SaveIcon className="size-4" />
          {enviando ? "Guardando…" : "Guardar serie"}
        </button>
      </div>
    </form>
  );
}
