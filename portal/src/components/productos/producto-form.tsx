"use client";

import { SaveIcon } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";
import { Campo } from "@/components/formularios/campo";
import { apiRequest } from "@/lib/api/browser";
import type { DatosDeProducto, ProductoGuardado } from "@/lib/api/productos";
import { etiquetaDelPrecio, GRUPOS_AFECTACION } from "@/lib/comprobantes/tributos";
import { type EntradaCatalogo, unidadesDelSelector } from "@/lib/comprobantes/unidades";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { avisarQueCambiaronLosProductos } from "./eventos";

const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

/** Alta (`POST`) o edición (`PUT`) de un producto guardado. Las reglas son las de una línea de comprobante: el backend las valida igual y lo guardado siempre se puede emitir. */
export function ProductoForm({ existente, onGuardado, onCancelar }: { existente?: ProductoGuardado | null; onGuardado?: (p: ProductoGuardado) => void; onCancelar?: () => void }) {
  const [descripcion, setDescripcion] = useState(existente?.descripcion ?? "");
  const [codigo, setCodigo] = useState(existente?.codigo ?? "");
  const [unidad, setUnidad] = useState(existente?.unidad ?? "NIU");
  const [afectacion, setAfectacion] = useState(existente?.tipo_afectacion_igv ?? "10");
  // El precio se teclea como texto: la API admite hasta 10 decimales (0.0123 es un precio válido) y un campo de importe lo redondearía a 2 al editar.
  const [precioTexto, setPrecioTexto] = useState(existente ? String(existente.precio_unitario) : "");
  const [codigoSunat, setCodigoSunat] = useState(existente?.codigo_sunat ?? "");
  const [unidades, setUnidades] = useState<EntradaCatalogo[] | null>(null);
  const [intento, setIntento] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // El catálogo 03 se pide una vez, al abrir el formulario; si no llega, el selector deja al menos la unidad actual.
  useEffect(() => {
    let vigente = true;
    void apiRequest<{ entradas: EntradaCatalogo[] }>("/api/proxy/catalogos/03", { method: "GET" }).then((res) => {
      if (vigente) setUnidades(res.estado === "exito" && res.datos ? unidadesDelSelector(res.datos.entradas) : []);
    });
    return () => {
      vigente = false;
    };
  }, []);

  const errorDescripcion = descripcion.trim() === "" ? "Escribe la descripción" : descripcion.trim().length > 500 ? "Hasta 500 caracteres" : null;
  const precioValido = /^\d{1,12}(\.\d{1,10})?$/.test(precioTexto.trim());
  const precio = precioValido ? Number(precioTexto.trim()) : null;
  const errorPrecio = precioTexto.trim() !== "" && !precioValido ? "Hasta 12 enteros y 10 decimales" : precio === null || precio <= 0 ? "El precio debe ser mayor que cero" : null;
  const errorCodigoSunat = codigoSunat.trim() !== "" && !/^\d{8}$/.test(codigoSunat.trim()) ? "Son 8 dígitos (UNSPSC, catálogo 25)" : null;
  const errorCodigo = codigo.trim().length > 30 ? "Hasta 30 caracteres" : null;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setIntento(true);
    setError(null);
    if (errorDescripcion || errorPrecio || errorCodigoSunat || errorCodigo || precio === null) return;
    setEnviando(true);
    const body: DatosDeProducto = {
      codigo: codigo.trim() || undefined,
      descripcion: descripcion.trim(),
      unidad,
      tipo_afectacion_igv: afectacion,
      precio_unitario: precio,
      codigo_sunat: codigoSunat.trim() || undefined,
    };
    const res = await apiRequest<ProductoGuardado>(existente ? `/api/proxy/productos/${existente.id}` : "/api/proxy/productos", { method: existente ? "PUT" : "POST", body });
    setEnviando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    avisarQueCambiaronLosProductos();
    onGuardado?.(res.datos);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4" data-testid="producto-form">
      <Campo id="prod-descripcion" etiqueta="Descripción" error={intento ? errorDescripcion : null}>
        <input id="prod-descripcion" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={500} autoFocus={!existente} placeholder="Ej. Servicio de consultoría" className={CAMPO_DENSO} />
      </Campo>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Campo id="prod-unidad" etiqueta="Unidad de medida" ayuda="Catálogo 03">
          <select id="prod-unidad" value={unidad} onChange={(e) => setUnidad(e.target.value)} className={cn(CAMPO_DENSO, "font-mono")}>
            {(unidades ?? []).some((u) => u.codigo === unidad) ? null : <option value={unidad}>{unidad}</option>}
            {(unidades ?? []).map((u) => (
              <option key={u.codigo} value={u.codigo}>
                {u.codigo} · {u.descripcion}
              </option>
            ))}
          </select>
        </Campo>
        <Campo id="prod-afectacion" etiqueta="Afectación del IGV" ayuda="Catálogo 07">
          <select id="prod-afectacion" value={afectacion} onChange={(e) => setAfectacion(e.target.value)} className={CAMPO_DENSO}>
            {GRUPOS_AFECTACION.map((g) => (
              <optgroup key={g.grupo} label={g.grupo}>
                {g.opciones.map((a) => (
                  <option key={a.codigo} value={a.codigo}>
                    {a.codigo} · {a.etiqueta}
                  </option>
                ))}
              </optgroup>
            ))}
            <option value="40">40 · Exportación de bienes o servicios</option>
          </select>
        </Campo>
        <Campo id="prod-precio" etiqueta={etiquetaDelPrecio(afectacion)} error={intento ? errorPrecio : null} ayuda="Sin moneda: se usa en la del comprobante">
          <input id="prod-precio" inputMode="decimal" value={precioTexto} onChange={(e) => setPrecioTexto(e.target.value.replace(/[^\d.]/g, ""))} placeholder="0.00" className={cn(CAMPO_DENSO, "text-right font-mono tabular-nums")} />
        </Campo>
        <Campo id="prod-codigo" etiqueta="Código interno" opcional error={intento ? errorCodigo : null} ayuda="Único por empresa">
          <input id="prod-codigo" value={codigo} onChange={(e) => setCodigo(e.target.value)} maxLength={30} placeholder="SKU-001" className={cn(CAMPO_DENSO, "font-mono")} />
        </Campo>
      </div>
      <Campo id="prod-codigo-sunat" etiqueta="Código de producto SUNAT" opcional error={intento ? errorCodigoSunat : null} ayuda="8 dígitos (UNSPSC, catálogo 25); lo exigen los padrones obligados">
        <input
          id="prod-codigo-sunat"
          value={codigoSunat}
          onChange={(e) => setCodigoSunat(e.target.value.replace(/\D/g, "").slice(0, 8))}
          inputMode="numeric"
          placeholder="80101500"
          className={cn(CAMPO_DENSO, "font-mono tabular-nums")}
        />
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
          {enviando ? "Guardando…" : existente ? "Guardar cambios" : "Guardar producto"}
        </button>
      </div>
    </form>
  );
}
