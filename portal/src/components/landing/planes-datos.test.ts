import { describe, expect, it } from "vitest";
import type { PlanPublicado } from "@/lib/api/planes-publicados";
import { PLANES_DE_RESPALDO, tarjetasDePlanes } from "./planes-datos";

const limites = (docs: number | null, rucs: number, usuarios: number | null, keys: number | null, retencion = 5) => ({
  documentos_al_mes: docs === null ? { ilimitado: true } : { maximo: docs, ilimitado: false },
  rucs,
  usuarios: usuarios === null ? { ilimitado: true } : { maximo: usuarios, ilimitado: false },
  api_keys: keys === null ? { ilimitado: true } : { maximo: keys, ilimitado: false },
  retencion_anios: retencion,
});

/** H20: la portada muestra lo que el backoffice publica, no una lista escrita a mano que se desactualiza al cambiar un precio. */
describe("tarjetasDePlanes", () => {
  it("el precio y los límites salen de lo publicado; el texto comercial, del plan del mismo nombre", () => {
    const publicados: PlanPublicado[] = [{ nombre: "Emprende", precio_mensual: 35, limites: limites(400, 1, 1, 2) }];

    const [emprende] = tarjetasDePlanes(publicados);

    expect(emprende).toMatchObject({ nombre: "Emprende", mensual: 35, docs: "400 documentos/mes", rucs: "1 RUC · 1 usuario", para: "Bodegas, freelancers y tiendas" });
  });

  it("un plan que la portada no conoce se muestra igual, con lo que dicen sus límites", () => {
    const [nuevo] = tarjetasDePlanes([{ nombre: "Corporativo", precio_mensual: 299, limites: limites(null, 20, null, null, 10) }]);

    expect(nuevo).toMatchObject({ nombre: "Corporativo", mensual: 299, docs: "Documentos ilimitados", rucs: "20 RUC · usuarios ilimitados", destacado: false });
    expect(nuevo.incluye).toEqual(["API keys ilimitadas", "XML y CDR guardados 10 años"]);
  });

  it("solo salen los publicados: un plan a medida o retirado no aparece aunque la portada tenga su texto", () => {
    const tarjetas = tarjetasDePlanes([{ nombre: "Gratis", precio_mensual: 0, limites: limites(30, 1, 1, 1, 1) }]);

    expect(tarjetas.map((x) => x.nombre)).toEqual(["Gratis"]);
  });

  it("sin planes publicados (backend caído) usa la lista de respaldo", () => {
    expect(tarjetasDePlanes(null)).toBe(PLANES_DE_RESPALDO);
  });
});
