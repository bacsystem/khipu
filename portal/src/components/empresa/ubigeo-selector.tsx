"use client";

import { useMessages } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { interpolar } from "@/i18n/mensajes";
import { apiRequest } from "@/lib/api/browser";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { Campo } from "khipu-design-system/formularios/campo";

type Ubigeo = { codigo: string; departamento: string; provincia: string; distrito: string };

/**
 * Departamento → provincia → distrito sobre el catálogo 13 (INEI), que se carga al montar. Devuelve el ubigeo elegido
 * ("" mientras falte un nivel). `inicial` preselecciona un domicilio ya guardado.
 */
export function UbigeoSelector({
  value,
  onChange,
  inicial,
  idPrefijo = "dom",
}: {
  value: string;
  onChange: (ubigeo: string) => void;
  inicial?: { departamento?: string | null; provincia?: string | null } | null;
  idPrefijo?: string;
}) {
  const t = useMessages().empresa.ubigeo;
  const [ubigeos, setUbigeos] = useState<Ubigeo[] | null>(null);
  const [errorCatalogo, setErrorCatalogo] = useState(false);
  const [intento, setIntento] = useState(0);
  const [departamento, setDepartamento] = useState(inicial?.departamento ?? "");
  const [provincia, setProvincia] = useState(inicial?.provincia ?? "");

  useEffect(() => {
    let vigente = true;
    setErrorCatalogo(false);
    apiRequest<CatalogoSunat>("/api/proxy/catalogos/13", { method: "GET" })
      .then((res) => {
        if (!vigente) return;
        if (res.estado !== "exito" || !res.datos) {
          setErrorCatalogo(true);
          return;
        }
        setUbigeos(
          res.datos.entradas.map((e) => ({
            codigo: e.codigo,
            departamento: e.extra.Departamento ?? "",
            provincia: e.extra.Provincia ?? "",
            distrito: e.extra.Distrito ?? "",
          })),
        );
      })
      .catch(() => {
        if (vigente) setErrorCatalogo(true);
      });
    return () => {
      vigente = false;
    };
  }, [intento]);

  const departamentos = useMemo(() => [...new Set((ubigeos ?? []).map((u) => u.departamento))].sort(), [ubigeos]);
  const provincias = useMemo(
    () => [...new Set((ubigeos ?? []).filter((u) => u.departamento === departamento).map((u) => u.provincia))].sort(),
    [ubigeos, departamento],
  );
  const distritos = useMemo(
    () => (ubigeos ?? []).filter((u) => u.departamento === departamento && u.provincia === provincia).sort((a, b) => a.distrito.localeCompare(b.distrito)),
    [ubigeos, departamento, provincia],
  );
  const cargando = ubigeos === null && !errorCatalogo;

  return (
    <>
      {errorCatalogo ? (
        <p className="text-cuerpo text-destructive sm:col-span-3">
          {t.errorCatalogo}{" "}
          <button type="button" onClick={() => setIntento((n) => n + 1)} className="font-medium underline">
            {t.reintentar}
          </button>
        </p>
      ) : null}
      <Campo id={`${idPrefijo}-departamento`} etiqueta={t.departamento}>
        <SelectCampo
          id={`${idPrefijo}-departamento`}
          valor={departamento}
          disabled={ubigeos === null}
          onCambio={(v) => {
            setDepartamento(v);
            setProvincia("");
            onChange("");
          }}
          opciones={[{ value: "", label: cargando ? t.cargando : errorCatalogo ? t.noDisponible : t.seleccione }, ...departamentos.map((d) => ({ value: d, label: d }))]}
        />
      </Campo>
      <Campo id={`${idPrefijo}-provincia`} etiqueta={t.provincia}>
        <SelectCampo
          id={`${idPrefijo}-provincia`}
          valor={provincia}
          disabled={!departamento}
          onCambio={(v) => {
            setProvincia(v);
            onChange("");
          }}
          opciones={[{ value: "", label: t.seleccione }, ...provincias.map((p) => ({ value: p, label: p }))]}
        />
      </Campo>
      <Campo id={`${idPrefijo}-distrito`} etiqueta={t.distrito} ayuda={value ? interpolar(t.elegido, { codigo: value }) : t.ayuda}>
        <SelectCampo
          id={`${idPrefijo}-distrito`}
          valor={value}
          disabled={!provincia}
          onCambio={onChange}
          opciones={[{ value: "", label: t.seleccione }, ...distritos.map((d) => ({ value: d.codigo, label: d.distrito }))]}
        />
      </Campo>
    </>
  );
}
