export type ApiEnvelope<T> = {
  estado: "exito" | "error";
  datos: T | null;
  mensaje: string | null;
  codigo: string | null;
  errores: Record<string, string[]> | null;
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
