import { FlaskIcon } from "@phosphor-icons/react/dist/ssr";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { useMessages } from "next-intl";

/**
 * La guía de remisión es experimental en el servidor (#39): su contrato con la API REST de SUNAT sale del manual y no se probó contra SUNAT, el envío viene apagado y
 * SUNAT no publica un ambiente de pruebas para la GRE. El aviso lo dice sin prometer lo que todavía no funciona, y lo que sí se puede hacer mientras tanto.
 */
export function AvisoExperimental() {
  const t = useMessages().guias.pagina;
  return (
    // `role="note"` en el envoltorio: es una nota de la página, no un estado que cambia (la `Alerta` del DS pone `status`).
    <aside role="note">
      <Alerta tono="aviso" icon={FlaskIcon} titulo={t.experimentalTitulo}>
        {t.experimentalTexto}
      </Alerta>
    </aside>
  );
}
