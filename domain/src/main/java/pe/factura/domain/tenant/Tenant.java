package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Empresa emisora. {@code domicilio} y {@code cuentaDetracciones} son opcionales: sin domicilio el XML solo lleva el código de
 * establecimiento (SUNAT no observa su ausencia, sí un formato inválido); la cuenta de detracciones del Banco de la Nación se
 * usa cuando una factura sujeta a detracción no la indica. {@code nombreComercial} (campo 11 de la hoja Factura2_0) va en el XML
 * como {@code cac:PartyName/cbc:Name} del emisor cuando existe. {@code personalizacionPdf} solo afecta a la representación impresa;
 * nunca es nula (por defecto, la plantilla clásica).
 */
public record Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado,
                     Domicilio domicilio, String cuentaDetracciones, String nombreComercial, PersonalizacionPdf personalizacionPdf) {
    public Tenant {
        personalizacionPdf = personalizacionPdf == null ? PersonalizacionPdf.porDefecto() : personalizacionPdf;
        if (ruc == null || !ruc.matches("\\d{11}")) throw new DomainException("RUC_INVALIDO", "RUC inválido: " + ruc);
        if (razonSocial == null || razonSocial.isBlank()) throw new DomainException("RAZON_SOCIAL_REQUERIDA", "Razón social requerida");
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
    public Tenant conCertificado(CertificadoDigital c) { return new Tenant(id, ruc, razonSocial, entorno, sol, c, domicilio, cuentaDetracciones, nombreComercial, personalizacionPdf); }
    public Tenant conCredencialesSol(CredencialesSol s) { return new Tenant(id, ruc, razonSocial, entorno, s, certificado, domicilio, cuentaDetracciones, nombreComercial, personalizacionPdf); }
    public Tenant conDatosFiscales(Domicilio d, String cuentaDetracciones) { return conDatosFiscales(d, cuentaDetracciones, nombreComercial); }
    public Tenant conDatosFiscales(Domicilio d, String cuentaDetracciones, String nombreComercial) { return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, d, cuentaDetracciones, nombreComercial, personalizacionPdf); }
    public Tenant conPersonalizacionPdf(PersonalizacionPdf p) { return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, domicilio, cuentaDetracciones, nombreComercial, p); }
}
