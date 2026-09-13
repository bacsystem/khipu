rootProject.name = "factura"
include(
    "domain", "application", "bootstrap",
    "adapters:in-rest", "adapters:in-scheduler",
    "adapters:out-ubl", "adapters:out-signing", "adapters:out-sunat-soap",
    "adapters:out-storage", "adapters:out-persistence", "adapters:out-crypto"
)
