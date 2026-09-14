"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";

export function CertificadoForm() {
  const router = useRouter();
  const [archivo, setArchivo] = useState<File | null>(null);
  const [clave, setClave] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!archivo) {
      setError("Selecciona el archivo .p12 de tu certificado");
      return;
    }
    setEnviando(true);
    setError(null);
    setOk(false);
    const form = new FormData();
    form.set("archivo", archivo);
    form.set("clave", clave);
    const res = await apiRequest("/api/proxy/empresa/certificado", { method: "POST", body: form });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(mensajeError(res.codigo));
      return;
    }
    setOk(true);
    setClave("");
    setArchivo(null);
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor="archivo-cert">Certificado digital (.p12)</Label>
        <input
          id="archivo-cert"
          type="file"
          accept=".p12,.pfx"
          onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
          className="text-sm file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1.5 file:text-sm file:font-medium"
        />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="clave-cert">Clave del certificado</Label>
        <Input id="clave-cert" type="password" value={clave} onChange={(e) => setClave(e.target.value)} />
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? <p className="text-sm text-success-foreground">Certificado actualizado.</p> : null}
      <Button type="submit" size="sm" disabled={enviando} className="w-fit">
        {enviando ? "Subiendo…" : "Reemplazar certificado"}
      </Button>
    </form>
  );
}
