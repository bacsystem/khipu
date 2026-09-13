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
