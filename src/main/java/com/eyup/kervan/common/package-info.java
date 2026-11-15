// Shared building blocks used by every module; open = all its packages are visible; it depends on no other module.
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = {})
package com.eyup.kervan.common;
