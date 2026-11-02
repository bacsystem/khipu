"use client";

import { useCallback } from "react";
import { useLocale, useMessages } from "next-intl";
import { mensajeErrorEn } from "@/lib/messages";

/**
 * La versión con locale de `mensajeError` (`lib/messages.ts`): el texto de un código de error de la API en el idioma de la sesión.
 * `mensajeDelServidor`: con `CUPO_DEL_PLAN` (#498) el backend dice cuál es el plan, cuánto incluye, cuánto tiene la cuenta y cómo liberar un lugar; eso informa más que el texto
 * genérico, pero está en español, así que en otro idioma se queda el genérico.
 */
export function useMensajeError(): (codigo: string | null | undefined, mensajeDelServidor?: string | null) => string {
  const messages = useMessages();
  const locale = useLocale();
  return useCallback(
    (codigo, mensajeDelServidor) => (codigo === "CUPO_DEL_PLAN" && mensajeDelServidor && locale === "es" ? mensajeDelServidor : mensajeErrorEn(messages, codigo)),
    [messages, locale],
  );
}
