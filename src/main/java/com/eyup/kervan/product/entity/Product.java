package com.eyup.kervan.product.entity;

import com.eyup.kervan.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq")
    @SequenceGenerator(name = "products_seq", sequenceName = "products_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    // Without columnDefinition Hibernate expects varchar(255) and ddl-auto=validate fails on the TEXT column.
    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    // Same module, so a JPA relation is fine here (unlike references to other modules, which use plain ids).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private boolean active = true;

    // Detects lost updates when two admins edit the same product at the same time.
    @Version
    @Column(nullable = false)
    private Long version;

    public Product(String name, String slug, String description, BigDecimal price, int stock, Category category) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public void update(String name, String slug, String description, BigDecimal price, Category category) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    public void deactivate() {
        this.active = false;
    }
}
