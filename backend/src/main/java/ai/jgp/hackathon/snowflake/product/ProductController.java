package ai.jgp.hackathon.snowflake.product;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
class ProductController {

    private final ProductService products;

    ProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping("/generate")
    Bundle generate(@Valid @RequestBody GenerateRequest request) {
        return products.generate(request);
    }

    @PostMapping("/yaml")
    Map<String, Object> yaml(@RequestBody Bundle bundle) {
        return products.yaml(bundle);
    }
}
