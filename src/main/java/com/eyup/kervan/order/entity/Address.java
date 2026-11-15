package com.eyup.kervan.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Stored in the orders table itself: the address belongs to this order and must not change
// if the user later edits their profile.
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Column(name = "ship_line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "ship_city", nullable = false, length = 100)
    private String city;

    @Column(name = "ship_postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "ship_country", nullable = false, length = 100)
    private String country;

    public Address(String line1, String city, String postalCode, String country) {
        this.line1 = line1;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country;
    }
}
