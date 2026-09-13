<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
         xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
  <ext:UBLExtensions>
    <ext:UBLExtension>
      <ext:ExtensionContent><fx:PendienteFirma xmlns:fx="urn:pe:factura:internal"/></ext:ExtensionContent>
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
      <cac:PartyIdentification><cbc:ID schemeID="6">${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
        <cac:RegistrationAddress><cbc:AddressTypeCode>0000</cbc:AddressTypeCode></cac:RegistrationAddress>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="${c.receptor().tipoDoc()}">${c.receptor().numDoc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${c.receptor().razonSocial()}</cbc:RegistrationName>
        <#if c.receptor().direccion()??>
        <cac:RegistrationAddress><cac:AddressLine><cbc:Line>${c.receptor().direccion()}</cbc:Line></cac:AddressLine></cac:RegistrationAddress>
        </#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Contado</cbc:PaymentMeansID>
  </cac:PaymentTerms>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv()}</cbc:TaxAmount>
    <#if (tot.gravado() > 0)>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${tot.gravado()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv()}</cbc:TaxAmount>
      <cac:TaxCategory><cac:TaxScheme><cbc:ID>1000</cbc:ID><cbc:Name>IGV</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>
    </cac:TaxSubtotal>
    </#if>
    <#if (tot.exonerado() > 0)>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${tot.exonerado()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">0.00</cbc:TaxAmount>
      <cac:TaxCategory><cac:TaxScheme><cbc:ID>9997</cbc:ID><cbc:Name>EXO</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>
    </cac:TaxSubtotal>
    </#if>
    <#if (tot.inafecto() > 0)>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${tot.inafecto()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">0.00</cbc:TaxAmount>
      <cac:TaxCategory><cac:TaxScheme><cbc:ID>9998</cbc:ID><cbc:Name>INA</cbc:Name><cbc:TaxTypeCode>FRE</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>
    </cac:TaxSubtotal>
    </#if>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${tot.gravado() + tot.exonerado() + tot.inafecto()}</cbc:LineExtensionAmount>
    <cbc:TaxInclusiveAmount currencyID="${c.moneda()}">${tot.total()}</cbc:TaxInclusiveAmount>
    <cbc:PayableAmount currencyID="${c.moneda()}">${tot.total()}</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <#list tot.items() as it>
  <cac:InvoiceLine>
    <cbc:ID>${it?index + 1}</cbc:ID>
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
          <cbc:Percent>${it.porcentajeIgv()}</cbc:Percent>
          <cbc:TaxExemptionReasonCode listAgencyName="PE:SUNAT" listName="Afectacion del IGV" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07">${it.item().afectacion().codigo()}</cbc:TaxExemptionReasonCode>
          <cac:TaxScheme>
            <cbc:ID schemeID="UN/ECE 5153" schemeAgencyID="6">${it.item().afectacion().tributoId()}</cbc:ID>
            <cbc:Name>${it.item().afectacion().tributoNombre()}</cbc:Name>
            <cbc:TaxTypeCode>${it.item().afectacion().tributoTipo()}</cbc:TaxTypeCode>
          </cac:TaxScheme>
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
