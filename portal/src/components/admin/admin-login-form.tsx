"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
import { mensajeError, messages } from "@/lib/messages";
import { rutaSegura } from "@/lib/ruta-segura";
import { emailSchema } from "@/lib/validacion";

const schema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Ingresa tu contraseña"),
});

type FormValues = z.infer<typeof schema>;

export function AdminLoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setError(null);
    const res = await postJson<{ administrador: unknown }>("/api/admin/auth/login", values);
    if (res.estado === "exito") {
      router.push(rutaSegura(params.get("next"), "/admin"));
      router.refresh();
      return;
    }
    setError(mensajeError(res.codigo));
  }

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
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? messages.admin.login.enviando : messages.admin.login.enviar}
      </Button>
    </form>
  );
}
