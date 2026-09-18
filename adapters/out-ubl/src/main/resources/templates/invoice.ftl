<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<#-- Categoría (catálogo 05, UN/ECE 5305) y tributo (UN/ECE 5153) de una afectación: mismo bloque en los subtotales globales y en cada línea. -->
<#macro categoriaTributo tr>
<cbc:ID schemeID="UN/ECE 5305" schemeName="Tax Category Identifier" schemeAgencyName="United Nations Economic Commission for Europe">${tr.categoria()}</cbc:ID>
<#nested>
<cac:TaxScheme>
  <cbc:ID schemeID="UN/ECE 5153" schemeName="Tax Scheme Identifier" schemeAgencyName="United Nations Economic Commission for Europe">${tr.codigo()}</cbc:ID>
  <cbc:Name>${tr.nombre()}</cbc:Name>
  <cbc:TaxTypeCode>${tr.tipoInternacional()}</cbc:TaxTypeCode>
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
  <#if tot.tieneGratuitas()>
  <cbc:Note languageLocaleID="1002">TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE</cbc:Note>
  </#if>
  <#if c.detraccion()??>
  <cbc:Note languageLocaleID="2006">OPERACIÓN SUJETA AL SISTEMA DE PAGO DE OBLIGACIONES TRIBUTARIAS - SPOT</cbc:Note>
  </#if>
  <#if c.percepcion()??>
  <cbc:Note languageLocaleID="2000">COMPROBANTE DE PERCEPCIÓN</cbc:Note>
  </#if>
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
  <#-- Detracción (SPOT, reglas 3033–3037, 3127–3129, 3174, 3208): cuenta BN en PaymentMeans y bien/servicio, % y monto en PEN en PaymentTerms. -->
  <#if c.detraccion()??>
  <cac:PaymentMeans>
    <cbc:ID>Detraccion</cbc:ID>
    <cbc:PaymentMeansCode listName="Medio de pago" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo59">${c.detraccion().medioPago()}</cbc:PaymentMeansCode>
    <cac:PayeeFinancialAccount><cbc:ID>${c.detraccion().cuentaBancoNacion()}</cbc:ID></cac:PayeeFinancialAccount>
  </cac:PaymentMeans>
  <cac:PaymentTerms>
    <cbc:ID>Detraccion</cbc:ID>
    <cbc:PaymentMeansID schemeName="Codigo de detraccion" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo54">${c.detraccion().codigoBienServicio()}</cbc:PaymentMeansID>
    <cbc:PaymentPercent>${c.detraccion().porcentaje()?string["0.#####"]}</cbc:PaymentPercent>
    <cbc:Amount currencyID="PEN">${c.detraccion().monto()}</cbc:Amount>
  </cac:PaymentTerms>
  </#if>
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
  <#if c.percepcion()??>
  <cac:PaymentTerms>
    <cbc:ID>Percepcion</cbc:ID>
    <cbc:Amount currencyID="PEN">${c.percepcion().totalConPercepcion(tot.total())}</cbc:Amount>
  </cac:PaymentTerms>
  </#if>
  <#-- Retención del IGV (62, reglas 3262–3264) y percepción (51/52/53, reglas 2788–2798, 3233): AllowanceCharge globales informativos. -->
  <#if c.retencion()??>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">62</cbc:AllowanceChargeReasonCode>
    <cbc:MultiplierFactorNumeric>${c.retencion().factor()?string["0.00000"]}</cbc:MultiplierFactorNumeric>
    <cbc:Amount currencyID="${c.moneda()}">${c.retencion().monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="${c.moneda()}">${tot.total()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#if>
  <#if c.percepcion()??>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${c.percepcion().regimen()}</cbc:AllowanceChargeReasonCode>
    <cbc:MultiplierFactorNumeric>${c.percepcion().factor()?string["0.00000"]}</cbc:MultiplierFactorNumeric>
    <cbc:Amount currencyID="PEN">${c.percepcion().monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="PEN">${c.percepcion().base()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#if>
  <#-- Descuento global (catálogo 53: 02 afecta la base del IGV, 03 no). Reglas 3072, 3025, 2968, 3016. -->
  <#if tot.descuentoGlobal()??>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${tot.descuentoGlobal().codigo()}</cbc:AllowanceChargeReasonCode>
    <#if tot.descuentoGlobal().factor().isPresent()><cbc:MultiplierFactorNumeric>${tot.descuentoGlobal().factor().get()?string["0.00000"]}</cbc:MultiplierFactorNumeric></#if>
    <cbc:Amount currencyID="${c.moneda()}">${tot.descuentoGlobal().monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="${c.moneda()}">${tot.descuentoGlobal().base()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#if>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv() + tot.isc() + tot.icbper()}</cbc:TaxAmount>
    <#list tot.subtotales() as st>
    <cac:TaxSubtotal>
      <#if st.tributo().codigo() != "7152"><cbc:TaxableAmount currencyID="${c.moneda()}">${st.base()}</cbc:TaxableAmount></#if>
      <cbc:TaxAmount currencyID="${c.moneda()}">${st.impuesto()}</cbc:TaxAmount>
      <cac:TaxCategory>
        <@categoriaTributo tr=st.tributo()/>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
    </#list>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${tot.totalValorVenta()}</cbc:LineExtensionAmount>
    <cbc:TaxInclusiveAmount currencyID="${c.moneda()}">${tot.totalPrecioVenta()}</cbc:TaxInclusiveAmount>
    <#if (tot.totalDescuentos() > 0)>
    <cbc:AllowanceTotalAmount currencyID="${c.moneda()}">${tot.totalDescuentos()}</cbc:AllowanceTotalAmount>
    </#if>
    <cbc:PayableAmount currencyID="${c.moneda()}">${tot.total()}</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <#list tot.items() as it>
  <cac:InvoiceLine>
    <cbc:ID>${(it?index + 1)?c}</cbc:ID>
    <cbc:InvoicedQuantity unitCode="${it.item().unidad()}" unitCodeListID="UN/ECE rec 20" unitCodeListAgencyName="United Nations Economic Commission for Europe">${it.item().cantidad()?string["0.####"]}</cbc:InvoicedQuantity>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:LineExtensionAmount>
    <cac:PricingReference>
      <cac:AlternativeConditionPrice>
        <cbc:PriceAmount currencyID="${c.moneda()}">${it.precioVentaUnitario()?string["0.0000000000"]}</cbc:PriceAmount>
        <cbc:PriceTypeCode listName="Tipo de Precio" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16">${it.tipoPrecio()}</cbc:PriceTypeCode>
      </cac:AlternativeConditionPrice>
    </cac:PricingReference>
    <#if it.item().tieneDescuento()>
    <cac:AllowanceCharge>
      <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
      <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${it.item().descuento().codigoSunat(false)}</cbc:AllowanceChargeReasonCode>
      <#if it.descuentoFactor().isPresent()><cbc:MultiplierFactorNumeric>${it.descuentoFactor().get()?string["0.00000"]}</cbc:MultiplierFactorNumeric></#if>
      <cbc:Amount currencyID="${c.moneda()}">${it.descuento()}</cbc:Amount>
      <cbc:BaseAmount currencyID="${c.moneda()}">${it.baseBruta()}</cbc:BaseAmount>
    </cac:AllowanceCharge>
    </#if>
    <cac:TaxTotal>
      <cbc:TaxAmount currencyID="${c.moneda()}">${it.totalTributos()}</cbc:TaxAmount>
      <#-- ISC (2000): base = valor de venta, TierRange = sistema (catálogo 08); reglas 3108, 2373. -->
      <#if it.tieneIsc()>
      <cac:TaxSubtotal>
        <cbc:TaxableAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:TaxableAmount>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.isc()}</cbc:TaxAmount>
        <cac:TaxCategory>
          <@categoriaTributo tr=statics["pe.factura.domain.documento.Tributo"].ISC>
          <cbc:Percent>${it.iscPorcentaje()?string["0.00###"]}</cbc:Percent>
          <cbc:TierRange>${it.item().isc().sistema()}</cbc:TierRange>
          </@categoriaTributo>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
      </#if>
      <#-- ICBPER (7152): sin base ni tasa; bolsas = cantidad del ítem y monto unitario vigente (reglas 3236–3238). -->
      <#if it.tieneIcbper()>
      <cac:TaxSubtotal>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.icbper()}</cbc:TaxAmount>
        <cbc:BaseUnitMeasure unitCode="NIU">${it.item().cantidad()?string["0"]}</cbc:BaseUnitMeasure>
        <cac:TaxCategory>
          <@categoriaTributo tr=statics["pe.factura.domain.documento.Tributo"].ICBPER>
          <cbc:PerUnitAmount currencyID="${c.moneda()}">${it.icbperUnitario()}</cbc:PerUnitAmount>
          </@categoriaTributo>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
      </#if>
      <cac:TaxSubtotal>
        <cbc:TaxableAmount currencyID="${c.moneda()}">${it.baseIgv()}</cbc:TaxableAmount>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.igv()}</cbc:TaxAmount>
        <cac:TaxCategory>
          <@categoriaTributo tr=it.tributo()>
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
