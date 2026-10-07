package com.mock.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TaskmanagerApplicationTests {

    @Value("${app.version}")
    private String appVersion;

    @Test
    void contextLoads() {
    }

    @Test
    void appVersionIsFilledInFromThePom() {
        assertThat(appVersion).matches("\\d+\\.\\d+\\.\\d+.*");
    }
}
