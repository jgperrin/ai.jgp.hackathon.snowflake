package ai.jgp.hackathon.snowflake.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.osi.OsiExporter;
import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.product.YamlWriter;
import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.ArtifactSummary;
import ai.jgp.hackathon.snowflake.store.StoreModel.SaveResult;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedSave;
import ai.jgp.hackathon.snowflake.store.StoreModel.StoredRow;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class StoreService {

    private final ArtifactStore store;
    private final YamlWriter yaml;
    private final StandardsValidator validator;
    private final JsonMapper json = JsonMapper.builder().build();

    StoreService(ArtifactStore store, YamlWriter yaml, StandardsValidator validator) {
        this.store = store;
        this.yaml = yaml;
        this.validator = validator;
    }

    /** Saves the product, every contract and the derived OSI/Apache Ossie model under one new save id. */
    public SaveResult save(Bundle bundle) {
        Map<String, Object> product = bundle.product();
        if (product == null || product.get("id") == null) {
            throw new IllegalArgumentException("Bundle has no product id");
        }
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("ODPS", product));
        bundle.contracts().forEach(c -> artifacts.add(artifact("ODCS", c)));
        Map<String, Object> osi = OsiExporter.export(product, bundle.contracts());
        artifacts.add(new Artifact(String.valueOf(osi.get("name")), "OSI", String.valueOf(osi.get("name")),
                OsiExporter.OSSIE_VERSION, null, yaml.write(osi), json.writeValueAsString(osi)));
        String saveId = UUID.randomUUID().toString();
        String savedAt = store.save(saveId, String.valueOf(product.get("id")), artifacts);
        return new SaveResult(saveId, String.valueOf(product.get("version")), savedAt);
    }

    private Artifact artifact(String kind, Map<String, Object> doc) {
        return new Artifact(String.valueOf(doc.get("id")), kind, String.valueOf(doc.get("name")),
                String.valueOf(doc.get("version")), String.valueOf(doc.get("status")), yaml.write(doc),
                json.writeValueAsString(doc));
    }

    public List<SavedProduct> latest() {
        return store.latest();
    }

    public List<SavedSave> saves() {
        return group(store.saves());
    }

    /** The artifacts of one save exactly as stored, YAML included; unknown saves are a bad request. */
    public List<Artifact> artifacts(String saveId) {
        List<Artifact> artifacts = store.load(saveId);
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("No saved product " + saveId);
        }
        return artifacts;
    }

    private static int kindRank(String kind) {
        return switch (kind == null ? "" : kind) {
            case "ODPS" -> 0;
            case "ODCS" -> 1;
            case "OSI" -> 2;
            default -> 3;
        };
    }

    /**
     * The store returns one row per artifact; this folds them into saves, newest first, each listing its
     * product, then its contracts by name, then the OSI model. A save's name and version are its product's.
     */
    static List<SavedSave> group(List<StoredRow> rows) {
        Map<String, List<StoredRow>> bySave = new LinkedHashMap<>();
        rows.forEach(r -> bySave.computeIfAbsent(r.saveId(), k -> new ArrayList<>()).add(r));
        return bySave.values().stream().map(saveRows -> {
            StoredRow first = saveRows.get(0);
            List<ArtifactSummary> artifacts = saveRows.stream().map(StoredRow::artifact)
                    .sorted(Comparator.comparingInt((ArtifactSummary a) -> kindRank(a.kind()))
                            .thenComparing(ArtifactSummary::name, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            ArtifactSummary product = artifacts.stream().filter(a -> "ODPS".equals(a.kind())).findFirst()
                    .orElse(null);
            return new SavedSave(first.saveId(), first.productId(), product == null ? null : product.name(),
                    product == null ? null : product.version(), first.savedAt(), first.savedBy(), artifacts);
        }).sorted(Comparator.comparing(SavedSave::savedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Bundle load(String saveId) {
        List<Artifact> artifacts = store.load(saveId);
        Map<String, Object> product = null;
        List<Map<String, Object>> contracts = new ArrayList<>();
        for (Artifact a : artifacts) {
            Map<String, Object> doc = json.readValue(a.json(), new TypeReference<Map<String, Object>>() {
            });
            if ("ODPS".equals(a.kind())) {
                product = doc;
            } else if ("ODCS".equals(a.kind())) {
                contracts.add(doc);
            }
        }
        if (product == null) {
            throw new IllegalArgumentException("No saved product " + saveId);
        }
        return new Bundle(product, contracts, validator.validate(product, contracts));
    }
}
