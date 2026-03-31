ALTER TABLE im_message
    ADD COLUMN retracted_at DATETIME NULL COMMENT '撤回时间' AFTER acked_at,
    ADD COLUMN retracted_by BIGINT NULL COMMENT '撤回操作人' AFTER retracted_at;

ALTER TABLE im_group_message
    ADD COLUMN retracted_at DATETIME NULL COMMENT '撤回时间' AFTER created_at,
    ADD COLUMN retracted_by BIGINT NULL COMMENT '撤回操作人' AFTER retracted_at;

ALTER TABLE im_message
    MODIFY COLUMN status VARCHAR(32) NOT NULL COMMENT '消息状态: SENT/DELIVERED/ACKED/RETRACTED';

ALTER TABLE im_group_message
    MODIFY COLUMN status INT NOT NULL COMMENT '群消息状态: 1=SENT, 2=RETRACTED';
