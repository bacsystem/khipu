/**
 * #5: la guarda de contrato entre los tipos escritos a mano de `lib/api/*.ts` y el OpenAPI de la API (`openapi.d.ts`, generado con `npm run generate:api`).
 *
 * Los tipos a mano se conservan. springdoc no marca ningún campo como requerido (todo sale `campo?: T` en el generado) aunque el JSON sí los trae casi siempre, y la API omite las
 * claves nulas: un tipo generado obligaría a `!` y a comprobaciones en todo el portal y diría menos que el escrito a mano. Lo que sí cuida el generado es que el contrato no
 * cambie sin que `tsc` lo note, y eso es lo que comprueba este archivo, solo con tipos (no se importa desde ningún componente ni cuesta nada en tiempo de ejecución):
 *
 * - **Claves**: ninguna clave del tipo a mano, ni de los objetos que contiene, falta en el esquema. Renombrar o quitar un campo en el backend rompe la compilación aquí, con el
 *   camino de la clave (`totales.total_precio_venta(ya no existe)`).
 * - **Formas**: en cada clave común, el tipo a mano y el del esquema se solapan (`string` frente a `number`, objeto frente a arreglo, falla). Un literal más estrecho que el `string`
 *   del esquema, un campo opcional frente a uno requerido o un `null` de más no cuentan: esas son decisiones del lado del portal.
 *
 * Los pares están en `contrato-openapi.ts`. Una clave que el esquema tiene y el tipo a mano no, no es error: el portal no usa todo lo que la API devuelve.
 */

type Solapan<A, B> = [A] extends [B] ? true : [B] extends [A] ? true : false;

/** Los caminos de lo que no encaja entre `A` (a mano) y `B` (esquema), sin `null` ni `undefined`; `never` si todo encaja. `""` es el propio nodo. */
type Fallos<A, B> = [A] extends [readonly unknown[]]
  ? [B] extends [readonly unknown[]]
    ? Fallos<NonNullable<A[number]>, NonNullable<B[number]>>
    : ""
  : [B] extends [readonly unknown[]]
    ? ""
    : [A] extends [object]
      ? [B] extends [object]
        ? FallosDeObjeto<A, B>
        : ""
      : [B] extends [object]
        ? ""
        : Solapan<A, B> extends true
          ? never
          : "";

type FallosDeObjeto<A, B> =
  | { [K in Exclude<keyof A, keyof B> & string]: `${K}(ya no existe)` }[Exclude<keyof A, keyof B> & string]
  | {
      [K in keyof A & keyof B & string]-?: Fallos<NonNullable<A[K]>, NonNullable<B[K]>> extends infer F ? ([F] extends [never] ? never : F extends "" ? K : `${K}.${F & string}`) : never;
    }[keyof A & keyof B & string];

/**
 * `true` si el tipo a mano cabe en el contrato. Si no, un objeto con una propiedad por cada camino que falla, y el nombre de la propiedad lo dice: la compilación lo muestra como el
 * error de no poder asignar ese objeto a `true`, por ejemplo `{ "la_api_cambio:nombre(ya no existe)": true }`. Úselo como `type _X = Verifica<Contrato<Mano, Esquema>>`.
 */
export type Contrato<Mano, Esquema> = [Fallos<Mano, Esquema>] extends [never] ? true : { [K in Fallos<Mano, Esquema> as `la_api_cambio:${K}`]: true };

/** Falla la compilación salvo que el contrato sea `true`. */
export type Verifica<T extends true> = T;
