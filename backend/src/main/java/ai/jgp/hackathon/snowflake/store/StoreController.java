package ai.jgp.hackathon.snowflake.store;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.SaveResult;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedSave;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;

@RestController
@RequestMapping("/api/products")
class StoreController {

    private final StoreService store;

    StoreController(StoreService store) {
        this.store = store;
    }

    @PostMapping("/save")
    SaveResult save(@RequestBody Bundle bundle) {
        return store.save(bundle);
    }

    @GetMapping
    List<SavedProduct> latest() {
        return store.latest();
    }

    /** Every save in the table, all kinds; the extra segment keeps it clear of the {id} route. */
    @GetMapping("/saves")
    List<SavedSave> saves() {
        return store.saves();
    }

    @GetMapping("/{id}")
    Bundle load(@PathVariable String id) {
        return store.load(saveId(id));
    }

    /** One save's artifacts as stored, YAML included. */
    @GetMapping("/{id}/artifacts")
    List<Artifact> artifacts(@PathVariable String id) {
        return store.artifacts(saveId(id));
    }

    private static String saveId(String id) {
        if (!id.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid save id");
        }
        return id;
    }
}
