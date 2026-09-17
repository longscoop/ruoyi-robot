package com.robot.platform.device.device.service;

/** Authoritative tenant namespace seam; replace when tenants gain an explicit immutable code. */
public interface TenantNamespaceResolver {
    String resolve(long tenantId);
}
