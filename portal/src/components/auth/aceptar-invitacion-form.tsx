"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button, buttonVariants } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
import { mensajeError, messages } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { passwordSchema } from "@/lib/validacion";

const t = messages.auth.aceptarInvitacion;
const schema = z.object({ password: passwordSchema });
type FormValues = z.infer<typeof schema>;

/** #21: el invitado crea su contraseña y queda en la cuenta con el rol de la invitación. Después entra por el login de siempre. */
export function AceptarInvitacionForm({ token, email }: { token: string; email: string }) {
  const [error, setError] = useState<string | null>(null);
  const [listo, setListo] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setError(null);
    const res = await postJson<null>("/api/auth/invitacion/aceptar", { token, password: values.password });
    if (res.estado === "exito") {
      setListo(true);
      return;
    }
    setError(res.codigo === "TOKEN_INVALIDO" ? t.enlaceInvalido : mensajeError(res.codigo));
  }

  if (listo) {
    return (
      <div className="grid gap-4">
        <p role="status" className="text-sm">{t.exito}</p>
        <p className="text-sm text-muted-foreground">{t.email.replace("{email}", email)}</p>
        <Link href="/login" className={cn(buttonVariants(), "w-full")}>
          {t.irALogin}
        </Link>
      </div>
    );
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormField
        id="password"
        label={t.password}
        type="password"
        autoComplete="new-password"
        register={register("password")}
        error={errors.password?.message}
        hint={t.ayudaPassword}
      />
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? t.enviando : t.enviar}
      </Button>
    </form>
  );
}
