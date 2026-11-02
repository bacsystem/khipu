import { describe, expect, it } from "vitest";
import { fijarLocaleDePrueba, mensajesDePrueba } from "@/i18n/prueba";
import { errorDeUrlWebhook, etiquetaDeEvento, EVENTOS_WEBHOOK, resultadoDelIntento, resumenDeEventos } from "./eventos";
import es from "@/messages/es.json";

describe("eventos de webhook (#19)", () => {
  it("son los siete que publica el backend, con etiqueta propia", () => {
    expect(EVENTOS_WEBHOOK.map((e) => e.evento)).toEqual([
      "comprobante.aceptado",
      "comprobante.aceptado_con_obs",
      "comprobante.rechazado",
      "comprobante.error_envio",
      "comprobante.fuera_de_plazo",
      "comprobante.anulado",
      "comprobante.descartado",
    ]);
    expect(etiquetaDeEvento("comprobante.aceptado_con_obs", es)).toBe("Aceptado con observaciones");
    expect(etiquetaDeEvento("webhook.prueba", es)).toBe("Prueba");
    expect(etiquetaDeEvento("otro.evento", es)).toBe("otro.evento");
  });

  it("resume «Todos los eventos» solo si están los siete", () => {
    expect(resumenDeEventos(EVENTOS_WEBHOOK.map((e) => e.evento), es)).toBe("Todos los eventos");
    expect(resumenDeEventos(["comprobante.aceptado", "comprobante.rechazado"], es)).toBe("Aceptado, Rechazado");
  });

  it("nombra los tres estados de una entrega", () => {
    expect(es.webhooks.estadoEntrega).toEqual({ PENDIENTE: "Pendiente", EXITOSA: "Entregada", FALLIDA: "Fallida" });
  });

  it("muestra el código HTTP o el error del último intento", () => {
    expect(resultadoDelIntento(200, null)).toBe("HTTP 200");
    expect(resultadoDelIntento(503, "El servidor respondió HTTP 503")).toBe("El servidor respondió HTTP 503");
    expect(resultadoDelIntento(null, "No se pudo conectar: Connection refused")).toBe("No se pudo conectar: Connection refused");
    expect(resultadoDelIntento(null, null)).toBe("—");
  });
});

describe("errorDeUrlWebhook (misma regla de forma que UrlDeWebhook)", () => {
  it("acepta una URL https con servidor, puerto, ruta y consulta", () => {
    for (const buena of ["https://mi-sistema.pe/khipu", "https://mi-sistema.pe:8443/h?x=1", "  https://api.cliente.com/webhooks/khipu  "])
      expect(errorDeUrlWebhook(buena, es), buena).toBeNull();
  });

  it("pide la URL y rechaza lo que no es una URL", () => {
    expect(errorDeUrlWebhook("", es)).toMatch(/Escribe la URL/);
    expect(errorDeUrlWebhook("   ", es)).toMatch(/Escribe la URL/);
    expect(errorDeUrlWebhook("mi-sistema.pe/h", es)).toMatch(/No es una URL válida/);
  });

  it("exige https, sin usuario, contraseña ni fragmento", () => {
    expect(errorDeUrlWebhook("http://mi-sistema.pe/h", es)).toMatch(/https:\/\//);
    expect(errorDeUrlWebhook("ftp://mi-sistema.pe/h", es)).toMatch(/https:\/\//);
    expect(errorDeUrlWebhook("https://user:clave@mi-sistema.pe/h", es)).toMatch(/usuario ni contraseña/);
    expect(errorDeUrlWebhook("https://user@mi-sistema.pe/h", es)).toMatch(/usuario ni contraseña/);
    expect(errorDeUrlWebhook("https://mi-sistema.pe/h#frag", es)).toMatch(/fragmento/);
  });

  it("da los mensajes y las etiquetas en el idioma de `messages` (#25)", () => {
    fijarLocaleDePrueba("en");
    const en = mensajesDePrueba();
    expect(errorDeUrlWebhook("http://mi-sistema.pe/h", en)).toBe("The URL must start with https://");
    expect(etiquetaDeEvento("comprobante.rechazado", en)).toBe("Rejected");
    expect(resumenDeEventos(EVENTOS_WEBHOOK.map((e) => e.evento), en)).toBe("All events");
  });

  it("tiene el mismo tope de largo que el backend", () => {
    const justa = "https://mi-sistema.pe/" + "a".repeat(500 - "https://mi-sistema.pe/".length);
    expect(errorDeUrlWebhook(justa, es)).toBeNull();
    expect(errorDeUrlWebhook(justa + "a", es)).toMatch(/500/);
  });
});
