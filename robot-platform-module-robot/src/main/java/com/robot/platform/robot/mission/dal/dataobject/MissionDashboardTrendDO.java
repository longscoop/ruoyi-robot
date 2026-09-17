package com.robot.platform.robot.mission.dal.dataobject;

import lombok.Data;

import java.time.LocalDate;

/** MySQL GROUP BY projection used only by the tenant dashboard. */
@Data
public class MissionDashboardTrendDO {
    private LocalDate missionDate;
    private long total;
    private long failed;
}
