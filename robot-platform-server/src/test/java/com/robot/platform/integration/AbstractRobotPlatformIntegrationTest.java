package com.robot.platform.integration;

import com.robot.platform.server.RobotPlatformApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Real, isolated infrastructure for server integration tests. Docker is required.
 * Framework reference records survive cleanup; domain fixtures must be created by each test.
 */
@SpringBootTest(classes = RobotPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("unit-test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ResourceLock("robot-platform-integration-containers")
public abstract class AbstractRobotPlatformIntegrationTest {

    @Container
    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("robot_platform_test")
            // Isolated disposable container: avoid host/VM clock skew invalidating its generated TLS certificate.
            .withUrlParam("sslMode", "DISABLED")
            .withUsername("robot_test")
            .withPassword(UUID.randomUUID().toString())
            .withCopyFileToContainer(MountableFile.forClasspathResource("mysql/ruoyi-vue-pro.sql"),
                    "/docker-entrypoint-initdb.d/01-framework.sql")
            .withCopyFileToContainer(MountableFile.forClasspathResource("mysql/quartz.sql"),
                    "/docker-entrypoint-initdb.d/02-quartz.sql")
            .withCopyFileToContainer(MountableFile.forClasspathResource("mysql/robot-platform.sql"),
                    "/docker-entrypoint-initdb.d/03-robot.sql")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        // The upstream dynamic datasource/Redisson also read properties directly.
        registry.add("spring.datasource.dynamic.primary", () -> "master");
        registry.add("spring.datasource.dynamic.datasource.master.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.master.username", MYSQL::getUsername);
        registry.add("spring.datasource.dynamic.datasource.master.password", MYSQL::getPassword);
        registry.add("spring.datasource.dynamic.datasource.master.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.database", () -> 0);
        registry.add("spring.data.redis.password", () -> "");
    }

    @BeforeEach
    @AfterEach
    protected final void clearIntegrationState() throws SQLException {
        // A direct container connection avoids any test transaction or tenant interceptor.
        try (var connection = MYSQL.createConnection("");
             var statement = connection.createStatement()) {
            var tables = new ArrayList<String>();
            try (var result = statement.executeQuery("""
                    SELECT TABLE_NAME FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                      AND TABLE_NAME REGEXP '^(tenant|device|product|robot|member|mission)(_|$)'
                    """)) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) {
                    statement.execute("TRUNCATE TABLE `" + table.replace("`", "``") + "`");
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
        try (var connection = redisTemplate.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
