/** Lo que SUNAT observará aunque acepte el comprobante (#378): `4013` RUC no activo, `4014` RUC no habido. */
export type AvisoSunat = { codigo: string; mensaje: string };

export type ApiEnvelope<T> = {
  estado: "exito" | "error";
  datos: T | null;
  mensaje: string | null;
  codigo: string | null;
  errores: Record<string, string[]> | null;
  /** Solo en la emisión (`POST /v1/facturas` y `/v1/notas`), al nivel de `datos`; el backend lo omite si no hay nada que avisar. */
  avisos?: AvisoSunat[] | null;
};

export class ApiError extends Error {
  status: number;
  codigo: string | null;
  errores: Record<string, string[]> | null;

  constructor(
    status: number,
    codigo: string | null,
    message: string,
    errores: Record<string, string[]> | null = null,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.codigo = codigo;
    this.errores = errores;
  }
}
