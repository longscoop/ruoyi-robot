-- Add the merged Realtime Agent admin menus to an existing robot_platform database.
-- Runtime pages appear under the existing AI 大模型 group; duplicate AI Center
-- navigation is hidden while its menu IDs remain available for existing grants.
-- The MySQL init scripts run only when Docker creates a fresh data volume.
-- Safe to run more than once; role grants remain managed by the admin UI.

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 920000, 'AI 中心', '', 1, 5, 910000, 'ai', 'ep:chat-dot-round', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 920000);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT m.id, m.name, m.permission, 2, m.sort, 920000, m.path, m.icon, m.component, m.component_name, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
  SELECT 920010 id, '智能体' name, 'ai:agent:query' permission, 1 sort, 'agent' path, 'ep:cpu' icon, 'ai/agent/index' component, 'AiAgent' component_name UNION ALL
  SELECT 920020, 'Prompt', 'ai:prompt:query', 2, 'prompt', 'ep:document', 'ai/prompt/index', 'AiPrompt' UNION ALL
  SELECT 920030, '模型', 'ai:model:query', 3, 'model', 'ep:connection', 'ai/model/index', 'AiModel' UNION ALL
  SELECT 920040, '对话记录', 'ai:conversation:query', 4, 'conversation', 'ep:chat-line-square', 'ai/conversation/index', 'AiConversation' UNION ALL
  SELECT 920050, '长期记忆', 'ai:memory:query', 5, 'memory', 'ep:collection', 'ai/memory/index', 'AiMemory' UNION ALL
  SELECT 920060, '实时会话', 'ai:realtime:query', 6, 'realtime', 'ep:video-play', 'ai/realtime/index', 'AiRealtime' UNION ALL
  SELECT 920070, '数字人', 'ai:digital-human:query', 7, 'digital-human', 'ep:user', 'ai/digital-human/index', 'AiDigitalHuman'
) m
WHERE NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.id = m.id);

-- The legacy AI 大模型 group owns the shared enterprise configuration pages.
-- Keep the old AI Center IDs for role assignments while removing duplicate
-- navigation entries and moving runtime pages under AI 大模型.
UPDATE system_menu SET visible = b'0'
WHERE id IN (920000, 920010, 920020, 920030);
UPDATE system_menu SET parent_id = 791, sort = CASE id
  WHEN 920040 THEN 3 WHEN 920050 THEN 4 WHEN 920060 THEN 5 WHEN 920070 THEN 6 END,
  visible = b'1'
WHERE id IN (920040, 920050, 920060, 920070);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT m.id, m.name, m.permission, 3, m.sort, m.parent_id, '', '', '', NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
  SELECT 920101 id, '智能体新增' name, 'ai:agent:create' permission, 1 sort, 920010 parent_id UNION ALL
  SELECT 920102, '智能体修改', 'ai:agent:update', 2, 920010 UNION ALL
  SELECT 920103, '智能体删除', 'ai:agent:delete', 3, 920010 UNION ALL
  SELECT 920104, '智能体绑定', 'ai:agent:bind', 4, 920010 UNION ALL
  SELECT 920111, 'Prompt 新增', 'ai:prompt:create', 1, 920020 UNION ALL
  SELECT 920121, 'Provider 查询', 'ai:provider:query', 1, 920030 UNION ALL
  SELECT 920122, 'Provider 新增', 'ai:provider:create', 2, 920030 UNION ALL
  SELECT 920123, 'Provider 修改', 'ai:provider:update', 3, 920030 UNION ALL
  SELECT 920124, 'Provider 删除', 'ai:provider:delete', 4, 920030 UNION ALL
  SELECT 920125, '模型新增', 'ai:model:create', 5, 920030 UNION ALL
  SELECT 920126, '模型修改', 'ai:model:update', 6, 920030 UNION ALL
  SELECT 920127, '模型删除', 'ai:model:delete', 7, 920030 UNION ALL
  SELECT 920131, '记忆修改', 'ai:memory:update', 1, 920050 UNION ALL
  SELECT 920132, '记忆删除', 'ai:memory:delete', 2, 920050 UNION ALL
  SELECT 920141, '数字人新增', 'ai:digital-human:create', 1, 920070 UNION ALL
  SELECT 920142, '数字人修改', 'ai:digital-human:update', 2, 920070 UNION ALL
  SELECT 920143, '数字人删除', 'ai:digital-human:delete', 3, 920070 UNION ALL
  SELECT 920144, '数字人预览', 'ai:digital-human:preview', 4, 920070
) m
WHERE NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.id = m.id);
