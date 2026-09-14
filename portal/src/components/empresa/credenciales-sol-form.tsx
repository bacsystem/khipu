"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";

export function CredencialesSolForm() {
  const router = useRouter();
  const [usuario, setUsuario] = useState("");
  const [clave, setClave] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    setOk(false);
    const res = await apiRequest("/api/proxy/empresa/credenciales-sol", { method: "PUT", body: { usuario, clave } });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(mensajeError(res.codigo));
      return;
    }
    setOk(true);
    setUsuario("");
    setClave("");
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor="usuario-sol">Usuario SOL secundario</Label>
        <Input id="usuario-sol" value={usuario} onChange={(e) => setUsuario(e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="clave-sol">Clave SOL</Label>
        <Input id="clave-sol" type="password" value={clave} onChange={(e) => setClave(e.target.value)} />
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? <p className="text-sm text-success-foreground">Credenciales SOL actualizadas.</p> : null}
      <Button type="submit" size="sm" disabled={enviando} className="w-fit">
        {enviando ? "Guardando…" : "Reemplazar credenciales SOL"}
      </Button>
    </form>
  );
}
