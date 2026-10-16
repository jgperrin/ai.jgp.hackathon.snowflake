package ai.jgp.hackathon.snowflake.store;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.store.StoreModel.SaveResult;
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

    @GetMapping("/{id}")
    Bundle load(@PathVariable String id) {
        if (!id.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid save id");
        }
        return store.load(id);
    }
}
