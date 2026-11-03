"use client";

import { useEffect, useRef, useState } from "react";
import { consultarRuc, type ContribuyenteSunat, type OrigenConsultaRuc, type ResultadoConsultaRuc } from "@/lib/api/consulta-ruc";
import { rucValido } from "@/lib/validacion";

/**
 * Consulta el padrón de SUNAT cuando `ruc` es un RUC completo y válido (11 dígitos con su dígito verificador), y solo entonces: al teclear los
 * primeros dígitos no hay pedido, y un RUC mal digitado no gasta el cupo de consultas. Cada RUC se pide una vez mientras el formulario
 * viva —volver a un RUC ya consultado responde de la memoria— y un pedido en vuelo no se repite (tampoco con el doble efecto de StrictMode).
 *
 * Los resultados se guardan por RUC, así que una respuesta que llega tarde, cuando ya se digitó otro, no pisa nada: solo queda guardada.
 */
export function useConsultaRuc(ruc: string, activa: boolean, origen: OrigenConsultaRuc = "portal") {
  const [resultados, setResultados] = useState<Record<string, ResultadoConsultaRuc>>({});
  const enVuelo = useRef(new Set<string>());
  const aConsultar = activa && rucValido(ruc);

  useEffect(() => {
    if (!aConsultar || ruc in resultados || enVuelo.current.has(ruc)) return;
    enVuelo.current.add(ruc);
    consultarRuc(ruc, origen).then((r) => {
      enVuelo.current.delete(ruc);
      setResultados((prev) => ({ ...prev, [ruc]: r }));
    });
  }, [ruc, aConsultar, resultados, origen]);

  return {
    /** El resultado del RUC que está digitado ahora; `null` si no hay RUC completo o la respuesta no llegó todavía. */
    resultado: aConsultar ? (resultados[ruc] ?? null) : null,
    consultando: aConsultar && !(ruc in resultados),
    /** Tras un cupo agotado o un error: olvida el resultado de este RUC para pedirlo de nuevo. */
    reintentar: () =>
      setResultados((prev) => Object.fromEntries(Object.entries(prev).filter(([r]) => r !== ruc))),
  };
}

/** Un campo que el padrón puede rellenar: lo que tiene escrito, cómo cambiarlo y qué dato del contribuyente le corresponde. */
export type CampoDelPadron = {
  valor: string;
  poner: (valor: string) => void;
  dato: (c: ContribuyenteSunat) => string | null | undefined;
};

/**
 * La consulta más el autocompletado (#575): lo que hace el modal de emisión (#379), para cualquier formulario con un RUC. Cuando llega un contribuyente se
 * rellena solo lo que está vacío: lo que el usuario escribió se respeta (y `EstadoConsultaRuc` le ofrece el nombre de SUNAT aparte). Al cambiar de RUC se
 * quita lo que se rellenó y sigue intacto: el nombre de un contribuyente no puede quedar bajo el RUC de otro; lo que el usuario cambió a mano se queda.
 *
 * `campos` se lee en cada render (no hace falta memorizarlo): solo se usa cuando cambia el RUC o llega una respuesta.
 */
export function useAutocompletadoDelPadron({
  ruc,
  activa = true,
  origen = "portal",
  campos,
}: {
  ruc: string;
  activa?: boolean;
  origen?: OrigenConsultaRuc;
  campos: CampoDelPadron[];
}) {
  const consulta = useConsultaRuc(ruc, activa, origen);
  const { resultado } = consulta;
  const actuales = useRef(campos);
  actuales.current = campos;
  /** Qué se rellenó, campo por campo, y para qué RUC. */
  const rellenado = useRef<{ ruc: string; valores: string[] } | null>(null);

  useEffect(() => {
    const previo = rellenado.current;
    if (!previo || previo.ruc === ruc) return;
    actuales.current.forEach((c, i) => {
      if (previo.valores[i] && c.valor === previo.valores[i]) c.poner("");
    });
    rellenado.current = null;
  }, [ruc]);

  useEffect(() => {
    if (resultado?.tipo !== "encontrado") return;
    const contribuyente = resultado.contribuyente;
    const valores = actuales.current.map((c) => {
      const dato = c.dato(contribuyente)?.trim() ?? "";
      if (!dato || c.valor.trim()) return "";
      c.poner(dato);
      return dato;
    });
    rellenado.current = { ruc: contribuyente.ruc, valores };
  }, [resultado]);

  return {
    ...consulta,
    /** El usuario eligió el dato de SUNAT (p. ej. «Usar este nombre»): cuenta como rellenado por el padrón, así que también se suelta al cambiar de RUC. */
    usarDato: (indice: number, valor: string) => {
      actuales.current[indice]?.poner(valor);
      const valores = [...(rellenado.current?.valores ?? [])];
      valores[indice] = valor;
      rellenado.current = { ruc, valores };
    },
  };
}
