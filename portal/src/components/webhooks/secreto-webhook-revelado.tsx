"use client";

import { useMessages, useTranslations } from "next-intl";
import { ApiKeyRevelada } from "@/components/api-keys/api-key-revelada";

/**
 * El secreto de firma de un webhook (#19), que se muestra una sola vez como una API key: el backend lo guarda cifrado para poder firmar, pero no lo vuelve a entregar.
 * Reutiliza el tratamiento de la API key (caja, copiar, aviso) con su propio texto.
 */
export function SecretoWebhookRevelado({ secreto }: { secreto: string }) {
  const t = useMessages().webhooks.secreto;
  const tr = useTranslations("webhooks.secreto");
  return (
    <ApiKeyRevelada
      apiKey={secreto}
      etiqueta={t.etiqueta}
      aviso={
        <p>
          {tr.rich("aviso", {
            b: (texto) => <strong className="font-semibold">{texto}</strong>,
            code: (texto) => <code className="font-mono">{texto}</code>,
          })}
        </p>
      }
    />
  );
}
