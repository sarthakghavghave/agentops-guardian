package com.agentops.guardian.service;

import com.agentops.guardian.domain.product.Product;
import com.agentops.guardian.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product getProduct(String productId) {
        return productRepository.findById(productId).orElse(null);
    }
}