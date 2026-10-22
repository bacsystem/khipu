import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { fijarLocaleDePrueba, mensajesDePrueba } from "@/i18n/prueba";
import { Faq } from "./faq";
import { Planes } from "./planes";
import { tarjetasDePlanes } from "./planes-datos";

/** #25: la portada pública se lee en el idioma de la sesión, incluidos los textos que arma `planes-datos` a partir de lo publicado. */
describe("landing en inglés", () => {
  it("las preguntas frecuentes salen en inglés", () => {
    fijarLocaleDePrueba("en");

    render(<Faq />);

    expect(screen.getByRole("heading", { name: "Frequently asked questions" })).toBeInTheDocument();
    expect(screen.getByText("Do I need to install anything on my system?")).toBeInTheDocument();
  });

  it("los planes y sus límites publicados salen en inglés", () => {
    fijarLocaleDePrueba("en");
    const planes = tarjetasDePlanes(
      [
        {
          nombre: "Corporativo",
          precio_mensual: 299,
          limites: {
            documentos_al_mes: { maximo: 1500, ilimitado: false },
            rucs: 20,
            usuarios: { ilimitado: true },
            api_keys: { maximo: 3, ilimitado: false },
            retencion_anios: 10,
          },
        },
      ],
      mensajesDePrueba(),
    );

    render(<Planes planes={planes} abierto contacto={null} />);

    expect(screen.getByRole("heading", { name: "Clear plans, free to start" })).toBeInTheDocument();
    expect(screen.getByText("1 500 documents/month")).toBeInTheDocument();
    expect(screen.getByText("20 RUC · unlimited users")).toBeInTheDocument();
    expect(screen.getByText("XML and CDR stored for 10 years")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Choose Corporativo" })).toBeInTheDocument();
  });

  it("en castellano el texto queda igual que antes", () => {
    render(<Faq />);

    expect(screen.getByRole("heading", { name: "Preguntas frecuentes" })).toBeInTheDocument();
  });
});
