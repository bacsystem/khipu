# 02 · Dominio

Índice del plan: [README](README.md). Módulo `domain` — sin dependencias externas.

### Task 2: Tipos base, estados y excepción de dominio

**Files:**
- Create: `domain/src/main/java/pe/factura/domain/DomainException.java`
- Create: `domain/src/main/java/pe/factura/domain/documento/TipoDocumento.java`
- Create: `domain/src/main/java/pe/factura/domain/documento/EstadoDocumento.java`
- Create: `domain/src/main/java/pe/factura/domain/documento/NombreArchivo.java`
- Test: `domain/src/test/java/pe/factura/domain/documento/TipoDocumentoTest.java`, `EstadoDocumentoTest.java`, `NombreArchivoTest.java`

**Interfaces:**
- Produces: `DomainException(String codigo, String mensaje)`; `TipoDocumento { String codigo(); boolean serieValida(String) }`; `EstadoDocumento { boolean puedeTransitarA(EstadoDocumento) }`; `NombreArchivo.de(String ruc, TipoDocumento, String serie, long numero) -> String`.

- [ ] **Step 1: Tests**

`TipoDocumentoTest.java`:
```java
package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TipoDocumentoTest {
    @Test void facturaAceptaSerieF() {
        assertThat(TipoDocumento.FACTURA.serieValida("F001")).isTrue();
        assertThat(TipoDocumento.FACTURA.serieValida("FA01")).isTrue();
        assertThat(TipoDocumento.FACTURA.serieValida("B001")).isFalse();
        assertThat(TipoDocumento.FACTURA.serieValida("F01")).isFalse();
    }
    @Test void boletaAceptaSerieB() {
        assertThat(TipoDocumento.BOLETA.serieValida("B001")).isTrue();
        assertThat(TipoDocumento.BOLETA.serieValida("F001")).isFalse();
    }
    @Test void notaAceptaFoB() {
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("FC01")).isTrue();
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("BC01")).isTrue();
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("XC01")).isFalse();
    }
    @Test void codigoSunat() {
        assertThat(TipoDocumento.FACTURA.codigo()).isEqualTo("01");
        assertThat(TipoDocumento.porCodigo("01")).isEqualTo(TipoDocumento.FACTURA);
    }
}
```

`EstadoDocumentoTest.java`:
```java
package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.domain.documento.EstadoDocumento.*;

class EstadoDocumentoTest {
    @Test void transicionesValidas() {
        assertThat(RECIBIDO.puedeTransitarA(FIRMADO)).isTrue();
        assertThat(FIRMADO.puedeTransitarA(ENVIADO)).isTrue();
        assertThat(FIRMADO.puedeTransitarA(ERROR_ENVIO)).isTrue();
        assertThat(ERROR_ENVIO.puedeTransitarA(ENVIADO)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(ACEPTADO)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(ACEPTADO_CON_OBS)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(RECHAZADO)).isTrue();
        assertThat(ACEPTADO.puedeTransitarA(ANULADO)).isTrue();
    }
    @Test void transicionesInvalidas() {
        assertThat(RECIBIDO.puedeTransitarA(ACEPTADO)).isFalse();
        assertThat(RECHAZADO.puedeTransitarA(ENVIADO)).isFalse();
        assertThat(ACEPTADO.puedeTransitarA(ENVIADO)).isFalse();
    }
    @Test void enviable() {
        assertThat(FIRMADO.esEnviable()).isTrue();
        assertThat(ERROR_ENVIO.esEnviable()).isTrue();
        assertThat(ACEPTADO.esEnviable()).isFalse();
    }
}
```

`NombreArchivoTest.java`:
```java
package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NombreArchivoTest {
    @Test void formatoSunat() {
        assertThat(NombreArchivo.de("20100066603", TipoDocumento.FACTURA, "F001", 1))
                .isEqualTo("20100066603-01-F001-1");
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `./gradlew :domain:test`
Expected: FAIL de compilación (clases inexistentes).

- [ ] **Step 3: Implementación**

`DomainException.java`:
```java
package pe.factura.domain;

