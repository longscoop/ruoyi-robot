/** Inventory changes are platform operations; permission alone is not sufficient. */
export const canMaintainInventory = (roles: readonly string[]) => roles.includes('super_admin')
