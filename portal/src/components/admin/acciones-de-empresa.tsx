"use client";

import { ArrowRightLeftIcon, KeyRoundIcon, PlugZapIcon } from "lucide-react";
import { useRef, useState } from "react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import { Etiqueta } from "@/components/admin/etiquetas";
import type { ResultadoDeConexionAdmin } from "@/lib/api/admin-acciones-empresa";
import type { EntornoAdmin } from "@/lib/api/admin-empresas";
import { apiRequest } from "@/lib/api/browser";
import { BOTON_SECUNDARIO } from "@/lib/estilos";
import { messages, mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.accionesEmpresa;

/**
 * Pasar la empresa al otro entorno (#187). Es la acción con más consecuencias del backoffice: decide contra qué URLs de SUNAT se emite. Por eso el diálogo
 * dice a cuál pasa, qué credenciales hacen falta, que no toca lo ya emitido y que no se puede con envíos pendientes. Siempre ofrece el entorno contrario
 * al que la página muestra.
 */
export function CambiarEntorno({ empresaId, razonSocial, entorno }: { empresaId: string; razonSocial: string; entorno: EntornoAdmin }) {
  const hacia: EntornoAdmin = entorno === "BETA" ? "PRODUCCION" : "BETA";
  const textos = hacia === "PRODUCCION" ? t.entorno.aProduccion : t.entorno.aBeta;
  return (
    <DialogoDeAccion
      testId="cambiar-entorno"
      boton={t.entorno.boton}
      icono={ArrowRightLeftIcon}
      tono={hacia === "PRODUCCION" ? "peligro" : "secundario"}
      titulo={textos.titulo}
      descripcion={textos.descripcion.replace("{empresa}", razonSocial)}
      efectos={textos.efectos}
      advertencia
      confirmar={textos.confirmar}
      enviando={t.entorno.enviando}
      cancelar={t.cancelar}
      ruta={`/api/admin/empresas/${empresaId}/entorno`}
      cuerpo={() => ({ entorno: hacia })}
      estadoViejo={["ENTORNO_SIN_CAMBIOS"]}
    />
  );
}

/** Revocar una API key concreta (#187). Deja de autenticar de inmediato y no se deshace: el diálogo lo dice, y dice que las demás keys siguen sirviendo. */
export function RevocarApiKey({ empresaId, apiKeyId, prefijo, razonSocial }: { empresaId: string; apiKeyId: string; prefijo: string; razonSocial: string }) {
  return (
    <DialogoDeAccion
      testId={`revocar-api-key-${apiKeyId}`}
      boton={t.apiKey.boton}
      icono={KeyRoundIcon}
      tono="peligro"
      chico
      titulo={t.apiKey.titulo}
      descripcion={t.apiKey.descripcion.replace("{prefijo}", prefijo).replace("{empresa}", razonSocial)}
      efectos={t.apiKey.efectos}
      advertencia
      confirmar={t.apiKey.confirmar}
      enviando={t.apiKey.enviando}
      cancelar={t.cancelar}
      ruta={`/api/admin/empresas/${empresaId}/api-keys/${apiKeyId}/revocar`}
      estadoViejo={["API_KEY_YA_REVOCADA"]}
    />
  );
}

const TONO_DEL_RESULTADO = { CONECTADO: "ok", RECHAZADO: "aviso", SIN_RESPUESTA: "error" } as const;

/**
 * Probar la conexión con SUNAT (#187). No pide confirmación: no cambia nada ni envía nada, y el resultado se muestra en la propia página, no se esconde en
 * un diálogo. Se dice el código y el mensaje de SUNAT tal cual (la prueba no diagnostica por sí sola), y no se promete nada que el backend no verificó.
 */
export function ProbarConexion({ empresaId, tieneSol }: { empresaId: string; tieneSol: boolean }) {
  const [probando, setProbando] = useState(false);
  const [resultado, setResultado] = useState<ResultadoDeConexionAdmin | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `probando` viejo del closure y harían dos consultas a SUNAT (y dos filas en la bitácora).
  const probandoRef = useRef(false);

  async function probar() {
    if (probandoRef.current) return;
    probandoRef.current = true;
    setProbando(true);
    setError(null);
    setResultado(null);
    const res = await apiRequest<ResultadoDeConexionAdmin>(`/api/admin/empresas/${empresaId}/prueba-de-conexion`, { method: "POST" });
    probandoRef.current = false;
    setProbando(false);
    if (res.estado === "exito" && res.datos) setResultado(res.datos);
    else setError(res.mensaje ?? mensajeError(res.codigo));
  }

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <button type="button" onClick={probar} disabled={probando || !tieneSol} data-testid="probar-conexion" className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>
          <PlugZapIcon className="size-4" />
          {probando ? t.conexion.probando : t.conexion.boton}
        </button>
        {tieneSol ? null : <span className="text-xs text-muted-foreground">{t.conexion.sinSol}</span>}
      </div>
      <p className="text-xs text-muted-foreground">{t.conexion.nota}</p>
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
      {resultado ? (
        <div role="status" data-testid="resultado-conexion" data-resultado={resultado.resultado} className="grid gap-2 rounded-lg border border-border bg-muted/40 px-3 py-2.5 text-[13px]">
          <div className="flex flex-wrap items-center gap-2">
            <Etiqueta tono={TONO_DEL_RESULTADO[resultado.resultado]}>{t.conexion.resultados[resultado.resultado]}</Etiqueta>
            <span className="text-[11px] text-muted-foreground">
              {t.conexion.entornoProbado}: {resultado.entorno === "PRODUCCION" ? messages.admin.empresas.produccion : messages.admin.empresas.beta}
            </span>
          </div>
          {resultado.codigo ? (
            <dl className="grid gap-0.5 text-[12px]">
              <div className="flex gap-2">
                <dt className="text-muted-foreground">{t.conexion.codigo}:</dt>
                <dd className="font-mono">{resultado.codigo}</dd>
              </div>
              {resultado.mensaje ? (
                <div className="flex gap-2">
                  <dt className="text-muted-foreground">{t.conexion.mensaje}:</dt>
                  <dd>{resultado.mensaje}</dd>
                </div>
              ) : null}
            </dl>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
