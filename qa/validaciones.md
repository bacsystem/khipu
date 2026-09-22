# Validación de entradas · transversal

Estado al 2026-09-22 (`main@37e1a75`). Pedido del usuario: validar formato en **frontend y endpoints**, con error visible al usuario.

## Inyección SQL — ✅ cubierto en persistencia

| | |
|---|---|
| Repositorios JDBC | 12 (`JdbcTemplate`), 61 placeholders `?` |
| SQL concatenado con valores | **0** — el único `+` arma el `WHERE` con `?` + `args` |
| `String.format` en SQL | 0 |

La defensa real es esta. Un regex en el navegador no protege nada: se saltea con `curl`.

## Endpoints — ✅ validan

| | |
|---|---|
| Bodies con `@Valid @RequestBody` | 18 / 18 |
| Campos en DTOs de request | 151 en 13 DTOs; 29 sin anotación Bean Validation |
| Esos 29 | los valida el **dominio** (`Descuento` 5 reglas, `Cargo` 8, `Isc` 9, `Detraccion` 14, `Percepcion` 11, `FormaPago` 12, `Receptor` 9, `Domicilio` 5, `Establecimiento` 6, `Tenant` 8…) |
| Respuesta | `422 VALIDACION` con **`errores` por campo** (`Map<campo, mensajes[]>`) |

## Frontend — ⚠️ desparejo

| | |
|---|---|
| Controles | 68 en 21 archivos · 32 de texto |
| Con zod | solo `onboarding-wizard` (email, password, teléfono vía `lib/validacion.ts`) |
| Sin ninguna validación en cliente | 5 → relevantes: `nd-descripcion` (nota de débito; backend exige no vacío ≤ 500) y `nc-direccion` (opcional, sin regla). Los otros 3 son buscadores / componente genérico. |

## El hueco que importa

**El backend ya devuelve el error por campo y el portal lo tira.** `ApiEnvelope.errores` existe; lo lee **un solo** componente (`correo-button.tsx`). Todos los demás formularios hacen `res.mensaje ?? mensajeError(res.codigo)` → el usuario ve «Validación fallida» sin saber qué campo.

→ Arreglo transversal, una vez: helper que mapee `errores[campo]` al `Campo` correspondiente (`error=` ya existe en `formularios/campo.tsx`). Se aplica flujo por flujo al certificarlo.

## Qué NO es hallazgo

- Falta de regex en cliente en campos libres (dirección, sustento, observaciones): el backend acota longitud y caracteres de control; el cliente pone `maxLength`.
- «Sin anotación en el DTO» ≠ «sin validación»: contar anotaciones engaña; hay que leer el dominio.
