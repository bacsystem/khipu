"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";
import { codigoSerie, razonSocialSchema, rucSchema, soloDigitos } from "@/lib/validacion";
import { cn } from "@/lib/utils";

const PASOS = ["Empresa", "Certificado y SOL", "Primera serie"] as const;

const empresaSchema = z.object({
  ruc: rucSchema,
  razon_social: razonSocialSchema,
  entorno: z.enum(["BETA", "PRODUCCION"]),
});
type EmpresaValues = z.infer<typeof empresaSchema>;

const credencialesSchema = z.object({
  clave: z.string().min(1, "Ingresa la clave del certificado"),
  usuarioSol: z.string().min(1, "Ingresa el usuario SOL"),
  claveSol: z.string().min(1, "Ingresa la clave SOL"),
});
type CredencialesValues = z.infer<typeof credencialesSchema>;

// 1001: la serie de una factura es `F` + 3 alfanuméricos y la de una boleta, `B` + 3. El cliente pedía «4 caracteres»
// y el mock lo aceptaba, así que el onboarding terminaba en verde con series que el backend rechaza.
const serieSchema = z
  .object({
    tipo: z.enum(["01", "03"]),
    serie: z.string().transform((v) => v.toUpperCase().trim()),
  })
  .refine((v) => new RegExp(`^${v.tipo === "01" ? "F" : "B"}[A-Z0-9]{3}$`).test(v.serie), {
    path: ["serie"],
    message: "La serie de una factura empieza con F y la de una boleta con B, más 3 caracteres (p. ej. F001)",
  });
type SerieValues = z.infer<typeof serieSchema>;

