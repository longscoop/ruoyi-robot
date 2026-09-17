package com.robot.platform.robot.dashboard.service;

import com.robot.platform.robot.dashboard.model.RobotDashboard;

public interface RobotDashboardService {
    RobotDashboard getDashboard(long tenantId);
}
