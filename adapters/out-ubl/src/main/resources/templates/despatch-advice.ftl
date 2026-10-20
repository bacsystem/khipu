<#ftl output_format="XML" strip_whitespace=true>
<#setting locale="en_US">
<#import "comun.ftl" as u>
<#--
  Guía de remisión electrónica (#39): UBL 2.1 DespatchAdvice, remitente (09) o transportista (31). El orden de los elementos es el del XSD UBL 2.1 (se
  valida antes de guardar). Los nombres de los nodos y atributos SUNAT siguen los que citan las reglas de «CódigosRetorno» (cac:CarrierParty 4160–4165,
  cac:DriverPerson 2571 y 4171–4174, cac:DeliveryAddress 4175–4179, cac:TransportHandlingUnit 4166–4168, cbc:GrossWeightMeasure 4154) y la GRE 2022
  de las implementaciones conocidas: el repositorio no tiene la guía de elaboración del XML de la GRE, y no hubo una corrida contra SUNAT (ver
  deploy/README.md §14). El modelo trae g (GuiaRemision), t (el emisor), tr (el traslado) y remitente / transportista (los datos de cada tipo).
-->
<#macro punto p>
      <cbc:ID schemeAgencyName="PE:INEI" schemeName="Ubigeos">${p.ubigeo()}</cbc:ID>
      <#-- Establecimiento anexo (3365–3371, 3410): el código y, en el listID, el RUC al que pertenece. -->
      <#if p.codigoEstablecimiento()??><cbc:AddressTypeCode listAgencyName="PE:SUNAT" listName="Establecimientos anexos" listID="${p.rucEstablecimiento()}">${p.codigoEstablecimiento()}</cbc:AddressTypeCode></#if>
      <cac:AddressLine><cbc:Line>${p.direccion()}</cbc:Line></cac:AddressLine>
</#macro>
<#macro documento tipo numero>
  <cbc:ID schemeID="${tipo}" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${numero}</cbc:ID>
</#macro>
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<DespatchAdvice xmlns="urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2"
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
  <cbc:ID>${g.serie()}-${g.numero()?c}</cbc:ID>
  <cbc:IssueDate>${fechaEmision}</cbc:IssueDate>
  <cbc:IssueTime>${horaEmision}</cbc:IssueTime>
  <#-- 1051: tipo de guía del catálogo 01 (09 remitente, 31 transportista). -->
  <cbc:DespatchAdviceTypeCode listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${g.tipo().codigo()}</cbc:DespatchAdviceTypeCode>
  <#if g.observaciones()??><cbc:Note>${g.observaciones()}</cbc:Note></#if>
  <#-- Documentos relacionados con el traslado (catálogo 61; 3340, 3441, 3445): la factura de la venta, la guía remitente, la DAM… -->
  <#list g.documentos() as d>
  <cac:AdditionalDocumentReference>
    <cbc:ID>${d.numero()}</cbc:ID>
    <cbc:DocumentTypeCode listAgencyName="PE:SUNAT" listName="Documento relacionado al transporte" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo61">${d.tipo()}</cbc:DocumentTypeCode>
    <cbc:DocumentType>${d.descripcionTipo()}</cbc:DocumentType>
    <#if d.rucEmisor()??>
    <cac:IssuerParty>
      <cac:PartyIdentification><@documento tipo="6" numero=d.rucEmisor()/></cac:PartyIdentification>
    </cac:IssuerParty>
    </#if>
  </cac:AdditionalDocumentReference>
  </#list>
