"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";

export function NuevaSerieForm() {
  const router = useRouter();
  const [tipo, setTipo] = useState("01");
  const [serie, setSerie] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    const res = await apiRequest("/api/proxy/series", { method: "POST", body: { tipo, serie } });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(mensajeError(res.codigo));
      return;
    }
    setSerie("");
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-3">
      <div className="grid gap-1.5">
        <Label htmlFor="tipo-serie">Tipo de comprobante</Label>
        <select
          id="tipo-serie"
          value={tipo}
          onChange={(e) => setTipo(e.target.value)}
          className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <option value="01">Factura</option>
          <option value="03">Boleta</option>
          <option value="07">Nota de crédito</option>
          <option value="08">Nota de débito</option>
        </select>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="serie">Serie</Label>
        <Input id="serie" value={serie} onChange={(e) => setSerie(e.target.value)} placeholder="F001" />
        <p className="text-sm text-muted-foreground">Por ejemplo F001 para facturas o B001 para boletas.</p>
      </div>
      {error ? <p className="text-sm text-destructive sm:col-span-3">{error}</p> : null}
      <Button type="submit" size="sm" disabled={enviando} className="w-fit self-end">
        {enviando ? "Creando…" : "Crear serie"}
      </Button>
    </form>
  );
}
