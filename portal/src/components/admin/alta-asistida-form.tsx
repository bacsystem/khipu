"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ApiKeyRevelada } from "@/components/api-keys/api-key-revelada";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import type { AltaAsistidaCreada } from "@/lib/api/admin-alta";
import { postJson } from "@/lib/api/browser";
import type { ApiEnvelope } from "@/lib/api/types";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { mensajeError, messages } from "@/lib/messages";
import { codigoSerie, MENSAJE_SERIE, razonSocialSchema, rucSchema, serieCoincideConTipo, soloDigitos, soloTelefono, telefonoSchema } from "@/lib/validacion";

const t = messages.admin.alta;

const SELECT =
  "h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50";

/** El celular es opcional aquí (quien da de alta puede no tenerlo); si se escribe, se valida y normaliza como en el registro. */
const telefonoOpcional = z
  .string()
  .transform((v) => v.trim())
  .superRefine((v, ctx) => {
    if (v === "") return;
    const r = telefonoSchema.safeParse(v);
    if (!r.success) ctx.addIssue({ code: "custom", message: r.error.issues[0].message });
  })
  .transform((v) => (v === "" ? undefined : telefonoSchema.parse(v)));

const schema = z
  .object({
    nombre: z
      .string()
      .transform((v) => v.trim())
      .refine((v) => v.length > 0, "Ingresa el nombre de la cuenta")
      .refine((v) => v.length <= 150, "El nombre admite hasta 150 caracteres"),
    email: z
      .string()
      .transform((v) => v.trim())
      .refine((v) => v.length > 0, "Ingresa el correo del cliente")
      .refine((v) => v.length <= 254, "El correo admite hasta 254 caracteres")
      .pipe(z.email("Correo inválido")),
    telefono: telefonoOpcional,
    ruc: rucSchema,
    razon_social: razonSocialSchema,
    entorno: z.enum(["BETA", "PRODUCCION"]),
    tipo: z.enum(["01", "03"]),
    serie: z.string().transform((v) => v.toUpperCase().trim()),
  })
  .superRefine((v, ctx) => {
    if (!serieCoincideConTipo(v.tipo, v.serie)) ctx.addIssue({ code: "custom", path: ["serie"], message: MENSAJE_SERIE });
  });

type Entrada = z.input<typeof schema>;
type Salida = z.output<typeof schema>;

const VALORES_INICIALES: Entrada = { nombre: "", email: "", telefono: "", ruc: "", razon_social: "", entorno: "BETA", tipo: "01", serie: "F001" };

/** Lo que dijo el backend cuando es más específico que el texto por código: un 409 puede ser el correo o el RUC. */
function textoDeError(res: ApiEnvelope<unknown>): string {
  if (res.codigo === "NO_AUTORIZADO") return t.sesionExpirada;
  if (res.codigo === "DUPLICADO" && res.mensaje) return res.mensaje;
  return mensajeError(res.codigo);
}

/**
 * Alta asistida de un cliente (#188): cuenta, primera empresa y primera serie desde una sola pantalla. El administrador nunca define
 * una contraseña: el cliente la elige con la invitación que recibe por correo. La API key inicial se muestra una sola vez.
 */
