package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Empresa emisora. {@code domicilio} y {@code cuentaDetracciones} son opcionales: sin domicilio el XML solo lleva el código de
 * establecimiento (SUNAT no observa su ausencia, sí un formato inválido); la cuenta de detracciones del Banco de la Nación se
 * usa cuando una factura sujeta a detracción no la indica.
 */
public record Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado,
                     Domicilio domicilio, String cuentaDetracciones) {
    public Tenant {
        if (ruc == null || !ruc.matches("\\d{11}")) throw new DomainException("RUC_INVALIDO", "RUC inválido: " + ruc);
        if (razonSocial == null || razonSocial.isBlank()) throw new DomainException("RAZON_SOCIAL_REQUERIDA", "Razón social requerida");
        cuentaDetracciones = cuentaDetracciones == null || cuentaDetracciones.isBlank() ? null : cuentaDetracciones.strip();
        if (cuentaDetracciones != null && !cuentaDetracciones.matches("[0-9-]{8,20}"))
            throw new DomainException("CUENTA_DETRACCIONES_INVALIDA", "3034 - La cuenta de detracciones del Banco de la Nación son dígitos y guiones (p. ej. 00-000-123456)");
    }
    public Tenant(UUID id, String ruc, String razonSocial, Entorno entorno, CredencialesSol sol, CertificadoDigital certificado) {
        this(id, ruc, razonSocial, entorno, sol, certificado, null, null);
    }
    public void exigirListoParaEmitir(LocalDate hoy) {
        if (certificado == null) throw new DomainException("CERTIFICADO_NO_CARGADO", "El tenant no tiene certificado digital");
        if (certificado.vigenciaHasta() != null && certificado.vigenciaHasta().isBefore(hoy))
            throw new DomainException("CERTIFICADO_VENCIDO", "El certificado venció el " + certificado.vigenciaHasta());
    }
    public void exigirCredencialesSol() {
        if (sol == null) throw new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "El tenant no tiene credenciales SOL");
    }
    public Tenant conCertificado(CertificadoDigital c) { return new Tenant(id, ruc, razonSocial, entorno, sol, c, domicilio, cuentaDetracciones); }
    public Tenant conCredencialesSol(CredencialesSol s) { return new Tenant(id, ruc, razonSocial, entorno, s, certificado, domicilio, cuentaDetracciones); }
    public Tenant conDatosFiscales(Domicilio d, String cuentaDetracciones) { return new Tenant(id, ruc, razonSocial, entorno, sol, certificado, d, cuentaDetracciones); }
}
