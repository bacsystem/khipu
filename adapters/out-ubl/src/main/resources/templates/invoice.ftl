<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<#import "comun.ftl" as u>
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
         xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
  <ext:UBLExtensions>
    <ext:UBLExtension>
      <ext:ExtensionContent/>
    </ext:UBLExtension>
  </ext:UBLExtensions>
  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
  <cbc:CustomizationID>2.0</cbc:CustomizationID>
  <cbc:ID>${c.serie()}-${c.numero()?c}</cbc:ID>
  <cbc:IssueDate>${fechaEmision}</cbc:IssueDate>
  <#if horaEmision??><cbc:IssueTime>${horaEmision}</cbc:IssueTime></#if>
  <#if c.fechaVencimiento()??><cbc:DueDate>${c.fechaVencimiento().toString()}</cbc:DueDate></#if>
  <cbc:InvoiceTypeCode listID="${c.tipoOperacion()}" listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${c.tipo().codigo()}</cbc:InvoiceTypeCode>
  <cbc:Note languageLocaleID="1000">${montoEnLetras}</cbc:Note>
  <#if tot.tieneGratuitas()>
  <cbc:Note languageLocaleID="1002">TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE</cbc:Note>
  </#if>
  <#if tot.tieneIvap()>
  <cbc:Note languageLocaleID="2007">OPERACIÓN SUJETA AL IVAP</cbc:Note>
  </#if>
  <#if c.detraccion()??>
  <cbc:Note languageLocaleID="2006">OPERACIÓN SUJETA AL SISTEMA DE PAGO DE OBLIGACIONES TRIBUTARIAS - SPOT</cbc:Note>
  </#if>
  <#if c.percepcion()??>
  <cbc:Note languageLocaleID="2000">COMPROBANTE DE PERCEPCIÓN</cbc:Note>
  </#if>
  <#list c.leyendas() as ley>
  <cbc:Note languageLocaleID="${ley}">${statics["pe.factura.domain.documento.Leyenda"].texto(ley)}</cbc:Note>
  </#list>
  <cbc:DocumentCurrencyCode listID="ISO 4217 Alpha" listName="Currency" listAgencyName="United Nations Economic Commission for Europe">${c.moneda()}</cbc:DocumentCurrencyCode>
<@u.ordenCompra/>
<@u.guiasYOtrosDocumentos/>
  <#-- Facturas de anticipo que se regularizan (reglas 2505, 2520, 2521, 3214–3218): el identificador de pago enlaza con cac:PrepaidPayment. -->
  <#list tot.anticipos() as ac>
  <cac:AdditionalDocumentReference>
    <cbc:ID>${ac.anticipo().comprobante()}</cbc:ID>
    <cbc:DocumentTypeCode listName="Documento Relacionado" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo12">${statics["pe.factura.domain.documento.Anticipo"].TIPO_COMPROBANTE}</cbc:DocumentTypeCode>
    <cbc:DocumentStatusCode listName="Anticipo" listAgencyName="PE:SUNAT">${(ac?index + 1)?c}</cbc:DocumentStatusCode>
    <cac:IssuerParty>
      <cac:PartyIdentification><cbc:ID schemeID="6" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${t.ruc()}</cbc:ID></cac:PartyIdentification>
    </cac:IssuerParty>
  </cac:AdditionalDocumentReference>
  </#list>
<@u.firmaYPartes/>
<@u.exportacion/>
<@u.pagos/>
<@u.cargosYDescuentosGlobales/>
<@u.impuestos/>
<@u.totalMonetario elemento="LegalMonetaryTotal"/>
<@u.lineas elemento="InvoiceLine" cantidad="InvoicedQuantity"/>
</Invoice>
