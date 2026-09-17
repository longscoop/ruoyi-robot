package com.robot.platform.mqtt;

import org.springframework.context.SmartLifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts and stops the configured broker client; construction itself never opens a connection.
 * Start and stop are serialized so shutdown cannot overtake an in-flight broker connection.
 */
public final class RobotMqttLifecycle implements SmartLifecycle {
    private final HiveMqRobotMqttClient client;
    private final AtomicBoolean running = new AtomicBoolean();

    public RobotMqttLifecycle(HiveMqRobotMqttClient client) {
        this.client = client;
    }

    @Override
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            try {
                client.connect().toCompletableFuture().join();
            } catch (RuntimeException e) {
                running.set(false);
                throw e;
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) client.disconnect().toCompletableFuture().join();
    }

    /** Spring must still invoke stop after transport failure; readiness belongs to the health indicator. */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
