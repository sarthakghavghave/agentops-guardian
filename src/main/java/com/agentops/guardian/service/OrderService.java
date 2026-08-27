package com.agentops.guardian.service;

import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.domain.order.OrderStatus;
import com.agentops.guardian.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId).orElse(null);
    }

    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomer_Id(customerId);
    }

    @Transactional
    public Order returnOrder(String orderId) {

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return null;

        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Only completed orders can be returned.");
        }

        order.setOrderStatus(OrderStatus.RETURNED);
        return orderRepository.save(order);
    }
}