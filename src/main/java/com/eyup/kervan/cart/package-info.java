// Cart may only see the product module's DTOs, service API and exceptions, plus common building blocks.
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"product :: dto", "product :: service", "product :: exception", "common"})
package com.eyup.kervan.cart;
