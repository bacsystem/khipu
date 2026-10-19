/** Un cliente guardado de la empresa (#319). Lo opcional que no se informó no viene en el JSON. */
export type ClienteGuardado = {
  id: string;
  tipo_doc: string;
  num_doc: string;
  razon_social: string;
  direccion?: string | null;
  pais?: string | null;
  email?: string | null;
  creado_en: string;
  actualizado_en: string;
};

/** Lo que se manda para crear o cambiar un cliente. */
export type DatosDeCliente = {
  tipo_doc: string;
  num_doc: string;
  razon_social: string;
  direccion?: string;
  pais?: string;
  email?: string;
};

export type PaginaDeClientes = { clientes: ClienteGuardado[]; total: number };
