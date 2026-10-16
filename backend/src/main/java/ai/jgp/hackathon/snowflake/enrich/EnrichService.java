package ai.jgp.hackathon.snowflake.enrich;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.cortex.CortexClient;
import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ProfileRequest;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileService;

/** Profile each table, ask Cortex once per table (a few in parallel), parse into proposals. */
@Service
public class EnrichService {

    private static final Logger log = LoggerFactory.getLogger(EnrichService.class);

    /** Matches the connection pool size, so Cortex calls never queue for a connection. */
    private static final int PARALLEL_CALLS = 4;

    private final ProfileService profiles;
    private final CortexClient cortex;
    private final StandardsValidator validator;

    EnrichService(ProfileService profiles, CortexClient cortex, StandardsValidator validator) {
        this.profiles = profiles;
        this.cortex = cortex;
        this.validator = validator;
    }

    public record EnrichResult(List<Proposal> proposals, List<String> warnings) {
    }

    private record Job(String contractId, String database, String schema, TableProfile table) {
    }

    public EnrichResult enrich(Bundle bundle) {
        List<Job> jobs = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger();
        List<Proposal> keys = new ArrayList<>();
        for (Map<String, Object> contract : bundle.contracts()) {
            Map<?, ?> server = server(contract);
            String database = String.valueOf(server.get("database"));
            String schema = String.valueOf(server.get("schema"));
            List<String> tables = ((List<?>) contract.get("schema")).stream()
                    .map(o -> String.valueOf(((Map<?, ?>) o).get("name"))).toList();
            for (TableProfile t : profiles.profile(new ProfileRequest(database, schema, tables)).tables()) {
                String contractId = String.valueOf(contract.get("id"));
                jobs.add(new Job(contractId, database, schema, t));
                Map<?, ?> object = ((List<?>) contract.get("schema")).stream().map(o -> (Map<?, ?>) o)
                        .filter(o -> t.table().equals(o.get("name"))).findFirst().orElse(null);
                KeyCandidates.propose(contractId, t, object, ids).ifPresent(keys::add);
            }
        }
        List<Proposal> proposals = new ArrayList<>(keys);
        List<String> warnings = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_CALLS)) {
            List<CompletableFuture<AnswerParser.Result>> futures = jobs.stream()
                    .map(job -> CompletableFuture.supplyAsync(UserContext.wrap(() -> run(job, ids)), pool)).toList();
            for (CompletableFuture<AnswerParser.Result> f : futures) {
                AnswerParser.Result r = f.join();
                proposals.addAll(r.proposals());
                warnings.addAll(r.warnings());
            }
        }
        return new EnrichResult(proposals, warnings);
    }

    private AnswerParser.Result run(Job job, AtomicInteger ids) {
        Set<String> columns = job.table().columns().stream().map(ColumnProfile::column).collect(Collectors.toSet());
        try {
            String prompt = PromptBuilder.build(job.database(), job.schema(), job.table());
            String answer = cortex.complete(prompt);
            if (AnswerParser.json(answer) == null) {
                log.warn("Unparseable Cortex answer for {} ({} chars, ends with: {}); retrying once",
                        job.table().table(), answer == null ? 0 : answer.length(), tail(answer));
                answer = cortex.complete(prompt);
            }
            return AnswerParser.parse(job.contractId(), job.table().table(), columns, answer, ids);
        } catch (RuntimeException e) {
            log.warn("Cortex failed for {}", job.table().table(), e);
            return new AnswerParser.Result(List.of(),
                    List.of(job.table().table() + ": Cortex call failed: " + e.getMessage()));
        }
    }

    private static String tail(String s) {
        return s == null ? "null" : s.substring(Math.max(0, s.length() - 200));
    }

    public Bundle apply(Bundle bundle, List<Proposal> accepted) {
        List<Map<String, Object>> contracts = ProposalApplier.apply(bundle.contracts(), accepted);
        return new Bundle(bundle.product(), contracts, validator.validate(bundle.product(), contracts));
    }

    static Map<?, ?> server(Map<String, Object> contract) {
        if (contract.get("servers") instanceof List<?> servers && !servers.isEmpty()
                && servers.get(0) instanceof Map<?, ?> server) {
            return server;
        }
        throw new IllegalArgumentException("Contract " + contract.get("id") + " has no Snowflake server");
    }
}
