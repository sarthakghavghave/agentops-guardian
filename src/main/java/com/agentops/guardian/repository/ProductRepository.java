package com.agentops.guardian.repository;

import com.agentops.guardian.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
}