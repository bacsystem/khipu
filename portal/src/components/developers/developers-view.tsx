"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiReference } from "./api-reference";
import { GenerarKeyPrueba } from "./generar-key-prueba";

/** Las otras secciones del developer portal, para que quien llega a la referencia sepa dónde seguir (#13). */
const OTRAS_SECCIONES = [
  { href: "/developers/guia", etiqueta: "Guía de integración" },
  { href: "/developers/catalogos", etiqueta: "Catálogos SUNAT" },
  { href: "/developers/errores", etiqueta: "Errores y estados" },
];

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
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 pt-10 md:px-6">
        <header className="flex flex-col gap-1.5">
          <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">Referencia de la API</h1>
          <p className="max-w-2xl text-[14px] leading-relaxed text-muted-foreground">
            Los endpoints de <span className="font-mono text-[12px]">/v1</span> con sus campos y respuestas. Los de emisión y consulta se autentican con una API key en la cabecera{" "}
            <span className="font-mono text-[12px]">X-Api-Key</span>; la que generes aquí se carga sola en el campo de autenticación de la referencia.
          </p>
          <nav aria-label="Más documentación" className="flex flex-wrap gap-x-4 gap-y-1 pt-1 text-[12px]">
            {OTRAS_SECCIONES.map((s) => (
              <Link key={s.href} href={s.href} className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
                {s.etiqueta}
              </Link>
            ))}
          </nav>
        </header>
        {mostrarGenerarKey ? <div className="max-w-2xl"><GenerarKeyPrueba onGenerada={setApiKey} /></div> : null}
      </div>
      <ApiReference spec={spec} baseServerURL={baseServerURL} apiKey={apiKey} />
    </div>
  );
}
