import { TriangleAlertIcon } from "lucide-react";
import Link from "next/link";
import { LogoMarca } from "@/components/nav/logo";
import type { DocumentoLegal } from "@/content/legal/tipos";
import { REVISADO_POR_LEGAL } from "@/content/legal/version";
import { formatearFecha } from "@/lib/formato";
import { useLocale, useMessages } from "next-intl";

const ENLACE = "font-medium text-foreground underline underline-offset-2 hover:text-primary";

/**
 * Una página pública con un texto legal (#342): los términos o la política de privacidad. El texto vive en `src/content/legal/`; aquí solo se
 * pinta. `revisado` es para las pruebas: la página usa la bandera de `version.ts`.
 */
export function DocumentoLegalVista({
  documento,
  otro,
  revisado = REVISADO_POR_LEGAL,
}: {
  documento: DocumentoLegal;
  /** El otro texto legal, enlazado al pie. */
  otro: { href: string; titulo: string };
  revisado?: boolean;
}) {
  const t = useMessages().legal;
  const locale = useLocale();
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-4 sm:px-6">
          <LogoMarca href="/" />
          <Link href="/" className="text-sm text-muted-foreground hover:text-foreground">
            {t.volver}
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        {/* Mientras nadie con formación legal lo haya revisado, se dice arriba de todo: no se presenta un borrador como texto definitivo. */}
        {revisado ? null : (
          <div role="note" data-testid="borrador-legal" className="mb-8 flex gap-3 rounded-xl border border-warning-border bg-warning px-4 py-3 text-sm text-warning-foreground">
            <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-warning-solid" aria-hidden="true" />
            <div>
              <p className="font-semibold">{t.borradorTitulo}</p>
              <p className="mt-0.5">{t.borradorDescripcion}</p>
            </div>
          </div>
        )}

        <h1 className="font-heading text-3xl">{documento.titulo}</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {t.version} <time dateTime={documento.version}>{formatearFecha(documento.version, locale)}</time>
        </p>
        <p className="mt-6 text-base leading-relaxed text-foreground/90">{documento.resumen}</p>

        <div className="mt-8 grid gap-8">
          {documento.secciones.map((seccion) => (
            <section key={seccion.titulo} className="grid gap-3">
              <h2 className="font-heading text-xl">{seccion.titulo}</h2>
              {seccion.parrafos.map((parrafo) => (
                <p key={parrafo} className="leading-relaxed text-foreground/85">
                  {parrafo}
                </p>
              ))}
              {seccion.lista ? (
                <ul className="grid list-disc gap-2 pl-5 leading-relaxed text-foreground/85">
                  {seccion.lista.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : null}
              {seccion.cierre?.map((parrafo) => (
                <p key={parrafo} className="leading-relaxed text-foreground/85">
                  {parrafo}
                </p>
              ))}
            </section>
          ))}
        </div>

        <p className="mt-12 border-t border-border pt-6 text-sm text-muted-foreground">
          {t.verTambien}:{" "}
          <Link href={otro.href} className={ENLACE}>
            {otro.titulo}
          </Link>
        </p>
      </main>
    </div>
  );
}
