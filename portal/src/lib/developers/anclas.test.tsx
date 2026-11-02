import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import EntornosPage from "@/app/developers/entornos/page";
import ErroresPage from "@/app/developers/errores/page";
import GuiaPage from "@/app/developers/guia/page";
import { entradas } from "./entradas";
import es from "@/messages/es.json";

const ENTRADAS = entradas(es);

// El índice lateral observa las secciones con IntersectionObserver, que jsdom no trae.
beforeAll(() => {
  vi.stubGlobal("IntersectionObserver", class { observe() {} unobserve() {} disconnect() {} takeRecords() { return []; } });
});
afterEach(cleanup);

const PAGINAS: Record<string, () => React.JSX.Element> = { guia: GuiaPage, errores: ErroresPage, entornos: EntornosPage };

/** Un resultado del buscador que lleva a un ancla que no existe es un enlace muerto: se renderiza cada página y se comprueba que cada ancla esté. */
describe("las anclas del buscador existen en las páginas (#26)", () => {
  for (const [pagina, Pagina] of Object.entries(PAGINAS)) {
    it(`/developers/${pagina}`, () => {
      const { container } = render(<Pagina />);
      const anclas = [...new Set(ENTRADAS.filter((e) => e.href.startsWith(`/developers/${pagina}#`)).map((e) => e.href.split("#")[1]))];
      expect(anclas.length).toBeGreaterThan(0);
      for (const ancla of anclas) expect(container.querySelector(`[id="${ancla}"]`), `#${ancla} en /developers/${pagina}`).not.toBeNull();
    });
  }
});
