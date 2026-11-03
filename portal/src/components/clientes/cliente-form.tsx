"use client";

import { FloppyDiskIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { type FormEvent, useMemo, useState } from "react";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { EstadoConsultaRuc } from "@/components/consulta-ruc/estado-consulta-ruc";
import { useAutocompletadoDelPadron } from "@/components/consulta-ruc/use-consulta-ruc";
import { apiRequest } from "@/lib/api/browser";
import type { ClienteGuardado, DatosDeCliente } from "@/lib/api/clientes";
import { documentosDeCliente, errorDeDocumento } from "@/lib/comprobantes/documentos";
import { listarPaises } from "@/lib/comprobantes/paises";
import { Combobox } from "khipu-design-system/formularios/combobox";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { avisarQueCambiaronLosClientes } from "./eventos";


/** Alta (`POST`) o edición (`PUT`) de un cliente guardado. El documento se valida con las mismas reglas que el comprador de una boleta, así que lo guardado siempre se puede usar al emitir. */
export function ClienteForm({ existente, onGuardado, onCancelar }: { existente?: ClienteGuardado | null; onGuardado?: (c: ClienteGuardado) => void; onCancelar?: () => void }) {
  const messages = useMessages();
  const t = messages.clientes.form;
  const DOCUMENTOS_DE_CLIENTE = documentosDeCliente(messages);
  const mensajeError = useMensajeError();
  const [tipoDoc, setTipoDoc] = useState(existente?.tipo_doc ?? "6");
  const [numDoc, setNumDoc] = useState(existente?.num_doc ?? "");
  const [razon, setRazon] = useState(existente?.razon_social ?? "");
  const [direccion, setDireccion] = useState(existente?.direccion ?? "");
  const [pais, setPais] = useState(existente?.pais ?? "");
  const locale = useLocale();
  const itemsPais = useMemo(() => [{ value: "", label: t.sinPais }, ...listarPaises(locale).map((p) => ({ value: p.codigo, label: p.nombre }))], [locale, t.sinPais]);
  const [email, setEmail] = useState(existente?.email ?? "");
  const [intento, setIntento] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // #575: con un RUC completo se consulta el padrón de SUNAT y se rellenan el nombre y la dirección vacíos. Al editar, solo si el RUC cambió: abrir un cliente
  // guardado no gasta una consulta.
  const padron = useAutocompletadoDelPadron({
    ruc: numDoc,
    activa: tipoDoc === "6" && numDoc !== (existente?.num_doc ?? ""),
    campos: [
      { valor: razon, poner: setRazon, dato: (c) => c.razon_social },
      { valor: direccion, poner: setDireccion, dato: (c) => c.direccion },
    ],
  });

  const documento = DOCUMENTOS_DE_CLIENTE.find((d) => d.codigo === tipoDoc) ?? DOCUMENTOS_DE_CLIENTE[0];
  const errorDoc = errorDeDocumento(tipoDoc, numDoc, messages);
  const errorNombre = razon.trim().length < 3 ? t.errorNombre : null;
  const errorCorreo = email.trim() !== "" && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()) ? t.errorCorreo : null;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setIntento(true);
    setError(null);
    if (errorDoc || errorNombre || errorCorreo) return;
    setEnviando(true);
    const body: DatosDeCliente = {
      tipo_doc: tipoDoc,
      num_doc: numDoc.trim(),
      razon_social: razon.trim(),
      direccion: direccion.trim() || undefined,
      pais: pais || undefined,
      email: email.trim() || undefined,
    };
    const res = await apiRequest<ClienteGuardado>(existente ? `/api/proxy/clientes/${existente.id}` : "/api/proxy/clientes", { method: existente ? "PUT" : "POST", body });
    setEnviando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    avisarQueCambiaronLosClientes();
    onGuardado?.(res.datos);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4" data-testid="cliente-form">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-[220px_1fr]">
        <Campo id="cli-tipo-doc" etiqueta={t.documento}>
          <SelectCampo
            id="cli-tipo-doc"
            valor={tipoDoc}
            onCambio={(v) => {
              setTipoDoc(v);
              setNumDoc("");
            }}
            opciones={DOCUMENTOS_DE_CLIENTE.map((d) => ({ value: d.codigo, label: d.etiqueta }))}
          />
        </Campo>
        <Campo id="cli-num-doc" etiqueta={t.numero} error={intento ? errorDoc : null} ayuda={documento.titulo}>
          <Entrada
            id="cli-num-doc"
            key={tipoDoc}
            value={numDoc}
            onChange={(e) => setNumDoc((documento.codigo === "1" || documento.codigo === "6" ? e.target.value.replace(/\D/g, "") : e.target.value.replace(/\s/g, "")).slice(0, documento.max))}
            inputMode={documento.codigo === "1" || documento.codigo === "6" ? "numeric" : "text"}
            placeholder={documento.ejemplo}
            autoFocus={!existente}
            invalido={intento && !!errorDoc}
            aria-describedby={intento && errorDoc ? "cli-num-doc-error" : "cli-num-doc-ayuda"}
            mono
            className="tabular-nums"
          />
        </Campo>
      </div>
      <EstadoConsultaRuc
        consultando={padron.consultando}
        resultado={padron.resultado}
        nombreActual={razon}
        onReintentar={padron.reintentar}
        onUsarNombre={(n) => padron.usarDato(0, n)}
        className="-mt-2"
      />
      <Campo id="cli-razon" etiqueta={tipoDoc === "6" ? t.razonSocial : t.nombre} error={intento ? errorNombre : null}>
        <Entrada id="cli-razon" value={razon} onChange={(e) => setRazon(e.target.value)} maxLength={1500} invalido={intento && !!errorNombre} aria-describedby={intento && errorNombre ? "cli-razon-error" : undefined} />
      </Campo>
      <Campo id="cli-direccion" etiqueta={t.direccion} opcional>
        <Entrada id="cli-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} maxLength={300} />
      </Campo>
      <Campo id="cli-pais" etiqueta={t.pais} opcional ayuda={t.paisAyuda}>
        {/* Unos 250 países: se escribe para buscar. «Sin país» sigue siendo una opción más, la primera de la lista. */}
        <Combobox
          id="cli-pais"
          valor={pais}
          onCambio={(v) => setPais(v ?? "")}
          items={itemsPais}
          placeholder={t.sinPais}
          vacio={messages.comun.sinResultados}
          etiquetaAbrir={messages.comun.abrirOpciones}
        />
      </Campo>
      <Campo id="cli-email" etiqueta={t.correo} opcional error={intento ? errorCorreo : null} ayuda={t.correoAyuda}>
        <Entrada id="cli-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={150} invalido={intento && !!errorCorreo} aria-describedby={intento && errorCorreo ? "cli-email-error" : "cli-email-ayuda"} />
      </Campo>

      {error ? (
        <p className="text-cuerpo text-destructive" role="alert">
          {error}
        </p>
      ) : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            {messages.comun.cancelar}
          </button>
        ) : null}
        <BotonAsync type="submit" pendiente={enviando} icon={<FloppyDiskIcon className="size-4" />} textoPendiente={t.guardando} className={BOTON_PRIMARIO}>
          {existente ? t.guardarCambios : t.guardar}
        </BotonAsync>
      </div>
    </form>
  );
}
