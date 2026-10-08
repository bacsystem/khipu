import { describe, expect, it } from "vitest";
import { detalleLegible, motivoDe } from "./bitacora";

describe("detalleLegible (H12)", () => {
  it("un pago se lee con nombres y fechas, sin el id del pago ni los códigos", () => {
    expect(
      detalleLegible("pago=90b818fb-0d77-4440-8a6f-c2ea9e11e6f8 periodo=2026-11-09/2026-12-08 monto=29.00 medio=TRANSFERENCIA fecha=2026-10-08 vence=sin_cambio"),
    ).toBe("Periodo: 9 Nov 2026 – 8 Dic 2026 · Monto: S/ 29.00 · Medio: Transferencia · Fecha de pago: 8 Oct 2026 · Vence: sin cambio");
  });

  it("un cambio de plan traduce la dirección y el efecto", () => {
    expect(detalleLegible("desde=Gratis hacia=Emprende direccion=SUBIDA efecto=INMEDIATO vence=2026-11-09 gracia=5")).toBe(
      "De: Gratis · A: Emprende · Cambio: Subida de plan · Efecto: Inmediato · Vence: 9 Nov 2026 · Días de gracia: 5",
    );
  });

  it("la duración de una sesión de soporte va en minutos", () => {
    expect(detalleLegible("usuario=ana@x.pe duracion_s=900")).toBe("Usuario: ana@x.pe · Duración: 15 min");
  });

  it("un valor con espacios no se corta, y un instante va en hora de Lima", () => {
    expect(detalleLegible("texto=Mantenimiento de 13:00 a 13:30. desde=2026-10-08T17:37:00Z hasta=2026-10-08T19:00:00Z")).toBe(
      "Texto: Mantenimiento de 13:00 a 13:30. · Desde: 8 Oct 2026, 12:37 · Hasta: 8 Oct 2026, 14:00",
    );
  });

  it("un antes y un después (editar un plan, cambiar el remitente) se lee con una flecha", () => {
    expect(detalleLegible("plan=Plan QA; precio=45.50>49.90; documentos=800>1000; limites_desde=2026-11-01")).toBe(
      "Plan: Plan QA · Precio: 45.50 → 49.90 · Documentos: 800 → 1000 · Límites desde: 1 Nov 2026",
    );
    expect(detalleLegible("remitente=<no-responder@khipu.pe> -> khipu <no-responder@khipu.pe>")).toBe(
      "Remitente: <no-responder@khipu.pe> → khipu <no-responder@khipu.pe>",
    );
  });

  it("el entorno se dice con su nombre", () => {
    expect(detalleLegible("desde=PRODUCCION hacia=BETA")).toBe("De: Producción · A: Beta");
  });

  it("una clave que todavía no conoce se muestra igual, legible, en vez de esconderse", () => {
    expect(detalleLegible("algo_nuevo=valor")).toBe("Algo nuevo: valor");
  });

  it("sin detalle no dice nada, y un texto sin claves se deja como está", () => {
    expect(detalleLegible(undefined)).toBe("");
    expect(detalleLegible("texto libre")).toBe("texto libre");
  });
});

describe("motivoDe", () => {
  it("saca el motivo de una suspensión o de una baja", () => {
    expect(motivoDe("motivo=Falta de pago")).toBe("Falta de pago");
    expect(motivoDe(undefined)).toBeNull();
    expect(motivoDe("usuario=a@x.pe")).toBeNull();
  });
});
