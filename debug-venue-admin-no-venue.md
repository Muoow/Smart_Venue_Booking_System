[OPEN] Venue Admin No Venue

- Session ID: `venue-admin-no-venue`
- Symptom: 场地管理员登录后请求返回 `403`，提示“当前场地管理员未绑定可管理的场馆。”
- Scope: 远端部署环境，场地管理员账号
- Current status: 收集运行时证据中，尚未修改业务逻辑

## Hypotheses

1. `venueadmin1/venueadmin2` 已写入 `user` 和 `user_auth`，但未写入管理员与场馆的绑定关系。
2. 远端存在绑定关系，但记录中的 `user_id`、`venue_id` 或状态字段与代码查询条件不匹配。
3. 代码查询场馆管理员绑定时依赖了特定角色值或额外字段，当前远端账号数据不满足条件。
4. 远端数据库缺少当前代码版本所需的绑定表或表数据，导致查询结果为空。

## Evidence Log

- 用户反馈：登录后 F12 看到请求反复出现 `login/profile`
- 用户补充接口响应：`403 当前场地管理员未绑定可管理的场馆`
- 代码定位：[AdminController.java](file:///c:/Users/GALAXY/Desktop/%E6%96%B0%E5%BB%BA%E6%96%87%E4%BB%B6%E5%A4%B9/main/Smart_Venue_Booking_System/src/main/java/com/courtflow/homework/controller/AdminController.java#L577-L598) 在查询 `venue_admin` 为空时抛出 `403`
- 远端修复前证据：`venue_admin` 表仅有 `user_id=2(admin)` 绑定 `1-6` 号场馆，`user_id=4/5` 无记录
- 远端修复动作：补入 `venue_admin(id=7,user_id=4,venue_id=1)` 与 `venue_admin(id=8,user_id=5,venue_id=2)`
- 修复后证据：`/admin/profile` 返回 `200`，`managedVenueIds=[1]`，`managedVenueNames=["主体育馆"]`

## Current Status

- 远端数据已修复
- 本地正式种子文件 `deploy/mysql/init/02_seed.sql` 已补齐场地管理员绑定，防止后续重部署再次丢失
- 等待用户在浏览器中复测确认
