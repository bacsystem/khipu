"use client";

import { FloppyDiskIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { type FormEvent, useEffect, useState } from "react";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { apiRequest } from "@/lib/api/browser";
import type { DatosDeProducto, ProductoGuardado } from "@/lib/api/productos";
import { etiquetaDelPrecio, gruposAfectacion } from "@/lib/comprobantes/tributos";
import { type EntradaCatalogo, unidadesDelSelector } from "@/lib/comprobantes/unidades";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { avisarQueCambiaronLosProductos } from "./eventos";


/** Alta (`POST`) o edición (`PUT`) de un producto guardado. Las reglas son las de una línea de comprobante: el backend las valida igual y lo guardado siempre se puede emitir. */
export function ProductoForm({ existente, onGuardado, onCancelar }: { existente?: ProductoGuardado | null; onGuardado?: (p: ProductoGuardado) => void; onCancelar?: () => void }) {
  const messages = useMessages();
  const t = messages.productos.form;
  const mensajeError = useMensajeError();
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

  const errorDescripcion = descripcion.trim() === "" ? t.errorDescripcion : descripcion.trim().length > 500 ? t.errorDescripcionLarga : null;
  const precioValido = /^\d{1,12}(\.\d{1,10})?$/.test(precioTexto.trim());
  const precio = precioValido ? Number(precioTexto.trim()) : null;
  const errorPrecio = precioTexto.trim() !== "" && !precioValido ? t.errorPrecioFormato : precio === null || precio <= 0 ? t.errorPrecioCero : null;
  const errorCodigoSunat = codigoSunat.trim() !== "" && !/^\d{8}$/.test(codigoSunat.trim()) ? t.errorCodigoSunat : null;
  const errorCodigo = codigo.trim().length > 30 ? t.errorCodigo : null;

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
      <Campo id="prod-descripcion" etiqueta={t.descripcion} error={intento ? errorDescripcion : null}>
        <Entrada id="prod-descripcion" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={500} autoFocus={!existente} placeholder={t.descripcionPlaceholder} invalido={intento && !!errorDescripcion} aria-describedby={intento && errorDescripcion ? "prod-descripcion-error" : undefined} />
      </Campo>
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
        <Campo id="prod-unidad" etiqueta={t.unidad} ayuda={t.unidadAyuda}>
          {/* Las unidades más usadas del catálogo 03 son pocas: un combo basta. Si la actual no está entre ellas (o el catálogo no llegó), se ofrece igual. */}
          <SelectCampo
            id="prod-unidad"
            mono
            valor={unidad}
            onCambio={setUnidad}
            opciones={[
              ...((unidades ?? []).some((u) => u.codigo === unidad) ? [] : [{ value: unidad, label: unidad }]),
              ...(unidades ?? []).map((u) => ({ value: u.codigo, label: `${u.codigo} · ${u.descripcion}` })),
            ]}
          />
        </Campo>
        <Campo id="prod-afectacion" etiqueta={t.afectacion} ayuda={t.afectacionAyuda}>
          <SelectCampo
            id="prod-afectacion"
            valor={afectacion}
            onCambio={setAfectacion}
            opciones={[
              ...gruposAfectacion(messages).flatMap((g) => g.opciones.map((a) => ({ value: a.codigo, label: `${a.codigo} · ${a.etiqueta}`, grupo: g.grupo }))),
              { value: "40", label: t.exportacion },
            ]}
          />
        </Campo>
        <Campo id="prod-precio" etiqueta={etiquetaDelPrecio(afectacion, messages)} error={intento ? errorPrecio : null} ayuda={t.precioAyuda}>
          {/* `Entrada` y no `EntradaMonto`: el precio admite hasta 10 decimales y `EntradaMonto` lo redondearía a 2. */}
          <Entrada id="prod-precio" inputMode="decimal" value={precioTexto} onChange={(e) => setPrecioTexto(e.target.value.replace(/[^\d.]/g, ""))} placeholder="0.00" mono className="text-right tabular-nums" invalido={intento && !!errorPrecio} aria-describedby={intento && errorPrecio ? "prod-precio-error" : "prod-precio-ayuda"} />
        </Campo>
        <Campo id="prod-codigo" etiqueta={t.codigo} opcional error={intento ? errorCodigo : null} ayuda={t.codigoAyuda}>
          <Entrada id="prod-codigo" value={codigo} onChange={(e) => setCodigo(e.target.value)} maxLength={30} placeholder="SKU-001" mono invalido={intento && !!errorCodigo} aria-describedby={intento && errorCodigo ? "prod-codigo-error" : "prod-codigo-ayuda"} />
        </Campo>
      </div>
      <Campo id="prod-codigo-sunat" etiqueta={t.codigoSunat} opcional error={intento ? errorCodigoSunat : null} ayuda={t.codigoSunatAyuda}>
        <Entrada
          id="prod-codigo-sunat"
          value={codigoSunat}
          onChange={(e) => setCodigoSunat(e.target.value.replace(/\D/g, "").slice(0, 8))}
          inputMode="numeric"
          placeholder="80101500"
          mono
          className="tabular-nums"
          invalido={intento && !!errorCodigoSunat}
          aria-describedby={intento && errorCodigoSunat ? "prod-codigo-sunat-error" : "prod-codigo-sunat-ayuda"}
        />
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
