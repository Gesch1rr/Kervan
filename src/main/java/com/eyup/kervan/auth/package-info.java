// Auth may only see the user module's DTOs, service API and the role enum, plus common building blocks.
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"user :: dto", "user :: service", "user :: role", "common"})
package com.eyup.kervan.auth;
