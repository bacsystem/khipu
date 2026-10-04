import { NextResponse } from "next/server";

/** Sin la cookie del desafío (venció a los 5 minutos o nunca hubo contraseña): hay que volver al paso 1. */
export function sinDesafio() {
  return NextResponse.json(
    { estado: "error", datos: null, mensaje: "El inicio de sesión venció. Vuelve a ingresar tu contraseña.", codigo: "SESION_INVALIDA", errores: null },
    { status: 401 },
  );
}
