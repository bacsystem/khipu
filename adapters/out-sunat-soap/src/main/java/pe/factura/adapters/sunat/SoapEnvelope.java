package pe.factura.adapters.sunat;

import java.util.Base64;

final class SoapEnvelope {
    private SoapEnvelope() {}

    static String sendBill(String usuario, String clave, String nombreZip, byte[] zip) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
            <soapenv:Body><ser:sendBill><fileName>%s</fileName><contentFile>%s</contentFile></ser:sendBill></soapenv:Body>
            </soapenv:Envelope>""".formatted(esc(usuario), esc(clave), esc(nombreZip), Base64.getEncoder().encodeToString(zip));
    }

    static String sendSummary(String usuario, String clave, String nombreZip, byte[] zip) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
            <soapenv:Body><ser:sendSummary><fileName>%s</fileName><contentFile>%s</contentFile></ser:sendSummary></soapenv:Body>
            </soapenv:Envelope>""".formatted(esc(usuario), esc(clave), esc(nombreZip), Base64.getEncoder().encodeToString(zip));
    }

    static String getStatus(String usuario, String clave, String ticket) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
            <soapenv:Body><ser:getStatus><ticket>%s</ticket></ser:getStatus></soapenv:Body>
            </soapenv:Envelope>""".formatted(esc(usuario), esc(clave), esc(ticket));
    }

    /** {@code getStatus} / {@code getStatusCdr} de billConsultService (mismo cuerpo, distinta operación). */
    static String consulta(String operacion, String usuario, String clave, String ruc, String tipo, String serie, long numero) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
            <soapenv:Body><ser:%s><rucComprobante>%s</rucComprobante><tipoComprobante>%s</tipoComprobante><serieComprobante>%s</serieComprobante><numeroComprobante>%d</numeroComprobante></ser:%s></soapenv:Body>
            </soapenv:Envelope>""".formatted(esc(usuario), esc(clave), operacion, esc(ruc), esc(tipo), esc(serie), numero, operacion);
    }

    /** {@code validaCDPcriterios} de billValidService: los campos opcionales se omiten cuando son nulos. */
    static String validaCdp(String usuario, String clave, String rucEmisor, String tipo, String serie, long numero, String tipoDocReceptor, String numDocReceptor, String fechaEmision, java.math.BigDecimal importeTotal) {
        StringBuilder campos = new StringBuilder()
                .append("<rucEmisor>").append(esc(rucEmisor)).append("</rucEmisor>")
                .append("<tipoCDP>").append(esc(tipo)).append("</tipoCDP>")
                .append("<serieCDP>").append(esc(serie)).append("</serieCDP>")
                .append("<numeroCDP>").append(numero).append("</numeroCDP>");
        if (tipoDocReceptor != null) campos.append("<tipoDocIdReceptor>").append(esc(tipoDocReceptor)).append("</tipoDocIdReceptor>");
        if (numDocReceptor != null) campos.append("<numeroDocIdReceptor>").append(esc(numDocReceptor)).append("</numeroDocIdReceptor>");
        if (fechaEmision != null) campos.append("<fechaEmision>").append(esc(fechaEmision)).append("</fechaEmision>");
        if (importeTotal != null) campos.append("<importeTotal>").append(importeTotal.toPlainString()).append("</importeTotal>");
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
            <soapenv:Body><ser:validaCDPcriterios>%s</ser:validaCDPcriterios></soapenv:Body>
            </soapenv:Envelope>""".formatted(esc(usuario), esc(clave), campos);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Extrae el texto del primer elemento con ese nombre local (sin importar prefijo). Devuelve null si no existe. */
    static String textoDe(String xml, String nombreLocal) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(?:[\\w-]+:)?" + nombreLocal + "(?:\\s[^>]*)?>(.*?)</(?:[\\w-]+:)?" + nombreLocal + ">", java.util.regex.Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    /** "soap-env:Client.1033" → "1033"; "1033" → "1033"; "soap-env:Server" → "0000". */
    static String codigoDeFault(String faultcode) {
        if (faultcode == null) return "0000";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})\\s*$").matcher(faultcode.trim());
        return m.find() ? m.group(1) : "0000";
    }
}
