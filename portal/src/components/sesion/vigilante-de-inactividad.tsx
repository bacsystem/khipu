"use client";

import { HourglassMediumIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useRef } from "react";
import { CabeceraAviso, AvisoResponsivo, PieAviso } from "khipu-design-system/patrones/aviso-responsivo";
import { FranjaCuentaRegresiva } from "khipu-design-system/patrones/franja-cuenta-regresiva";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useAvisosDeOtrasPestanas } from "./use-avisos-de-otras-pestanas";
import { useSesionTerminada } from "./use-sesion-terminada";
import { useVigilanteDeInactividad } from "./use-vigilante-de-inactividad";

const irAPorDefecto = (url: string) => window.location.assign(url);
const ahoraPorDefecto = () => Date.now();

/**
 * El aviso y el cierre por inactividad (#637) y, siempre, la escucha del cierre de sesión en otras pestañas (#653). `minutos` es el N que informa el servidor en `/me`: ausente o `0` (o una sesión de soporte, que no lo trae) y no hay contador
 * ni aviso. Va en el layout de `(privado)`, fuera de lo que dependa de los términos, para cubrir también la pantalla de aceptarlos, que es una sesión viva.
 */
export function VigilanteDeInactividad({ minutos, irA = irAPorDefecto, ahora = ahoraPorDefecto }: { minutos?: number; irA?: (url: string) => void; ahora?: () => number }) {
  // Los avisos de las otras pestañas se escuchan aunque no haya contador (#653): ver `useAvisosDeOtrasPestanas`.
  useAvisosDeOtrasPestanas(irA);
  // Con «Tu sesión terminó» abierto (#654) no hay nada que vigilar: desmontarlo limpia los temporizadores, los oyentes y cierra el aviso de inactividad.
  const sesionTerminada = useSesionTerminada();
  if (sesionTerminada || !Number.isInteger(minutos) || minutos === undefined || minutos <= 0) return null;
  return <Vigilando minutos={minutos} irA={irA} ahora={ahora} />;
}

function Vigilando({ minutos, irA, ahora }: { minutos: number; irA: (url: string) => void; ahora: () => number }) {
  const t = useMessages().sesion.inactividad;
  const { segundos, seguirConectado, cerrarSesion } = useVigilanteDeInactividad({ minutos, irA, ahora });
  const seguirRef = useRef<HTMLButtonElement>(null);

  // La franja cambia cada segundo y no es una región viva: el lector de pantalla lee el título y el texto al abrirse el aviso y no repite el tiempo en cada tic (#656).
  // Escape lo descarta y equivale a «seguir conectado»; el clic afuera nunca lo cierra. Hoja inferior bajo 640 px, diálogo centrado desde ahí (DS).
  return (
    <AvisoResponsivo open={segundos !== null} onOpenChange={(abierto) => (abierto ? undefined : seguirConectado())} initialFocus={seguirRef} data-testid="aviso-de-inactividad">
      <CabeceraAviso icon={HourglassMediumIcon} tono="aviso" titulo={t.titulo} descripcion={t.cuerpo} />
      <FranjaCuentaRegresiva segundos={segundos ?? 0} etiqueta={t.franja} tono="aviso" className="mx-5" />
      <PieAviso>
        <button type="button" onClick={cerrarSesion} className={BOTON_SECUNDARIO} data-testid="inactividad-salir">
          {t.salir}
        </button>
        <button ref={seguirRef} type="button" onClick={seguirConectado} className={BOTON_PRIMARIO} data-testid="inactividad-seguir">
          {t.seguir}
        </button>
      </PieAviso>
    </AvisoResponsivo>
  );
}
