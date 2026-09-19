<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<#import "comun.ftl" as u>
<#-- Nota de crédito (CreditNote) y de débito (DebitNote): mismo documento salvo la raíz, el catálogo del motivo (09/10) y tres nombres de
     elementos; el generador pasa esas diferencias en `n` (raiz, catalogo, listName, total, linea, cantidad). -->
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<${n.raiz} xmlns="urn:oasis:names:specification:ubl:schema:xsd:${n.raiz}-2"
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
  <cbc:Note languageLocaleID="1000">${montoEnLetras}</cbc:Note>
  <#if tot.tieneGratuitas()>
  <cbc:Note languageLocaleID="1002">TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE</cbc:Note>
  </#if>
  <#if tot.tieneIvap()>
  <cbc:Note languageLocaleID="2007">OPERACIÓN SUJETA AL IVAP</cbc:Note>
  </#if>
  <cbc:DocumentCurrencyCode listID="ISO 4217 Alpha" listName="Currency" listAgencyName="United Nations Economic Commission for Europe">${c.moneda()}</cbc:DocumentCurrencyCode>
  <#-- Motivo (catálogo 09/10; reglas 2128, 2172, 2135) y comprobante que se modifica (cac:BillingReference; reglas 2524, 2117, 2116). -->
  <cac:DiscrepancyResponse>
    <cbc:ReferenceID>${c.nota().documentoAfectado()}</cbc:ReferenceID>
    <cbc:ResponseCode listAgencyName="PE:SUNAT" listName="${n.listName}" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo${n.catalogo}">${c.nota().motivo()}</cbc:ResponseCode>
    <cbc:Description>${c.nota().descripcion()}</cbc:Description>
  </cac:DiscrepancyResponse>
<@u.ordenCompra/>
  <cac:BillingReference>
    <cac:InvoiceDocumentReference>
      <cbc:ID>${c.nota().documentoAfectado()}</cbc:ID>
      <cbc:DocumentTypeCode listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${c.nota().tipoAfectado().codigo()}</cbc:DocumentTypeCode>
    </cac:InvoiceDocumentReference>
  </cac:BillingReference>
<@u.guiasYOtrosDocumentos/>
<@u.firmaYPartes/>
<@u.pagos contado=false/>
<@u.cargosYDescuentosGlobales/>
<@u.impuestos/>
<@u.totalMonetario elemento=n.total/>
<@u.lineas elemento=n.linea cantidad=n.cantidad/>
</${n.raiz}>
