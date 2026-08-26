package com.agentops.guardian.domain.order;

import com.agentops.guardian.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @Column(name = "order_item_id", length = 20, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_item_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_item_product")
    )
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
            name = "item_price",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal itemPrice;

    @Column(
            name = "item_total",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal itemTotal;
}