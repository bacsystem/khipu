"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest } from "@/lib/api/browser";
import type { Empresa, Entorno } from "@/lib/api/empresas";
import { mensajeError } from "@/lib/messages";

export function NuevaEmpresaForm() {
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
    await apiRequest("/api/session/empresa", { method: "POST", body: { empresaId: res.datos.id } });
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-3">
      <div className="grid gap-1.5">
        <Label htmlFor="nueva-ruc">RUC</Label>
        <Input id="nueva-ruc" value={ruc} onChange={(e) => setRuc(e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="nueva-razon">Razón social</Label>
        <Input id="nueva-razon" value={razonSocial} onChange={(e) => setRazonSocial(e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="nuevo-entorno">Entorno</Label>
        <select
          id="nuevo-entorno"
          value={entorno}
          onChange={(e) => setEntorno(e.target.value as Entorno)}
          className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <option value="BETA">Beta</option>
          <option value="PRODUCCION">Producción</option>
        </select>
      </div>
      {error ? <p className="text-sm text-destructive sm:col-span-3">{error}</p> : null}
      <Button type="submit" size="sm" disabled={enviando} className="w-fit sm:col-span-3">
        {enviando ? "Creando…" : "Agregar empresa"}
      </Button>
    </form>
  );
}
