<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<#import "comun.ftl" as u>
<#-- Liquidación de compra electrónica (04, #327): SelfBilledInvoice UBL 2.1 de la hoja LiquidacionCompra2_0. Los papeles se invierten respecto de la factura: el emisor
     (el comprador, con RUC) va en AccountingCustomerParty y el vendedor sin RUC en AccountingSupplierParty, en ese orden (XSD). No hay forma de pago, cargos ni descuentos;
     cada línea lleva además la retención de renta (3000) y el importe a pagar es el neto de lo que el comprador retiene (4312). -->
<#assign lc = c.liquidacion()>
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<SelfBilledInvoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:SelfBilledInvoice-2"
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
  <#-- Tipo de documento 04 (1003, 1004) con el tipo de operación del catálogo 51 en listID (3205, 3206). -->
  <cbc:InvoiceTypeCode listID="${c.tipoOperacion()}" listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${c.tipo().codigo()}</cbc:InvoiceTypeCode>
  <cbc:Note languageLocaleID="1000">${montoEnLetras}</cbc:Note>
  <cbc:DocumentCurrencyCode listID="ISO 4217 Alpha" listName="Currency" listAgencyName="United Nations Economic Commission for Europe">${c.moneda()}</cbc:DocumentCurrencyCode>
<#-- Guías de remisión (09/31: 4005, 4006, 2364) y otros documentos (99: 4009, 4010, 2365). -->
<@u.guiasYOtrosDocumentos/>
  <cac:Signature>
    <cbc:ID>signatureFACTURA</cbc:ID>
    <cac:SignatoryParty>
      <cac:PartyIdentification><cbc:ID>${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyName><cbc:Name>${t.razonSocial()}</cbc:Name></cac:PartyName>
    </cac:SignatoryParty>
    <cac:DigitalSignatureAttachment>
      <cac:ExternalReference><cbc:URI>#signatureFACTURA</cbc:URI></cac:ExternalReference>
    </cac:DigitalSignatureAttachment>
  </cac:Signature>
  <#-- El emisor electrónico, que es el comprador: RUC con schemeID 6 (1007, 1008, 1034, 3089), razón social (1037, 4338), nombre comercial (4092) y domicilio fiscal
       (4093–4098, 4041), todos opcionales salvo el RUC y la razón social. Sin el AddressTypeCode del establecimiento anexo: la hoja no lo tiene en el comprador. -->
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="6" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <#if t.nombreComercial()??><cac:PartyName><cbc:Name>${t.nombreComercial()}</cbc:Name></cac:PartyName></#if>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
        <#if t.domicilio()??>
        <cac:RegistrationAddress>
          <cbc:ID schemeName="Ubigeos" schemeAgencyName="PE:INEI">${t.domicilio().ubigeo()}</cbc:ID>
          <#if t.domicilio().urbanizacion()??><cbc:CitySubdivisionName>${t.domicilio().urbanizacion()}</cbc:CitySubdivisionName></#if>
          <#if t.domicilio().provincia()??><cbc:CityName>${t.domicilio().provincia()}</cbc:CityName></#if>
          <#if t.domicilio().departamento()??><cbc:CountrySubentity>${t.domicilio().departamento()}</cbc:CountrySubentity></#if>
          <#if t.domicilio().distrito()??><cbc:District>${t.domicilio().distrito()}</cbc:District></#if>
          <cac:AddressLine><cbc:Line>${t.domicilio().direccion()}</cbc:Line></cac:AddressLine>
          <cac:Country><cbc:IdentificationCode listID="ISO 3166-1" listAgencyName="United Nations Economic Commission for Europe" listName="Country">${statics["pe.factura.domain.tenant.Domicilio"].PAIS}</cbc:IdentificationCode></cac:Country>
        </cac:RegistrationAddress>
        </#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <#-- El vendedor sin RUC: documento 1, 4 o 7 (2800, 2802, 2015), nombre (2021, 2022) y domicilio obligatorio: ubigeo (2453, 4339), condición del catálogo 60 (2456, 2457)
       y dirección (2452, 2593). -->
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="${c.receptor().tipoDoc()}" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${c.receptor().numDoc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${c.receptor().razonSocial()}</cbc:RegistrationName>
        <cac:RegistrationAddress>
          <cbc:ID schemeName="Ubigeos" schemeAgencyName="PE:INEI">${lc.ubigeoVendedor()}</cbc:ID>
          <cbc:AddressTypeCode>${lc.condicionVendedor()}</cbc:AddressTypeCode>
          <cac:AddressLine><cbc:Line>${c.receptor().direccion()}</cbc:Line></cac:AddressLine>
          <cac:Country><cbc:IdentificationCode listID="ISO 3166-1" listAgencyName="United Nations Economic Commission for Europe" listName="Country">${statics["pe.factura.domain.tenant.Domicilio"].PAIS}</cbc:IdentificationCode></cac:Country>
        </cac:RegistrationAddress>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <#-- Lugar donde se realiza la operación: condición del catálogo 60 (2458, 2459), ubigeo (2455, 2917) y dirección (2454, 2778). -->
  <cac:DeliveryTerms>
    <cac:DeliveryLocation>
      <cbc:LocationTypeCode>${lc.lugarOperacion().condicion()}</cbc:LocationTypeCode>
      <cac:Address>
        <cbc:ID schemeName="Ubigeos" schemeAgencyName="PE:INEI">${lc.lugarOperacion().ubigeo()}</cbc:ID>
        <cac:AddressLine><cbc:Line>${lc.lugarOperacion().direccion()}</cbc:Line></cac:AddressLine>
        <cac:Country><cbc:IdentificationCode listID="ISO 3166-1" listAgencyName="United Nations Economic Commission for Europe" listName="Country">${statics["pe.factura.domain.tenant.Domicilio"].PAIS}</cbc:IdentificationCode></cac:Country>
      </cac:Address>
    </cac:DeliveryLocation>
  </cac:DeliveryTerms>
<@u.impuestos renta=true/>
<#-- Total valor de venta (2591, 4309), subtotal (2592, 4310), redondeo (4314) e importe total neto: subtotal − IGV − renta + redondeo (2062, 4312). -->
<@u.totalMonetario elemento="LegalMonetaryTotal"/>
<@u.lineas elemento="InvoiceLine" cantidad="InvoicedQuantity" renta=true/>
</SelfBilledInvoice>
