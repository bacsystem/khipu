package pe.factura.domain.plan;

/**
 * Lo que un plan limita además de los documentos del mes (#498): cuántas API keys activas, usuarios y RUCs puede tener la cuenta. Cada uno sabe decir su cantidad en
 * palabras y cómo se libera un lugar, para que el rechazo diga qué hacer y no solo que no.
 */
public enum RecursoDelPlan {
    API_KEYS("API key", "API keys", " activa", " activas", "Revoca una que ya no uses, o pide un cambio de plan"),
    USUARIOS("usuario", "usuarios", " (activo o con invitación pendiente)", " (activos o con invitación pendiente)",
            "Desactiva a alguien o revoca una invitación pendiente, o pide un cambio de plan"),
    RUCS("RUC", "RUCs", "", "", "Pide un cambio de plan para sumar otra empresa");

    private final String singular;
    private final String plural;
    private final String loQueCuentaSingular;
    private final String loQueCuentaPlural;
    private final String paraLiberar;

    RecursoDelPlan(String singular, String plural, String loQueCuentaSingular, String loQueCuentaPlural, String paraLiberar) {
        this.singular = singular;
        this.plural = plural;
        this.loQueCuentaSingular = loQueCuentaSingular;
        this.loQueCuentaPlural = loQueCuentaPlural;
        this.paraLiberar = paraLiberar;
    }

    /** «1 API key», «2 API keys». */
    public String cantidad(long n) { return n + " " + (n == 1 ? singular : plural); }

    /** Lo que cuenta como usado, para que no sorprenda, concordando con la cantidad: «1 API key activa», «2 API keys activas». Vacío si no hace falta aclararlo. */
    public String loQueCuenta(long n) { return n == 1 ? loQueCuentaSingular : loQueCuentaPlural; }

    /** Qué hacer para recuperar un lugar. */
    public String paraLiberar() { return paraLiberar; }
}
