"use client";

import { ApiKeyRevelada } from "@/components/api-keys/api-key-revelada";

/**
 * El secreto de firma de un webhook (#19), que se muestra una sola vez como una API key: el backend lo guarda cifrado para poder firmar, pero no lo vuelve a entregar.
 * Reutiliza el tratamiento de la API key (caja, copiar, aviso) con su propio texto.
 */
export function SecretoWebhookRevelado({ secreto }: { secreto: string }) {
  return (
    <ApiKeyRevelada
      apiKey={secreto}
      etiqueta="Secreto de firma"
      aviso={
        <p>
          Guárdalo ahora en un lugar seguro: <strong className="font-semibold">no volverá a mostrarse</strong>. Con él tu servidor verifica la cabecera{" "}
          <code className="font-mono">X-Khipu-Signature</code> de cada entrega. Si lo pierdes, rota el secreto y actualiza tu servidor.
        </p>
      }
    />
  );
}
