"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import type { PasoAdmin } from "@/lib/api/admin-auth";
import { postJson } from "@/lib/api/browser";
import type { ApiEnvelope } from "@/lib/api/types";
import { mensajeError, messages } from "@/lib/messages";
import { rutaSegura } from "@/lib/ruta-segura";
import { emailSchema } from "@/lib/validacion";
import { CodigosRecuperacion, ConfigurarSegundoFactor, VerificarSegundoFactor } from "./segundo-factor";

const schema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Ingresa tu contraseña"),
});

type FormValues = z.infer<typeof schema>;

/** Login del backoffice en pasos (#177): contraseña, y después configurar o verificar el segundo factor. */
type Paso = { tipo: "credenciales" } | { tipo: "configurar" } | { tipo: "verificar" } | { tipo: "codigos"; codigos: string[] };

export function AdminLoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [paso, setPaso] = useState<Paso>({ tipo: "credenciales" });
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const entrar = useCallback(() => {
    router.push(rutaSegura(params.get("next"), "/admin"));
    router.refresh();
  }, [router, params]);

  /** El desafío venció (5 minutos) o el estado cambió entre pasos: se vuelve a pedir la contraseña con el motivo a la vista. */
  const volverAlInicio = useCallback((res: ApiEnvelope<unknown>) => {
    setError(mensajeError(res.codigo));
    setPaso({ tipo: "credenciales" });
  }, []);

  async function onSubmit(values: FormValues) {
    setError(null);
    const res = await postJson<{ paso: PasoAdmin }>("/api/admin/auth/login", values);
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo));
      return;
    }
    setPaso({ tipo: res.datos.paso === "CONFIGURAR_SEGUNDO_FACTOR" ? "configurar" : "verificar" });
  }

  if (paso.tipo === "configurar") return <ConfigurarSegundoFactor alConfirmar={(codigos) => setPaso({ tipo: "codigos", codigos })} alFallar={volverAlInicio} />;
  if (paso.tipo === "verificar") return <VerificarSegundoFactor alEntrar={entrar} alFallar={volverAlInicio} />;
  if (paso.tipo === "codigos") return <CodigosRecuperacion codigos={paso.codigos} alContinuar={entrar} />;

  return (
    <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormField
        id="email"
        label={messages.admin.login.email}
        type="email"
        autoComplete="email"
        register={register("email")}
        error={errors.email?.message}
      />
      <FormField
        id="password"
        label={messages.admin.login.password}
        type="password"
        autoComplete="current-password"
        register={register("password")}
        error={errors.password?.message}
      />
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? messages.admin.login.enviando : messages.admin.login.enviar}
      </Button>
    </form>
  );
}
