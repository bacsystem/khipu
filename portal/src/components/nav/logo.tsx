import Link from "next/link";
import { messages } from "@/lib/messages";

export function LogoMarca({ href = "/comprobantes" }: { href?: string }) {
  return (
    <Link href={href} className="group flex min-w-0 items-center gap-2.5">
      <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-primary to-primary-light text-primary-foreground shadow-xs">
        <svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M4 6H20" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
          <path d="M8 6V19M12 6V19M16 6V19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="8" cy="12" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="12" cy="15.5" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="16" cy="11" r="1.9" className="fill-primary-foreground" stroke="currentColor" strokeWidth="1.4" />
        </svg>
      </div>
      <div className="flex min-w-0 items-baseline gap-0.5 whitespace-nowrap">
        <span className="text-[16px] leading-none font-bold tracking-tight text-foreground">{messages.app.marca}</span>
        <span className="font-mono text-[11px] font-medium text-primary">{messages.app.dominio}</span>
      </div>
    </Link>
  );
}
