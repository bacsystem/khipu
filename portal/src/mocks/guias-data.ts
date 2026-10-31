import type { Guia } from "@/lib/api/guias";

/**
 * Las guías de remisión de la empresa del mock (#39), una por cada estado que la pantalla distingue. Los ids dicen el caso, como el resto de las fixtures. Las
 * respuestas del envío dependen del id (ver `handlers-guias.ts`): el servidor real viene con el envío apagado (`422 GRE_NO_DISPONIBLE`), así que `g-firmada` lo
 * recibe, y `g-firmada-habilitada` hace de la empresa a la que el operador ya le encendió el envío.
 */
const ENLACES = (id: string, conCdr: boolean) => ({
  xml: `/v1/guias-remision/${id}/xml`,
  pdf: `/v1/guias-remision/${id}/pdf`,
  ...(conCdr ? { cdr: `/v1/guias-remision/${id}/cdr` } : {}),
});

const PARTIDA_FISCAL = { establecimiento: "0000", ubigeo: "150101", direccion: "Av. Argentina 1234, Cercado de Lima" };
const LLEGADA_CLIENTE = { ubigeo: "150131", direccion: "Calle Los Pinos 450, San Isidro" };

const DESTINATARIO = { tipo_doc: "6", num_doc: "20601234565", nombre: "PESQUERA PACÍFICO S.A.C." };

const ITEM = { codigo: "LAP-15", descripcion: "Laptop 15 pulgadas", unidad: "NIU", cantidad: 2, codigo_sunat: "43211503" };

const PRIVADO = {
  fecha_inicio: "2026-10-21",
  peso_bruto: 25.5,
  unidad_peso: "KGM",
  partida: PARTIDA_FISCAL,
  llegada: LLEGADA_CLIENTE,
  vehiculos: [{ placa: "ABC123", principal: true }],
  conductores: [{ tipo_doc: "1", num_doc: "45678912", nombres: "JUAN", apellidos: "QUISPE MAMANI", licencia: "Q45678912", principal: true }],
  indicadores: [] as string[],
};

const PUBLICO = { ...PRIVADO, vehiculos: [], conductores: [] };

/** Una guía remitente (09) de la serie T001 con lo común de las fixtures; `parcial` pone lo que cambia entre una y otra. */
function remitente(parcial: Partial<Guia> & Pick<Guia, "id" | "numero" | "estado">): Guia {
  const serie = "T001";
  return {
    fecha_emision: "2026-10-20",
    hora_emision: "10:04:05",
    motivo: "01",
    descripcion_motivo: "Venta",
    modalidad: "02",
    tipo: "09",
    serie,
    nombre_archivo: `20100047226-09-${serie}-${parcial.numero}`,
    destinatario: DESTINATARIO,
    traslado: PRIVADO,
    items: [ITEM],
    documentos_relacionados: [{ tipo: "01", descripcion: "Factura", numero: "F001-123", ruc_emisor: "20100047226" }],
    intentos: 0,
    consultas_ticket: 0,
    ...parcial,
  };
}

