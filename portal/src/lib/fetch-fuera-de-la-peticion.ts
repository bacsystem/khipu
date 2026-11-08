import { AsyncResource } from "node:async_hooks";

const MARCA = Symbol.for("khipu.fetchFueraDeLaPeticion");

/**
 * Fuga de memoria del portal bajo carga: el `fetch` de Node (undici) abre los sockets keep-alive hacia el backend dentro de la petición que los necesitó, y
 * Node le pega al socket el `AsyncLocalStorage` de ese momento. Los callbacks de ese socket corren después con ese contexto viejo, así que lo que React guarda
 * en `cache()` durante peticiones posteriores se acumula en el caché de una petición ya terminada, con las respuestas del backend adentro, y nunca se libera
 * mientras el socket viva. Con carga sostenida, el portal crecía hasta el límite del heap (~4 GB) y se caía con «JavaScript heap out of memory»: una instantánea
 * mostró 17 peticiones de render retenidas por sockets con 9 589 respuestas adentro.
 *
 * Esto envuelve el `fetch` global para que la llamada que llega a la red corra en el contexto vacío de cuando arrancó el servidor: el socket nace sin petición
 * pegada. Quien llamó a `fetch` sigue en su petición al volver del `await`, así que Next y React no notan nada. Tiene que llamarse desde `register()`
 * (src/instrumentation.ts), que Next corre una vez por proceso al arrancar, fuera de toda petición y antes de envolver `fetch` con lo suyo y lo de React: así
 * queda como la capa más interna. La marca evita envolverlo dos veces solo mientras nadie lo envolvió por encima; llamada después de que Next lo hizo,
 * envolvería el `fetch` de Next por fuera y lo correría fuera de la petición. Por eso se llama solo desde register().
 */
export function aislarFetchDeLaPeticion(): void {
  const actual = globalThis.fetch as typeof fetch & { [MARCA]?: true };
  if (actual[MARCA]) return;
  const arranque = new AsyncResource("khipu.fetch-fuera-de-la-peticion");
  const aislado = function fetch(input: Parameters<typeof globalThis.fetch>[0], init?: Parameters<typeof globalThis.fetch>[1]) {
    return arranque.runInAsyncScope(actual, globalThis, input, init);
  } as typeof fetch & { [MARCA]?: true };
  aislado[MARCA] = true;
  globalThis.fetch = aislado;
}
