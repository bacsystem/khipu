function base64url(obj: unknown): string {
  return btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fakeJwt(payload: Record<string, unknown>): string {
  return `${base64url({ alg: "none" })}.${base64url(payload)}.firma-de-prueba`;
}

export type Usuario = { id: string; cuenta_id: string; email: string; rol: string };
export type Empresa = {
  id: string;
  ruc: string;
  razon_social: string;
  entorno: "BETA" | "PRODUCCION";
  tiene_certificado: boolean;
  tiene_credenciales_sol: boolean;
  certificado_vigencia_hasta: string | null;
};
export type Serie = { tipo: string; serie: string; ultimo_numero: number; activa: boolean };
export type ApiKey = { id: string; prefijo: string; activa: boolean; creada_en: string; revocada_en?: string };
export type Comprobante = {
  id: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  moneda: string;
  tipo_operacion: string;
  receptor: { tipo_doc: string; num_doc: string; razon_social: string; direccion: string | null };
  items: Array<{
    codigo: string | null;
    descripcion: string;
    unidad: string;
    cantidad: number;
    precio_unitario: number;
    tipo_afectacion_igv: string;
  }>;
  estado_documento: string;
  hash: string;
  nombre_archivo: string;
  intentos: number;
  ultimo_error: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  totales: { gravado: number; exonerado: number; inafecto: number; igv: number; total: number };
  enlaces: { xml: string; cdr: string };
};

type Sesion = { usuario: Usuario };

export const db = {
  usuariosPorEmail: new Map<string, { usuario: Usuario; password: string }>(),
  empresasPorCuenta: new Map<string, Empresa[]>(),
  seriesPorEmpresa: new Map<string, Serie[]>(),
  apiKeysPorEmpresa: new Map<string, ApiKey[]>(),
  facturasPorEmpresa: new Map<string, Comprobante[]>(),
  sesionesPorToken: new Map<string, Sesion>(),
};

export function resetDb() {
  db.usuariosPorEmail.clear();
  db.empresasPorCuenta.clear();
  db.seriesPorEmpresa.clear();
  db.apiKeysPorEmpresa.clear();
  db.facturasPorEmpresa.clear();
  db.sesionesPorToken.clear();

  const usuario: Usuario = {
    id: "u-demo",
    cuenta_id: "c-demo",
    email: "demo@example.com",
    rol: "ADMIN",
  };
  db.usuariosPorEmail.set(usuario.email, { usuario, password: "Passw0rd1" });

  const empresa: Empresa = {
    id: "e-demo",
    ruc: "20123456789",
    razon_social: "Demo SAC",
    entorno: "BETA",
    tiene_certificado: true,
    tiene_credenciales_sol: true,
    certificado_vigencia_hasta: "2036-01-01",
  };
  db.empresasPorCuenta.set(usuario.cuenta_id, [empresa]);
  db.seriesPorEmpresa.set(empresa.id, [{ tipo: "01", serie: "F001", ultimo_numero: 2, activa: true }]);
  db.apiKeysPorEmpresa.set(empresa.id, [
    { id: "k-activa", prefijo: "fk_demo001", activa: true, creada_en: "2026-09-01T15:00:00Z" },
    { id: "k-revocada", prefijo: "fk_demo000", activa: false, creada_en: "2026-08-01T15:00:00Z", revocada_en: "2026-08-20T12:00:00Z" },
  ]);
  db.facturasPorEmpresa.set(empresa.id, [
    {
      id: "f-aceptada",
      tipo: "01",
      serie: "F001",
      numero: 1,
      fecha_emision: "2026-09-01",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C.", direccion: "Av. Argentina 2450, Lima" },
      items: [
        { codigo: "SRV-001", descripcion: "Servicio de desarrollo de software", unidad: "ZZ", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "10" },
      ],
      estado_documento: "ACEPTADO",
      hash: "y4M8+jW8Xp278K1aM02q19KjvO3k=",
      nombre_archivo: "20123456789-01-F001-00000001",
      intentos: 1,
      ultimo_error: null,
      cdr: { codigo: "0", descripcion: "La Factura numero F001-1, ha sido aceptada", observaciones: [] },
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
      enlaces: { xml: "/v1/facturas/f-aceptada/xml", cdr: "/v1/facturas/f-aceptada/cdr" },
    },
    {
      id: "f-error",
      tipo: "01",
      serie: "F001",
      numero: 2,
      fecha_emision: "2026-09-02",
      moneda: "PEN",
      tipo_operacion: "0101",
      receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL ANGEL VALENCIA RAMOS", direccion: null },
      items: [
        { codigo: null, descripcion: "Consultoría técnica", unidad: "ZZ", cantidad: 2, precio_unitario: 25, tipo_afectacion_igv: "10" },
      ],
      estado_documento: "ERROR_ENVIO",
      hash: "hash-2",
      nombre_archivo: "20123456789-01-F001-00000002",
      intentos: 2,
      ultimo_error: "SUNAT no respondió a tiempo",
      cdr: null,
      totales: { gravado: 50, exonerado: 0, inafecto: 0, igv: 9, total: 59 },
      enlaces: { xml: "/v1/facturas/f-error/xml", cdr: "/v1/facturas/f-error/cdr" },
    },
  ]);
}

resetDb();