export function guiasIniciales(): Guia[] {
  return [
    remitente({
      id: "g-aceptada",
      numero: 8,
      estado: "ACEPTADO",
      ticket: "7f7b00ab-4fee-4ae2-9e04-fbd16ca18677",
      fecha_recepcion: "2026-10-20T10:04:07",
      intentos: 1,
      consultas_ticket: 2,
      reintento_automatico: false,
      cdr: { codigo: "0", descripcion: "La Guia de Remision numero T001-8, ha sido aceptada" },
      enlace_consulta: "https://e-factura.sunat.gob.pe/v1/contribuyente/gre/comprobantes/consultarcpe",
      enlaces: ENLACES("g-aceptada", true),
      hash: "6Qj1m1x0h0aV3p5nFZ1Nq2Zr1xs=",
    }),
    remitente({
      id: "g-firmada",
      numero: 7,
      estado: "FIRMADO",
      modalidad: "01",
      transportista: { ruc: "20480072872", razon_social: "TRANSPORTES DEL SUR S.A.C.", registro_mtc: "1512345CNG" },
      traslado: PUBLICO,
      enlaces: ENLACES("g-firmada", false),
      hash: "Pj0r8d2Yx9Qm4kLw1sVn7TgHc0A=",
    }),
    remitente({
      id: "g-firmada-habilitada",
      numero: 6,
      estado: "FIRMADO",
      observaciones: "Mercadería frágil",
      enlaces: ENLACES("g-firmada-habilitada", false),
      hash: "Kk4w9b7Tt1Zq3xEr5uYh8NmVc2I=",
    }),
    remitente({
      id: "g-error-envio",
      numero: 5,
      estado: "ERROR_ENVIO",
      intentos: 2,
      ultimo_error: "CREDENCIALES_API_SUNAT_NO_CARGADAS: la empresa no tiene cargadas las credenciales de la API de SUNAT",
      reintento_automatico: false,
      enlaces: ENLACES("g-error-envio", false),
    }),
    remitente({
      id: "g-pendiente",
      numero: 4,
      estado: "ENVIADO_PENDIENTE_TICKET",
      ticket: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      fecha_recepcion: "2026-10-20T10:04:07",
      intentos: 1,
      consultas_ticket: 1,
      reintento_automatico: true,
      enlaces: ENLACES("g-pendiente", false),
    }),
    remitente({
      id: "g-enviando",
      numero: 3,
      estado: "ENVIANDO",
      intentos: 1,
      enlaces: ENLACES("g-enviando", false),
    }),
    remitente({
      id: "g-rechazada",
      numero: 2,
      estado: "RECHAZADO",
      ticket: "5e6f7a8b-9c0d-4e1f-a2b3-c4d5e6f7a8b9",
      intentos: 1,
      consultas_ticket: 1,
      cdr: { codigo: "2018", descripcion: "El numero de documento del destinatario no existe en el padron de contribuyentes", observaciones: [] },
      enlaces: ENLACES("g-rechazada", true),
    }),
    {
      id: "g-transportista",
      tipo: "31",
      serie: "V001",
      numero: 1,
      nombre_archivo: "20100047226-31-V001-1",
      fecha_emision: "2026-10-19",
      hora_emision: "16:30:00",
      estado: "ACEPTADO_CON_OBS",
      destinatario: { tipo_doc: "6", num_doc: "20555555551", nombre: "DISTRIBUIDORA ANDINA S.A.C." },
      remitente: { tipo_doc: "6", num_doc: "20601234565", nombre: "PESQUERA PACÍFICO S.A.C." },
      registro_mtc: "1512345CNG",
      traslado: {
        ...PRIVADO,
        fecha_inicio: "2026-10-19",
        peso_bruto: 3.2,
        unidad_peso: "TNE",
        partida: { ubigeo: "070101", direccion: "Av. Contralmirante Mora 100, Callao" },
        indicadores: ["retorno_vehiculo_vacio"],
      },
      items: [{ descripcion: "Pallets de conservas", unidad: "NIU", cantidad: 40 }],
      documentos_relacionados: [{ tipo: "09", descripcion: "Guía de Remisión Remitente", numero: "T001-9", ruc_emisor: "20601234565" }],
      ticket: "9a8b7c6d-5e4f-4a3b-b2c1-d0e9f8a7b6c5",
      fecha_recepcion: "2026-10-19T16:30:04",
      intentos: 1,
      consultas_ticket: 3,
      reintento_automatico: false,
      cdr: { codigo: "0", descripcion: "La Guia de Remision numero V001-1, ha sido aceptada", observaciones: ["4252 - El número de licencia del conductor no sigue el formato esperado"] },
      enlaces: ENLACES("g-transportista", true),
    },
  ];
}
