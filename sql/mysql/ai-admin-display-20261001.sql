-- Merge connection diagnostics into conversations; preserve existing IDs and URLs.
UPDATE system_menu SET visible=b'0' WHERE id=920060;
UPDATE system_menu SET name='角色查询' WHERE id=910205;
UPDATE system_menu SET name='角色新增' WHERE id IN (910206,920111);
UPDATE system_menu SET name='角色' WHERE id=920020;
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 910223,'角色修改','ai:prompt:update',3,15,910200,'','','',NULL,0,b'1',b'0',b'0','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=910223);
INSERT INTO system_role_menu (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT DISTINCT r.role_id,910223,r.tenant_id,'admin',NOW(),'admin',NOW(),b'0'
FROM system_role_menu r WHERE r.menu_id IN (910206,920111) AND r.deleted=0
AND NOT EXISTS (SELECT 1 FROM system_role_menu e WHERE e.role_id=r.role_id AND e.menu_id=910223 AND e.tenant_id=r.tenant_id AND e.deleted=0);
INSERT INTO system_role_menu (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT DISTINCT r.role_id,920040,r.tenant_id,'admin',NOW(),'admin',NOW(),b'0'
FROM system_role_menu r WHERE r.menu_id=920060 AND r.deleted=0
AND NOT EXISTS (SELECT 1 FROM system_role_menu e WHERE e.role_id=r.role_id AND e.menu_id=920040 AND e.tenant_id=r.tenant_id AND e.deleted=0);
