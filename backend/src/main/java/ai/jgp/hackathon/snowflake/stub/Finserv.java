package ai.jgp.hackathon.snowflake.stub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ForeignKey;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;

/**
 * An offline stand-in shaped like the hackathon's FINSERV_DW database, used by every stub
 * under the 'local' profile so the UI can be demoed without a Snowflake account.
 */
public final class Finserv {

    public static final String DATABASE = "FINSERV_DW";

    /** schema → tables, in display order. */
    public static final Map<String, List<TableInfo>> SCHEMAS = new LinkedHashMap<>();

    static {
        SCHEMAS.put("CORE", List.of(
                table("COMPANIES", 15, "Financial firms",
                        pk("COMPANY_ID", "NUMBER"), col("COMPANY_NAME", "TEXT", false),
                        col("COMPANY_TYPE", "TEXT", true), col("HEADQUARTERS_STATE", "TEXT", true),
                        col("FOUNDED_YEAR", "NUMBER", true)),
                table("CUSTOMERS", 50_000, null,
                        pk("CUSTOMER_ID", "NUMBER"), col("COMPANY_ID", "NUMBER", false),
                        col("FIRST_NAME", "TEXT", false), col("LAST_NAME", "TEXT", false),
                        col("EMAIL", "TEXT", true), col("PHONE", "TEXT", true),
                        col("DATE_OF_BIRTH", "DATE", true), col("CUSTOMER_TIER", "TEXT", false),
                        col("CREDIT_SCORE", "NUMBER", true), col("CREATED_AT", "TIMESTAMP_NTZ", false)),
                table("ACCOUNTS", 85_000, null,
                        pk("ACCOUNT_ID", "NUMBER"), col("CUSTOMER_ID", "NUMBER", false),
                        col("ACCOUNT_TYPE", "TEXT", false), money("BALANCE"),
                        col("OPENED_DATE", "DATE", false), col("STATUS", "TEXT", false))));
        SCHEMAS.put("TRANSACTIONS", List.of(
                table("TRANSACTIONS", 200_000, null,
                        pk("TRANSACTION_ID", "NUMBER"), col("ACCOUNT_ID", "NUMBER", false),
                        col("TRANSACTION_TYPE", "TEXT", false), money("AMOUNT"),
                        col("MERCHANT_NAME", "TEXT", true), col("TRANSACTION_DATE", "TIMESTAMP_NTZ", false))));
        SCHEMAS.put("INSURANCE", List.of(
                table("POLICIES", 35_000, null,
                        pk("POLICY_ID", "NUMBER"), col("CUSTOMER_ID", "NUMBER", false),
                        col("POLICY_TYPE", "TEXT", false), money("PREMIUM_AMOUNT"), money("COVERAGE_AMOUNT"),
                        col("START_DATE", "DATE", false), col("END_DATE", "DATE", true),
                        col("RISK_LEVEL", "TEXT", false)),
                table("CLAIMS", 14_000, null,
                        pk("CLAIM_ID", "NUMBER"), col("POLICY_ID", "NUMBER", false),
                        col("INCIDENT_DATE", "DATE", false), col("REPORTED_DATE", "DATE", false),
                        money("CLAIM_AMOUNT"), col("CLAIM_STATUS", "TEXT", false))));
        SCHEMAS.put("RISK", List.of(
                table("RISK_ASSESSMENTS", 50_000, null,
                        pk("ASSESSMENT_ID", "NUMBER"), col("CUSTOMER_ID", "NUMBER", false),
                        col("RISK_SCORE", "NUMBER", false), col("ASSESSED_AT", "TIMESTAMP_NTZ", false)),
                table("FRAUD_ALERTS", 8_000, null,
                        pk("ALERT_ID", "NUMBER"), col("CUSTOMER_ID", "NUMBER", false),
                        col("TRANSACTION_ID", "NUMBER", true), col("ALERT_TYPE", "TEXT", false),
                        col("SEVERITY", "TEXT", false), col("CREATED_AT", "TIMESTAMP_NTZ", false))));
        SCHEMAS.put("ANALYTICS", List.of(
                view("CUSTOMER_360", "Customer lifetime value",
                        col("CUSTOMER_ID", "NUMBER", false), col("CUSTOMER_TIER", "TEXT", false),
                        col("ACCOUNT_COUNT", "NUMBER", true), money("TOTAL_BALANCE"), money("LIFETIME_VALUE")),
                view("POLICY_PERFORMANCE", "Claims ratio by policy type",
                        col("POLICY_TYPE", "TEXT", false), col("POLICY_COUNT", "NUMBER", true),
                        col("CLAIM_COUNT", "NUMBER", true), col("CLAIMS_RATIO", "FLOAT", true))));
    }

    private Finserv() {
    }

    private static TableInfo table(String name, long rows, String comment, ColumnInfo... columns) {
        return new TableInfo(name, "TABLE", rows, comment, numbered(columns), List.<ForeignKey>of());
    }

    private static TableInfo view(String name, String comment, ColumnInfo... columns) {
        return new TableInfo(name, "VIEW", null, comment, numbered(columns), List.<ForeignKey>of());
    }

    private static List<ColumnInfo> numbered(ColumnInfo... columns) {
        List<ColumnInfo> out = new ArrayList<>();
        for (int i = 0; i < columns.length; i++) {
            ColumnInfo c = columns[i];
            out.add(new ColumnInfo(c.name(), c.dataType(), c.nullable(), c.comment(), i + 1, c.primaryKey(),
                    c.primaryKeyPosition(), c.numericPrecision(), c.numericScale(), c.maxLength()));
        }
        return out;
    }

    private static ColumnInfo pk(String name, String type) {
        return new ColumnInfo(name, type, false, null, 0, true, 1, 38, 0, null);
    }

    private static ColumnInfo money(String name) {
        return new ColumnInfo(name, "NUMBER", false, null, 0, false, null, 18, 2, null);
    }

    private static ColumnInfo col(String name, String type, boolean nullable) {
        boolean number = "NUMBER".equals(type);
        boolean text = "TEXT".equals(type);
        return new ColumnInfo(name, type, nullable, null, 0, false, null,
                number ? 38 : null, number ? 0 : null, text ? 16_777_216 : null);
    }
}
