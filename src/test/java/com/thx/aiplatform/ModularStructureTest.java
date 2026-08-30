package com.thx.aiplatform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularStructureTest {

    @Test
    void 模块依赖必须符合声明边界() {
        ApplicationModules.of(AiPlatformApplication.class).verify();
    }
}
