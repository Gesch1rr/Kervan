package com.eyup.kervan.cart.entity;

import com.eyup.kervan.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
@Entity
@Table(name = "carts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "carts_seq")
    @SequenceGenerator(name = "carts_seq", sequenceName = "carts_seq", allocationSize = 50)
    private Long id;

    // Plain id: the cart module must not depend on the user module's entities.
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // The cart owns its items: saving the cart saves them, removing one from the list deletes its row.
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CartItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    public Cart(Long userId) {
        this.userId = userId;
    }

    // Read-only view: items are changed only through the methods below.
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    public int quantityOf(Long productId) {
        return findItem(productId).map(CartItem::getQuantity).orElse(0);
    }

    // Adding a product that is already in the cart increases its quantity instead of adding a second line.
    public void addItem(Long productId, int quantity) {
        findItem(productId).ifPresentOrElse(
                item -> item.changeQuantity(item.getQuantity() + quantity),
                () -> items.add(new CartItem(this, productId, quantity)));
    }

    // orphanRemoval turns removing an item from the list into a DELETE of its row.
    public boolean removeItem(Long productId) {
        return items.removeIf(item -> item.getProductId().equals(productId));
    }

    public void clear() {
        items.clear();
    }
}
