import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { EnvioAlClienteEstado } from "./envio-al-cliente-estado";

afterEach(cleanup);

describe("EnvioAlClienteEstado (#325)", () => {
  it("enviado: a quién y cuándo, sin el error", () => {
    render(<EnvioAlClienteEstado envio={{ destinatario: "compras@cliente.pe", estado: "ENVIADO", intentos: 1, enviado_en: "2026-09-13T15:00:12Z" }} />);
    expect(screen.getByTestId("envio-al-cliente")).toHaveAttribute("data-estado", "ENVIADO");
    expect(screen.getByText("Enviado al cliente")).toBeInTheDocument();
    expect(screen.getByTestId("envio-al-cliente")).toHaveTextContent("compras@cliente.pe");
    expect(screen.getByTestId("envio-al-cliente")).toHaveTextContent("13 Set 2026, 10:00");
    expect(screen.queryByTestId("envio-al-cliente-error")).toBeNull();
  });

  it("pendiente tras un fallo: dice cuántos intentos y que se reintenta solo, con el motivo", () => {
    render(<EnvioAlClienteEstado envio={{ destinatario: "compras@cliente.pe", estado: "PENDIENTE", intentos: 2, ultimo_error: "SMTP caído", siguiente_intento: "2026-09-13T15:30:00Z" }} />);
    expect(screen.getByText("Envío al cliente pendiente")).toBeInTheDocument();
    expect(screen.getByTestId("envio-al-cliente")).toHaveTextContent("Falló 2 intentos; se reintenta solo el");
    expect(screen.getByTestId("envio-al-cliente-error")).toHaveTextContent("SMTP caído");
  });

  it("pendiente sin intentos: sale en unos segundos", () => {
    render(<EnvioAlClienteEstado envio={{ destinatario: "compras@cliente.pe", estado: "PENDIENTE", intentos: 0 }} />);
    expect(screen.getByTestId("envio-al-cliente")).toHaveTextContent("Sale en unos segundos.");
  });

  it("omitido o fallido: el motivo y que se puede enviar a mano", () => {
    render(<EnvioAlClienteEstado envio={{ destinatario: "compras@cliente.pe", estado: "OMITIDO", intentos: 0, ultimo_error: "El servidor no tiene correo configurado: no se envió." }} />);
    expect(screen.getByText("No se envió al cliente")).toBeInTheDocument();
    expect(screen.getByTestId("envio-al-cliente")).toHaveTextContent("Enviar por correo");
    expect(screen.getByTestId("envio-al-cliente-error")).toHaveTextContent("no tiene correo configurado");

    cleanup();
    render(<EnvioAlClienteEstado envio={{ destinatario: "compras@cliente.pe", estado: "FALLIDO", intentos: 5, ultimo_error: "Buzón inexistente" }} />);
    expect(screen.getByText("No se pudo enviar al cliente")).toBeInTheDocument();
  });
});
