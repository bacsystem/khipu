import { LogoMarca as LogoDelDiseno } from "khipu-design-system/nav/logo";
import { useMessages } from "next-intl";

/** La marca de khipu: el `LogoMarca` del design system con el nombre, el dominio y el glifo de los tres nudos. */
export function LogoMarca({ href = "/inicio" }: { href?: string }) {
  const messages = useMessages();
  return (
    <LogoDelDiseno
      nombre={messages.app.marca}
      sufijo={messages.app.dominio}
      href={href}
      icono={
        <svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M4 6H20" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
          <path d="M8 6V19M12 6V19M16 6V19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="8" cy="12" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="12" cy="15.5" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="16" cy="11" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
        </svg>
      }
    />
  );
}
