package com.agentops.guardian.service;

import com.agentops.guardian.domain.order.OrderItem;
import com.agentops.guardian.repository.OrderItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;

    public OrderItemService(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    public List<OrderItem> getOrderItems(String orderId) {
        return orderItemRepository.findByOrder_Id(orderId);
    }
}