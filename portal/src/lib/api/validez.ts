/** Respuesta de `GET /v1/consultas/validez` (#315, #318). Los campos de la API REST solo vienen con `fuente: "API_REST"`. */
export type Validez = {
  estado: string;
  codigo: string | null;
  mensaje: string | null;
  fuente?: "API_REST" | "SOAP";
  estado_ruc?: string;
  condicion_domicilio?: string;
  observaciones?: string[];
  /** Por qué se respondió por SOAP teniendo credenciales API SUNAT. */
  aviso?: string;
};
