package com.robot.platform.robot.mission.job;

import com.robot.platform.framework.tenant.core.util.TenantUtils;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Finds pending work platform-wide but performs every mutation through the tenant-scoped service. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissionDispatchJob {
    private final MissionMapper mapper;
    private final MissionService missions;
    @Scheduled(fixedDelayString = "${robot.mission.dispatch-scan-interval:PT5S}")
    public void dispatch() {
        mapper.selectPendingForDispatchIgnoringTenant(100).forEach(mission -> TenantUtils.execute(mission.getTenantId(), () -> {
            try { missions.dispatchPending(mission.getId()); }
            catch (RuntimeException failure) { log.debug("[dispatch][Mission remains pending missionId({})]", mission.getId(), failure); }
        }));
    }
}
