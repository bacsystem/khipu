import { CheckCircle2, FileCode2, PackageCheck, ShieldCheck, Upload } from "lucide-react";

const PASOS = [
  { texto: "Valida y procesa los datos", detalle: "reglas de negocio", icono: ShieldCheck, destacado: false },
  { texto: "Genera el XML UBL", detalle: "comprobante en XML", icono: FileCode2, destacado: false },
  { texto: "Envía el XML a SUNAT", detalle: "comprimido en .zip", icono: Upload, destacado: false },
  { texto: "Recibe respuesta de SUNAT", detalle: null, icono: CheckCircle2, destacado: true },
  { texto: "Entrega XML, CDR y PDF", detalle: "3 archivos", icono: PackageCheck, destacado: false },
];

export function ComprobantePreview() {
  return (
    <div className="relative mx-auto w-full max-w-sm">
      <div
        aria-hidden
        className="h-3 w-full rounded-t-xl bg-[radial-gradient(circle_at_center,var(--sidebar)_2.5px,transparent_2.6px)] [background-size:14px_14px] [background-position:7px_0]"
      />
      <div className="rounded-b-xl bg-sidebar px-6 py-6 text-sidebar-foreground shadow-xl">
        <p className="text-xs text-sidebar-foreground/60">Procesamiento automático con la API</p>

        <div className="relative mt-5">
          <div aria-hidden className="absolute top-4 bottom-4 left-4 w-px bg-sidebar-border" />
          {PASOS.map((paso, i) => {
            const Icono = paso.icono;
            const esUltimo = i === PASOS.length - 1;
            return (
              <div
                key={paso.texto}
                className={`relative flex animate-in items-center gap-3 fade-in slide-in-from-left-2 fill-mode-both duration-500 motion-reduce:animate-none ${
                  esUltimo ? "" : "pb-4"
                }`}
                style={{ animationDelay: `${i * 220}ms` }}
              >
                <span
                  className={`relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full ${
                    paso.destacado ? "bg-success text-success-foreground" : "bg-sidebar-accent text-sidebar-primary"
                  }`}
                >
                  <Icono className="size-4" />
                </span>
                <div className="flex min-w-0 flex-1 items-baseline justify-between gap-3">
                  <span className="text-sm">{paso.texto}</span>
                  {paso.detalle ? (
                    <span className="shrink-0 font-mono text-xs text-sidebar-foreground/50">{paso.detalle}</span>
                  ) : (
                    <span className="shrink-0 rounded-full bg-success px-2 py-0.5 text-xs font-medium text-success-foreground">
                      Aceptado
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        <div className="mt-5 flex items-center justify-between border-t border-sidebar-border pt-4 font-mono text-sm">
          <span>F001-00001024</span>
          <span className="text-sidebar-foreground/60">S/ 2,360.00</span>
        </div>
      </div>
    </div>
  );
}
