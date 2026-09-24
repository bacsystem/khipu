import { Plus } from "lucide-react";

const PREGUNTAS = [
  {
    pregunta: "¿Qué pasa si SUNAT no responde?",
    respuesta:
      "khipu reintenta automáticamente con espera creciente. El comprobante queda pendiente, nunca se pierde ni se numera dos veces.",
  },
  {
    pregunta: "¿Necesito instalar algo en mi sistema?",
    respuesta: "No. Es una API REST: la llamas desde tu sistema, o usas el portal directamente sin escribir código.",
  },
  {
    pregunta: "¿Qué pasa con mi certificado digital?",
    respuesta:
      "Se sube una vez y se guarda cifrado. khipu lo usa para firmar cada XML con XML-DSig (RSA-SHA256), como exige SUNAT.",
  },
  {
    pregunta: "¿El plan gratis tiene fecha de vencimiento?",
    respuesta: "No. Es permanente, no un trial — mientras tu volumen entre en los 30 documentos mensuales.",
  },
  {
    pregunta: "¿Puedo emitir boletas o notas de crédito?",
    respuesta: "Por ahora emitimos factura electrónica. El resto del catálogo SUNAT — boletas, notas, guías — está en camino.",
  },
];

export function Faq() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <h2 className="font-heading text-3xl">Preguntas frecuentes</h2>
      <div className="mt-8 divide-y divide-border border-t border-border">
        {PREGUNTAS.map((item) => (
          <details key={item.pregunta} className="group py-5">
            <summary className="flex cursor-pointer list-none items-start justify-between gap-4 font-heading text-base leading-snug text-foreground marker:content-none">
              {item.pregunta}
              <Plus className="mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform duration-200 group-open:rotate-45" />
            </summary>
            <p className="mt-3 max-w-xl text-sm text-muted-foreground">{item.respuesta}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
