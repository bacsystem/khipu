import { describe, expect, it } from "vitest";
import { aNumero, conductorVacio, cuerpoDeGuia, esDeTransportista, guiaVacia, itemVacio, llevaVehiculos, validarGuia, type GuiaForm } from "./formulario";

const HOY = "2026-10-20";

/** Una guía remitente en transporte privado con todo lo necesario; cada test cambia lo que prueba. */
function remitentePrivada(cambios: Partial<GuiaForm> = {}): GuiaForm {
  return {
    ...guiaVacia("T001", HOY),
    destinatario: { tipo_doc: "6", num_doc: "20601234565", nombre: "PESQUERA PACÍFICO S.A.C." },
    motivo: "01",
    peso_bruto: "25.5",
    llegada: { modo: "direccion", establecimiento: "0000", ubigeo: "150131", direccion: "Calle Los Pinos 450" },
    vehiculos: ["abc123"],
    conductores: [{ tipo_doc: "1", num_doc: "45678912", nombres: "JUAN", apellidos: "QUISPE MAMANI", licencia: "Q45678912" }],
    items: [{ ...itemVacio(), descripcion: "Laptop", cantidad: "2" }],
    ...cambios,
  };
}

function transportista(cambios: Partial<GuiaForm> = {}): GuiaForm {
  return remitentePrivada({
    serie: "V001",
    motivo: "",
    remitente: { tipo_doc: "6", num_doc: "20555555551", nombre: "DISTRIBUIDORA ANDINA S.A.C." },
    registro_mtc: "1512345CNG",
    partida: { modo: "direccion", establecimiento: "0000", ubigeo: "070101", direccion: "Av. Contralmirante Mora 100" },
    ...cambios,
  });
}

describe("la letra de la serie decide la guía", () => {
  it("T es la remitente y V la transportista, en mayúscula o minúscula", () => {
    expect(esDeTransportista("V001")).toBe(true);
    expect(esDeTransportista("v001")).toBe(true);
    expect(esDeTransportista("T001")).toBe(false);
  });
});

describe("aNumero", () => {
  it("acepta la coma decimal y rechaza lo que no es un número", () => {
    expect(aNumero("25,5")).toBe(25.5);
    expect(aNumero(" 3 ")).toBe(3);
    expect(aNumero("")).toBeNaN();
    expect(aNumero("abc")).toBeNaN();
  });
});

