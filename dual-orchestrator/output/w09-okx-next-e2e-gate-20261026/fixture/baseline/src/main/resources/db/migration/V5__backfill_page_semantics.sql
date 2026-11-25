-- 统计口径修正(V4 已应用, 不回改):
-- 1. 原 new_facts 列实际记录的是"观察关系数", 迁移为正确命名 new_observation_links;
-- 2. 新增 new_global_facts: 本页真正新增到 funding_rate_fact 的数量。
--    V4 历史行无法可靠推导该值, 保持 NULL(UNKNOWN), 不得伪造;
-- 3. 新增 in_window_received / out_of_window_received: 按回补窗口 [from,to) 拆分本页接收数,
--    一页跨过 from 边界时窗口内外都要记账。V4 历史行同样保持 NULL。
ALTER TABLE funding_backfill_page
    CHANGE COLUMN new_facts new_observation_links INT UNSIGNED NOT NULL;

ALTER TABLE funding_backfill_page
    ADD COLUMN in_window_received INT UNSIGNED NULL AFTER received,
    ADD COLUMN out_of_window_received INT UNSIGNED NULL AFTER in_window_received,
    ADD COLUMN new_global_facts INT UNSIGNED NULL AFTER new_observation_links,
    ADD CONSTRAINT chk_backfill_page_window_split CHECK (
        in_window_received IS NULL
        OR in_window_received + out_of_window_received = received
    );
