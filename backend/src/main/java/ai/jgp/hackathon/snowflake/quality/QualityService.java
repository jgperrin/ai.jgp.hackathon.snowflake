package ai.jgp.hackathon.snowflake.quality;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.quality.QualityModel.RuleResult;
import ai.jgp.hackathon.snowflake.quality.QualityModel.RunResult;

/** Runs every quality rule of every contract. One failing rule never stops the run. */
@Service
public class QualityService {

    private static final Logger log = LoggerFactory.getLogger(QualityService.class);

    private final SqlRunner runner;

    QualityService(SqlRunner runner) {
        this.runner = runner;
    }

    private record Check(String contractId, String database, String schema, String table, String column,
            Map<?, ?> rule) {
    }

    public RunResult run(Bundle bundle) {
        List<Check> checks = new ArrayList<>();
        for (Map<String, Object> contract : bundle.contracts()) {
            Map<?, ?> server = ((List<?>) contract.get("servers")).stream().map(s -> (Map<?, ?>) s).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Contract has no server"));
            String db = String.valueOf(server.get("database"));
            String schema = String.valueOf(server.get("schema"));
            String id = String.valueOf(contract.get("id"));
            for (Object o : list(contract.get("schema"))) {
                Map<?, ?> object = (Map<?, ?>) o;
                String table = String.valueOf(object.get("name"));
                for (Object r : list(object.get("quality"))) {
                    checks.add(new Check(id, db, schema, table, null, (Map<?, ?>) r));
                }
                for (Object p : list(object.get("properties"))) {
                    Map<?, ?> property = (Map<?, ?>) p;
                    for (Object r : list(property.get("quality"))) {
                        checks.add(new Check(id, db, schema, table, String.valueOf(property.get("name")),
                                (Map<?, ?>) r));
                    }
                }
            }
        }
        List<RuleResult> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<CompletableFuture<RuleResult>> futures = checks.stream()
                    .map(c -> CompletableFuture.supplyAsync(UserContext.wrap(() -> check(c)), pool)).toList();
            results = futures.stream().map(CompletableFuture::join).toList();
        }
        int passed = (int) results.stream().filter(RuleResult::passed).count();
        return new RunResult(results, passed, results.size() - passed);
    }

    private RuleResult check(Check c) {
        String label = label(c.rule(), c.column());
        String threshold = null;
        String sql = null;
        try {
            threshold = RuleCompiler.threshold(c.rule());
            sql = RuleCompiler.sql(c.rule(), c.database(), c.schema(), c.table(), c.column());
            Double observed = runner.scalar(sql);
            boolean passed = observed != null && RuleCompiler.passes(c.rule(), observed);
            return new RuleResult(c.contractId(), c.table(), c.column(), label, sql, passed, observed, threshold,
                    null);
        } catch (IllegalArgumentException | DataAccessException e) {
            log.info("Quality rule failed to run on {}.{}: {}", c.table(), c.column(), e.getMessage());
            String reason = e instanceof DataAccessException d ? d.getMostSpecificCause().getMessage() : e.getMessage();
            return new RuleResult(c.contractId(), c.table(), c.column(), label, sql, false, null, threshold, reason);
        }
    }

    static String label(Map<?, ?> rule, String column) {
        if (rule.get("description") instanceof String d && !d.isBlank()) {
            return d;
        }
        if ("sql".equals(rule.get("type"))) {
            return "SQL check";
        }
        return rule.get("metric") + (column == null ? "" : " on " + column);
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }
}
