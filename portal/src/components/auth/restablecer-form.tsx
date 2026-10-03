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

/**
 * `invitacion`: el enlace viene del alta asistida (#188), que reutiliza este mismo flujo con `?invitacion=1`. El endpoint es el mismo;
 * cambia el texto, porque el cliente no restablece nada: crea la contraseña con la que entra por primera vez.
 */
export function RestablecerForm({ token, invitacion = false }: { token: string; invitacion?: boolean }) {
  const t = invitacion ? messages.auth.invitacion : messages.auth.restablecer;
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
    setError(invitacion && res.codigo === "TOKEN_INVALIDO" ? messages.auth.invitacion.enlaceInvalido : mensajeError(res.codigo));
  }

  if (listo) {
    return (
      <div className="grid gap-4">
        <p className="text-sm text-muted-foreground">{t.exito}</p>
        <Button render={<Link href="/login" />} nativeButton={false} className="w-full">
          {t.irALogin}
        </Button>
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
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? t.enviando : t.enviar}
      </Button>
    </form>
  );
}
