import { CheckCircle2 } from "lucide-react";

const PASOS = [
  { texto: "Totales calculados y validados", detalle: "sin descuadres" },
  { texto: "Firmado XML-DSig", detalle: "RSA-SHA256" },
  { texto: "Enviado a SUNAT", detalle: "UBL 2.1 · SOAP" },
  { texto: "CDR recibido", detalle: null },
];

export function ComprobantePreview() {
  return (
    <div className="relative mx-auto w-full max-w-sm">
      <div
        aria-hidden
        className="h-3 w-full rounded-t-xl bg-[radial-gradient(circle_at_center,var(--sidebar)_2.5px,transparent_2.6px)] [background-size:14px_14px] [background-position:7px_0]"
      />
      <div className="rounded-b-xl bg-sidebar px-6 py-6 text-sidebar-foreground shadow-xl">
        <p className="text-xs text-sidebar-foreground/60">Emisión en curso</p>

        <ol className="mt-4 grid gap-3">
          {PASOS.map((paso, i) => (
            <li
              key={paso.texto}
              className="flex animate-in items-center gap-2.5 fade-in slide-in-from-left-2 fill-mode-both duration-500 motion-reduce:animate-none"
              style={{ animationDelay: `${i * 220}ms` }}
            >
              <CheckCircle2 className="size-4 shrink-0 text-sidebar-primary" />
              <span className="text-sm">{paso.texto}</span>
              {paso.detalle ? (
                <span className="ml-auto font-mono text-xs text-sidebar-foreground/50">{paso.detalle}</span>
              ) : (
                <span className="ml-auto rounded-full bg-success px-2 py-0.5 text-xs font-medium text-success-foreground">
                  Aceptado
                </span>
              )}
            </li>
          ))}
        </ol>

        <div className="mt-5 flex items-center justify-between border-t border-sidebar-border pt-4 font-mono text-sm">
          <span>F001-00001024</span>
          <span className="text-sidebar-foreground/60">S/ 2,360.00</span>
        </div>
      </div>
    </div>
  );
}
