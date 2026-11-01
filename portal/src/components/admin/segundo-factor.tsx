"use client";

import { useMessages } from "next-intl";
import { type FormEvent, useEffect, useState } from "react";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import type { AdminConfiguracion } from "@/lib/api/admin-auth";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import type { ApiEnvelope } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { useMensajeError } from "@/i18n/use-mensaje-error";

/** Qué hace el formulario con un error del segundo factor: un desafío vencido vuelve a pedir la contraseña; el resto se muestra. */
export type AlFallar = (res: ApiEnvelope<unknown>) => void;

/** El secreto en grupos de 4, como lo muestran las apps: se tipea a mano sin perderse. */
function enGrupos(secreto: string) {
  return secreto.match(/.{1,4}/g)?.join(" ") ?? secreto;
}

/** El `Campo` y la `Entrada` del panel (#12), como el resto del login: etiqueta de 12 px, control de h-control y el error anunciado y atado al input. */
function CampoCodigo({ id, recuperacion, valor, onChange, error }: { id: string; recuperacion: boolean; valor: string; onChange: (v: string) => void; error: string | null }) {
  const t = useMessages().admin.login.segundoFactor;
  return (
    <Campo id={id} etiqueta={recuperacion ? t.codigoRecuperacion : t.codigo} error={error}>
      <Entrada
        id={id}
        value={valor}
        onChange={(e) => onChange(recuperacion ? e.target.value.toUpperCase().slice(0, 11) : e.target.value.replace(/\D/g, "").slice(0, 6))}
        inputMode={recuperacion ? "text" : "numeric"}
        autoComplete="one-time-code"
        placeholder={recuperacion ? "XXXXX-XXXXX" : "123456"}
        mono
        className="tracking-widest"
        invalido={!!error}
        aria-describedby={error ? `${id}-error` : undefined}
        autoFocus
      />
    </Campo>
  );
}

/** Cabecera de cada paso: va debajo del título de `AuthShell`, así que es un escalón más chica. */
function CabeceraPaso({ titulo, descripcion }: { titulo: string; descripcion: string }) {
  return (
    <div className="grid gap-1">
      <h3 className="text-[15px] font-semibold tracking-tight">{titulo}</h3>
      <p className="text-[13px] text-muted-foreground">{descripcion}</p>
    </div>
  );
}

/** Primera vez: QR y secreto de la app, y el primer código confirma y abre la sesión. */
export function ConfigurarSegundoFactor({ alConfirmar, alFallar }: { alConfirmar: (codigos: string[]) => void; alFallar: AlFallar }) {
  const t = useMessages().admin.login.segundoFactor;
  const mensajeError = useMensajeError();
  const [configuracion, setConfiguracion] = useState<AdminConfiguracion | null>(null);
  const [codigo, setCodigo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let vigente = true;
    postJson<AdminConfiguracion>("/api/admin/auth/segundo-factor/configurar", {}).then((res) => {
      if (!vigente) return;
      if (res.estado === "exito" && res.datos) setConfiguracion(res.datos);
      else alFallar(res);
    });
    return () => {
      vigente = false;
    };
  }, [alFallar]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    const res = await postJson<{ codigos_recuperacion: string[] }>("/api/admin/auth/segundo-factor/confirmar", { codigo });
    setEnviando(false);
    if (res.estado === "exito" && res.datos) return alConfirmar(res.datos.codigos_recuperacion);
    if (res.codigo === "SESION_INVALIDA" || res.codigo === "SEGUNDO_FACTOR_YA_CONFIGURADO") return alFallar(res);
    setError(mensajeError(res.codigo));
  }

  return (
    <form className="grid gap-4" onSubmit={onSubmit} noValidate data-testid="configurar-segundo-factor">
      <CabeceraPaso titulo={t.configurarTitulo} descripcion={t.configurarDescripcion} />
      <p className="text-[13px]">1. {t.configurarPaso1}</p>
      {configuracion ? (
        <div className="grid justify-items-center gap-3">
          {/* eslint-disable-next-line @next/next/no-img-element -- PNG en data URI generado por el backend: no hay nada que optimizar */}
          <img src={`data:image/png;base64,${configuracion.qr_png}`} alt={t.configurarQrAlt} width={220} height={220} className="rounded-lg border border-border bg-white" />
          <div className="grid w-full gap-1.5">
            <span className="text-[12px] font-medium text-foreground">{t.configurarSecreto}</span>
            <div className="flex items-center justify-between gap-2 rounded-lg border border-border bg-muted px-3 py-2">
              <code className="font-mono text-[12px] break-all" data-testid="secreto">
                {enGrupos(configuracion.secreto)}
              </code>
              <BotonCopiar texto={configuracion.secreto} />
            </div>
          </div>
        </div>
      ) : (
        <p className="text-[13px] text-muted-foreground" role="status">
          {t.cargandoQr}
        </p>
      )}
      <p className="text-[13px]">2. {t.configurarPaso2}</p>
      <CampoCodigo id="codigo" recuperacion={false} valor={codigo} onChange={setCodigo} error={error} />
      <BotonAsync type="submit" pendiente={enviando} textoPendiente={t.activando} disabled={!configuracion || codigo.length !== 6} className={cn(BOTON_PRIMARIO, "w-full")}>
        {t.activar}
      </BotonAsync>
    </form>
  );
}

