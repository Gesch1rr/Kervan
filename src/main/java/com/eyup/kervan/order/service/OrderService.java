package com.eyup.kervan.order.service;

import com.eyup.kervan.cart.dto.CartSnapshot;
import com.eyup.kervan.cart.service.CartService;
import com.eyup.kervan.common.dto.PageResponse;
import com.eyup.kervan.common.exception.InvalidSortException;
import com.eyup.kervan.order.dto.CreateOrderRequest;
import com.eyup.kervan.order.dto.OrderResponse;
import com.eyup.kervan.order.dto.OrderSummaryResponse;
import com.eyup.kervan.order.dto.ShippingAddress;
import com.eyup.kervan.order.entity.Address;
import com.eyup.kervan.order.entity.Order;
import com.eyup.kervan.order.entity.OrderItem;
import com.eyup.kervan.order.exception.EmptyCartException;
import com.eyup.kervan.order.exception.OrderNotCancellableException;
import com.eyup.kervan.order.exception.OrderNotFoundException;
import com.eyup.kervan.order.exception.ProductUnavailableException;
import com.eyup.kervan.order.mapper.OrderMapper;
import com.eyup.kervan.order.repository.OrderRepository;
import com.eyup.kervan.product.dto.ProductSummary;
import com.eyup.kervan.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    // whitelist: blocks sorting by unknown or non-column fields, which would otherwise be a 500
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount", "status");

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final OrderMapper orderMapper;

    // One transaction: if any step fails (e.g. stock runs out for the last line), the stock already taken
    // for earlier lines, the order and the emptied cart are all rolled back.
    @Transactional
    public OrderResponse placeOrder(Long userId, CreateOrderRequest request) {
        CartSnapshot cart = cartService.getCartForCheckout(userId);
        if (cart.isEmpty()) {
            throw new EmptyCartException();
        }

        // Names and prices are read from the database, never from the cache or the client.
        Map<Long, ProductSummary> products = productService.findSummaries(
                cart.lines().stream().map(CartSnapshot.Line::productId).toList());
        for (CartSnapshot.Line line : cart.lines()) {
            ProductSummary product = products.get(line.productId());
            if (product == null || !product.active()) {
                throw new ProductUnavailableException(line.productId());
            }
        }

        // Product rows are always locked in the same order. If one checkout took A then B and another
        // B then A, each could hold one row and wait for the other (deadlock).
        List<CartSnapshot.Line> lines = cart.lines().stream()
                .sorted(Comparator.comparing(CartSnapshot.Line::productId))
                .toList();
        for (CartSnapshot.Line line : lines) {
            productService.decreaseStock(line.productId(), line.quantity());
        }

        Order order = new Order(userId, toAddress(request.shippingAddress()));
        for (CartSnapshot.Line line : lines) {
            ProductSummary product = products.get(line.productId());
            order.addItem(product.id(), product.name(), product.price(), line.quantity());
        }
        orderRepository.save(order);
        cartService.clearCart(userId);
        return orderMapper.toResponse(order);
    }

    // One transaction: if restoring the stock fails, the cancel is rolled back too.
    @Transactional
    public OrderResponse cancel(Long id, Long userId) {
        // Conditional update first: of two concurrent cancels only one gets past this point.
        if (orderRepository.cancelIfOwner(id, userId, Instant.now()) == 0) {
            // owner with a non-pending order vs. unknown/foreign order
            throw orderRepository.existsByIdAndUserId(id, userId)
                    ? new OrderNotCancellableException()
                    : new OrderNotFoundException();
        }

        // Loaded only AFTER the update; an Order loaded before it would still be PENDING in the
        // persistence context, because the update does not clear it.
        Order order = orderRepository.findWithItemsById(id).orElseThrow(OrderNotFoundException::new);

        // Same lock order as checkout, so a concurrent order cannot deadlock with this cancel.
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(item -> productService.increaseStock(item.getProductId(), item.getQuantity()));

        return orderMapper.toResponse(order);
    }

    public PageResponse<OrderSummaryResponse> listOwn(Long userId, Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        }
        return PageResponse.from(orderRepository.findByUserId(userId, pageable).map(orderMapper::toSummary));
    }

    public OrderResponse getOrder(Long id, Long userId, boolean isAdmin) {
        // an order of another user looks exactly like a missing one
        return orderRepository.findWithItemsById(id)
                .filter(order -> isAdmin || order.getUserId().equals(userId))
                .map(orderMapper::toResponse)
                .orElseThrow(OrderNotFoundException::new);
    }

    private static Address toAddress(ShippingAddress address) {
        return new Address(address.line1().trim(), address.city().trim(),
                address.postalCode().trim(), address.country().trim());
    }
}
