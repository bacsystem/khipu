import { NextResponse } from "next/server";
import { ApiError, CODIGO_COMUNICACION, CODIGO_INESPERADO, type ApiEnvelope } from "./types";

export function errorResponse(err: unknown): NextResponse<ApiEnvelope<null>> {
  if (err instanceof ApiError) {
    return NextResponse.json(
      { estado: "error", datos: null, mensaje: err.codigo === CODIGO_COMUNICACION ? null : err.message, codigo: err.codigo, errores: err.errores },
      { status: err.status },
    );
  }
  return NextResponse.json(
    { estado: "error", datos: null, mensaje: null, codigo: CODIGO_INESPERADO, errores: null },
    { status: 502 },
  );
}
