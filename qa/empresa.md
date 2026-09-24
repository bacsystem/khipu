# Flujo 5 · Empresa: datos fiscales, certificado, credenciales SOL

Auditoría independiente (contexto limpio, worktree `wt-empresa`, `E2E_PORT=3260`, `API_BASE_URL` a puerto muerto) sobre `main@472f9d4`.
Arnés Java propio con 5 PKCS#12 generados con `keytool`, validado con una mutación conocida y un no-op. 15 casos, 5 fallos.
**Veredicto de la auditoría #1: NO CERTIFICADO — 1 bloqueante, 11 importantes.**

Leyenda: 🔧 corregido, a la espera de recert · ✅ verificado por una recert posterior · ⬜ abierto, en [pendientes.md](pendientes.md).

## Bloqueantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | **Un certificado que todavía no entró en vigencia (`notBefore` futuro) se aceptaba, reemplazaba al vigente y el portal lo pintaba «VIGENTE».** `getNotBefore()` no se leía en ningún punto del repo. El caso es el habitual: la CA emite la renovación arrancando el día que expira la anterior, y el cliente la sube el día que llega. Cada comprobante se firmaría fuera de la ventana de validez y SUNAT lo rechaza con **2327** con el correlativo ya consumido | `AdministrarTenantService.cargarCertificado` | se compara el **instante** de `notBefore` contra el reloj y se rechaza con `CERTIFICADO_NO_VIGENTE` (422), citando 2327 |

## Importantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | Corte de red durante cualquier envío del flujo → botón en «Subiendo…»/«Guardando…» para siempre. En el certificado es grave: no había forma de saber si el `.p12` llegó | `lib/api/browser.ts` | el cliente ya no lanza nunca; devuelve sobre `RED` (arreglo compartido con el flujo 4) |
| 🔧 | El motivo del rechazo del certificado se descartaba: OU≠RUC, clave equivocada, p12 corrupto y p12 sin clave privada mostraban el mismo texto genérico | `certificado-form.tsx`, `credenciales-sol-form.tsx`, `nueva-empresa-form.tsx` | `res.mensaje ?? mensajeError(res.codigo)`, como ya hacía datos fiscales |
| 🔧 | Razón social sin tope de longitud ni filtro de caracteres de control (SUNAT `an..1500`, OBSERV **4338**) | `domain/tenant/Tenant.java` | ya corregido en el trabajo del flujo 3 (commit `3b2bd4a`, PR del stack de registro) |
| ⬜ | Usuario y clave SOL se guardan sin recortar espacios: `" MODDATOS "` produce un `usernameToken` inválido y SUNAT rechaza la autenticación en todos los envíos, con el portal mostrando «CONFIGURADAS» | `AdministrarTenantService.cargarCredencialesSol` | `strip()` en el usuario, aviso en la clave |
| ⬜ | Una dirección escrita sin elegir el distrito se descarta en silencio **y borra el domicilio guardado** (`domicilio: null` reemplaza los tres valores), informando éxito | `datos-fiscales-form.tsx` | exigir el ubigeo, o no mandar `domicilio` cuando el formulario está a medias |
| ⬜ | Sin `spring.servlet.multipart` configurado, el `.p12` pasa por un archivo temporal en claro y un archivo de más de 1 MB responde 500 sin handler | `bootstrap/application.yml`, `GlobalExceptionHandler` | `file-size-threshold` ≥ `max-file-size`, tope explícito, handler 422 |
| ⬜ | Paridad del mock (5 huecos): acepta cualquier «certificado» sin parsearlo, acepta SOL en blanco, solo valida el ubigeo de los datos fiscales, no persiste `padron_tasa_especial_igv` (campo de dinero: decide 10.5 % vs 18 %) y deja registrar dos empresas con el mismo RUC | `src/mocks/handlers.ts` | ver pendientes |
| ⬜ | Cobertura: las tres reglas propias del certificado y de SOL no tenían test (sobrevivían las mutaciones de OU↔RUC, vencimiento y SOL en blanco); `empresa.spec.ts` no toca ninguno de los dos formularios; el test de cifrado en BD solo asegura `sol_clave_enc` | tests | el vencimiento y la vigencia quedan atados abajo; el resto, pendiente |

## Menores

Todos en [pendientes.md](pendientes.md): cookie `factura_empresa` sin comprobar pertenencia (el backend corta con 403, no hay fuga), fault SOAP de credenciales clasificado como transitorio, `CREDENCIALES_INVALIDAS` mapeado a 401 para un caso de validación, cuatro claves de mensaje faltantes, `cert_vigencia_hasta` nullable, RUC sin dígito verificador en cliente, y la cita equivocada de la regla 3034.

## Verificación de los arreglos

Test nuevo `vigenciaDelCertificado`: genera tres PKCS#12 con `keytool` (ventanas exactas contra el reloj fijo de los tests) y comprueba que el vigente se acepta, el futuro se rechaza con `CERTIFICADO_NO_VIGENTE`, el vencido con `CERTIFICADO_VENCIDO`, y que **ninguno de los dos rechazos pisa al que servía**.

| Mutación | Resultado |
|---|---|
| Sin la guarda de `notBefore` | muere |
| `notBefore` invertido | muere |
| Sin la guarda de `notAfter` | muere |
| No-op (control) | sobrevive |

**Pendiente**: corregir los importantes abiertos y recert #1.
