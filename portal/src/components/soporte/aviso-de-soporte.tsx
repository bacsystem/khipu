"use client";

import { EyeIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useRef, useState } from "react";
import { Banner } from "khipu-design-system/feedback/banner";
import { interpolar } from "@/i18n/mensajes";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_SECUNDARIA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";

/**
 * El aviso permanente de que se está actuando como un cliente (#184). Va arriba de todo el portal privado, fijo al hacer scroll, para que nadie —ni el
 * administrador ni quien mire su pantalla— confunda esto con una sesión normal. Salir borra las cookies de cliente de este navegador y vuelve a la cuenta en
 * el backoffice; la sesión del administrador es otra cookie y sigue abierta. `irA` es para las pruebas: por defecto navega de verdad.
 */
export function AvisoDeSoporte({
  email,
  hasta,
  cuentaId,
  irA = (url) => window.location.assign(url),
}: {
  email: string;
  hasta: string;
  cuentaId: string;
  irA?: (url: string) => void;
}) {
  const t = useMessages().soporte.aviso;
  const locale = useLocale();
  const [saliendo, setSaliendo] = useState(false);
  const [error, setError] = useState(false);
  // Un ref, no el estado: dos clics en el mismo tick leen el `saliendo` viejo del closure.
  const saliendoRef = useRef(false);

  async function salir() {
    if (saliendoRef.current) return;
    saliendoRef.current = true;
    setSaliendo(true);
    setError(false);
    const res = await apiRequest<null>("/api/soporte/salir", { method: "POST" });
    if (res.estado !== "exito") {
      saliendoRef.current = false;
      setSaliendo(false);
      setError(true);
      return;
    }
    irA(`/admin/cuentas/${cuentaId}`);
  }

  return (
    <div role="region" aria-label={t.region} data-testid="aviso-de-soporte" className="sticky top-0 z-40">
      <Banner
        tono="aviso"
        icon={EyeIcon}
        accion={
          <div className="flex items-center gap-3">
            {error ? (
              <span role="alert" className="text-etiqueta font-medium text-destructive">
                {t.error}
              </span>
            ) : null}
            <button type="button" onClick={salir} disabled={saliendo} data-testid="salir-de-soporte" className={ACCION_SECUNDARIA}>
              {saliendo ? t.saliendo : t.salir}
            </button>
          </div>
        }
      >
        <strong className="font-semibold">{t.titulo}.</strong> {interpolar(t.descripcion, { email, hasta: formatearFechaHora(hasta, locale) })}
      </Banner>
    </div>
  );
}
