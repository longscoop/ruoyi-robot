package com.robot.platform.ai.live;
import org.junit.jupiter.api.Assumptions;
final class LiveTestGate{static String require(String key){Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_AI_PROVIDER_LIVE_TESTS")));String v=System.getenv(key);Assumptions.assumeTrue(v!=null&&!v.isBlank(),key+" required");return v;}private LiveTestGate(){}}