export function OnboardingWizard() {
  const router = useRouter();
  const [paso, setPaso] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [cargando, setCargando] = useState(true);

  /**
   * Retoma el alta donde haya quedado. El paso vivía solo en `useState`: recargar (o volver desde un error) devolvía al paso
   * 1 con los campos vacíos, y reenviar el mismo RUC daba 409 «Ya existe una cuenta con ese correo» —el mensaje equivocado—
   * sin ninguna salida. Ahora se leen las empresas de la cuenta y se salta al paso que falta.
   */
  useEffect(() => {
    let vigente = true;
    (async () => {
      try {
        const lista = await apiRequest<Array<{ id: string; tiene_certificado: boolean; tiene_credenciales_sol: boolean }>>("/api/proxy/empresas", { method: "GET" });
        const empresa = lista.estado === "exito" ? lista.datos?.[0] : null;
        if (!vigente || !empresa) return;
        await apiRequest("/api/session/empresa", { method: "POST", body: { empresaId: empresa.id } });
        if (!vigente) return;
        setPaso(empresa.tiene_certificado && empresa.tiene_credenciales_sol ? 2 : 1);
      } catch {
        // Sin conexión se arranca del paso 1: el POST de empresa avisará si ya existe.
      } finally {
        if (vigente) setCargando(false);
      }
    })();
    return () => {
      vigente = false;
    };
  }, []);

  /**
   * Un corte de conexión no dice si el paso se completó, y los cuatro envíos del alta fallaban en silencio (el botón
   * volvía a su estado y no aparecía nada). Devuelve `null` cuando no se sabe, con el aviso ya puesto. El cliente no
   * lanza: devuelve el sobre de error, así que la decisión se toma sobre el código y no en un `catch`.
   */
  async function enviar<T>(ruta: string, init: { method: string; body?: unknown }) {
    const res = await apiRequest<T>(ruta, init);
    if (noSeSabeSiLlego(res)) {
      setError("Se cortó la conexión y no sabemos si el paso se completó. Recargá la página: el asistente retoma donde quedó.");
      return null;
    }
    return res;
  }

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
    const res = await enviar<{ id: string }>("/api/proxy/empresas", { method: "POST", body: values });
    if (!res) return;
    if (res.estado !== "exito" || !res.datos) {
      // El 409 de una empresa ya registrada no es «ya existe una cuenta con ese correo»: es este RUC.
      setError(res.codigo === "DUPLICADO" ? `Ya hay una empresa registrada con el RUC ${values.ruc}. Si es tuya, entrá desde el selector de empresa; si no, revisá el número.` : mensajeError(res.codigo));
      return;
    }
    const sesion = await enviar("/api/session/empresa", { method: "POST", body: { empresaId: res.datos.id } });
    // Sin esto la empresa quedaba creada, el wizard no avanzaba y no decía nada: los pasos 2 y 3 van contra la empresa activa.
    if (!sesion) return;
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
    const certRes = await enviar("/api/proxy/empresa/certificado", { method: "POST", body: form });
    if (!certRes) return;
    if (certRes.estado !== "exito") {
      setError(mensajeError(certRes.codigo));
      return;
    }
    const solRes = await enviar("/api/proxy/empresa/credenciales-sol", {
      method: "PUT",
      body: { usuario: values.usuarioSol, clave: values.claveSol },
    });
    if (!solRes) return;
    if (solRes.estado !== "exito") {
      setError(mensajeError(solRes.codigo));
      return;
    }
    setPaso(2);
  }

  async function onSerie(values: SerieValues) {
    setError(null);
    const res = await enviar("/api/proxy/series", {
      method: "POST",
      body: { tipo: values.tipo, serie: values.serie },
    });
    if (!res) return;
    if (res.estado !== "exito") {
      setError(res.codigo === "DUPLICADO" ? `Ya tenés una serie ${values.serie} configurada. Elegí otra (p. ej. ${values.tipo === "01" ? "F002" : "B002"}).` : mensajeError(res.codigo));
      return;
    }
    router.push("/comprobantes");
    router.refresh();
  }

  if (cargando) return <div className="mx-auto w-full max-w-lg py-16 text-center text-sm text-muted-foreground" role="status">Cargando tu configuración…</div>;

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

      {error ? <p role="alert" className="mb-4 text-sm text-destructive">{error}</p> : null}

      {paso === 0 ? (
        <form className="grid gap-4" onSubmit={empresaForm.handleSubmit(onEmpresa)} noValidate>
          <FormField
            id="ruc"
            label="RUC"
            inputMode="numeric"
            maxLength={11}
            filtrar={soloDigitos}
            placeholder="20123456786"
            hint="11 dígitos. Lo verificamos acá mismo antes de enviarlo."
            register={empresaForm.register("ruc")}
            error={empresaForm.formState.errors.ruc?.message}
          />
          <FormField
            id="razon_social"
            label="Razón social"
            maxLength={1500}
            // Un pegado desde una planilla trae tabuladores: SUNAT los rechaza (4338) y la razón social no se puede
            // corregir después, así que se limpian al escribir en vez de avisar cuando ya es tarde.
            filtrar={(v) => v.replace(/[\u0000-\u001F\u007F]/g, " ")}
            hint="Tal como figura en tu ficha RUC. No se puede cambiar después."
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
            {/* El entorno no se puede cambiar después (no hay endpoint que lo edite): quien onboardea en Beta por descuido
                necesita intervención manual para emitir de verdad. */}
            <p className="text-sm text-muted-foreground">
              En <strong className="font-medium">Beta</strong> los comprobantes no tienen validez tributaria: es para probar.
              El entorno <strong className="font-medium">no se puede cambiar</strong> después de crear la empresa.
            </p>
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
            maxLength={4}
            filtrar={codigoSerie}
            placeholder={serieForm.watch("tipo") === "03" ? "B001" : "F001"}
            register={serieForm.register("serie")}
            error={serieForm.formState.errors.serie?.message}
            hint="Por ejemplo F001 para facturas o B001 para boletas (SUNAT 1001)."
          />
          <Button type="submit" disabled={serieForm.formState.isSubmitting} className="w-full">
            Terminar
          </Button>
        </form>
      ) : null}
    </div>
  );
}
