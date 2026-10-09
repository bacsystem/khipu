<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<#-- Baja de una boleta (#20): resumen diario (hoja "Resumen Diario1_1"), UBL 2.0, CustomizationID 1.1, una línea por resumen con la boleta en estado 3
     (catálogo 19: anulado). La línea repite comprador, valores de venta y tributos de la boleta; un valor en cero no se informa (2254). El orden de los
     elementos es el del XSD (SummaryDocumentsLineType). -->
<SummaryDocuments xmlns="urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1"
                  xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                  xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                  xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2"
                  xmlns:sac="urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1">
  <ext:UBLExtensions>
    <ext:UBLExtension>
      <ext:ExtensionContent/>
    </ext:UBLExtension>
  </ext:UBLExtensions>
  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>
  <cbc:CustomizationID>1.1</cbc:CustomizationID>
  <cbc:ID>${b.identificador()}</cbc:ID>
  <cbc:ReferenceDate>${b.fechaReferencia().toString()}</cbc:ReferenceDate>
  <cbc:IssueDate>${b.fechaGeneracion().toString()}</cbc:IssueDate>
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
    <cbc:CustomerAssignedAccountID>${t.ruc()}</cbc:CustomerAssignedAccountID>
    <cbc:AdditionalAccountID>6</cbc:AdditionalAccountID>
    <cac:Party>
      <cac:PartyLegalEntity>
        <#-- 2228: de 3 hasta 100 caracteres en el resumen (en la boleta llega a 1500). -->
        <cbc:RegistrationName>${t.razonSocial()?truncate_c(100, "")}</cbc:RegistrationName>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <sac:SummaryDocumentsLine>
    <cbc:LineID>1</cbc:LineID>
    <cbc:DocumentTypeCode>${b.tipoComprobante().codigo()}</cbc:DocumentTypeCode>
    <cbc:ID>${b.serie()}-${b.numero()?c}</cbc:ID>
    <#-- 2514/2016: el comprador va siempre; sin documento (hasta S/ 700) con guion en tipo y número. -->
    <cac:AccountingCustomerParty>
      <cbc:CustomerAssignedAccountID>${c.receptor().numDoc()}</cbc:CustomerAssignedAccountID>
      <cbc:AdditionalAccountID>${c.receptor().tipoDoc()}</cbc:AdditionalAccountID>
    </cac:AccountingCustomerParty>
    <cac:Status>
      <cbc:ConditionCode>3</cbc:ConditionCode>
    </cac:Status>
    <sac:TotalAmount currencyID="${c.moneda()}">${tot.total()}</sac:TotalAmount>
    <#-- Catálogo 11: 01 gravadas, 02 exoneradas, 03 inafectas, 04 exportación. -->
    <#list [["01", tot.gravado()], ["02", tot.exonerado()], ["03", tot.inafecto()], ["04", tot.exportacion()]] as v>
    <#if (v[1] > 0)>
    <sac:BillingPayment>
      <cbc:PaidAmount currencyID="${c.moneda()}">${v[1]}</cbc:PaidAmount>
      <cbc:InstructionID>${v[0]}</cbc:InstructionID>
    </sac:BillingPayment>
    </#if>
    </#list>
    <#if (tot.totalCargos() > 0)>
    <cac:AllowanceCharge>
      <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
      <cbc:Amount currencyID="${c.moneda()}">${tot.totalCargos()}</cbc:Amount>
    </cac:AllowanceCharge>
    </#if>
    <#if (tot.isc() > 0)>
    <@tributo monto=tot.isc() codigo="2000" nombre="ISC" internacional="EXC"/>
    </#if>
    <#-- 2278: siempre el IGV (1000) o, en una boleta del arroz pilado, el IVAP (1016). -->
    <#if (tot.ivap() > 0)>
    <@tributo monto=tot.ivap() codigo="1016" nombre="IVAP" internacional="VAT"/>
    <#else>
    <@tributo monto=tot.igv() codigo="1000" nombre="IGV" internacional="VAT" tasa=c.tasaIgv()/>
    </#if>
    <#if (tot.icbper() > 0)>
    <@tributo monto=tot.icbper() codigo="7152" nombre="ICBPER" internacional="OTH"/>
    </#if>
  </sac:SummaryDocumentsLine>
</SummaryDocuments>
<#macro tributo monto codigo nombre internacional tasa="">
    <cac:TaxTotal>
      <cbc:TaxAmount currencyID="${c.moneda()}">${monto}</cbc:TaxAmount>
      <cac:TaxSubtotal>
        <cbc:TaxAmount currencyID="${c.moneda()}">${monto}</cbc:TaxAmount>
        <cac:TaxCategory>
          <#-- 2992/3504: la tasa del IGV (18 o 10.5). -->
          <#if tasa?has_content><cbc:Percent>${tasa?string["0.##"]}</cbc:Percent></#if>
          <cac:TaxScheme>
            <cbc:ID>${codigo}</cbc:ID>
            <cbc:Name>${nombre}</cbc:Name>
            <cbc:TaxTypeCode>${internacional}</cbc:TaxTypeCode>
          </cac:TaxScheme>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
    </cac:TaxTotal>
</#macro>
