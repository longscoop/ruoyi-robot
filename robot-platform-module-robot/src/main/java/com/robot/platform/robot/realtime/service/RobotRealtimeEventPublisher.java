package com.robot.platform.robot.realtime.service;

import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
public interface RobotRealtimeEventPublisher { void publish(TenantRobotRealtimeEvent event); }