public class DomainException extends RuntimeException {
    private final String codigo;
    public DomainException(String codigo, String mensaje) { super(mensaje); this.codigo = codigo; }
    public String codigo() { return codigo; }
}
```

`TipoDocumento.java`:
```java
package pe.factura.domain.documento;

import java.util.regex.Pattern;

public enum TipoDocumento {
    FACTURA("01", "F"), BOLETA("03", "B"), NOTA_CREDITO("07", "FB"), NOTA_DEBITO("08", "FB");

    private final String codigo;
    private final Pattern serie;

    TipoDocumento(String codigo, String prefijos) {
        this.codigo = codigo;
        this.serie = Pattern.compile("[" + prefijos + "][A-Z0-9]{3}");
    }
    public String codigo() { return codigo; }
    public boolean serieValida(String s) { return s != null && serie.matcher(s).matches(); }
    public static TipoDocumento porCodigo(String codigo) {
        for (TipoDocumento t : values()) if (t.codigo.equals(codigo)) return t;
        throw new IllegalArgumentException("Tipo de documento desconocido: " + codigo);
    }
}
```

`EstadoDocumento.java`:
```java
package pe.factura.domain.documento;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoDocumento {
    RECIBIDO, INVALIDO, FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO,
    ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ANULADO;

    private Set<EstadoDocumento> siguientes = EnumSet.noneOf(EstadoDocumento.class);

    static {
        RECIBIDO.siguientes = EnumSet.of(FIRMADO, INVALIDO);
        FIRMADO.siguientes = EnumSet.of(ENVIADO, ERROR_ENVIO, PENDIENTE_AGRUPACION);
        ERROR_ENVIO.siguientes = EnumSet.of(ENVIADO, ERROR_ENVIO);
        PENDIENTE_AGRUPACION.siguientes = EnumSet.of(ENVIADO);
        ENVIADO.siguientes = EnumSet.of(ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ERROR_ENVIO, PENDIENTE_AGRUPACION);
        ACEPTADO.siguientes = EnumSet.of(ANULADO);
        ACEPTADO_CON_OBS.siguientes = EnumSet.of(ANULADO);
    }

    public boolean puedeTransitarA(EstadoDocumento destino) { return siguientes.contains(destino); }
    public boolean esEnviable() { return this == FIRMADO || this == ERROR_ENVIO; }
    public boolean esFinalAceptado() { return this == ACEPTADO || this == ACEPTADO_CON_OBS; }
}
```

`NombreArchivo.java`:
```java
package pe.factura.domain.documento;

