/**
 * #5: la guarda de contrato entre los tipos escritos a mano de `lib/api/*.ts` y el OpenAPI de la API (`openapi.d.ts`, generado con `npm run generate:api`).
 *
 * Los tipos a mano se conservan. springdoc no marca como requerido ningún campo de las **respuestas** (todo sale `campo?: T` en el generado) aunque el JSON sí los trae casi siempre,
 * y la API omite las claves nulas: un tipo generado obligaría a `!` y a comprobaciones en todo el portal y diría menos que el escrito a mano. (En las **peticiones** sí marca los
 * `@NotNull` / `@NotBlank`: ver {@link ContratoDePeticion}.) Lo que sí cuida el generado es que el contrato no cambie sin que `tsc` lo note, y eso es lo que comprueba este archivo,
 * solo con tipos (no se importa desde ningún componente ni cuesta nada en tiempo de ejecución):
 *
 * - **Claves**: ninguna clave del tipo a mano, ni de los objetos que contiene, falta en el esquema. Renombrar o quitar un campo en el backend rompe la compilación aquí, con el
 *   camino de la clave (`totales.total_precio_venta(ya no existe)`).
 * - **Formas**: en cada clave común, el tipo a mano y el del esquema se solapan (`string` frente a `number`, objeto frente a arreglo, falla). Un literal más estrecho que el `string`
 *   del esquema, un campo opcional frente a uno requerido o un `null` de más no cuentan: esas son decisiones del lado del portal.
 * - **Valores de un enum**: si el esquema lista los valores (`"VIGENTE" | "VENCIDA"`) y el tipo a mano también, el tipo a mano tiene que cubrirlos todos: un estado nuevo en el backend
 *   rompe la compilación en vez de llegar a un `Record<Estado, …>` que devuelve `undefined`. El camino sale como `estado(valor nuevo en la API)`.
 * - **Peticiones** ({@link ContratoDePeticion}): además, el tipo a mano trae todos los campos que el esquema marca requeridos; si no, la API contesta 400.
 *
 * Los pares están en `contrato-openapi.ts`. Una clave que el esquema tiene y el tipo a mano no, no es error: el portal no usa todo lo que la API devuelve.
 *
 * Límites conocidos (no se detectan, o hay que contorsionar el tipo a mano):
 * - Un tipo a mano que es una **unión de objetos** solo se compara por las claves comunes a todas sus ramas; renombrar una clave de una sola rama pasa.
 * - Una **tupla** frente a un arreglo del mismo elemento pasa.
 * - Un tipo **recursivo** (que se contiene a sí mismo) no se puede comparar: TypeScript lo rechaza. No hay ninguno emparejado.
 * - `unknown` / `any` en uno de los lados, `Record<string, never>` en el esquema o un `oneOf` frente a la clave de una sola rama dan un falso positivo: se resuelve dejando ese
 *   campo fuera del tipo a mano o fuera del par.
 * - El d.ts es el **commiteado**: que coincida con la API en marcha lo comprueba `DeveloperPortalE2ETest` en el backend (el `openapi.json` commiteado).
 */

type EsLiteral<T> = [T] extends [string] ? (string extends T ? false : true) : false;

/** Si los dos lados son uniones de literales, el esquema tiene que caber en el tipo a mano (cubre todos los valores); si no, basta con que se solapen. */
type Solapan<A, B> = [EsLiteral<A>, EsLiteral<B>] extends [true, true] ? ([B] extends [A] ? true : false) : [A] extends [B] ? true : [B] extends [A] ? true : false;

/** Los valores literales del esquema que el tipo a mano no cubre, como `"V1" | "V2"`; `never` si cubre todos. */
type ValoresNuevos<A, B> = [EsLiteral<A>, EsLiteral<B>] extends [true, true] ? Exclude<B & string, A> : never;

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
        : [ValoresNuevos<A, B>] extends [never]
          ? Solapan<A, B> extends true
            ? never
            : ""
          : `(valor nuevo en la API: ${ValoresNuevos<A, B>})`;

type FallosDeObjeto<A, B> =
  | { [K in Exclude<keyof A, keyof B> & string]: `${K}(ya no existe)` }[Exclude<keyof A, keyof B> & string]
  | {
      [K in keyof A & keyof B & string]-?: Fallos<NonNullable<A[K]>, NonNullable<B[K]>> extends infer F
        ? [F] extends [never]
          ? never
          : F extends ""
            ? K
            : F extends `(${string}`
              ? `${K}${F}`
              : `${K}.${F & string}`
        : never;
    }[keyof A & keyof B & string];

/**
 * `true` si el tipo a mano cabe en el contrato. Si no, un objeto con una propiedad por cada camino que falla, y el nombre de la propiedad lo dice: la compilación lo muestra como el
 * error de no poder asignar ese objeto a `true`, por ejemplo `{ "la_api_cambio:nombre(ya no existe)": true }`. Úselo como `type _X = Verifica<Contrato<Mano, Esquema>>`.
 */
export type Contrato<Mano, Esquema> = [Fallos<Mano, Esquema>] extends [never] ? true : { [K in Fallos<Mano, Esquema> as `la_api_cambio:${K}`]: true };

/** Las claves del esquema que no admiten `undefined`: las que la API marca requeridas. */
type Requeridas<E> = { [K in keyof E]-?: object extends Pick<E, K> ? never : K }[keyof E];

/**
 * Como {@link Contrato}, para un cuerpo que el portal **envía**: además exige que el tipo a mano traiga todas las claves que el esquema marca requeridas (`@NotNull`, `@NotBlank`).
 * Sin ellas la API contesta 400. Falla con `la_api_exige:clave`.
 */
export type ContratoDePeticion<Mano, Esquema> = [Exclude<Requeridas<Esquema>, keyof Mano>] extends [never]
  ? Contrato<Mano, Esquema>
  : { [K in Exclude<Requeridas<Esquema>, keyof Mano> & string as `la_api_exige:${K}`]: true };

/** Falla la compilación salvo que el contrato sea `true`. */
export type Verifica<T extends true> = T;
