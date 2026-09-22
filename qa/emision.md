# Emisión de factura · ✅ CERTIFICADO

| | |
|---|---|
| Commit | `37e1a75` · 2026-09-22 |
| Veredicto | 0 bloqueantes · 0 importantes |
| Auditores | Opus + Fable (paralelo) + matriz de contrato · 2 recertificaciones |
| Suite | 101/101 Vitest · 40/40 e2e |
| Informe largo | [artefacto](https://claude.ai/artifact/WffwrXeJsfM4We5AhgFm5G) |

## Encontrado y corregido

| Sev | Qué | PR |
|---|---|---|
| 🔴 | Cantidad fraccionaria bloqueada (`stepMismatch`, botón sin señal) | #111 |
| 🔴 | Corte de red → «Emitiendo…» eterno, sin saber si consumió correlativo | #111 |
| 🔴 | Enter en `<select>` emitía (reabierto en recert 1) | #116 |
| 🟠 | Enter en cualquier input emitía | #112 |
| 🟠 | RUC / razón social / descripción sin el contrato en cliente | #112 |
| 🟠 | Precio mostrado ≠ enviado con foco | #112 |
| 🟠 | Foco a `<body>` tras error | #112 |
| 🟠 | Aviso «ítems incompletos» mudo para lector de pantalla | #112 |
| 🟠 | Aserción del correlativo no discriminaba en paralelo | #116 |
| 🧪 | 7 aserciones muertas | #113 |

## Descartado con evidencia

- «Foco escapa del diálogo en prod» → carrera de la sonda; 0 escapes con 150 ms entre teclas.
- «Cancelar sin `type=button` emite» → inocuo; React desmonta antes del submit.

## Pendiente (no bloquea)

- **#115** idempotencia `POST /v1/facturas` — backend.
- **#117** 7 menores agrupados.
- ⚠️ **Verificar a mano** (30 s): abrir Moneda con Espacio, elegir con flechas, Enter → cambia moneda, no emite. Popup nativo no automatizable en macOS.

## Certificado con evidencia

- Seguridad: cabeceras falsas (`X-Empresa`, `Authorization`) ignoradas; JWT nunca en el navegador; sin inyección.
- Integridad: 1 POST ante doble clic / Enter repetido; validación fallida no consume correlativo; una sola definición de «línea completa» para previsualizar y enviar.
- Aritmética: `totales.ts` = `BigDecimal` en 800 000 comprobantes, 0 divergencias.
- A11y: 17 controles con nombre; foco contenido; `role=alert` / `role=status`.
