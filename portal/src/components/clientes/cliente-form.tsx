"use client";

import { SaveIcon } from "lucide-react";
import { type FormEvent, useMemo, useState } from "react";
import { Campo } from "@/components/formularios/campo";
import { apiRequest } from "@/lib/api/browser";
import type { ClienteGuardado, DatosDeCliente } from "@/lib/api/clientes";
import { DOCUMENTOS_DE_CLIENTE, errorDeDocumento } from "@/lib/comprobantes/documentos";
import { listarPaises } from "@/lib/comprobantes/paises";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { avisarQueCambiaronLosClientes } from "./eventos";

const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

/** Alta (`POST`) o edición (`PUT`) de un cliente guardado. El documento se valida con las mismas reglas que el comprador de una boleta, así que lo guardado siempre se puede usar al emitir. */
export function ClienteForm({ existente, onGuardado, onCancelar }: { existente?: ClienteGuardado | null; onGuardado?: (c: ClienteGuardado) => void; onCancelar?: () => void }) {
  const [tipoDoc, setTipoDoc] = useState(existente?.tipo_doc ?? "6");
  const [numDoc, setNumDoc] = useState(existente?.num_doc ?? "");
  const [razon, setRazon] = useState(existente?.razon_social ?? "");
  const [direccion, setDireccion] = useState(existente?.direccion ?? "");
  const [pais, setPais] = useState(existente?.pais ?? "");
  const paises = useMemo(() => listarPaises(), []);
  const [email, setEmail] = useState(existente?.email ?? "");
  const [intento, setIntento] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const documento = DOCUMENTOS_DE_CLIENTE.find((d) => d.codigo === tipoDoc) ?? DOCUMENTOS_DE_CLIENTE[0];
  const errorDoc = errorDeDocumento(tipoDoc, numDoc);
  const errorNombre = razon.trim().length < 3 ? "Escribe el nombre o la razón social (3 caracteres o más)" : null;
  const errorCorreo = email.trim() !== "" && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()) ? "El correo no es válido" : null;

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
        <Campo id="cli-tipo-doc" etiqueta="Documento">
          <select
            id="cli-tipo-doc"
            value={tipoDoc}
            onChange={(e) => {
              setTipoDoc(e.target.value);
              setNumDoc("");
            }}
            className={CAMPO_DENSO}
          >
            {DOCUMENTOS_DE_CLIENTE.map((d) => (
              <option key={d.codigo} value={d.codigo}>
                {d.etiqueta}
              </option>
            ))}
          </select>
        </Campo>
        <Campo id="cli-num-doc" etiqueta="Número" error={intento ? errorDoc : null} ayuda={documento.titulo}>
          <input
            id="cli-num-doc"
            key={tipoDoc}
            value={numDoc}
            onChange={(e) => setNumDoc((documento.codigo === "1" || documento.codigo === "6" ? e.target.value.replace(/\D/g, "") : e.target.value.replace(/\s/g, "")).slice(0, documento.max))}
            inputMode={documento.codigo === "1" || documento.codigo === "6" ? "numeric" : "text"}
            placeholder={documento.ejemplo}
            autoFocus={!existente}
            className={cn(CAMPO_DENSO, "font-mono tabular-nums")}
          />
        </Campo>
      </div>
      <Campo id="cli-razon" etiqueta={tipoDoc === "6" ? "Razón social" : "Nombre"} error={intento ? errorNombre : null}>
        <input id="cli-razon" value={razon} onChange={(e) => setRazon(e.target.value)} maxLength={1500} className={CAMPO_DENSO} />
      </Campo>
      <Campo id="cli-direccion" etiqueta="Dirección" opcional>
        <input id="cli-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} maxLength={300} className={CAMPO_DENSO} />
      </Campo>
      <Campo id="cli-pais" etiqueta="País" opcional ayuda="Para sus exportaciones">
        <select id="cli-pais" value={pais} onChange={(e) => setPais(e.target.value)} className={CAMPO_DENSO}>
          <option value="">Sin país</option>
          {paises.map((p) => (
            <option key={p.codigo} value={p.codigo}>
              {p.nombre}
            </option>
          ))}
        </select>
      </Campo>
      <Campo id="cli-email" etiqueta="Correo" opcional error={intento ? errorCorreo : null} ayuda="Para enviarle sus comprobantes">
        <input id="cli-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={150} className={CAMPO_DENSO} />
      </Campo>

      {error ? (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            Cancelar
          </button>
        ) : null}
        <button type="submit" disabled={enviando} className={BOTON_PRIMARIO}>
          <SaveIcon className="size-4" />
          {enviando ? "Guardando…" : existente ? "Guardar cambios" : "Guardar cliente"}
        </button>
      </div>
    </form>
  );
}
