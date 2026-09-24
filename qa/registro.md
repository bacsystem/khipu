# Registro + onboarding · 🔴 NO CERTIFICADO → corregido (#158 + portal), pendiente de recert #1

Puerta de entrada del producto: crea la cuenta, la empresa y la primera serie. De él depende abrir el autoservicio
(hoy cerrado con `REGISTRO_ABIERTO=false`, ver [`../deploy/frontend/README.md`](../deploy/frontend/README.md)).
Leyenda de estados en [README](README.md).

| Ronda | Base | Veredicto | Corrección | Verificada en |
|---|---|---|---|---|
| Auditoría #1 | `472f9d4` | 1 🔴 · 12 🟠 | #158 (backend) → portal | **recert #1 pendiente** |

Suite tras la corrección: 130/130 Vitest · 86/86 e2e ×2 · backend 352/352 · 4/4 mutaciones del backend mueren.

## Auditoría #1 → #158 + portal

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🔴 | La razón social del emisor se guardaba sin la regla 4338 (tabuladores, saltos de línea) y va cruda al XML: **todos** los comprobantes de esa empresa se rechazan, y no hay forma de corregirla (no existe endpoint que la edite y el RUC es único) | #158 dominio + DTO + columna a 1500 · portal: se limpia al escribir | 🔧 recert #1 |
| 🟠 | El celular aceptaba letras y largo ilimitado, y solo avisaba al enviar (reportado por el usuario en el portal desplegado) | portal: `filtrar` + `maxLength` + error al salir del campo | 🔧 recert #1 |
| 🟠 | El RUC no cruzaba el módulo 11 en el cliente y el campo aceptaba letras | portal: `rucSchema` con módulo 11, solo dígitos, 11 | 🔧 recert #1 |
| 🟠 | La serie solo exigía «4 caracteres» y el mock la aceptaba: el onboarding terminaba en verde con series que el backend rechaza (1001) | portal + mock: `F###`/`B###` según el tipo | 🔧 recert #1 |
| 🟠 | Corte de red = fallo silencioso en los cuatro envíos del alta; el peor: la empresa quedaba creada y el wizard no avanzaba ni decía nada | portal: `enviar()` con aviso y sin avanzar | 🔧 recert #1 |
| 🟠 | Recargar volvía al paso 1 vacío y reenviar el mismo RUC daba «Ya existe una cuenta con ese correo» (mensaje equivocado), sin salida | portal: retoma el paso que falta al montar; mensaje de DUPLICADO por contexto (empresa/serie) | 🔧 recert #1 |
| 🟠 | El entorno SUNAT es irreversible y no lo avisaba | portal: aviso en el paso 1 | 🔧 recert #1 |
| 🟠 | Razón social sin tope: 300 caracteres → `DataIntegrityViolation` → 500 «Intenta de nuevo en unos minutos» | #158: `@Size(max=1500)` + columna 1500 | 🔧 recert #1 |
| 🟠 | Mock más permisivo que el backend: sin RUC duplicado, sin normalizar el correo, sin fuerza de contraseña ni formato | portal: paridad en el mock | 🔧 recert #1 |
| 🟠 | Los errores del registro y del wizard no se anunciaban (`role="alert"`, `aria-describedby`) | portal: `FormField` con el patrón de `formularios/campo.tsx` | 🔧 recert #1 |
| 🟠 | Cobertura del backend: módulo 11 en el alta de empresa, prefijo del RUC, contraseña sin letras y razón social sin test | #158: `TenantTest` y `GestionarEmpresasServiceTest` nuevos | 🔧 recert #1 |
| 🟠 | `REGISTRO_ABIERTO=false` tapa el endpoint pero sin test | portal: e2e | 🔧 recert #1 |
| 🟡 | Mensajes faltantes (`TELEFONO_INVALIDO`, `REGISTRO_CERRADO`, `RAZON_SOCIAL_INVALIDA`) · nombre y razón social sin recortar | portal | 🔧 recert #1 |

## Contraste SUNAT

- **4338 / 1037** (`Factura2_0` filas 49-50, `Boleta2_0` 47-48): razón social `an..1500`, sin tab ni salto de línea → dominio, portal y mock. Sin mínimo de 3: esa regla es del receptor (2022).
- **1001** (`Factura2_0` fila 12): serie `[F][A-Z0-9]{3}` / `[B][A-Z0-9]{3}` → backend, portal y mock.
- **RUC** `n11` con módulo 11 y prefijo 10/15/16/17/20 → dominio, portal y mock.
- El domicilio fiscal no se pide en el alta (es opcional y vive en el flujo 5); sin él la plantilla escribe `0000` (3030/4242).

## Pendiente (no bloquea) · detalle en [pendientes.md](pendientes.md)

Un RUC ajeno tecleado por error queda tomado (denegación de registro, no impersonación: emitir exige que el certificado traiga ese RUC) · no se verifica el correo · sin límite de empresas por cuenta · el wizard no tiene «Volver» · `POST /api/auth/registro` con cuerpo no-JSON da 500 en vez de 400 · el mock revienta con cuerpo vacío en `POST /v1/empresas` · no existe endpoint para editar razón social ni entorno.

## Siguiente paso

Mergear #158 → portal → **recert #1**.
