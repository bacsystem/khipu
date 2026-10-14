"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { BotonAsync } from "@/components/patrones/boton-async";
import { Alerta } from "@/components/feedback/alerta";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { mensajeError, messages } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { emailSchema } from "@/lib/validacion";

const schema = z.object({ email: emailSchema });

type FormValues = z.infer<typeof schema>;

export function RecuperarForm() {
  const [error, setError] = useState<string | null>(null);
  const [enviado, setEnviado] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setError(null);
    const res = await postJson<null>("/api/auth/recuperar", values);
    if (res.estado === "exito") {
      setEnviado(true);
      return;
    }
    setError(mensajeError(res.codigo));
  }

  if (enviado) {
    return <Alerta tono="ok">{messages.auth.recuperar.confirmacion}</Alerta>;
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormField
        id="email"
        label={messages.auth.recuperar.email}
        type="email"
        autoComplete="email"
        register={register("email")}
        error={errors.email?.message}
      />
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
      <BotonAsync type="submit" pendiente={isSubmitting} textoPendiente={messages.auth.recuperar.enviando} className={cn(BOTON_PRIMARIO, "w-full")}>
        {messages.auth.recuperar.enviar}
      </BotonAsync>
    </form>
  );
}
