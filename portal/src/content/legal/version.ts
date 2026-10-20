/**
 * Las versiones de los textos legales (#342). Es el ÚNICO lugar donde se editan, junto con `terminos.ts` y `privacidad.ts`.
 *
 * Cómo publicar una versión nueva (ver también deploy/README.md, «Términos y privacidad»):
 * 1. Editar el texto en `terminos.ts` o `privacidad.ts`.
 * 2. Poner la fecha de hoy (AAAA-MM-DD) en `VERSION_TERMINOS` o `VERSION_PRIVACIDAD`, la del documento que cambió.
 * 3. `VERSION_TEXTOS_LEGALES` es la más reciente de las dos (`version.test.ts` lo comprueba) y tiene que ser la misma que
 *    `app.terminos-version` en `bootstrap/src/main/resources/application.yml` (`TerminosVersionTest` del backend lo comprueba). Al subirla,
 *    todos los usuarios vuelven a aceptar al entrar. Una corrección que no cambia lo que se acepta (una errata) no la sube.
 * 4. Cuando un abogado haya revisado los textos, `REVISADO_POR_LEGAL = true` quita el aviso de borrador de las dos páginas.
 */

/** `false` mientras los textos sean el borrador escrito sin asesoría legal: las páginas lo dicen arriba de todo. */
export const REVISADO_POR_LEGAL = false;

/** Fecha (AAAA-MM-DD) de la versión vigente de los términos del servicio. */
export const VERSION_TERMINOS = "2026-10-20";

/** Fecha (AAAA-MM-DD) de la versión vigente de la política de privacidad. */
export const VERSION_PRIVACIDAD = "2026-10-20";

/** La que acepta el usuario: los dos textos juntos. Literal a propósito, porque el test del backend la lee del archivo. */
export const VERSION_TEXTOS_LEGALES = "2026-10-20";

/**
 * Quién es el responsable del servicio y del banco de datos. Los corchetes son datos que el dueño completa: mientras queden, las páginas
 * siguen siendo un borrador.
 */
export const RESPONSABLE = {
  razonSocial: "Bacsystem Solutions E.I.R.L.",
  ruc: "[RUC por completar]",
  domicilio: "[domicilio fiscal por completar], Lima, Perú",
  correo: "[correo de contacto por completar]",
  /** Código de inscripción del banco de datos en el Registro Nacional de Protección de Datos Personales, cuando exista. */
  inscripcionBancoDeDatos: "[código de inscripción ante la ANPD por completar, o retirar si se decide que no corresponde]",
} as const;
