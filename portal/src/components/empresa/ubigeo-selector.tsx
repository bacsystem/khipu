"use client";

import { useMessages } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { interpolar } from "@/i18n/mensajes";
import { apiRequest } from "@/lib/api/browser";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import { AYUDA_CAMPO, ETIQUETA_CAMPO, SELECT_NATIVO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

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
      <div className="flex flex-col gap-1.5">
        <label htmlFor={`${idPrefijo}-departamento`} className={ETIQUETA_CAMPO}>
          {t.departamento}
        </label>
        <select
          id={`${idPrefijo}-departamento`}
          value={departamento}
          disabled={ubigeos === null}
          onChange={(e) => {
            setDepartamento(e.target.value);
            setProvincia("");
            onChange("");
          }}
          className={cn(SELECT_NATIVO, "w-full cursor-pointer")}
        >
          <option value="">{cargando ? t.cargando : errorCatalogo ? t.noDisponible : t.seleccione}</option>
          {departamentos.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={`${idPrefijo}-provincia`} className={ETIQUETA_CAMPO}>
          {t.provincia}
        </label>
        <select
          id={`${idPrefijo}-provincia`}
          value={provincia}
          disabled={!departamento}
          onChange={(e) => {
            setProvincia(e.target.value);
            onChange("");
          }}
          className={cn(SELECT_NATIVO, "w-full cursor-pointer")}
        >
          <option value="">{t.seleccione}</option>
          {provincias.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={`${idPrefijo}-distrito`} className={ETIQUETA_CAMPO}>
          {t.distrito}
        </label>
        <select id={`${idPrefijo}-distrito`} value={value} disabled={!provincia} onChange={(e) => onChange(e.target.value)} className={cn(SELECT_NATIVO, "w-full cursor-pointer")}>
          <option value="">{t.seleccione}</option>
          {distritos.map((d) => (
            <option key={d.codigo} value={d.codigo}>
              {d.distrito}
            </option>
          ))}
        </select>
        <span className={AYUDA_CAMPO}>{value ? interpolar(t.elegido, { codigo: value }) : t.ayuda}</span>
      </div>
    </>
  );
}
