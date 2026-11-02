<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<#import "comun.ftl" as u>
<#-- Comprobante de retención (20) o de percepción (40), #40: Retention / Perception UBL 2.0 con CustomizationID 1.0 (hojas Retenciones1_0 y Percepciones1_0; XSD
     UBLPE-Retention-1.0 y UBLPE-Perception-1.0). Los dos tienen la misma forma y solo cambian los nombres de sus elementos sac:, que llegan en {@code n}. El emisor es el agente
     (cac:AgentParty) y la contraparte el proveedor o el cliente (cac:ReceiverParty); no hay líneas de venta: cada documento pagado o cobrado lleva su pago, lo retenido o percibido
     y el neto, en soles. -->
<#assign a = c.agente()>
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<${n.raiz} xmlns="urn:sunat:names:specification:ubl:peru:schema:xsd:${n.raiz}-1"
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
  <#-- 2110/2111 y 2112/2113: UBL 2.0 y estructura 1.0. -->
  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>
  <cbc:CustomizationID>1.0</cbc:CustomizationID>
<@u.firma/>
  <#-- 1001, 1049: R###-n o P###-n, el mismo del nombre del archivo. -->
  <cbc:ID>${c.serie()}-${c.numero()?c}</cbc:ID>
  <cbc:IssueDate>${fechaEmision}</cbc:IssueDate>
  <#if horaEmision??><cbc:IssueTime>${horaEmision}</cbc:IssueTime></#if>
  <#-- El agente: RUC con schemeID 6 (1034, 2511, 2678), nombre comercial (2901), domicilio fiscal (2916–2917, 2902–2905; país PE, 2548) y razón social (1037, 1038). -->
  <cac:AgentParty>
    <cac:PartyIdentification><cbc:ID schemeID="6">${t.ruc()}</cbc:ID></cac:PartyIdentification>
    <#if t.nombreComercial()??><cac:PartyName><cbc:Name>${t.nombreComercial()}</cbc:Name></cac:PartyName></#if>
    <#if t.domicilio()??>
    <cac:PostalAddress>
      <cbc:ID>${t.domicilio().ubigeo()}</cbc:ID>
      <cbc:StreetName>${t.domicilio().direccion()}</cbc:StreetName>
      <#if t.domicilio().urbanizacion()??><cbc:CitySubdivisionName>${t.domicilio().urbanizacion()}</cbc:CitySubdivisionName></#if>
      <#if t.domicilio().provincia()??><cbc:CityName>${t.domicilio().provincia()}</cbc:CityName></#if>
      <#if t.domicilio().departamento()??><cbc:CountrySubentity>${t.domicilio().departamento()}</cbc:CountrySubentity></#if>
      <#if t.domicilio().distrito()??><cbc:District>${t.domicilio().distrito()}</cbc:District></#if>
      <cac:Country><cbc:IdentificationCode>${statics["pe.factura.domain.tenant.Domicilio"].PAIS}</cbc:IdentificationCode></cac:Country>
    </cac:PostalAddress>
    </#if>
    <cac:PartyLegalEntity><cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName></cac:PartyLegalEntity>
  </cac:AgentParty>
  <#-- El proveedor (retención: RUC, 2723, 2724, 2511) o el cliente (percepción: catálogo 06, 2679, 2680, 2516), su razón social (2133, 2134) y, si la hay, su dirección (2918/2919). -->
  <cac:ReceiverParty>
    <cac:PartyIdentification><cbc:ID schemeID="${c.receptor().tipoDoc()}">${c.receptor().numDoc()}</cbc:ID></cac:PartyIdentification>
    <#if c.receptor().direccion()??>
    <cac:PostalAddress>
      <cbc:StreetName>${c.receptor().direccion()}</cbc:StreetName>
      <cac:Country><cbc:IdentificationCode>PE</cbc:IdentificationCode></cac:Country>
    </cac:PostalAddress>
    </#if>
    <cac:PartyLegalEntity><cbc:RegistrationName>${c.receptor().razonSocial()}</cbc:RegistrationName></cac:PartyLegalEntity>
  </cac:ReceiverParty>
  <#-- Régimen (catálogo 23 o 22: 2618/2602) y su tasa (2619/2603). -->
  <sac:SUNAT${n.sistema}SystemCode>${a.regimen()}</sac:SUNAT${n.sistema}SystemCode>
  <sac:SUNAT${n.sistema}Percent>${a.tasaXml()}</sac:SUNAT${n.sistema}Percent>
  <#-- Importe total retenido o percibido (2669, 2628/2667), en soles (2728/2685); redondeo del total pagado o cobrado (3303, 3304) y ese total (2730/2687, 2629/2668). -->
  <cbc:TotalInvoiceAmount currencyID="PEN">${tot.total()}</cbc:TotalInvoiceAmount>
  <#if tot.tieneRedondeo()><cbc:PayableRoundingAmount currencyID="PEN">${tot.redondeo()}</cbc:PayableRoundingAmount></#if>
  <sac:SUNATTotal${n.total} currencyID="PEN">${c.importeNetoDeAgente()}</sac:SUNATTotal${n.total}>
  <#list a.documentos() as d>
  <sac:SUNAT${n.sistema}DocumentReference>
    <#-- Tipo (catálogo 01: 2691, 2692), serie-número (2693, 2694), fecha (2985 en la retención del 6 %), importe total y su moneda (2696). -->
    <cbc:ID schemeID="${d.tipo()}">${d.numero()}</cbc:ID>
    <cbc:IssueDate>${d.fechaEmision().toString()}</cbc:IssueDate>
    <cbc:TotalInvoiceAmount currencyID="${d.moneda()}">${d.importeTotal()}</cbc:TotalInvoiceAmount>
    <#-- El pago o cobro: número (2733/2697, 2734/2698, 2626), importe sin retención/percepción en la moneda del documento (2735/2699, 2736/2700, 2622/2607) y su fecha (2737/2702, 2625/2612). -->
    <cac:Payment>
      <cbc:ID>${d.numeroPago()?c}</cbc:ID>
      <cbc:PaidAmount currencyID="${d.moneda()}">${d.importePago()}</cbc:PaidAmount>
      <cbc:PaidDate>${d.fechaPago().toString()}</cbc:PaidDate>
    </cac:Payment>
    <#-- Lo retenido o percibido (2740/2705, 2623/2608, ±1), su fecha y el neto pagado o cobrado (2746/2711), los tres en soles (2742/2707, 2748/2713). -->
    <sac:SUNAT${n.sistema}Information>
      <sac:SUNAT${n.sistema}Amount currencyID="PEN">${d.importeDelAgente(a.tasa())}</sac:SUNAT${n.sistema}Amount>
      <sac:SUNAT${n.sistema}Date>${d.fechaDelAgente().toString()}</sac:SUNAT${n.sistema}Date>
      <sac:SUNATNetTotal${n.neto} currencyID="PEN">${d.neto(c.tipo(), a.tasa())}</sac:SUNATNetTotal${n.neto}>
      <#-- Fuera de soles, el tipo de cambio: moneda del documento (2719, 2749) a PEN (2715), factor n(4,6) (2721, 2716) y su fecha (2722). -->
      <#if !d.enSoles()>
      <cac:ExchangeRate>
        <cbc:SourceCurrencyCode>${d.moneda()}</cbc:SourceCurrencyCode>
        <cbc:TargetCurrencyCode>PEN</cbc:TargetCurrencyCode>
        <cbc:CalculationRate>${d.tipoCambio()?c}</cbc:CalculationRate>
        <cbc:Date>${d.fechaTipoCambio().toString()}</cbc:Date>
      </cac:ExchangeRate>
      </#if>
    </sac:SUNAT${n.sistema}Information>
  </sac:SUNAT${n.sistema}DocumentReference>
  </#list>
</${n.raiz}>
