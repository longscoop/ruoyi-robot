package com.robot.platform.integration;

import com.robot.platform.framework.tenant.core.util.TenantUtils;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.service.TenantNamespaceResolver;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.mqtt.*;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import com.robot.platform.robot.command.outbox.dal.mysql.RobotCommandOutboxMapper;
import com.robot.platform.robot.command.outbox.service.RobotCommandDeliveryFailureHandler;
import com.robot.platform.robot.command.outbox.service.RobotCommandOutboxService;
import com.robot.platform.robot.command.gateway.MqttRobotCommandGateway;
import com.robot.platform.robot.command.gateway.RobotCommandGateway;
import com.robot.platform.robot.mission.dal.dataobject.MissionEventDO;
import com.robot.platform.robot.mission.dal.mysql.MissionEventMapper;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.mission.service.MissionService;
import com.robot.platform.robot.mission.service.command.MissionActionCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.*;
import org.mybatis.spring.annotation.MapperScan;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** A real EMQX/Paho process loop; unlike HTTP fallback tests, every mission fact crosses MQTT. */
@Tag("mqtt-e2e") @EnabledIfEnvironmentVariable(named="RUN_MQTT_MISSION_LOOP_IT", matches="true")
@Testcontainers
@Import(MqttMissionLoopIT.Ports.class)
@TestPropertySource(properties={"robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "robot.mqtt.enabled=true", "robot.mqtt.cloud.client-id=mission-loop-cloud", "robot.mqtt.cloud.username=mission-loop-cloud",
        "robot.mqtt.cloud.password=mission-loop-cloud", "robot.command.outbox-scan-interval=PT1H", "robot.mission.dispatch-scan-interval=PT1H"})
class MqttMissionLoopIT extends AbstractRobotPlatformIntegrationTest {
    private static final long TENANT=10L; private static final String PRODUCT="product-e2e", SERIAL="SIM-E2E-1";
    @Container static final GenericContainer<?> EMQX=new GenericContainer<>("emqx/emqx:5.8.4")
            // OrbStack can briefly move its monotonic clock backwards during container startup.
            // Erlang reads this VM flag before EMQX's runtime configuration is loaded. Editing
            // vm.args in the command is too late on this image and leaves HiveMQ reconnecting forever.
            .withEnv("ERL_FLAGS", "+c false")
            .withEnv("EMQX_ALLOW_ANONYMOUS","true").withExposedPorts(1883).withStartupTimeout(Duration.ofMinutes(2));
    @DynamicPropertySource static void mqtt(DynamicPropertyRegistry p) {
        p.add("robot.mqtt.host",EMQX::getHost); p.add("robot.mqtt.port",()->EMQX.getMappedPort(1883)); }
    @Autowired ProductMapper products; @Autowired DeviceMapper devices; @Autowired RobotMapper robots;
    @Autowired MissionService missions; @Autowired MissionMapper missionRows; @Autowired MissionEventMapper events;
    @Autowired RobotCommandOutboxService outbox; @Autowired RobotCommandOutboxMapper outboxRows;
    @Autowired RobotMqttPublisher publisher; @Autowired RobotMessageEnvelopeCodec envelopes; private Process simulator;
    @AfterEach void stop(){if(simulator!=null) simulator.destroyForcibly();}