public final class NombreArchivo {
    private NombreArchivo() {}
    public static String de(String ruc, TipoDocumento tipo, String serie, long numero) {
        return ruc + "-" + tipo.codigo() + "-" + serie + "-" + numero;
    }
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `./gradlew :domain:test`
Expected: PASS (3 clases de test).

- [ ] **Step 5: Commit**

```bash
git add domain
git commit -m "feat(domain): tipos de documento, máquina de estados y nombre de archivo SUNAT"
```

---

### Task 3: Ítems, totales y monto en letras

**Files:**
- Create: `domain/src/main/java/pe/factura/domain/documento/TipoAfectacionIgv.java`, `Item.java`, `ItemCalculado.java`, `Totales.java`, `MontoEnLetras.java`
- Test: `domain/src/test/java/pe/factura/domain/documento/TotalesTest.java`, `MontoEnLetrasTest.java`

**Interfaces:**
- Produces: `Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion)`; `ItemCalculado.de(Item)` con `valorUnitario, valorVenta, igv, precioVenta`; `Totales.calcular(List<Item>)` con `gravado, exonerado, inafecto, igv, total` y `List<ItemCalculado> items()`; `MontoEnLetras.de(BigDecimal, String moneda)`.

- [ ] **Step 1: Tests**

`TotalesTest.java`:
```java
package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TotalesTest {
    private Item item(String precio, String cant, TipoAfectacionIgv af) {
        return new Item("P1", "Prod", "NIU", new BigDecimal(cant), new BigDecimal(precio), af);
    }

    @Test void gravadoDesglosaIgvDelPrecio() {
        Totales t = Totales.calcular(List.of(item("118.00", "1", TipoAfectacionIgv.GRAVADO)));
        assertThat(t.gravado()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
        assertThat(t.total()).isEqualByComparingTo("118.00");
        ItemCalculado i = t.items().get(0);
        assertThat(i.valorUnitario()).isEqualByComparingTo("100.0000000000");
        assertThat(i.precioVenta()).isEqualByComparingTo("118.00");
    }

    @Test void mezclaDeAfectaciones() {
        Totales t = Totales.calcular(List.of(
                item("118.00", "2", TipoAfectacionIgv.GRAVADO),
                item("50.00", "2", TipoAfectacionIgv.EXONERADO),
                item("100.00", "1", TipoAfectacionIgv.INAFECTO)));
        assertThat(t.gravado()).isEqualByComparingTo("200.00");
        assertThat(t.exonerado()).isEqualByComparingTo("100.00");
        assertThat(t.inafecto()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("36.00");
        assertThat(t.total()).isEqualByComparingTo("436.00");
    }

    @Test void redondeoADosDecimales() {
        Totales t = Totales.calcular(List.of(item("10.00", "3", TipoAfectacionIgv.GRAVADO)));
        assertThat(t.gravado()).isEqualByComparingTo("25.42");
        assertThat(t.igv()).isEqualByComparingTo("4.58");
        assertThat(t.total()).isEqualByComparingTo("30.00");
    }
}
```

`MontoEnLetrasTest.java`:
```java
package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MontoEnLetrasTest {
    @Test void casos() {
        assertThat(MontoEnLetras.de(new BigDecimal("118.00"), "PEN")).isEqualTo("CIENTO DIECIOCHO CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1.50"), "PEN")).isEqualTo("UNO CON 50/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("21.00"), "USD")).isEqualTo("VEINTIUNO CON 00/100 DOLARES AMERICANOS");
        assertThat(MontoEnLetras.de(new BigDecimal("100.00"), "PEN")).isEqualTo("CIEN CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1000.00"), "PEN")).isEqualTo("MIL CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("2500.75"), "PEN")).isEqualTo("DOS MIL QUINIENTOS CON 75/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1000000.00"), "PEN")).isEqualTo("UN MILLON CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("3016.00"), "PEN")).isEqualTo("TRES MIL DIECISEIS CON 00/100 SOLES");
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `./gradlew :domain:test --tests '*TotalesTest*' --tests '*MontoEnLetrasTest*'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementación**

`TipoAfectacionIgv.java`:
```java
package pe.factura.domain.documento;

public enum TipoAfectacionIgv {
    GRAVADO("10", "1000", "IGV", "VAT"), EXONERADO("20", "9997", "EXO", "VAT"), INAFECTO("30", "9998", "INA", "FRE");

    private final String codigo, tributoId, tributoNombre, tributoTipo;
    TipoAfectacionIgv(String codigo, String tributoId, String tributoNombre, String tributoTipo) {
        this.codigo = codigo; this.tributoId = tributoId; this.tributoNombre = tributoNombre; this.tributoTipo = tributoTipo;
    }
    public String codigo() { return codigo; }
    public String tributoId() { return tributoId; }
    public String tributoNombre() { return tributoNombre; }
    public String tributoTipo() { return tributoTipo; }
    public boolean gravado() { return this == GRAVADO; }
    public static TipoAfectacionIgv porCodigo(String c) {
        for (TipoAfectacionIgv t : values()) if (t.codigo.equals(c)) return t;
        throw new IllegalArgumentException("Afectación IGV desconocida: " + c);
    }
}
```

`Item.java`:
```java
package pe.factura.domain.documento;

import java.math.BigDecimal;

public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad,
                   BigDecimal precioUnitario, TipoAfectacionIgv afectacion) {}
```

`ItemCalculado.java`:
```java
package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ItemCalculado(Item item, BigDecimal valorUnitario, BigDecimal valorVenta,
                            BigDecimal igv, BigDecimal precioVenta, BigDecimal porcentajeIgv) {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);

    public static ItemCalculado de(Item item) {
        BigDecimal precio = item.precioUnitario();
        BigDecimal valorUnitario = item.afectacion().gravado()
                ? precio.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP)
                : precio.setScale(10, RoundingMode.HALF_UP);
        BigDecimal valorVenta = valorUnitario.multiply(item.cantidad()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igv = item.afectacion().gravado()
                ? valorVenta.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal precioVenta = valorVenta.add(igv);
        BigDecimal pct = item.afectacion().gravado() ? new BigDecimal("18.00") : new BigDecimal("0.00");
        return new ItemCalculado(item, valorUnitario, valorVenta, igv, precioVenta, pct);
    }
}
```

`Totales.java`:
```java
package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.util.List;

public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto,
                      BigDecimal igv, BigDecimal total, List<ItemCalculado> items) {

    public static Totales calcular(List<Item> items) {
        BigDecimal gravado = z(), exonerado = z(), inafecto = z(), igv = z();
        List<ItemCalculado> calculados = items.stream().map(ItemCalculado::de).toList();
        for (ItemCalculado c : calculados) {
            switch (c.item().afectacion()) {
                case GRAVADO -> gravado = gravado.add(c.valorVenta());
                case EXONERADO -> exonerado = exonerado.add(c.valorVenta());
                case INAFECTO -> inafecto = inafecto.add(c.valorVenta());
            }
            igv = igv.add(c.igv());
        }
        BigDecimal total = gravado.add(exonerado).add(inafecto).add(igv);
        return new Totales(gravado, exonerado, inafecto, igv, total, calculados);
    }
    private static BigDecimal z() { return BigDecimal.ZERO.setScale(2); }
}
```

`MontoEnLetras.java`:
```java
package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MontoEnLetras {
    private MontoEnLetras() {}

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    public static String de(BigDecimal monto, String moneda) {
        BigDecimal m = monto.setScale(2, RoundingMode.HALF_UP);
        long entero = m.longValue();
        int centavos = m.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String nombreMoneda = switch (moneda) { case "USD" -> "DOLARES AMERICANOS"; case "EUR" -> "EUROS"; default -> "SOLES"; };
        return numero(entero) + " CON " + String.format("%02d", centavos) + "/100 " + nombreMoneda;
    }

    static String numero(long n) {
        if (n == 0) return "CERO";
        StringBuilder sb = new StringBuilder();
        long millones = n / 1_000_000, miles = (n % 1_000_000) / 1000, resto = n % 1000;
        if (millones == 1) sb.append("UN MILLON ");
        else if (millones > 1) sb.append(cientos(millones)).append(" MILLONES ");
        if (miles == 1) sb.append("MIL ");
        else if (miles > 1) sb.append(cientos(miles)).append(" MIL ");
        if (resto > 0) sb.append(cientos(resto));
        return sb.toString().trim();
    }

    private static String cientos(long n) {
        if (n == 100) return "CIEN";
        int c = (int) (n / 100), d = (int) (n % 100);
        String s = CENTENAS[c];
        if (d > 0) {
            String parte = d < 30 ? UNIDADES[d] : DECENAS[d / 10] + (d % 10 > 0 ? " Y " + UNIDADES[d % 10] : "");
            s = s.isEmpty() ? parte : s + " " + parte;
        }
        return s;
    }
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `./gradlew :domain:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain
git commit -m "feat(domain): cálculo de ítems y totales con IGV y monto en letras"
```

---

### Task 4: Agregado `Comprobante` y entidades de tenant

**Files:**
- Create: `domain/src/main/java/pe/factura/domain/documento/Receptor.java`, `Cdr.java`, `Comprobante.java`
- Create: `domain/src/main/java/pe/factura/domain/tenant/Entorno.java`, `CredencialesSol.java`, `CertificadoDigital.java`, `Tenant.java`, `Serie.java`, `ApiKey.java`
- Test: `domain/src/test/java/pe/factura/domain/documento/ComprobanteTest.java`

**Interfaces:**
- Produces:
  - `Receptor(String tipoDoc, String numDoc, String razonSocial, String direccion)`
  - `Cdr(String codigo, String descripcion, List<String> observaciones)` con `esAceptado()`, `esRechazo()`, `tieneObservaciones()`
  - `Comprobante.crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor, List<Item>, Clock)`; getters; `asignarNumero(long, String ruc)`, `firmar(String hash, String xmlKey)`, `marcarEnviado()`, `aplicarCdr(Cdr, String cdrKey)`, `marcarErrorEnvio(String)`, `rechazarPorFault(String codigo, String desc)`; y un constructor de rehidratación `Comprobante.rehidratar(...)` usado por persistencia.
  - `Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado)` con `certificado`/`sol` opcionales (`null` si no cargados) y `exigirListoParaEmitir()`.
  - `Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa)`, `ApiKey(UUID id, UUID tenantId, String hash, String prefijo, boolean activa)`.

- [ ] **Step 1: Test**

`ComprobanteTest.java`:
```java
package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ComprobanteTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final Receptor empresa = new Receptor("6", "20601234567", "CLIENTE SAC", "AV. LIMA 1");
    private final List<Item> items = List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO));

    @Test void facturaValidaNaceRecibidaConTotales() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECIBIDO);
        assertThat(c.totales().total()).isEqualByComparingTo("118.00");
        assertThat(c.numero()).isNull();
    }

    @Test void facturaExigeRucDelReceptor() {
        Receptor dni = new Receptor("1", "12345678", "JUAN PEREZ", null);
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", dni, items, clock))
                .isInstanceOf(DomainException.class).hasMessageContaining("RUC");
    }

    @Test void serieDebeCorresponderAlTipo() {
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void fechaNoPuedeSerFutura() {
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 14), "PEN", "0101", empresa, items, clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("FECHA_INVALIDA");
    }

    @Test void sinItemsFalla() {
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, List.of(), clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SIN_ITEMS");
    }

    @Test void cicloDeVidaHastaAceptado() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        c.asignarNumero(7, "20100066603");
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-01-F001-7");
        c.firmar("abc123", "t/2026/09/20100066603-01-F001-7.xml");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "La Factura numero F001-7, ha sido aceptada", List.of()), "t/2026/09/R-20100066603-01-F001-7.xml");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void cdrConObservacionesYRechazo() {
        Comprobante a = firmado(); a.marcarEnviado();
        a.aplicarCdr(new Cdr("0", "ok", List.of("4252 - dato observado")), "k");
        assertThat(a.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);

        Comprobante r = firmado(); r.marcarEnviado();
        r.aplicarCdr(new Cdr("2324", "El comprobante fue registrado previamente con otros datos", List.of()), "k");
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test void errorEnvioCuentaIntentosYPermiteReenviar() {
        Comprobante c = firmado();
        c.marcarErrorEnvio("timeout");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(c.intentos()).isEqualTo(1);
        assertThat(c.estado().esEnviable()).isTrue();
        c.marcarEnviado();
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ENVIADO);
    }

    @Test void faultDefinitivoRechaza() {
        Comprobante c = firmado();
        c.rechazarPorFault("2324", "registrado previamente");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(c.cdr().codigo()).isEqualTo("2324");
    }

    @Test void transicionInvalidaLanza() {
        Comprobante c = firmado();
        assertThatThrownBy(() -> c.aplicarCdr(new Cdr("0", "x", List.of()), "k"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TRANSICION_INVALIDA");
    }

    private Comprobante firmado() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("h", "k");
        return c;
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `./gradlew :domain:test --tests '*ComprobanteTest*'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementación**

`Receptor.java`:
```java
package pe.factura.domain.documento;

public record Receptor(String tipoDoc, String numDoc, String razonSocial, String direccion) {
    public boolean esRuc() { return "6".equals(tipoDoc) && numDoc != null && numDoc.matches("\\d{11}"); }
}
```

`Cdr.java`:
```java
package pe.factura.domain.documento;

import java.util.List;

public record Cdr(String codigo, String descripcion, List<String> observaciones) {
    public boolean esAceptado() { return "0".equals(codigo); }
    public boolean esRechazo() { int c = Integer.parseInt(codigo); return c >= 2000 && c <= 3999; }
    public boolean tieneObservaciones() { return observaciones != null && !observaciones.isEmpty(); }
}
```

`Comprobante.java`:
```java
package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class Comprobante {
    private final UUID id;
    private final UUID tenantId;
    private final TipoDocumento tipo;
    private final String serie;
    private Long numero;
    private final LocalDate fechaEmision;
    private final String moneda;
    private final String tipoOperacion;
    private final Receptor receptor;
    private final List<Item> items;
    private final Totales totales;
    private EstadoDocumento estado;
    private String hash;
    private String nombreArchivo;
    private String xmlKey;
    private String cdrKey;
    private Cdr cdr;
    private int intentos;
    private String ultimoError;

    private Comprobante(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero, LocalDate fechaEmision,
                        String moneda, String tipoOperacion, Receptor receptor, List<Item> items, EstadoDocumento estado) {
        this.id = id; this.tenantId = tenantId; this.tipo = tipo; this.serie = serie; this.numero = numero;
        this.fechaEmision = fechaEmision; this.moneda = moneda; this.tipoOperacion = tipoOperacion;
        this.receptor = receptor; this.items = List.copyOf(items); this.totales = Totales.calcular(this.items);
        this.estado = estado;
    }

    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda,
                                           String tipoOperacion, Receptor receptor, List<Item> items, Clock clock) {
        if (!TipoDocumento.FACTURA.serieValida(serie)) throw new DomainException("SERIE_INVALIDA", "Serie de factura inválida: " + serie);
        if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
        if (items == null || items.isEmpty()) throw new DomainException("SIN_ITEMS", "La factura debe tener al menos un ítem");
        if (receptor == null || !receptor.esRuc()) throw new DomainException("RECEPTOR_INVALIDO", "La factura requiere un receptor con RUC válido");
        if (moneda == null || !moneda.matches("PEN|USD|EUR")) throw new DomainException("MONEDA_INVALIDA", "Moneda no soportada: " + moneda);
        return new Comprobante(UUID.randomUUID(), tenantId, TipoDocumento.FACTURA, serie, null, fechaEmision,
                moneda, tipoOperacion == null ? "0101" : tipoOperacion, receptor, items, EstadoDocumento.RECIBIDO);
    }

    /** Solo para persistencia: reconstruye sin validar reglas de creación. */
    public static Comprobante rehidratar(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero,
                                         LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor,
                                         List<Item> items, EstadoDocumento estado, String hash, String nombreArchivo,
                                         String xmlKey, String cdrKey, Cdr cdr, int intentos, String ultimoError) {
        Comprobante c = new Comprobante(id, tenantId, tipo, serie, numero, fechaEmision, moneda, tipoOperacion, receptor, items, estado);
        c.hash = hash; c.nombreArchivo = nombreArchivo; c.xmlKey = xmlKey; c.cdrKey = cdrKey; c.cdr = cdr;
        c.intentos = intentos; c.ultimoError = ultimoError;
        return c;
    }

    public void asignarNumero(long numero, String rucEmisor) {
        if (this.numero != null) throw new DomainException("NUMERO_YA_ASIGNADO", "El comprobante ya tiene número");
        this.numero = numero;
        this.nombreArchivo = NombreArchivo.de(rucEmisor, tipo, serie, numero);
    }

    public void firmar(String hash, String xmlKey) {
        transitar(EstadoDocumento.FIRMADO);
        this.hash = hash; this.xmlKey = xmlKey;
    }

    public void marcarEnviado() { transitar(EstadoDocumento.ENVIADO); }

    public void aplicarCdr(Cdr cdr, String cdrKey) {
        EstadoDocumento destino = cdr.esRechazo() ? EstadoDocumento.RECHAZADO
                : cdr.tieneObservaciones() ? EstadoDocumento.ACEPTADO_CON_OBS : EstadoDocumento.ACEPTADO;
        transitar(destino);
        this.cdr = cdr; this.cdrKey = cdrKey; this.ultimoError = null;
    }

    public void marcarErrorEnvio(String motivo) {
        transitar(EstadoDocumento.ERROR_ENVIO);
        this.intentos++; this.ultimoError = motivo;
    }

    public void rechazarPorFault(String codigo, String descripcion) {
        if (estado == EstadoDocumento.FIRMADO || estado == EstadoDocumento.ERROR_ENVIO) estado = EstadoDocumento.ENVIADO;
        transitar(EstadoDocumento.RECHAZADO);
        this.cdr = new Cdr(codigo, descripcion, List.of());
    }

    private void transitar(EstadoDocumento destino) {
        if (!estado.puedeTransitarA(destino))
            throw new DomainException("TRANSICION_INVALIDA", "No se puede pasar de " + estado + " a " + destino);
        estado = destino;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public TipoDocumento tipo() { return tipo; }
    public String serie() { return serie; }
    public Long numero() { return numero; }
    public LocalDate fechaEmision() { return fechaEmision; }
    public String moneda() { return moneda; }
    public String tipoOperacion() { return tipoOperacion; }
    public Receptor receptor() { return receptor; }
    public List<Item> items() { return items; }
    public Totales totales() { return totales; }
    public EstadoDocumento estado() { return estado; }
    public String hash() { return hash; }
    public String nombreArchivo() { return nombreArchivo; }
    public String xmlKey() { return xmlKey; }
    public String cdrKey() { return cdrKey; }
    public Cdr cdr() { return cdr; }
    public int intentos() { return intentos; }
    public String ultimoError() { return ultimoError; }
}
```

`tenant/Entorno.java`:
```java
package pe.factura.domain.tenant;
public enum Entorno { BETA, PRODUCCION }
```

`tenant/CredencialesSol.java`:
```java
package pe.factura.domain.tenant;
public record CredencialesSol(String usuario, String clave) {
    /** SUNAT concatena RUC + usuario en el UsernameToken. */
    public String usernameToken(String ruc) { return ruc + usuario; }
}
```

`tenant/CertificadoDigital.java`:
```java
package pe.factura.domain.tenant;
import java.time.LocalDate;
public record CertificadoDigital(byte[] pkcs12, String clave, LocalDate vigenciaHasta) {}
```

`tenant/Tenant.java`:
```java
package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import java.time.LocalDate;
import java.util.UUID;

public record Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado) {
    public Tenant {
        if (ruc == null || !ruc.matches("\\d{11}")) throw new DomainException("RUC_INVALIDO", "RUC inválido: " + ruc);
        if (razonSocial == null || razonSocial.isBlank()) throw new DomainException("RAZON_SOCIAL_REQUERIDA", "Razón social requerida");
    }
    public void exigirListoParaEmitir(LocalDate hoy) {
        if (certificado == null) throw new DomainException("CERTIFICADO_NO_CARGADO", "El tenant no tiene certificado digital");
        if (certificado.vigenciaHasta() != null && certificado.vigenciaHasta().isBefore(hoy))
            throw new DomainException("CERTIFICADO_VENCIDO", "El certificado venció el " + certificado.vigenciaHasta());
    }
    public void exigirCredencialesSol() {
        if (sol == null) throw new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "El tenant no tiene credenciales SOL");
    }
    public Tenant conCertificado(CertificadoDigital c) { return new Tenant(id, ruc, razonSocial, entorno, sol, c); }
    public Tenant conCredencialesSol(CredencialesSol s) { return new Tenant(id, ruc, razonSocial, entorno, s, certificado); }
}
```

`tenant/Serie.java`:
```java
package pe.factura.domain.tenant;
import pe.factura.domain.documento.TipoDocumento;
import java.util.UUID;
public record Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa) {}
```

`tenant/ApiKey.java`:
```java
package pe.factura.domain.tenant;
import java.util.UUID;
public record ApiKey(UUID id, UUID tenantId, String hash, String prefijo, boolean activa) {}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `./gradlew :domain:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain
git commit -m "feat(domain): agregado Comprobante con ciclo de vida y entidades de tenant"
```
