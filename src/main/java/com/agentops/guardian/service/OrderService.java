package com.agentops.guardian.service;

import com.agentops.guardian.entity.Order;
import com.agentops.guardian.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElse(null);
    }

    @Transactional
    public Order refundOrder(Long orderId) {

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) return null;

        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Only PAID orders can be refunded.");
        }

        order.setStatus("REFUNDED");

        return orderRepository.save(order);
    }
}