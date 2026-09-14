const PASOS = [
  { texto: "Generar UBL 2.1", detalle: "Freemarker" },
  { texto: "Firmar XML-DSig", detalle: "RSA-SHA256" },
  { texto: "Validar contra XSD", detalle: "esquema oficial SUNAT" },
  { texto: "Enviar SOAP sendBill", detalle: "WS-Security" },
  { texto: "CDR", detalle: null },
];

export function ComprobantePreview() {
  return (
    <div className="relative mx-auto w-full max-w-sm">
      <div
        aria-hidden
        className="h-3 w-full rounded-t-xl bg-[radial-gradient(circle_at_center,var(--sidebar)_2.5px,transparent_2.6px)] [background-size:14px_14px] [background-position:7px_0]"
      />
      <div className="rounded-b-xl bg-sidebar px-6 py-6 text-sidebar-foreground shadow-xl">
        <p className="text-xs text-sidebar-foreground/60">Flujo de emisión</p>

        <div className="relative mt-5 pl-5">
          <div aria-hidden className="absolute top-1.5 bottom-1.5 left-[3px] w-px bg-sidebar-border" />
          {PASOS.map((paso, i) => {
            const esUltimo = i === PASOS.length - 1;
            return (
              <div
                key={paso.texto}
                className={`relative animate-in fade-in slide-in-from-left-2 fill-mode-both duration-500 motion-reduce:animate-none ${
                  esUltimo ? "" : "pb-4"
                }`}
                style={{ animationDelay: `${i * 220}ms` }}
              >
                <span
                  aria-hidden
                  className={`absolute top-1 left-[-20px] size-[7px] rounded-full ring-4 ring-sidebar ${
                    esUltimo ? "bg-success-foreground" : "bg-sidebar-primary"
                  }`}
                />
                <div className="flex items-baseline justify-between gap-3">
                  <span className="text-sm">{paso.texto}</span>
                  {paso.detalle ? (
                    <span className="font-mono text-xs text-sidebar-foreground/50">{paso.detalle}</span>
                  ) : (
                    <span className="rounded-full bg-success px-2 py-0.5 text-xs font-medium text-success-foreground">
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
