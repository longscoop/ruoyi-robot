package com.robot.platform.robot.mission.domain;

/** Immutable action facts supplied to the central Mission lifecycle validator. */
public record MissionActionState(Long id, String status, boolean required) { }
