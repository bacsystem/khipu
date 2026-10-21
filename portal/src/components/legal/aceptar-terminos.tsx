"use client";

import { LogOutIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { CasillaDeTerminos } from "@/components/legal/casilla-de-terminos";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useMessages } from "next-intl";
import { useMensajeError } from "@/i18n/use-mensaje-error";

/**
 * Lo único que puede hacer quien no aceptó la versión vigente de los términos (#342): leerlos, aceptarlos o cerrar la sesión. Al aceptar se
 * vuelve a pintar la misma página, que ya no lo detiene: sigue donde quería ir.
 */
export function AceptarTerminos() {
  const t = useMessages().aceptarTerminos;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [marcada, setMarcada] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [saliendo, setSaliendo] = useState(false);

  async function aceptar(e: FormEvent) {
    e.preventDefault();
    if (!marcada) {
      setError(t.requerido);
      return;
    }
    setError(null);
    setEnviando(true);
    const res = await postJson<unknown>("/api/auth/terminos", {});
    if (res.estado === "exito") {
      router.refresh();
      return;
    }
    setEnviando(false);
    setError(mensajeError(res.codigo));
  }

  async function cerrarSesion() {
    setSaliendo(true);
    // Como en la cuenta suspendida: un corte de red no deja el botón colgado; el login se muestra igual.
    await fetch("/api/auth/logout", { method: "POST" }).catch(() => undefined);
    window.location.assign("/login");
  }

  return (
    <form className="grid gap-4" onSubmit={aceptar} noValidate data-testid="aceptar-terminos">
      <p className="text-sm text-foreground/80">{t.descripcion}</p>
      <CasillaDeTerminos
        id="acepta-terminos"
        checked={marcada}
        onChange={(e) => {
          setMarcada(e.target.checked);
          if (e.target.checked) setError(null);
        }}
        error={error ?? undefined}
      />
      <button type="submit" disabled={enviando || saliendo} className={BOTON_PRIMARIO}>
        {enviando ? t.aceptando : t.aceptar}
      </button>
      <button type="button" disabled={enviando || saliendo} onClick={cerrarSesion} className={BOTON_SECUNDARIO}>
        <LogOutIcon className="size-4" />
        {t.cerrarSesion}
      </button>
    </form>
  );
}
