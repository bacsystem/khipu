"use client";

import { useEffect, useRef, useState } from "react";
import { consultarRuc, type ResultadoConsultaRuc } from "@/lib/api/consulta-ruc";
import { rucValido } from "@/lib/validacion";

/**
 * Consulta el padrón de SUNAT cuando `ruc` es un RUC completo y válido (11 dígitos con su dígito verificador), y solo entonces: al teclear los
 * primeros dígitos no hay pedido, y un RUC mal digitado no gasta el cupo de consultas de la empresa. Cada RUC se pide una vez mientras el formulario
 * viva —volver a un RUC ya consultado responde de la memoria— y un pedido en vuelo no se repite (tampoco con el doble efecto de StrictMode).
 *
 * Los resultados se guardan por RUC, así que una respuesta que llega tarde, cuando ya se digitó otro, no pisa nada: solo queda guardada.
 */
export function useConsultaRuc(ruc: string, activa: boolean) {
  const [resultados, setResultados] = useState<Record<string, ResultadoConsultaRuc>>({});
  const enVuelo = useRef(new Set<string>());
  const aConsultar = activa && rucValido(ruc);

  useEffect(() => {
    if (!aConsultar || ruc in resultados || enVuelo.current.has(ruc)) return;
    enVuelo.current.add(ruc);
    consultarRuc(ruc).then((r) => {
      enVuelo.current.delete(ruc);
      setResultados((prev) => ({ ...prev, [ruc]: r }));
    });
  }, [ruc, aConsultar, resultados]);

  return {
    /** El resultado del RUC que está digitado ahora; `null` si no hay RUC completo o la respuesta no llegó todavía. */
    resultado: aConsultar ? (resultados[ruc] ?? null) : null,
    consultando: aConsultar && !(ruc in resultados),
    /** Tras un cupo agotado o un error: olvida el resultado de este RUC para pedirlo de nuevo. */
    reintentar: () =>
      setResultados((prev) => Object.fromEntries(Object.entries(prev).filter(([r]) => r !== ruc))),
  };
}
