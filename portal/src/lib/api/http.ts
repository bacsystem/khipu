import { NextResponse } from "next/server";
import { ApiError, type ApiEnvelope } from "./types";

export function errorResponse(err: unknown): NextResponse<ApiEnvelope<null>> {
  if (err instanceof ApiError) {
    return NextResponse.json(
      { estado: "error", datos: null, mensaje: err.message, codigo: err.codigo, errores: err.errores },
      { status: err.status },
    );
  }
  return NextResponse.json(
    { estado: "error", datos: null, mensaje: "Error inesperado", codigo: null, errores: null },
    { status: 502 },
  );
}
