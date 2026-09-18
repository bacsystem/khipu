<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<#-- Categoría (catálogo 05, UN/ECE 5305) y tributo (UN/ECE 5153) de una afectación: mismo bloque en los subtotales globales y en cada línea. -->
<#macro categoriaTributo af>
<cbc:ID schemeID="UN/ECE 5305" schemeName="Tax Category Identifier" schemeAgencyName="United Nations Economic Commission for Europe">${af.categoria()}</cbc:ID>
<#nested>
<cac:TaxScheme>
  <cbc:ID schemeID="UN/ECE 5153" schemeName="Tax Scheme Identifier" schemeAgencyName="United Nations Economic Commission for Europe">${af.tributoId()}</cbc:ID>
  <cbc:Name>${af.tributoNombre()}</cbc:Name>
  <cbc:TaxTypeCode>${af.tributoTipo()}</cbc:TaxTypeCode>
</cac:TaxScheme>
</#macro>
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
  <cbc:InvoiceTypeCode listID="${c.tipoOperacion()}" listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${c.tipo().codigo()}</cbc:InvoiceTypeCode>
  <cbc:Note languageLocaleID="1000">${montoEnLetras}</cbc:Note>
  <cbc:DocumentCurrencyCode listID="ISO 4217 Alpha" listName="Currency" listAgencyName="United Nations Economic Commission for Europe">${c.moneda()}</cbc:DocumentCurrencyCode>
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
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="6" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
        <cac:RegistrationAddress><cbc:AddressTypeCode>0000</cbc:AddressTypeCode></cac:RegistrationAddress>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="${c.receptor().tipoDoc()}" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${c.receptor().numDoc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${c.receptor().razonSocial()}</cbc:RegistrationName>
        <#if c.receptor().direccion()??>
        <cac:RegistrationAddress><cac:AddressLine><cbc:Line>${c.receptor().direccion()}</cbc:Line></cac:AddressLine></cac:RegistrationAddress>
        </#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <#-- Forma de pago (reglas 3244–3267, 3319): un PaymentTerms por indicador; al crédito, el neto pendiente y una cuota por PaymentTerms. -->
  <#if c.formaPago().esCredito()>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Credito</cbc:PaymentMeansID>
    <cbc:Amount currencyID="${c.moneda()}">${c.formaPago().montoPendiente()}</cbc:Amount>
  </cac:PaymentTerms>
  <#list c.formaPago().cuotas() as q>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Cuota${(q?index + 1)?string["000"]}</cbc:PaymentMeansID>
    <cbc:Amount currencyID="${c.moneda()}">${q.monto()}</cbc:Amount>
    <cbc:PaymentDueDate>${q.vencimiento().toString()}</cbc:PaymentDueDate>
  </cac:PaymentTerms>
  </#list>
  <#else>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Contado</cbc:PaymentMeansID>
  </cac:PaymentTerms>
  </#if>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv()}</cbc:TaxAmount>
    <#list tot.subtotales() as st>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${st.base()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">${st.impuesto()}</cbc:TaxAmount>
      <cac:TaxCategory>
        <@categoriaTributo af=st.afectacion()/>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
    </#list>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${tot.gravado() + tot.exonerado() + tot.inafecto()}</cbc:LineExtensionAmount>
    <cbc:TaxInclusiveAmount currencyID="${c.moneda()}">${tot.total()}</cbc:TaxInclusiveAmount>
    <cbc:PayableAmount currencyID="${c.moneda()}">${tot.total()}</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <#list tot.items() as it>
  <cac:InvoiceLine>
    <cbc:ID>${(it?index + 1)?c}</cbc:ID>
    <cbc:InvoicedQuantity unitCode="${it.item().unidad()}" unitCodeListID="UN/ECE rec 20" unitCodeListAgencyName="United Nations Economic Commission for Europe">${it.item().cantidad()?string["0.####"]}</cbc:InvoicedQuantity>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:LineExtensionAmount>
    <cac:PricingReference>
      <cac:AlternativeConditionPrice>
        <cbc:PriceAmount currencyID="${c.moneda()}">${it.item().precioUnitario()}</cbc:PriceAmount>
        <cbc:PriceTypeCode listName="Tipo de Precio" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16">01</cbc:PriceTypeCode>
      </cac:AlternativeConditionPrice>
    </cac:PricingReference>
    <cac:TaxTotal>
      <cbc:TaxAmount currencyID="${c.moneda()}">${it.igv()}</cbc:TaxAmount>
      <cac:TaxSubtotal>
        <cbc:TaxableAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:TaxableAmount>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.igv()}</cbc:TaxAmount>
        <cac:TaxCategory>
          <@categoriaTributo af=it.item().afectacion()>
          <cbc:Percent>${it.porcentajeIgv()}</cbc:Percent>
          <cbc:TaxExemptionReasonCode listAgencyName="PE:SUNAT" listName="Afectacion del IGV" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07">${it.item().afectacion().codigo()}</cbc:TaxExemptionReasonCode>
          </@categoriaTributo>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
    </cac:TaxTotal>
    <cac:Item>
      <cbc:Description>${it.item().descripcion()}</cbc:Description>
      <#if it.item().codigo()??><cac:SellersItemIdentification><cbc:ID>${it.item().codigo()}</cbc:ID></cac:SellersItemIdentification></#if>
    </cac:Item>
    <cac:Price><cbc:PriceAmount currencyID="${c.moneda()}">${it.valorUnitario()?string["0.0000000000"]}</cbc:PriceAmount></cac:Price>
  </cac:InvoiceLine>
  </#list>
</Invoice>
