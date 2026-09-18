"use client";

import { useState } from "react";
import { ApiReference } from "./api-reference";
import { GenerarKeyPrueba } from "./generar-key-prueba";

export function DevelopersView({
  spec,
  baseServerURL,
  mostrarGenerarKey,
}: {
  spec: Record<string, unknown>;
  baseServerURL: string;
  mostrarGenerarKey: boolean;
}) {
  const [apiKey, setApiKey] = useState<string | undefined>(undefined);

  return (
    <div>
      {mostrarGenerarKey ? (
        <div className="mx-auto max-w-2xl px-6 pt-6">
          <GenerarKeyPrueba onGenerada={setApiKey} />
        </div>
      ) : null}
      <ApiReference spec={spec} baseServerURL={baseServerURL} apiKey={apiKey} />
    </div>
  );
}
