package com.robot.platform.robot.mission.enums;

/** V1 action vocabulary; validation is deliberately closed so unknown device work is never queued. */
public enum MissionActionType {
    NAVIGATE, SPEAK, PLAY_MEDIA, CAPTURE_IMAGE, INSPECT, FIND_PERSON, FIND_OBJECT, RETURN_HOME, WAIT, CUSTOM
}
