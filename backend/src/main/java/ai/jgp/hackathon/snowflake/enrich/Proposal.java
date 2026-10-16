package ai.jgp.hackathon.snowflake.enrich;

/**
 * One suggestion from Cortex, waiting for a human to accept, edit or reject it.
 *
 * @param column null for table-level proposals (and for SLAs)
 * @param kind   description, businessName, classification, tag, qualityRule or sla
 * @param value  a string, or an ODCS quality / SLA object
 */
public record Proposal(String id, String contractId, String table, String column, String kind, Object value,
        String rationale) {

    public static final String DESCRIPTION = "description";
    public static final String BUSINESS_NAME = "businessName";
    public static final String CLASSIFICATION = "classification";
    public static final String TAG = "tag";
    public static final String QUALITY_RULE = "qualityRule";
    public static final String SLA = "sla";
}
