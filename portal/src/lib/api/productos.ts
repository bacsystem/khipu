/** Un producto o servicio guardado de la empresa (#320). Lo opcional que no se informó no viene en el JSON. */
export type ProductoGuardado = {
  id: string;
  codigo?: string | null;
  descripcion: string;
  unidad: string;
  tipo_afectacion_igv: string;
  precio_unitario: number;
  codigo_sunat?: string | null;
  creado_en: string;
  actualizado_en: string;
};

/** Lo que se manda para crear o cambiar un producto. */
export type DatosDeProducto = {
  codigo?: string;
  descripcion: string;
  unidad: string;
  tipo_afectacion_igv: string;
  precio_unitario: number;
  codigo_sunat?: string;
};

export type PaginaDeProductos = { productos: ProductoGuardado[]; total: number };
