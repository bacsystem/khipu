"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { CasillaDeTerminos } from "@/components/legal/casilla-de-terminos";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { useMessages } from "next-intl";
import type { Mensajes } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";
import { esquemas, soloTelefono } from "@/lib/validacion";

function esquema(messages: Mensajes) {
  const v = esquemas(messages);
  return z.object({
    nombre: z.string().trim().min(1, messages.auth.registro.nombreRequerido).max(150, messages.auth.registro.nombreLargo),
    telefono: v.telefono,
    email: v.email,
    password: v.password,
    // #342: sin marcarla no se envía nada; el backend igual lo exige (400 TERMINOS_NO_ACEPTADOS).
    acepta_terminos: z.boolean().refine((acepta) => acepta, messages.auth.registro.terminosRequeridos),
  });
}

type FormValues = z.infer<ReturnType<typeof esquema>>;

export function RegistroForm() {
  const router = useRouter();
  const messages = useMessages();
  const mensajeError = useMensajeError();
  const schema = useMemo(() => esquema(messages), [messages]);
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
    // `onTouched`: el error aparece al salir del campo y no recién al enviar.
  } = useForm<FormValues>({ resolver: zodResolver(schema), mode: "onTouched", defaultValues: { acepta_terminos: false } });

  async function onSubmit(values: FormValues) {
    setError(null);
    const res = await postJson<{ usuario: unknown }>("/api/auth/registro", values);
    if (res.estado === "exito") {
      router.push("/onboarding");
      router.refresh();
      return;
    }
    setError(mensajeError(res.codigo));
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormField
        id="nombre"
        label={messages.auth.registro.nombre}
        autoComplete="organization"
        register={register("nombre")}
        error={errors.nombre?.message}
      />
      <FormField
        id="telefono"
        label={messages.auth.registro.telefono}
        type="tel"
        autoComplete="tel-national"
        inputMode="numeric"
        placeholder="987654321"
        maxLength={12}
        filtrar={soloTelefono}
        register={register("telefono")}
        error={errors.telefono?.message}
        hint={messages.auth.registro.ayudaTelefono}
      />
      <FormField
        id="email"
        label={messages.auth.registro.email}
        type="email"
        autoComplete="email"
        register={register("email")}
        error={errors.email?.message}
      />
      <FormField
        id="password"
        label={messages.auth.registro.password}
        type="password"
        autoComplete="new-password"
        register={register("password")}
        error={errors.password?.message}
        hint={messages.auth.registro.ayudaPassword}
      />
      {/* La casilla del design system es controlada: va por un `Controller`, no por `register`. */}
      <Controller
        name="acepta_terminos"
        control={control}
        render={({ field }) => <CasillaDeTerminos id="acepta_terminos" name={field.name} checked={field.value} onCheckedChange={field.onChange} error={errors.acepta_terminos?.message} />}
      />
      {error ? <Alerta tono="error">{error}</Alerta> : null}
      <BotonAsync type="submit" pendiente={isSubmitting} textoPendiente={messages.auth.registro.enviando} className={cn(BOTON_PRIMARIO, "w-full")}>
        {messages.auth.registro.enviar}
      </BotonAsync>
    </form>
  );
}
