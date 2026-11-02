"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { useMessages } from "next-intl";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";
import { esquemas } from "@/lib/validacion";

type FormValues = { email: string };

export function RecuperarForm() {
  const messages = useMessages();
  const mensajeError = useMensajeError();
  const schema = useMemo(() => z.object({ email: esquemas(messages).email }), [messages]);
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
      {error ? <Alerta tono="error">{error}</Alerta> : null}
      <BotonAsync type="submit" pendiente={isSubmitting} textoPendiente={messages.auth.recuperar.enviando} className={cn(BOTON_PRIMARIO, "w-full")}>
        {messages.auth.recuperar.enviar}
      </BotonAsync>
    </form>
  );
}
