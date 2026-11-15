package com.eyup.kervan.order.controller;

import com.eyup.kervan.common.dto.PageResponse;
import com.eyup.kervan.order.dto.CreateOrderRequest;
import com.eyup.kervan.order.dto.OrderResponse;
import com.eyup.kervan.order.dto.OrderSummaryResponse;
import com.eyup.kervan.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Checkout: turns the current cart into an order.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(JwtAuthenticationToken auth, @Valid @RequestBody CreateOrderRequest request) {
        return orderService.placeOrder(userId(auth), request);
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            JwtAuthenticationToken auth,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listOwn(userId(auth), pageable);
    }

    // no @PreAuthorize: owner or admin is checked in the service
    @GetMapping("/{id}")
    public OrderResponse get(JwtAuthenticationToken auth, @PathVariable Long id) {
        return orderService.getOrder(id, userId(auth), isAdmin(auth));
    }

    // owner only; admins cancel through the separate admin endpoint
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(JwtAuthenticationToken auth, @PathVariable Long id) {
        return orderService.cancel(id, userId(auth));
    }

    private static Long userId(JwtAuthenticationToken auth) {
        return Long.valueOf(auth.getToken().getSubject());
    }

    // uses the authorities built by SecurityConfig's converter, not the raw roles claim
    private static boolean isAdmin(JwtAuthenticationToken auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
