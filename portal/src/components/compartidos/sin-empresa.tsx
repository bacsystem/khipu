import { BuildingsIcon } from "@phosphor-icons/react/dist/ssr";
import Link from "next/link";
import { EstadoVacio } from "khipu-design-system/feedback/estado-vacio";
import { ACCION_SECUNDARIA } from "@/lib/estilos";

/** Lo que muestra una página del panel cuando la sesión todavía no tiene empresa: el estado vacío del design system con el enlace al alta. */
export function SinEmpresa({ texto, accion }: { texto: string; accion: string }) {
  return (
    <EstadoVacio
      icon={BuildingsIcon}
      titulo={texto}
      accion={
        <Link href="/onboarding" className={ACCION_SECUNDARIA}>
          {accion}
        </Link>
      }
    />
  );
}