    @Test void simulatorCompletesMissionAndDuplicateStartRunsActionOnce() throws Exception {
        long robot=fixture(); start(robot);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> TenantUtils.execute(TENANT,
                () -> assertThat(robots.selectByTenantAndId(TENANT, robot).getOnlineStatus()).isEqualTo("ONLINE")));
        long mission=TenantUtils.execute(TENANT,()->{ MissionCreateCommand c=new MissionCreateCommand(); c.setRobotId(robot); c.setMissionType("SIMULATED"); c.setSource("SYSTEM"); c.setPriority(1); c.setRequestId("mqtt-loop-request"); c.setActions(List.of(new MissionActionCommand("WAIT","{\"seconds\":1}"))); long id=missions.create(c).id(); missions.dispatchPending(id); return id; });
        assertThat(outbox.claimAndDispatch(10)).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> TenantUtils.execute(TENANT, () -> {
            assertThat(missionRows.selectById(mission).getStatus()).isEqualTo("SUCCESS"); assertThat(count(mission,"MISSION_ACK")).isEqualTo(1); assertThat(count(mission,"ACTION_RUNNING")).isEqualTo(1); assertThat(count(mission,"ACTION_SUCCESS")).isEqualTo(1);
        }));
        RobotCommandOutboxDO row=TenantUtils.execute(TENANT, () -> outboxRows.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RobotCommandOutboxDO>().eq(RobotCommandOutboxDO::getMissionId,mission).eq(RobotCommandOutboxDO::getMessageType,"MISSION_START")).get(0));
        publisher.publish(RobotTopic.parse(row.getTopic()), envelopes.decode(row.getEnvelope().getBytes(StandardCharsets.UTF_8),null)).toCompletableFuture().join();
        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> TenantUtils.execute(TENANT,
                () -> assertThat(count(mission,"ACTION_SUCCESS")).isEqualTo(1)));
    }
    private long fixture(){ return TenantUtils.execute(TENANT,()->{ ProductDO p=ProductDO.builder().tenantId(TENANT).productKey(PRODUCT).name("E2E").status(0).build(); products.insert(p); DeviceDO d=DeviceDO.builder().tenantId(TENANT).productId(p.getId()).deviceSn(SERIAL).name("E2E").lifecycleStatus("ACTIVATED").credentialVersion(1).mqttUsername("t-10/"+PRODUCT+"/"+SERIAL).mqttSecretHash("broker-only-test-secret").build(); devices.insert(d); RobotDO r=RobotDO.builder().tenantId(TENANT).deviceId(d.getId()).productId(p.getId()).robotCode("SIM-E2E").name("E2E").onlineStatus("OFFLINE").workStatus("IDLE").build(); robots.insert(r); d.setRobotId(r.getId()); devices.updateById(d); return r.getId(); }); }
    private void start(long robot) throws Exception { Path root=repositoryRoot(); ProcessBuilder p=new ProcessBuilder(System.getenv().getOrDefault("MQTT_PYTHON","python3"),"robot_simulator.py","--sn",SERIAL,"--broker",EMQX.getHost(),"--port",String.valueOf(EMQX.getMappedPort(1883)),"--tenant-namespace","t-10","--product-key",PRODUCT,"--robot-id",String.valueOf(robot),"--heartbeat-seconds","1","--action-delay","0","--store",root.resolve("robot-platform-server/target/e2e-simulator.sqlite").toString()); p.directory(root.resolve("robot-simulator").toFile()); p.redirectErrorStream(true); simulator=p.start(); }
    /** Surefire runs with a module user.dir; walk upward instead of assuming Maven's invocation directory. */
    private static Path repositoryRoot() {
        for (Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("robot-simulator"))) return candidate;
        }
        throw new IllegalStateException("could not locate repository root containing robot-simulator");
    }
    private long count(long mission,String type){return events.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MissionEventDO>().eq(MissionEventDO::getMissionId,mission).eq(MissionEventDO::getEventType,type)).size();}
    @TestConfiguration(proxyBeanMethods=false)
    @MapperScan(basePackages={"com.robot.platform.robot.robot.dal.mysql", "com.robot.platform.robot.mission.dal.mysql",
            "com.robot.platform.robot.command.outbox.dal.mysql", "com.robot.platform.robot.message.inbox.dal.mysql",
            "com.robot.platform.robot.realtime.outbox.dal.mysql", "com.robot.platform.device.device.dal.mysql",
            "com.robot.platform.device.group.dal.mysql", "com.robot.platform.device.product.dal.mysql", "com.robot.platform.tenant.quota.dal.mysql",
            "com.robot.platform.member.member.dal.mysql", "com.robot.platform.member.binding.dal.mysql"})
    static class Ports {
        @Bean @Primary TenantNamespaceResolver namespace(){return id->"t-"+id;}

        /** The loop only proves successful delivery; avoid eagerly resolving the optional
         * exhaustion bridge while MissionService is still being constructed. */
        @Bean @Primary RobotCommandDeliveryFailureHandler ignoredDeliveryFailure() {
            return (command, errorCode, errorMessage) -> { };
        }

        /**
         * MapperScan on a test import is registered after component conditions are evaluated.
         * Wire the production outbox explicitly so this test exercises the durable MQTT path,
         * rather than silently replacing it with a recording gateway.
         */
        @Bean RobotCommandOutboxService mqttOutbox(RobotCommandOutboxMapper mapper, RobotMqttPublisher publisher,
                                                    RobotMessageEnvelopeCodec envelopes, PlatformTransactionManager transactions,
                                                    ObjectProvider<RobotCommandDeliveryFailureHandler> failures) {
            return new RobotCommandOutboxService(mapper, publisher, envelopes, Clock.systemUTC(), transactions,
                    failures, Duration.ofMinutes(2));
        }

        @Bean RobotCommandGateway mqttGateway(RobotMapper robots, DeviceMapper devices,
                                               com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService identities,
                                               RobotCommandOutboxService outbox, RobotMessageEnvelopeCodec envelopes) {
            return new MqttRobotCommandGateway(robots, devices, identities, outbox, envelopes, Clock.systemUTC());
        }
    }
}
