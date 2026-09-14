"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
import { mensajeError, messages } from "@/lib/messages";
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
    return <p className="text-sm text-muted-foreground">{messages.auth.recuperar.confirmacion}</p>;
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
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? messages.auth.recuperar.enviando : messages.auth.recuperar.enviar}
      </Button>
    </form>
  );
}
