package com.robot.platform.robot.command.outbox.job;

import com.robot.platform.robot.command.outbox.service.RobotCommandOutboxService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled delivery is an accelerator; durable retry state keeps commands safe across restarts. */
@Component
public class RobotCommandOutboxDispatcher {
    private final ObjectProvider<RobotCommandOutboxService> outbox;

    public RobotCommandOutboxDispatcher(ObjectProvider<RobotCommandOutboxService> outbox) {
        this.outbox = outbox;
    }

    /**
     * Some focused server integration tests intentionally replace the command gateway and do not
     * provide MQTT infrastructure. Resolve lazily so that those test slices do not acquire a
     * startup-only dependency on the production outbox; production still dispatches normally.
     */
    @Scheduled(fixedDelayString = "${robot.command.outbox-scan-interval:PT5S}")
    public void dispatchDue() {
        RobotCommandOutboxService service = outbox.getIfAvailable();
        if (service != null) {
            service.claimAndDispatch(100);
        }
    }
}
