package com.robot.platform.robot.mission.service.command;

/** Validated by MissionService; command data is never trusted directly by a transport adapter. */
public record MissionActionCommand(String actionType, String parameters) { }