/** Logins siguientes: el código de la app o, si se perdió el teléfono, uno de recuperación. */
export function VerificarSegundoFactor({ alEntrar, alFallar }: { alEntrar: () => void; alFallar: AlFallar }) {
  const t = useMessages().admin.login.segundoFactor;
  const mensajeError = useMensajeError();
  const [recuperacion, setRecuperacion] = useState(false);
  const [codigo, setCodigo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const completo = recuperacion ? codigo.replace(/[\s-]/g, "").length === 10 : codigo.length === 6;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    const res = await postJson<unknown>("/api/admin/auth/segundo-factor/verificar", { codigo });
    setEnviando(false);
    if (res.estado === "exito") return alEntrar();
    if (res.codigo === "SESION_INVALIDA" || res.codigo === "SEGUNDO_FACTOR_NO_CONFIGURADO") return alFallar(res);
    setError(mensajeError(res.codigo));
  }

  return (
    <form className="grid gap-4" onSubmit={onSubmit} noValidate data-testid="verificar-segundo-factor">
      <CabeceraPaso titulo={t.verificarTitulo} descripcion={recuperacion ? t.verificarRecuperacionDescripcion : t.verificarDescripcion} />
      <CampoCodigo key={recuperacion ? "recuperacion" : "app"} id="codigo" recuperacion={recuperacion} valor={codigo} onChange={setCodigo} error={error} />
      <BotonAsync type="submit" pendiente={enviando} textoPendiente={t.verificando} disabled={!completo} className={cn(BOTON_PRIMARIO, "w-full")}>
        {t.verificar}
      </BotonAsync>
      <button
        type="button"
        className="text-[12px] text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
        onClick={() => {
          setRecuperacion(!recuperacion);
          setCodigo("");
          setError(null);
        }}
      >
        {recuperacion ? t.usarApp : t.usarRecuperacion}
      </button>
    </form>
  );
}

/** Se muestran una sola vez, justo después de configurar: el backend solo guarda su hash. */
export function CodigosRecuperacion({ codigos, alContinuar }: { codigos: string[]; alContinuar: () => void }) {
  const t = useMessages().admin.login.segundoFactor;
  return (
    <div className="grid gap-4" data-testid="codigos-recuperacion">
      <CabeceraPaso titulo={t.codigosTitulo} descripcion={t.codigosDescripcion} />
      <ul className="grid grid-cols-2 gap-2 rounded-lg border border-border bg-muted p-3 font-mono text-[13px]" aria-label={t.codigosTitulo}>
        {codigos.map((c) => (
          <li key={c}>{c}</li>
        ))}
      </ul>
      <BotonCopiar texto={codigos.join("\n")} titulo={t.codigosCopiar} etiqueta className="justify-self-start px-2 py-1 text-[12px]" />
      <button type="button" onClick={alContinuar} className={cn(BOTON_PRIMARIO, "w-full")}>
        {t.codigosContinuar}
      </button>
    </div>
  );
}
