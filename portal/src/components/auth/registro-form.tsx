"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
import { mensajeError, messages } from "@/lib/messages";
import { emailSchema, passwordSchema, soloTelefono, telefonoSchema } from "@/lib/validacion";

const schema = z.object({
  nombre: z.string().trim().min(1, "Ingresa el nombre de tu cuenta").max(150, "Hasta 150 caracteres"),
  telefono: telefonoSchema,
  email: emailSchema,
  password: passwordSchema,
});

type FormValues = z.infer<typeof schema>;

export function RegistroForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    // `onTouched`: el error aparece al salir del campo y no recién al enviar.
  } = useForm<FormValues>({ resolver: zodResolver(schema), mode: "onTouched" });

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
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? messages.auth.registro.enviando : messages.auth.registro.enviar}
      </Button>
    </form>
  );
}
