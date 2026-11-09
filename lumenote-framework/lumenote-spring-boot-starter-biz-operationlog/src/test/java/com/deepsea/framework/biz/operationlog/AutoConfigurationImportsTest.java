package com.deepsea.framework.biz.operationlog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.util.ClassUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoConfigurationImportsTest {

    @Test
    void importsShouldPointToExistingClasses() {
        List<String> ours = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates().stream()
                .filter(name -> name.startsWith("com.deepsea.framework"))
                .toList();

        assertFalse(ours.isEmpty());
        assertTrue(ours.contains("com.deepsea.framework.biz.operationlog.config.ApiOperationLogAutoConfiguration"));
        for (String name : ours) {
            assertTrue(ClassUtils.isPresent(name, getClass().getClassLoader()), "missing auto-configuration class: " + name);
        }
    }
}
