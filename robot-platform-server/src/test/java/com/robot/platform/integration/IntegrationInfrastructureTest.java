package com.robot.platform.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationInfrastructureTest extends AbstractRobotPlatformIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void initializesFrameworkAndQuartzOnRealMysql84AndReplaysRobotSchema() throws SQLException {
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_users", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS", Long.class)).isZero();
        try (var connection = dataSource.getConnection()) {
            var schema = new ClassPathResource("mysql/robot-platform.sql");
            ScriptUtils.executeSqlScript(connection, schema);
            ScriptUtils.executeSqlScript(connection, schema);
        }
    }

    @Test
    void clearsDomainTablesAndRedisWhilePreservingFrameworkRecords() throws SQLException {
        var jdbc = new JdbcTemplate(dataSource);
        long usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM system_users", Long.class);
        jdbc.execute("CREATE TABLE robot_integration_probe (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE robot_integration_child (id BIGINT PRIMARY KEY, parent_id BIGINT,"
                + " FOREIGN KEY (parent_id) REFERENCES robot_integration_probe(id))");
        try {
            jdbc.update("INSERT INTO robot_integration_probe VALUES (1)");
            jdbc.update("INSERT INTO robot_integration_child VALUES (1, 1)");
            redis.opsForValue().set("integration:cleanup", "persisted");
            assertThat(redis.opsForValue().get("integration:cleanup")).isEqualTo("persisted");

            clearIntegrationState();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_integration_probe", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_integration_child", Long.class)).isZero();
            assertThat(redis.hasKey("integration:cleanup")).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_users", Long.class)).isEqualTo(usersBefore);
        } finally {
            jdbc.execute("DROP TABLE robot_integration_child");
            jdbc.execute("DROP TABLE robot_integration_probe");
        }
    }
}
