package com.eyup.kervan.cart.service;

import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.cart.dto.CartItemResponse;
import com.eyup.kervan.cart.dto.CartResponse;
import com.eyup.kervan.cart.dto.UpdateCartItemRequest;
import com.eyup.kervan.cart.entity.Cart;
import com.eyup.kervan.cart.entity.CartItem;
import com.eyup.kervan.cart.exception.CartItemNotFoundException;
import com.eyup.kervan.cart.repository.CartRepository;
import com.eyup.kervan.product.dto.ProductSummary;
import com.eyup.kervan.product.exception.InsufficientStockException;
import com.eyup.kervan.product.exception.ProductNotFoundException;
import com.eyup.kervan.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;

    public CartResponse getCart(Long userId) {
        // reading does not create a cart
        return cartRepository.findWithItemsByUserId(userId)
                .map(this::toResponse)
                .orElseGet(CartService::emptyCart);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductSummary product = productService.getSummary(request.productId());
        if (!product.active()) {
            throw new ProductNotFoundException(product.id());
        }
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseGet(() -> cartRepository.save(new Cart(userId)));
        int newQuantity = cart.quantityOf(product.id()) + request.quantity();
        // early, friendly check; the real guarantee is the conditional stock update at checkout
        if (newQuantity > product.stock()) {
            throw new InsufficientStockException(product.id());
        }
        cart.addItem(product.id(), request.quantity());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        CartItem item = cart.findItem(productId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        ProductSummary product = productService.getSummary(productId);
        if (!product.active()) {
            throw new ProductNotFoundException(productId);
        }
        if (request.quantity() > product.stock()) {
            throw new InsufficientStockException(productId);
        }
        item.changeQuantity(request.quantity());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        if (!cart.removeItem(productId)) {
            throw new CartItemNotFoundException(productId);
        }
        return toResponse(cart);
    }

    // idempotent; checkout will call this too
    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findWithItemsByUserId(userId).ifPresent(Cart::clear);
    }

    private CartResponse toResponse(Cart cart) {
        if (cart.getItems().isEmpty()) {
            return emptyCart();
        }
        // one query for all lines instead of one per line (N+1)
        Map<Long, ProductSummary> products = productService.findSummaries(
                cart.getItems().stream().map(CartItem::getProductId).toList());
        List<CartItemResponse> items = cart.getItems().stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> toItemResponse(item, products.get(item.getProductId())))
                .toList();
        BigDecimal total = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, total);
    }

    private static CartItemResponse toItemResponse(CartItem item, ProductSummary product) {
        BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));
        boolean available = product.active() && product.stock() >= item.getQuantity();
        return new CartItemResponse(product.id(), product.name(), product.price(), item.getQuantity(), lineTotal, available);
    }

    private static CartResponse emptyCart() {
        return new CartResponse(List.of(), BigDecimal.ZERO);
    }
}