describe("validarGuia", () => {
  it("una guía completa no tiene nada que corregir, remitente o transportista", () => {
    expect(validarGuia(remitentePrivada())).toBeNull();
    expect(validarGuia(transportista())).toBeNull();
  });

  it("un formulario vacío empieza por el destinatario", () => {
    expect(validarGuia(guiaVacia("T001", HOY))).toBe("sinDestinatario");
  });

  it("sin serie no hay guía", () => {
    expect(validarGuia(remitentePrivada({ serie: "" }))).toBe("sinSerie");
    expect(validarGuia(remitentePrivada({ serie: "F001" }))).toBe("sinSerie");
  });

  it("la remitente pide motivo y, en transporte público, la empresa de transporte", () => {
    expect(validarGuia(remitentePrivada({ motivo: " " }))).toBe("sinMotivo");
    expect(validarGuia(remitentePrivada({ modalidad: "01" }))).toBe("sinTransportista");
    expect(validarGuia(remitentePrivada({ modalidad: "01", transportista: { ruc: "20480072872", razon_social: "", registro_mtc: "" } }))).toBe("sinTransportista");
    expect(validarGuia(remitentePrivada({ modalidad: "01", transportista: { ruc: "20480072872", razon_social: "TRANSPORTES DEL SUR", registro_mtc: "" } }))).toBeNull();
  });

  it("la transportista pide al remitente y su registro MTC", () => {
    expect(validarGuia(transportista({ remitente: { tipo_doc: "6", num_doc: "", nombre: "" } }))).toBe("sinRemitente");
    expect(validarGuia(transportista({ registro_mtc: " " }))).toBe("sinMtc");
  });

  it("el peso tiene que ser mayor que cero, y la coma decimal vale", () => {
    expect(validarGuia(remitentePrivada({ peso_bruto: "" }))).toBe("pesoInvalido");
    expect(validarGuia(remitentePrivada({ peso_bruto: "0" }))).toBe("pesoInvalido");
    expect(validarGuia(remitentePrivada({ peso_bruto: "-3" }))).toBe("pesoInvalido");
    expect(validarGuia(remitentePrivada({ peso_bruto: "12,5" }))).toBeNull();
  });

  it("cada punto necesita un establecimiento, o un ubigeo de 6 dígitos y una dirección de al menos 3 caracteres", () => {
    expect(validarGuia(remitentePrivada({ llegada: { modo: "direccion", establecimiento: "0000", ubigeo: "", direccion: "Calle 1" } }))).toBe("sinPuntoLlegada");
    expect(validarGuia(remitentePrivada({ llegada: { modo: "direccion", establecimiento: "0000", ubigeo: "15013", direccion: "Calle 1" } }))).toBe("sinPuntoLlegada");
    expect(validarGuia(remitentePrivada({ llegada: { modo: "direccion", establecimiento: "0000", ubigeo: "150131", direccion: "ab" } }))).toBe("sinPuntoLlegada");
    expect(validarGuia(remitentePrivada({ partida: { modo: "direccion", establecimiento: "0000", ubigeo: "", direccion: "" } }))).toBe("sinPuntoPartida");
    expect(validarGuia(remitentePrivada({ partida: { modo: "establecimiento", establecimiento: "0002", ubigeo: "", direccion: "" } }))).toBeNull();
  });

  it("el transporte privado pide el vehículo y el conductor principales, salvo con el indicador M1 o L", () => {
    expect(validarGuia(remitentePrivada({ vehiculos: [""] }))).toBe("sinVehiculo");
    expect(validarGuia(remitentePrivada({ conductores: [conductorVacio()] }))).toBe("sinConductor");
    expect(validarGuia(remitentePrivada({ vehiculos: [""], conductores: [conductorVacio()], indicadores: ["vehiculo_m1_o_l"] }))).toBeNull();
  });

  it("el transporte público no pide vehículos ni conductores", () => {
    expect(validarGuia(remitentePrivada({ modalidad: "01", vehiculos: [""], conductores: [conductorVacio()], transportista: { ruc: "20480072872", razon_social: "TRANSPORTES DEL SUR", registro_mtc: "" } }))).toBeNull();
  });

  it("pide al menos un bien con descripción y una cantidad mayor que cero; una línea en blanco se ignora", () => {
    expect(validarGuia(remitentePrivada({ items: [itemVacio()] }))).toBe("sinBienes");
    expect(validarGuia(remitentePrivada({ items: [{ ...itemVacio(), descripcion: "Laptop", cantidad: "0" }] }))).toBe("cantidadInvalida");
    expect(validarGuia(remitentePrivada({ items: [{ ...itemVacio(), descripcion: "Laptop", cantidad: "" }] }))).toBe("cantidadInvalida");
    expect(validarGuia(remitentePrivada({ items: [{ ...itemVacio(), descripcion: "Laptop", cantidad: "1" }, itemVacio()] }))).toBeNull();
    // Un código escrito sin descripción es una línea a medias, no una línea en blanco.
    expect(validarGuia(remitentePrivada({ items: [{ ...itemVacio(), codigo: "LAP-15" }] }))).toBe("sinBienes");
  });
});

describe("llevaVehiculos", () => {
  it("la transportista siempre; la remitente en privado sin el indicador M1 o L; en público solo con el indicador del transportista", () => {
    expect(llevaVehiculos(transportista())).toBe(true);
    expect(llevaVehiculos(remitentePrivada())).toBe(true);
    expect(llevaVehiculos(remitentePrivada({ indicadores: ["vehiculo_m1_o_l"] }))).toBe(false);
    expect(llevaVehiculos(remitentePrivada({ modalidad: "01" }))).toBe(false);
    expect(llevaVehiculos(remitentePrivada({ modalidad: "01", indicadores: ["vehiculos_conductores_transportista"] }))).toBe(true);
  });
});

