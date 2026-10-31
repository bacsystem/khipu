import { FlaskConicalIcon } from "lucide-react";
import { useMessages } from "next-intl";

/**
 * La guía de remisión es experimental en el servidor (#39): su contrato con la API REST de SUNAT sale del manual y no se probó contra SUNAT, el envío viene apagado y
 * SUNAT no publica un ambiente de pruebas para la GRE. El aviso lo dice sin prometer lo que todavía no funciona, y lo que sí se puede hacer mientras tanto.
 */
export function AvisoExperimental() {
  const t = useMessages().guias.pagina;
  return (
    <aside role="note" className="flex items-start gap-3 rounded-xl border border-warning-border bg-warning px-4 py-3 text-[13px] text-warning-foreground">
      <FlaskConicalIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="flex flex-col gap-0.5">
        <strong className="font-semibold">{t.experimentalTitulo}</strong>
        <span className="leading-relaxed">{t.experimentalTexto}</span>
      </div>
    </aside>
  );
}
