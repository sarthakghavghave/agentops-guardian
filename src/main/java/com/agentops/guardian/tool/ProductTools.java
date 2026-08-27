package com.agentops.guardian.tool;

import com.agentops.guardian.domain.product.Product;
import com.agentops.guardian.service.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ProductTools {

    private final ProductService productService;

    public ProductTools(ProductService productService) {
        this.productService = productService;
    }

    @Tool(description = "Retrieve product information using its unique product ID.")
    public String getProduct(String productId) {

        Product product = productService.getProduct(productId);
        if (product == null) return "No product found with ID " + productId + ".";

        return String.format("""
                Product ID: %s
                Name: %s
                Category: %s
                Brand: %s
                Price: %s
                Rating: %s
                """,
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getBrand(),
                product.getPrice(),
                product.getRating()
        );
    }
}