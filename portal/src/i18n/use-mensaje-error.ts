"use client";

import { useCallback } from "react";
import { useMessages } from "next-intl";
import { mensajeErrorEn } from "@/lib/messages";

/** La versión con locale de `mensajeError` (`lib/messages.ts`): el texto de un código de error de la API en el idioma de la sesión. */
export function useMensajeError(): (codigo: string | null | undefined) => string {
  const messages = useMessages();
  return useCallback((codigo) => mensajeErrorEn(messages, codigo), [messages]);
}
