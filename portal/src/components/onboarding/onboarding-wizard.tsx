"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const PASOS = ["Empresa", "Certificado y SOL", "Primera serie"] as const;

const empresaSchema = z.object({
  ruc: z.string().regex(/^\d{11}$/, "El RUC debe tener 11 dígitos"),
  razon_social: z.string().min(1, "Ingresa la razón social"),
  entorno: z.enum(["BETA", "PRODUCCION"]),
});
type EmpresaValues = z.infer<typeof empresaSchema>;

const credencialesSchema = z.object({
  clave: z.string().min(1, "Ingresa la clave del certificado"),
  usuarioSol: z.string().min(1, "Ingresa el usuario SOL"),
  claveSol: z.string().min(1, "Ingresa la clave SOL"),
});
type CredencialesValues = z.infer<typeof credencialesSchema>;

const serieSchema = z.object({
  tipo: z.enum(["01", "03"]),
  serie: z.string().min(4, "Ingresa la serie, p. ej. F001"),
});
type SerieValues = z.infer<typeof serieSchema>;

export function OnboardingWizard() {
  const router = useRouter();
  const [paso, setPaso] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [archivo, setArchivo] = useState<File | null>(null);

  const empresaForm = useForm<EmpresaValues>({
    resolver: zodResolver(empresaSchema),
    defaultValues: { entorno: "BETA" },
  });
  const credencialesForm = useForm<CredencialesValues>({ resolver: zodResolver(credencialesSchema) });
  const serieForm = useForm<SerieValues>({
    resolver: zodResolver(serieSchema),
    defaultValues: { tipo: "01", serie: "F001" },
  });

  async function onEmpresa(values: EmpresaValues) {
    setError(null);
    const res = await apiRequest<{ id: string }>("/api/proxy/empresas", { method: "POST", body: values });
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo));
      return;
    }
    await apiRequest("/api/session/empresa", { method: "POST", body: { empresaId: res.datos.id } });
    setPaso(1);
  }

  async function onCredenciales(values: CredencialesValues) {
    setError(null);
    if (!archivo) {
      setError("Selecciona el archivo .p12 de tu certificado");
      return;
    }
    const form = new FormData();
    form.set("archivo", archivo);
    form.set("clave", values.clave);
    const certRes = await apiRequest("/api/proxy/empresa/certificado", { method: "POST", body: form });
    if (certRes.estado !== "exito") {
      setError(mensajeError(certRes.codigo));
      return;
    }
    const solRes = await apiRequest("/api/proxy/empresa/credenciales-sol", {
      method: "PUT",
      body: { usuario: values.usuarioSol, clave: values.claveSol },
    });
    if (solRes.estado !== "exito") {
      setError(mensajeError(solRes.codigo));
      return;
    }
    setPaso(2);
  }

  async function onSerie(values: SerieValues) {
    setError(null);
    const res = await apiRequest("/api/proxy/series", {
      method: "POST",
      body: { tipo: values.tipo, serie: values.serie },
    });
    if (res.estado !== "exito") {
      setError(mensajeError(res.codigo));
      return;
    }
    router.push("/comprobantes");
    router.refresh();
  }

  return (
    <div className="mx-auto w-full max-w-lg">
      <ol className="mb-8 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
        {PASOS.map((label, i) => (
          <li key={label} className={cn("flex items-center gap-2", i === paso && "font-medium text-foreground")}>
            <span
              className={cn(
                "flex size-6 shrink-0 items-center justify-center rounded-full border text-xs",
                i <= paso ? "border-primary bg-primary text-primary-foreground" : "border-border",
              )}
            >
              {i + 1}
            </span>
            {label}
            {i < PASOS.length - 1 ? <span className="text-border">—</span> : null}
          </li>
        ))}
      </ol>

      {error ? <p className="mb-4 text-sm text-destructive">{error}</p> : null}

      {paso === 0 ? (
        <form className="grid gap-4" onSubmit={empresaForm.handleSubmit(onEmpresa)} noValidate>
          <FormField
            id="ruc"
            label="RUC"
            register={empresaForm.register("ruc")}
            error={empresaForm.formState.errors.ruc?.message}
          />
          <FormField
            id="razon_social"
            label="Razón social"
            register={empresaForm.register("razon_social")}
            error={empresaForm.formState.errors.razon_social?.message}
          />
          <div className="grid gap-1.5">
            <Label htmlFor="entorno">Entorno SUNAT</Label>
            <select
              id="entorno"
              {...empresaForm.register("entorno")}
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <option value="BETA">Beta (pruebas)</option>
              <option value="PRODUCCION">Producción</option>
            </select>
          </div>
          <Button type="submit" disabled={empresaForm.formState.isSubmitting} className="w-full">
            Continuar
          </Button>
        </form>
      ) : null}

      {paso === 1 ? (
        <form className="grid gap-4" onSubmit={credencialesForm.handleSubmit(onCredenciales)} noValidate>
          <div className="grid gap-1.5">
            <Label htmlFor="archivo">Certificado digital (.p12)</Label>
            <input
              id="archivo"
              type="file"
              accept=".p12,.pfx"
              onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
              className="text-sm file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1.5 file:text-sm file:font-medium"
            />
          </div>
          <FormField
            id="clave"
            label="Clave del certificado"
            type="password"
            register={credencialesForm.register("clave")}
            error={credencialesForm.formState.errors.clave?.message}
          />
          <FormField
            id="usuarioSol"
            label="Usuario SOL secundario"
            register={credencialesForm.register("usuarioSol")}
            error={credencialesForm.formState.errors.usuarioSol?.message}
            hint="Crea un usuario secundario en SOL con permisos de envío; no uses tu clave SOL principal."
          />
          <FormField
            id="claveSol"
            label="Clave SOL"
            type="password"
            register={credencialesForm.register("claveSol")}
            error={credencialesForm.formState.errors.claveSol?.message}
          />
          <Button type="submit" disabled={credencialesForm.formState.isSubmitting} className="w-full">
            Continuar
          </Button>
        </form>
      ) : null}

      {paso === 2 ? (
        <form className="grid gap-4" onSubmit={serieForm.handleSubmit(onSerie)} noValidate>
          <div className="grid gap-1.5">
            <Label htmlFor="tipo">Tipo de comprobante</Label>
            <select
              id="tipo"
              {...serieForm.register("tipo")}
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <option value="01">Factura</option>
              <option value="03">Boleta</option>
            </select>
          </div>
          <FormField
            id="serie"
            label="Serie"
            register={serieForm.register("serie")}
            error={serieForm.formState.errors.serie?.message}
            hint="Por ejemplo F001 para facturas o B001 para boletas."
          />
          <Button type="submit" disabled={serieForm.formState.isSubmitting} className="w-full">
            Terminar
          </Button>
        </form>
      ) : null}
    </div>
  );
}
