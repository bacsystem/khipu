"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
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

export function LoginForm() {
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
    const res = await postJson<{ usuario: unknown }>("/api/auth/login", values);
    if (res.estado === "exito") {
      router.push(rutaSegura(params.get("next")));
      router.refresh();
      return;
    }
    setError(mensajeError(res.codigo));
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormField
        id="email"
        label={messages.auth.login.email}
        type="email"
        autoComplete="email"
        register={register("email")}
        error={errors.email?.message}
      />
      <FormField
        id="password"
        label={messages.auth.login.password}
        type="password"
        autoComplete="current-password"
        register={register("password")}
        error={errors.password?.message}
      />
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <div className="-mt-2 flex justify-end">
        <Link href="/recuperar" className="text-sm text-muted-foreground hover:text-foreground">
          {messages.auth.login.olvidaste}
        </Link>
      </div>
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? messages.auth.login.enviando : messages.auth.login.enviar}
      </Button>
    </form>
  );
}
