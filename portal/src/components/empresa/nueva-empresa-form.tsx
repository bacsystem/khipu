"use client";

import { ChevronDownIcon, PlusIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Empresa, Entorno } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

export function NuevaEmpresaForm({ onGuardado, onCancelar }: { onGuardado?: () => void; onCancelar?: () => void }) {
  const router = useRouter();
  const [ruc, setRuc] = useState("");
  const [razonSocial, setRazonSocial] = useState("");
  const [entorno, setEntorno] = useState<Entorno>("BETA");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    const res = await apiRequest<Empresa>("/api/proxy/empresas", {
      method: "POST",
      body: { ruc, razon_social: razonSocial, entorno },
    });
    if (res.estado !== "exito" || !res.datos) {
      setEnviando(false);
      setError(mensajeError(res.codigo));
      return;
    }
    // La empresa nueva pasa a ser la activa de la sesión.
    await apiRequest("/api/session/empresa", {
      method: "POST",
      body: { empresaId: res.datos.id },
    });
    setEnviando(false);
    router.refresh();
    onGuardado?.();
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nueva-ruc" className={ETIQUETA_CAMPO}>
            RUC
          </label>
          <input
            id="nueva-ruc"
            value={ruc}
            onChange={(e) => setRuc(e.target.value.replace(/\D/g, "").slice(0, 11))}
            inputMode="numeric"
            pattern="[0-9]{11}"
            placeholder="20123456789"
            required
            autoFocus
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA_CAMPO}>11 dígitos</span>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nuevo-entorno" className={ETIQUETA_CAMPO}>
            Entorno SUNAT
          </label>
          <div className="relative">
            <select id="nuevo-entorno" value={entorno} onChange={(e) => setEntorno(e.target.value as Entorno)} className={cn(CAMPO, "appearance-none pr-8")}>
              <option value="BETA">BETA (homologación)</option>
              <option value="PRODUCCION">Producción</option>
            </select>
            <ChevronDownIcon className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
          </div>
          <span className={AYUDA_CAMPO}>En BETA los comprobantes no tienen validez tributaria</span>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="nueva-razon" className={ETIQUETA_CAMPO}>
          Razón social
        </label>
        <input
          id="nueva-razon"
          value={razonSocial}
          onChange={(e) => setRazonSocial(e.target.value)}
          placeholder="Tal como figura en la ficha RUC"
          required
          className={CAMPO}
        />
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            Cancelar
          </button>
        ) : null}
        <button type="submit" disabled={enviando} className={BOTON_PRIMARIO}>
          <PlusIcon className="size-4" />
          {enviando ? "Registrando…" : "Registrar empresa"}
        </button>
      </div>
    </form>
  );
}