export function AltaAsistidaForm() {
  const [error, setError] = useState<string | null>(null);
  const [creada, setCreada] = useState<{ datos: AltaAsistidaCreada; email: string } | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<Entrada, unknown, Salida>({ resolver: zodResolver(schema), defaultValues: VALORES_INICIALES });

  async function onSubmit(values: Salida) {
    setError(null);
    const res = await postJson<AltaAsistidaCreada>("/api/admin/cuentas", {
      nombre: values.nombre,
      email: values.email,
      telefono: values.telefono,
      empresa: { ruc: values.ruc, razon_social: values.razon_social, entorno: values.entorno },
      serie: { tipo: values.tipo, serie: values.serie },
    });
    if (res.estado === "exito" && res.datos) {
      setCreada({ datos: res.datos, email: values.email });
      return;
    }
    setError(textoDeError(res));
  }

  function otroCliente() {
    reset(VALORES_INICIALES);
    setError(null);
    setCreada(null);
  }

  if (creada) {
    const r = t.resultado;
    return (
      <div className="grid max-w-xl gap-4 rounded-xl border border-border bg-card p-5">
        <div className="grid gap-1">
          <h2 className="font-heading text-lg">{r.titulo}</h2>
          <p className="text-sm text-muted-foreground">{r.descripcion}</p>
        </div>
        <ApiKeyRevelada apiKey={creada.datos.api_key} etiqueta={r.etiquetaApiKey} aviso={<p>{r.avisoApiKey}</p>} />
        <p className="text-sm text-muted-foreground">
          {(creada.datos.invitacion_enviada ? r.invitacionEnviada : r.invitacionNoEnviada).replace("{email}", creada.email)}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {/* Un enlace de verdad (no `Button render={Link}`, que lo anuncia como botón): navega, no actúa. */}
          <Link href="/admin/cuentas" className={BOTON_PRIMARIO}>
            {r.verCuentas}
          </Link>
          <button type="button" onClick={otroCliente} className={BOTON_SECUNDARIO}>
            {r.otra}
          </button>
        </div>
      </div>
    );
  }

  const tipo = watch("tipo");
  return (
    <form className="grid max-w-xl gap-5" onSubmit={handleSubmit(onSubmit)} noValidate>
      <p className="text-sm text-muted-foreground">{t.descripcion}</p>

      <section className="grid gap-4 rounded-xl border border-border bg-card p-5">
        <h2 className="font-heading text-base">{t.seccionCuenta}</h2>
        <FormField id="alta-nombre" label={t.nombre} maxLength={150} register={register("nombre")} error={errors.nombre?.message} />
        <FormField
          id="alta-email"
          label={t.email}
          type="email"
          autoComplete="off"
          maxLength={254}
          register={register("email")}
          error={errors.email?.message}
          hint={t.emailAyuda}
        />
        <FormField
          id="alta-telefono"
          label={t.telefono}
          inputMode="tel"
          autoComplete="off"
          filtrar={soloTelefono}
          register={register("telefono")}
          error={errors.telefono?.message}
        />
      </section>

      <section className="grid gap-4 rounded-xl border border-border bg-card p-5">
        <h2 className="font-heading text-base">{t.seccionEmpresa}</h2>
        <FormField id="alta-ruc" label={t.ruc} inputMode="numeric" maxLength={11} filtrar={soloDigitos} register={register("ruc")} error={errors.ruc?.message} />
        <FormField id="alta-razon-social" label={t.razonSocial} register={register("razon_social")} error={errors.razon_social?.message} />
        <div className="grid gap-1.5">
          <Label htmlFor="alta-entorno">{t.entorno}</Label>
          <select id="alta-entorno" {...register("entorno")} className={SELECT}>
            <option value="BETA">{t.entornoBeta}</option>
            <option value="PRODUCCION">{t.entornoProduccion}</option>
          </select>
        </div>
      </section>

      <section className="grid gap-4 rounded-xl border border-border bg-card p-5">
        <h2 className="font-heading text-base">{t.seccionSerie}</h2>
        <div className="grid gap-1.5">
          <Label htmlFor="alta-tipo">{t.tipo}</Label>
          <select id="alta-tipo" {...register("tipo")} className={SELECT}>
            <option value="01">{t.tipoFactura}</option>
            <option value="03">{t.tipoBoleta}</option>
          </select>
        </div>
        <FormField
          id="alta-serie"
          label={t.serie}
          maxLength={4}
          filtrar={codigoSerie}
          placeholder={tipo === "03" ? "B001" : "F001"}
          register={register("serie")}
          error={errors.serie?.message}
          hint={t.serieAyuda}
        />
      </section>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <Button type="submit" disabled={isSubmitting} className="w-full sm:w-auto sm:justify-self-start">
        {isSubmitting ? t.enviando : t.enviar}
      </Button>
    </form>
  );
}
