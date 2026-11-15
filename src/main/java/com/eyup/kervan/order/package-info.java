// Order may only see the cart and product DTOs and service APIs, plus common building blocks.
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"cart :: dto", "cart :: service", "product :: dto", "product :: service", "common"})
package com.eyup.kervan.order;
