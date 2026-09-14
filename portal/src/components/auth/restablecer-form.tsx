"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
import { mensajeError, messages } from "@/lib/messages";
import { passwordSchema } from "@/lib/validacion";

const schema = z.object({ password: passwordSchema });

type FormValues = z.infer<typeof schema>;

export function RestablecerForm({ token }: { token: string }) {
  const [error, setError] = useState<string | null>(null);
  const [listo, setListo] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setError(null);
    const res = await postJson<null>("/api/auth/restablecer", { token, password: values.password });
    if (res.estado === "exito") {
      setListo(true);
      return;
    }
    setError(mensajeError(res.codigo));
  }

  if (listo) {
    return (
      <div className="grid gap-4">
        <p className="text-sm text-muted-foreground">{messages.auth.restablecer.exito}</p>
        <Button render={<Link href="/login" />} nativeButton={false} className="w-full">
          {messages.auth.restablecer.irALogin}
        </Button>
      </div>
    );
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormField
        id="password"
        label={messages.auth.restablecer.password}
        type="password"
        autoComplete="new-password"
        register={register("password")}
        error={errors.password?.message}
        hint={messages.auth.restablecer.ayudaPassword}
      />
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? messages.auth.restablecer.enviando : messages.auth.restablecer.enviar}
      </Button>
    </form>
  );
}