describe("cuerpoDeGuia", () => {
  it("la remitente en transporte privado manda motivo, modalidad, vehículos y conductores, y nada de la transportista", () => {
    const c = cuerpoDeGuia(remitentePrivada({ observaciones: " Frágil ", peso_bruto: "25,5" }));

    expect(c).toEqual({
      serie: "T001",
      fecha_emision: HOY,
      destinatario: { tipo_doc: "6", num_doc: "20601234565", nombre: "PESQUERA PACÍFICO S.A.C." },
      motivo: "01",
      modalidad: "02",
      traslado: {
        fecha_inicio: HOY,
        peso_bruto: 25.5,
        unidad_peso: "KGM",
        partida: { establecimiento: "0000" },
        llegada: { ubigeo: "150131", direccion: "Calle Los Pinos 450" },
        vehiculos: [{ placa: "ABC123" }],
        conductores: [{ tipo_doc: "1", num_doc: "45678912", nombres: "JUAN", apellidos: "QUISPE MAMANI", licencia: "Q45678912" }],
      },
      items: [{ descripcion: "Laptop", unidad: "NIU", cantidad: 2 }],
      observaciones: "Frágil",
      enviar_automatico: false,
    });
    expect(c).not.toHaveProperty("remitente");
    expect(c).not.toHaveProperty("registro_mtc");
  });

  it("en transporte público manda la empresa de transporte y ni un vehículo ni un conductor", () => {
    const c = cuerpoDeGuia(remitentePrivada({ modalidad: "01", transportista: { ruc: " 20480072872 ", razon_social: "TRANSPORTES DEL SUR", registro_mtc: "" } }));

    expect(c.modalidad).toBe("01");
    expect(c.transportista).toEqual({ ruc: "20480072872", razon_social: "TRANSPORTES DEL SUR" });
    expect(c.traslado).not.toHaveProperty("vehiculos");
    expect(c.traslado).not.toHaveProperty("conductores");
  });

  it("con el indicador M1 o L no manda vehículos ni conductores aunque estén escritos", () => {
    const c = cuerpoDeGuia(remitentePrivada({ indicadores: ["vehiculo_m1_o_l"] }));

    expect(c.traslado.indicadores).toEqual(["vehiculo_m1_o_l"]);
    expect(c.traslado).not.toHaveProperty("vehiculos");
    expect(c.traslado).not.toHaveProperty("conductores");
  });

  it("la transportista manda remitente y registro MTC, y nunca motivo, modalidad ni transportista", () => {
    const c = cuerpoDeGuia(transportista({ motivo: "01", modalidad: "01", transportista: { ruc: "1", razon_social: "x", registro_mtc: "" } }));

    expect(c.serie).toBe("V001");
    expect(c.remitente).toEqual({ tipo_doc: "6", num_doc: "20555555551", nombre: "DISTRIBUIDORA ANDINA S.A.C." });
    expect(c.registro_mtc).toBe("1512345CNG");
    for (const clave of ["motivo", "modalidad", "transportista", "descripcion_motivo"] as const) expect(c).not.toHaveProperty(clave);
  });

  it("la transportista nunca manda un código de establecimiento: sus puntos llevan ubigeo y dirección", () => {
    const c = cuerpoDeGuia(transportista({ partida: { modo: "establecimiento", establecimiento: "0002", ubigeo: "070101", direccion: "Av. Mora 100" } }));

    expect(c.traslado.partida).toEqual({ ubigeo: "070101", direccion: "Av. Mora 100" });
  });

  it("la transportista descarta los indicadores que solo existen en la remitente", () => {
    const c = cuerpoDeGuia(transportista({ indicadores: ["vehiculo_m1_o_l", "retorno_vehiculo_vacio", "vehiculos_conductores_transportista"] }));

    expect(c.traslado.indicadores).toEqual(["retorno_vehiculo_vacio"]);
  });

  it("omite lo opcional que está en blanco y recorta lo que se escribió con espacios", () => {
    const c = cuerpoDeGuia(
      remitentePrivada({
        descripcion_motivo: "  ",
        items: [{ codigo: " LAP-15 ", descripcion: " Laptop ", unidad: "niu", cantidad: "2", codigo_sunat: "" }, itemVacio()],
        documentos: [
          { tipo: "01", numero: " F001-123 ", ruc_emisor: "" },
          { tipo: "01", numero: "  ", ruc_emisor: "20100047226" },
        ],
      }),
    );

    expect(c).not.toHaveProperty("descripcion_motivo");
    expect(c.items).toEqual([{ codigo: "LAP-15", descripcion: "Laptop", unidad: "NIU", cantidad: 2 }]);
    expect(c.documentos_relacionados).toEqual([{ tipo: "01", numero: "F001-123" }]);
    expect(c).not.toHaveProperty("observaciones");
  });

  it("manda el RUC del emisor del documento cuando se escribió", () => {
    const c = cuerpoDeGuia(remitentePrivada({ documentos: [{ tipo: "09", numero: "T001-9", ruc_emisor: "20601234565" }] }));

    expect(c.documentos_relacionados).toEqual([{ tipo: "09", numero: "T001-9", ruc_emisor: "20601234565" }]);
  });

  it("manda enviar_automatico tal como está marcado", () => {
    expect(cuerpoDeGuia(remitentePrivada({ enviar_automatico: true })).enviar_automatico).toBe(true);
    expect(cuerpoDeGuia(remitentePrivada()).enviar_automatico).toBe(false);
  });
});
