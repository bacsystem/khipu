"use client";

import { LogOutIcon } from "lucide-react";
import { useState } from "react";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { messages } from "@/lib/messages";

/**
 * Lo que se le dice a un cliente cuya cuenta está suspendida (#182), con la única acción que tiene sentido: cerrar la sesión. No lleva
 * «reintentar»: no se arregla desde acá, y la suspensión no borró nada — todo vuelve en cuanto la reactiven.
 */
export function CuentaSuspendida() {
  const t = messages.cuentaSuspendida;
  const [saliendo, setSaliendo] = useState(false);

  async function cerrarSesion() {
    setSaliendo(true);
    // Un corte de red no debe dejar el botón colgado: la sesión se limpia del lado del servidor, y si no llegó, el login se muestra igual.
    await fetch("/api/auth/logout", { method: "POST" }).catch(() => undefined);
    window.location.assign("/login");
  }

  return (
    <div className="grid gap-4" data-testid="cuenta-suspendida">
      <p className="text-sm text-foreground/80" role="alert">
        {t.descripcion}
      </p>
      <p className="text-sm font-medium">{t.soporte}</p>
      <button type="button" disabled={saliendo} onClick={cerrarSesion} className={BOTON_PRIMARIO}>
        <LogOutIcon className="size-4" />
        {t.cerrarSesion}
      </button>
    </div>
  );
}
