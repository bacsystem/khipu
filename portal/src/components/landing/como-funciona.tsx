const PASOS = [
  {
    titulo: "Subes tu certificado y tus credenciales SOL",
    detalle: "Una sola vez. Se guardan cifrados — khipu los usa para firmar cada comprobante.",
  },
  {
    titulo: "Configuras tus series",
    detalle: "F001, B001… cada una con su correlativo y, si tienes locales, su establecimiento.",
  },
  {
    titulo: "Emites desde el portal o por tu sistema",
    detalle: "khipu calcula los totales, arma el XML UBL 2.1, lo firma y lo envía a SUNAT.",
  },
  {
    titulo: "Recibes la constancia",
    detalle: "El CDR llega en segundos. Si SUNAT no responde, khipu reintenta solo — nada se numera dos veces.",
  },
];

export function ComoFunciona() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-20">
      <h2 className="font-heading text-3xl">De tu certificado a la constancia de SUNAT</h2>
      <p className="mt-2 max-w-md text-muted-foreground">Cuatro pasos, la primera vez. Después, solo emitir.</p>

      <ol className="mt-12 grid gap-x-8 gap-y-9 md:grid-cols-4">
        {PASOS.map((paso, i) => (
          <li key={paso.titulo} className="border-t-2 border-border pt-5">
            <span className="font-mono text-sm text-primary tabular-nums">{String(i + 1).padStart(2, "0")}</span>
            <h3 className="font-heading mt-3 text-base leading-snug">{paso.titulo}</h3>
            <p className="mt-1.5 text-sm text-muted-foreground">{paso.detalle}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
