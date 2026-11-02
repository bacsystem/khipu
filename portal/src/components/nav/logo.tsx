import { LogoMarca as LogoDelDiseno } from "khipu-design-system/nav/logo";
import Link from "next/link";
import { useMessages } from "next-intl";

/** El glifo de los tres nudos, en blanco sobre la caja de la marca. */
function GlifoKhipu() {
  return (
    <svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4 6H20" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
      <path d="M8 6V19M12 6V19M16 6V19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="8" cy="12" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="12" cy="15.5" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="16" cy="11" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  );
}

/** La marca de khipu: el `LogoMarca` del design system con el nombre, el dominio y el glifo de los tres nudos. */
export function LogoMarca({ href = "/inicio" }: { href?: string }) {
  const messages = useMessages();
  return <LogoDelDiseno nombre={messages.app.marca} sufijo={messages.app.dominio} href={href} icono={<GlifoKhipu />} />;
}

/**
 * Solo la caja con el glifo, para donde el nombre no entra (el encabezado de la portada en el teléfono, #525). Es la misma caja del `LogoMarca` del design system; como
 * no lleva texto visible, el enlace se nombra con la marca.
 */
export function LogoIcono({ href = "/" }: { href?: string }) {
  const messages = useMessages();
  return (
    <Link
      href={href}
      aria-label={`${messages.app.marca}${messages.app.dominio}`}
      className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-primary to-primary-light text-primary-foreground shadow-xs"
    >
      <GlifoKhipu />
    </Link>
  );
}
