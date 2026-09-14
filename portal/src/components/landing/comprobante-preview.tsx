import { Download, Stamp } from "lucide-react";

export function ComprobantePreview() {
  return (
    <div className="relative mx-auto w-full max-w-sm">
      <div
        aria-hidden
        className="h-3 w-full rounded-t-xl bg-[radial-gradient(circle_at_center,var(--sidebar)_2.5px,transparent_2.6px)] [background-size:14px_14px] [background-position:7px_0]"
      />
      <div className="rounded-b-xl bg-sidebar px-6 py-6 text-sidebar-foreground shadow-xl">
        <div className="flex items-center justify-between">
          <span className="flex items-center gap-1.5 text-xs text-sidebar-foreground/60">
            <Stamp className="size-3.5 text-sidebar-primary" />
            SUNAT · Beta
          </span>
          <span className="rounded-full bg-success px-2 py-0.5 text-xs font-medium text-success-foreground">
            Aceptado
          </span>
        </div>

        <p className="mt-4 font-mono text-2xl">F001-00001024</p>
        <p className="text-sm text-sidebar-foreground/60">Emitida hoy · 12:45 p. m.</p>

        <dl className="mt-5 grid gap-2 border-t border-sidebar-border pt-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-sidebar-foreground/60">Cliente</dt>
            <dd className="text-right">Comercial Andina SAC</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-sidebar-foreground/60">RUC</dt>
            <dd className="font-mono">20123456789</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-sidebar-foreground/60">IGV</dt>
            <dd className="font-mono">S/ 424.80</dd>
          </div>
          <div className="flex justify-between gap-4 text-base">
            <dt className="font-medium">Total</dt>
            <dd className="font-mono font-medium">S/ 2,360.00</dd>
          </div>
        </dl>

        <div className="mt-5 flex items-center justify-between border-t border-sidebar-border pt-4">
          <span className="flex items-center gap-1.5 text-xs text-sidebar-foreground/60">
            <Download className="size-3.5" />
            XML · CDR
          </span>
          <span className="text-xs text-sidebar-foreground/60">CDR 0 · Aceptada</span>
        </div>
      </div>
    </div>
  );
}
