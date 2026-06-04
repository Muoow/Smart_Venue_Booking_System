# CourtFlow Bruno Collection

- 打开 Bruno 后选择 `bruno/CourtFlow-Core-APIs`
- 先运行 `auth/login.bru` 获取 `bearerToken`
- 再运行 `user/profile.bru`、`reservation/list.bru`
- 如需跑完整预约链路，依次执行 `reservation/apply.bru`、`reservation/detail.bru`、`reservation/cancel.bru`
- 若需要切换环境，修改 `environments/Local.bru` 中的 `baseUrl`
