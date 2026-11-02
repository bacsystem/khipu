"use client";

import Link from "next/link";
import { useMessages } from "next-intl";
import { useState } from "react";
import type { Mensajes } from "@/i18n/mensajes";
import { ApiReference } from "./api-reference";
import { GenerarKeyPrueba } from "./generar-key-prueba";
import { Rico } from "./guia/rico";

/** Las otras secciones del developer portal, para que quien llega a la referencia sepa dónde seguir (#13). */
const OTRAS_SECCIONES = (messages: Mensajes) => [
  { href: "/developers/guia", etiqueta: messages.developers.referencia.guia },
  { href: "/developers/catalogos", etiqueta: messages.developers.nav.catalogos },
  { href: "/developers/errores", etiqueta: messages.developers.nav.errores },
  { href: "/developers/entornos", etiqueta: messages.developers.nav.entornos },
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
  const messages = useMessages();
  const t = messages.developers.referencia;

  return (
    <div>
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 pt-10 md:px-6">
        <header className="flex flex-col gap-1.5">
          <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">{t.titulo}</h1>
          <p className="max-w-2xl text-cuerpo leading-relaxed text-muted-foreground">
            <Rico texto={t.intro} />
          </p>
          <nav aria-label={t.masDocumentacion} className="flex flex-wrap gap-x-4 gap-y-1 pt-1 text-control">
            {OTRAS_SECCIONES(messages).map((s) => (
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
