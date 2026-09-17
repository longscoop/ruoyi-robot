package com.robot.platform.robot.mission.message;

/** Transport-neutral inbound outcome; duplicates and terminal late reports are safe no-ops. */
public enum MessageHandleResult { ACCEPTED, DUPLICATE, LATE_IGNORED }
