package pe.factura.domain.documento;

public record Receptor(String tipoDoc, String numDoc, String razonSocial, String direccion) {
    public boolean esRuc() { return "6".equals(tipoDoc) && numDoc != null && numDoc.matches("\\d{11}"); }
}
