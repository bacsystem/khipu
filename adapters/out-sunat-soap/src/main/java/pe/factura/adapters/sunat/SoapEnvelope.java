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
