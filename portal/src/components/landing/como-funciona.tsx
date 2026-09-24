import { FileSignature, KeyRound, ListOrdered, ShieldCheck } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Cómo se pasa del certificado a la constancia.
 *
 * <p>Lo que esta sección tiene que dejar claro no es que sean cuatro pasos, sino que **dos son de una sola vez y dos
 * se repiten en cada comprobante**. Eso estaba dicho al pasar en el subtítulo, donde nadie lo lee, y es justo la
 * objeción de quien evalúa: cuánto trabajo es esto todas las semanas. Por eso la configuración y la emisión están
 * separadas visualmente y cada grupo dice cada cuánto ocurre.
 */
const ETAPAS = [
  {
    cuando: "Una sola vez",
    nota: "Configuración inicial",
    pasos: [
      {
        icono: KeyRound,
        titulo: "Subes tu certificado y tus credenciales SOL",
        detalle: "Se guardan cifrados. khipu los usa para firmar cada comprobante, y no vuelves a tocarlos.",
      },
      {
        icono: ListOrdered,
        titulo: "Configuras tus series",
        detalle: "F001, B001… cada una con su correlativo y, si tienes locales, su establecimiento.",
      },
    ],
  },
  {
    cuando: "En cada comprobante",
    nota: "Lo único que repites",
    pasos: [
      {
        icono: FileSignature,
        titulo: "Emites desde el portal o por tu sistema",
        detalle: "khipu calcula los totales, arma el XML UBL 2.1, lo firma y lo envía a SUNAT.",
      },
      {
        icono: ShieldCheck,
        titulo: "Recibes la constancia",
        detalle: "El CDR llega en segundos. Si SUNAT no responde, khipu reintenta solo y nada se numera dos veces.",
      },
    ],
  },
];

export function ComoFunciona() {
  let n = 0;
  return (
    <section className="mx-auto max-w-6xl px-6 py-24">
      <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-[12px] font-medium text-foreground/80">
        <span className="size-1.5 rounded-full bg-acento" aria-hidden />
        Cómo funciona
      </p>
      <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">
        De tu certificado a la constancia de SUNAT
      </h2>
      <p className="mt-2 max-w-xl text-muted-foreground">
        La configuración se hace una vez. Después, emitir es un paso y la constancia llega sola.
      </p>

      <div className="mt-12 grid gap-8 lg:grid-cols-2">
        {ETAPAS.map((etapa, e) => (
          <div key={etapa.cuando}>
            <div className="flex items-center gap-3">
              <span
                className={cn(
                  "rounded-full px-3 py-1 text-[12px] font-medium",
                  e === 0 ? "bg-muted text-muted-foreground" : "bg-acento-suave text-foreground/80 ring-1 ring-acento-borde",
                )}
              >
                {etapa.cuando}
              </span>
              <span className="text-[12px] text-muted-foreground">{etapa.nota}</span>
            </div>

            <ol className="mt-5 grid gap-4 sm:grid-cols-2">
              {etapa.pasos.map((paso) => {
                n += 1;
                const Icono = paso.icono;
                return (
                  <li
                    key={paso.titulo}
                    className="group rounded-xl border border-border bg-card p-5 transition-all duration-300 hover:-translate-y-1 hover:border-acento-borde hover:shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_28px_-18px_rgba(16,35,31,0.35)]"
                  >
                    <div className="flex items-center justify-between">
                      <span
                        className="flex size-9 items-center justify-center rounded-lg bg-acento-suave text-acento ring-1 ring-acento-borde transition-colors"
                        aria-hidden
                      >
                        <Icono className="size-[18px]" />
                      </span>
                      <span className="font-mono text-sm text-muted-foreground tabular-nums">{String(n).padStart(2, "0")}</span>
                    </div>
                    <h3 className="font-heading mt-4 text-base leading-snug font-semibold">{paso.titulo}</h3>
                    <p className="mt-1.5 text-sm text-muted-foreground">{paso.detalle}</p>
                  </li>
                );
              })}
            </ol>
          </div>
        ))}
      </div>
    </section>
  );
}