<@u.firma/>
  <#-- El emisor: el remitente en la 09, el transportista en la 31 (con su registro MTC, 4391–4392). -->
  <cac:DespatchSupplierParty>
    <cac:Party>
      <cac:PartyIdentification><@documento tipo="6" numero=t.ruc()/></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
        <#if transportista??><cbc:CompanyID>${transportista.registroMtc()}</cbc:CompanyID></#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:DespatchSupplierParty>
  <#-- Destinatario (2554, 2555, 3417). -->
  <cac:DeliveryCustomerParty>
    <cac:Party>
      <cac:PartyIdentification><@documento tipo=g.destinatario().tipoDoc() numero=g.destinatario().numDoc()/></cac:PartyIdentification>
      <cac:PartyLegalEntity><cbc:RegistrationName>${g.destinatario().nombre()}</cbc:RegistrationName></cac:PartyLegalEntity>
    </cac:Party>
  </cac:DeliveryCustomerParty>
  <cac:Shipment>
    <cbc:ID>SUNAT_Envio</cbc:ID>
    <#-- Motivo del catálogo 20 y su descripción (3404, 3405, 3457, 4190): solo la remitente. -->
    <#if remitente??>
    <cbc:HandlingCode listAgencyName="PE:SUNAT" listName="Motivo de traslado" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo20">${remitente.motivo()}</cbc:HandlingCode>
    <cbc:HandlingInstructions>${remitente.descripcionMotivo()}</cbc:HandlingInstructions>
    </#if>
    <#-- Peso bruto total y su unidad (2880, 2881). -->
    <cbc:GrossWeightMeasure unitCode="${tr.unidadPeso()}">${tr.pesoBruto()?string["0.###"]}</cbc:GrossWeightMeasure>
    <#list indicadores as i><cbc:SpecialInstructions>${i.valorXml()}</cbc:SpecialInstructions></#list>
    <cac:ShipmentStage>
      <#-- Modalidad del catálogo 18: solo la remitente. -->
      <#if remitente??><cbc:TransportModeCode listName="Modalidad de traslado" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo18">${remitente.modalidad().codigo()}</cbc:TransportModeCode></#if>
      <#-- Fecha de inicio del traslado (3343, 3406). -->
      <cac:TransitPeriod><cbc:StartDate>${tr.fechaInicio().toString()}</cbc:StartDate></cac:TransitPeriod>
      <#-- Empresa de transporte en transporte público (2534, 4160–4165, 4392). -->
      <#if remitente?? && remitente.transportista()??>
      <#assign e = remitente.transportista()>
      <cac:CarrierParty>
        <cac:PartyIdentification><@documento tipo="6" numero=e.ruc()/></cac:PartyIdentification>
        <cac:PartyLegalEntity>
          <cbc:RegistrationName>${e.razonSocial()}</cbc:RegistrationName>
          <#if e.registroMtc()??><cbc:CompanyID>${e.registroMtc()}</cbc:CompanyID></#if>
        </cac:PartyLegalEntity>
      </cac:CarrierParty>
      </#if>
      <#-- Conductores, el principal primero (2568–2573, 3357–3362, 4376). -->
      <#list tr.conductores() as c>
      <cac:DriverPerson>
        <@documento tipo=c.tipoDoc() numero=c.numDoc()/>
        <cbc:FirstName>${c.nombres()}</cbc:FirstName>
        <cbc:FamilyName>${c.apellidos()}</cbc:FamilyName>
        <cbc:JobTitle>${(c?index == 0)?then("Principal", "Secundario")}</cbc:JobTitle>
        <cac:IdentityDocumentReference><cbc:ID>${c.licencia()}</cbc:ID></cac:IdentityDocumentReference>
      </cac:DriverPerson>
      </#list>
    </cac:ShipmentStage>
    <cac:Delivery>
      <#-- Punto de llegada (1074, 2574, 3368–3371, 4179). -->
      <cac:DeliveryAddress>
<@punto p=tr.llegada()/>
      </cac:DeliveryAddress>
      <cac:Despatch>
        <#-- Punto de partida (1075, 2577, 3363–3367, 4184). -->
        <cac:DespatchAddress>
<@punto p=tr.partida()/>
        </cac:DespatchAddress>
        <#-- En la guía transportista, el remitente de los bienes (3383–3387). -->
        <#if transportista??>
        <cac:DespatchParty>
          <cac:PartyIdentification><@documento tipo=transportista.remitente().tipoDoc() numero=transportista.remitente().numDoc()/></cac:PartyIdentification>
          <cac:PartyLegalEntity><cbc:RegistrationName>${transportista.remitente().nombre()}</cbc:RegistrationName></cac:PartyLegalEntity>
        </cac:DespatchParty>
        </#if>
      </cac:Despatch>
    </cac:Delivery>
    <#-- Vehículos: el principal y hasta dos secundarios (2566, 2567, 4389). -->
    <#if tr.conVehiculos()>
    <cac:TransportHandlingUnit>
      <cac:TransportEquipment>
        <cbc:ID>${tr.vehiculos()[0].placa()}</cbc:ID>
        <#list tr.vehiculos()[1..] as v>
        <cac:AttachedTransportEquipment><cbc:ID>${v.placa()}</cbc:ID></cac:AttachedTransportEquipment>
        </#list>
      </cac:TransportEquipment>
    </cac:TransportHandlingUnit>
    </#if>
  </cac:Shipment>
  <#list g.items() as it>
  <cac:DespatchLine>
    <cbc:ID>${(it?index + 1)?c}</cbc:ID>
    <cbc:DeliveredQuantity unitCode="${it.unidad()}" unitCodeListID="UN/ECE rec 20" unitCodeListAgencyName="United Nations Economic Commission for Europe">${it.cantidad()?string["0.##########"]}</cbc:DeliveredQuantity>
    <cac:OrderLineReference><cbc:LineID>${(it?index + 1)?c}</cbc:LineID></cac:OrderLineReference>
    <cac:Item>
      <cbc:Description>${it.descripcion()}</cbc:Description>
      <#if it.codigo()??><cac:SellersItemIdentification><cbc:ID>${it.codigo()}</cbc:ID></cac:SellersItemIdentification></#if>
      <#if it.codigoSunat()??><cac:CommodityClassification><cbc:ItemClassificationCode listID="UNSPSC" listAgencyName="GS1 US" listName="Item Classification">${it.codigoSunat()}</cbc:ItemClassificationCode></cac:CommodityClassification></#if>
    </cac:Item>
  </cac:DespatchLine>
  </#list>
</DespatchAdvice>
