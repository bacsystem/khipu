# Flujo 7 · Series

Auditoría independiente (contexto limpio, worktree `wt-series`, `E2E_PORT=3270`, `API_BASE_URL` a puerto muerto) sobre `main@472f9d4`.
18 specs temporales, 24 mutaciones dirigidas con arnés validado.
**Veredicto de la auditoría #1: NO CERTIFICADO — 1 bloqueante, 8 importantes.**

Leyenda: 🔧 corregido, a la espera de recert · ✅ verificado por una recert posterior · ⬜ abierto, en [pendientes.md](pendientes.md).

## Bloqueantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | **El correlativo no tenía tope de 8 dígitos en ninguna capa.** SUNAT **1001** (hoja `Factura2_0` fila 12, ídem boleta y notas) define el ID como `[FB][A-Z0-9]{3}-[0-9]{1,8}`. Reproducido: una serie con último número 99999999 emite `#100000000`, que pasa dominio, firma y XSD, **consume el correlativo** y SUNAT lo rechaza con 1001; el intento siguiente vuelve a pasarse. La serie queda inservible y no existe endpoint para editarla, desactivarla ni borrarla. Se llega al mismo final **sin error del usuario**: cuando una serie viva alcanza el techo, nadie avisa | `domain/tenant/Serie.java`, `JdbcSerieRepository`, `SerieRequest`, `nueva-serie-form.tsx` | tope en las cuatro capas: `Serie.NUMERO_MAXIMO` con `CORRELATIVO_INVALIDO` en el dominio, `SERIE_AGOTADA` dentro del `FOR UPDATE` de `siguienteNumero` y de `avanzarHasta`, `@Max` en el DTO, y rechazo en el formulario antes del viaje |

## Importantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | Corte de red → «Guardando…» eterno sin aviso, y el POST pudo haber llegado | `lib/api/browser.ts` | el cliente ya no lanza (arreglo compartido con los flujos 4 y 5) |
| 🔧 | El formulario descartaba `res.mensaje`: una serie repetida (409 `DUPLICADO`) se mostraba como **«Ya existe una cuenta con ese correo»**, y `ESTABLECIMIENTO_INVALIDO` caía en el texto genérico perdiendo «el establecimiento 0002 está dado de baja» | `nueva-serie-form.tsx` | `res.mensaje ?? mensajeError(res.codigo)` |
| 🔧 | `Number(correlativo) || 0` mandaba float: «1e30» se guardaba como `1e+30` | `nueva-serie-form.tsx` | `correlativoValido`, solo dígitos y acotado, con test |
| 🔧 | El mensaje del 409 mentía además del lado del backend: «Ya existe un documento con esa serie y número» para quien crea una serie. Ahora se elige por la tabla del constraint (PR #162) | `GlobalExceptionHandler` | |
| 🔧 | Guarda de doble envío inerte (estado en vez de ref): dos clics en el mismo tick creaban la serie dos veces | `nueva-serie-form.tsx` | `enviandoRef`, el mismo patrón del botón de baja |
| ⬜ | Una serie asignada a un anexo lo bloquea para siempre: el error pide «reasígnelas antes de darlo de baja», pero no hay forma de reasignar, desactivar ni borrar una serie | backend | falta el endpoint; ver pendientes |
| ⬜ | Los establecimientos se cargan sin estado de carga ni de error: mientras no vuelven, el combo solo ofrece «0000 · Domicilio fiscal» sin aviso. Como la serie no se puede editar, **todos** sus comprobantes saldrían con el domicilio fiscal en vez del del anexo (regla 3030) | `nueva-serie-form.tsx` | el patrón de catálogos de notas (#120) |
| ⬜ | Paridad del mock: no replica `serieValida` (con tipo 01 acepta `BZZ9`, `F9`, `X001`, y una `BQQ1` queda ofrecida como serie de factura en el diálogo de emisión) ni la unicidad `(tipo, codigo)` | `src/mocks/handlers.ts` | ver pendientes |
| ⬜ | Cobertura: no existe `e2e/series.spec.ts` ni tests unitarios de `components/series/*`; sobrevivían 13 de 24 mutaciones, y las cuatro anotaciones de `SerieRequest` no están atadas por ningún test REST | tests | el tope y el parseo quedan atados abajo; el resto, pendiente |

## Menores

En [pendientes.md](pendientes.md): el alta no da señal cuando la fila nueva cae en la página 2 (buscador deshabilitado), filtro «Inactivas» e insignia «Inactiva» inalcanzables por API, series `BC##` legítimas invisibles en el formulario de notas, orden de la lista distinto en mock y backend, y `codigo` sin normalizar en el dominio.

## Lo que sí resiste

La numeración correlativa, que es lo que no se puede repetir ni saltear: `siguienteNumero` toma `SELECT … FOR UPDATE` sobre la fila de la serie y el lock dura toda la transacción de emisión, así que dos emisiones sobre la misma serie serializan; si firma, XSD o storage fallan, el `UPDATE` se deshace con la transacción y no queda hueco. El correlativo explícito pasa por `GREATEST`, que nunca reduce, y el `UNIQUE (tenant_id, tipo, serie, numero)` es la última red. El formato de la serie coincide exactamente con la regla 1001, y el aislamiento por empresa también resiste (403 `EMPRESA_AJENA`).

## Verificación de los arreglos

| Mutación | Resultado |
|---|---|
| Dominio sin el tope | muere |
| Tope con un dígito más (999 999 999) | muere |
| `agotada()` con `>` en vez de `>=` | muere |
| `siguienteNumero` sin la guarda | muere |
| `avanzarHasta` sin la guarda | muere |
| No-op (control) | sobrevive |
| DTO sin `@Max` en el correlativo (segunda ronda, PR #162) | muere |
| Mensaje del duplicado siempre «documento» (segunda ronda, PR #162) | muere |

Tests nuevos: `SerieTest` (dominio) y el caso de agotamiento en `JdbcSerieRepositoryTest` (Testcontainers, comprueba además que el rechazo **no** consume numeración), más `nueva-serie-form.test.ts` en el portal.

**Pendiente**: corregir los importantes abiertos y recert #1.
