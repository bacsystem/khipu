package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Empresa emisora. {@code domicilio} y {@code cuentaDetracciones} son opcionales: sin domicilio el XML solo lleva el código de
 * establecimiento (SUNAT no observa su ausencia, sí un formato inválido); la cuenta de detracciones del Banco de la Nación se
 * usa cuando una factura sujeta a detracción no la indica. {@code nombreComercial} (campo 11 de la hoja Factura2_0) va en el XML
 * como {@code cac:PartyName/cbc:Name} del emisor cuando existe. {@code personalizacionPdf} solo afecta a la representación impresa;
 * nunca es nula (por defecto, la plantilla clásica). {@code padronTasaEspecialIgv}: inscrito en el Padrón de Tasa Especial del IGV
 * (MYPE de restaurantes y hoteles, Ley 31556): sus comprobantes gravados llevan la tasa reducida ({@link pe.factura.domain.documento.TasaIgv}).
 */
public record Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado,
                     Domicilio domicilio, String cuentaDetracciones, String nombreComercial, PersonalizacionPdf personalizacionPdf, boolean padronTasaEspecialIgv) {
    public Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado,
                  Domicilio domicilio, String cuentaDetracciones, String nombreComercial, PersonalizacionPdf personalizacionPdf) {
        this(id, ruc, razonSocial, entorno, sol, certificado, domicilio, cuentaDetracciones, nombreComercial, personalizacionPdf, false);
    }
    public Tenant {
        personalizacionPdf = personalizacionPdf == null ? PersonalizacionPdf.porDefecto() : personalizacionPdf;
        if (ruc == null || !ruc.matches("\\d{11}")) throw new DomainException("RUC_INVALIDO", "RUC inválido: " + ruc);
        if (razonSocial == null || razonSocial.isBlank()) throw new DomainException("RAZON_SOCIAL_REQUERIDA", "Razón social requerida");
        // 1037/4338: `cbc:RegistrationName` del emisor admite hasta 1500 caracteres y ningún whitespace que no sea el espacio
        // (ni tab ni salto de línea). Iba cruda al XML: una razón social pegada desde una planilla hacía que SUNAT rechazara
        // TODOS los comprobantes de esa empresa, quemando correlativo, y no hay forma de corregirla después.
        razonSocial = razonSocial.strip();
        if (razonSocial.length() > 1500 || razonSocial.chars().anyMatch(Character::isISOControl))
            throw new DomainException("RAZON_SOCIAL_INVALIDA", "4338 - La razón social admite hasta 1500 caracteres, sin saltos de línea ni tabuladores");
        cuentaDetracciones = cuentaDetracciones == null || cuentaDetracciones.isBlank() ? null : cuentaDetracciones.strip();
        if (cuentaDetracciones != null && !cuentaDetracciones.matches("[0-9-]{8,20}"))
            throw new DomainException("CUENTA_DETRACCIONES_INVALIDA", "3034 - La cuenta de detracciones del Banco de la Nación son dígitos y guiones (p. ej. 00-000-123456)");
        nombreComercial = nombreComercial == null || nombreComercial.isBlank() ? null : nombreComercial.strip();
        if (nombreComercial != null && (nombreComercial.length() > 1500 || nombreComercial.chars().anyMatch(Character::isISOControl)))
            throw new DomainException("NOMBRE_COMERCIAL_INVALIDO", "4092 - El nombre comercial admite hasta 1500 caracteres sin saltos de línea ni tabuladores");
    }
    public Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado,
                  Domicilio domicilio, String cuentaDetracciones, String nombreComercial) {
        this(id, ruc, razonSocial, entorno, sol, certificado, domicilio, cuentaDetracciones, nombreComercial, null);
    }
    public Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado,
                  Domicilio domicilio, String cuentaDetracciones) {
        this(id, ruc, razonSocial, entorno, sol, certificado, domicilio, cuentaDetracciones, null, null);
    }
    public Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado) {
        this(id, ruc, razonSocial, entorno, sol, certificado, null, null, null, null);
    }
    public void exigirListoParaEmitir(LocalDate hoy) {
        if (certificado == null) throw new DomainException("CERTIFICADO_NO_CARGADO", "El tenant no tiene certificado digital");
        if (certificado.vigenciaHasta() != null && certificado.vigenciaHasta().isBefore(hoy))
            throw new DomainException("CERTIFICADO_VENCIDO", "El certificado venció el " + certificado.vigenciaHasta());
    }
    public void exigirCredencialesSol() {
        if (sol == null) throw new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "El tenant no tiene credenciales SOL");
    }
    public Tenant conCertificado(CertificadoDigital c) { return new Tenant(id, ruc, razonSocial, entorno, sol, c, domicilio, cuentaDetracciones, nombreComercial, personalizacionPdf, padronTasaEspecialIgv); }
    public Tenant conCredencialesSol(CredencialesSol s) { return new Tenant(id, ruc, razonSocial, entorno, s, certificado, domicilio, cuentaDetracciones, nombreComercial, personalizacionPdf, padronTasaEspecialIgv); }
    public Tenant conDatosFiscales(Domicilio d, String cuentaDetracciones) { return conDatosFiscales(d, cuentaDetracciones, nombreComercial); }
    public Tenant conDatosFiscales(Domicilio d, String cuentaDetracciones, String nombreComercial) { return conDatosFiscales(d, cuentaDetracciones, nombreComercial, padronTasaEspecialIgv); }
    public Tenant conDatosFiscales(Domicilio d, String cuentaDetracciones, String nombreComercial, boolean padronTasaEspecialIgv) { return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, d, cuentaDetracciones, nombreComercial, personalizacionPdf, padronTasaEspecialIgv); }
    /** Vista del emisor con el domicilio de un establecimiento anexo: lo que va en el XML y el PDF de una serie asignada a él. */
    /**
     * Identidad del emisor congelada para imprimir un comprobante ya firmado, conservando el diseño actual del PDF.
     * El RUC viene del XML igual que el resto: si alguna vez difiere del actual, manda el firmado, porque es el que
     * SUNAT recibió.
     */
    public Tenant conIdentidadImpresa(String ruc, String razonSocial, String nombreComercial, Domicilio domicilio) {
        return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, domicilio, cuentaDetracciones, nombreComercial, personalizacionPdf, padronTasaEspecialIgv);
    }

    public Tenant conDomicilio(Domicilio d) { return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, d, cuentaDetracciones, nombreComercial, personalizacionPdf, padronTasaEspecialIgv); }
    public Tenant conPersonalizacionPdf(PersonalizacionPdf p) { return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, domicilio, cuentaDetracciones, nombreComercial, p, padronTasaEspecialIgv); }
}
