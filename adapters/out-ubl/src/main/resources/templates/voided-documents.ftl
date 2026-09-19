<#ftl output_format="XML" strip_whitespace=true>
<#setting locale="en_US">
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<#-- Comunicación de baja (hoja "Comunicación de Baja1_0"): UBL 2.0, CustomizationID 1.0, un RA por comprobante (regla 2375: ReferenceDate única). -->
<VoidedDocuments xmlns="urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1"
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
  <cbc:CustomizationID>1.0</cbc:CustomizationID>
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
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <sac:VoidedDocumentsLine>
    <cbc:LineID>1</cbc:LineID>
    <cbc:DocumentTypeCode>${b.tipoComprobante().codigo()}</cbc:DocumentTypeCode>
    <sac:DocumentSerialID>${b.serie()}</sac:DocumentSerialID>
    <sac:DocumentNumberID>${b.numero()?c}</sac:DocumentNumberID>
    <sac:VoidReasonDescription>${b.motivo()}</sac:VoidReasonDescription>
  </sac:VoidedDocumentsLine>
</VoidedDocuments>
