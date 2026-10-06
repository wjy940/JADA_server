# Severe Equipment Assets Java 后端

Spring Boot 4.1.1 / Java 17+ / PostgreSQL。源码仅服务 `severe_equipment_assets`，连接其他数据库会拒绝启动。前端基准目录为 `../WEB_Equipment_Assets`；本仓库不修改前端页面、路由或样式。GPS 数据由既有外部 API 提供，本服务不采集、模拟或保存 GPS 定位/轨迹。

## 交付状态与证据

后端支持真实持久化、数据库授权、审批实例、多级审批、事务联动、执行任务、真实附件、Excel 导入导出、付款登记及冲销。验收结果见：

- `scripts/verification-result.json`：基础 CRUD、状态、权限、7 类数据范围、跨模块联动、CORS、审计。
- `scripts/release-verification-result.json`：Bearer、多级审批/退回/加签/转交、附件字节、Excel、付款、执行任务及新增业务联动。
- `scripts/check-migrations.sh`：隔离 schema 中验证空库初始化，并回滚。
- `scripts/check-deployment.py`：正式域名/反向代理的只读验收。

这些结果证明本地后端接口能力；不证明未执行的正式服务器部署或浏览器全流程。当前前端 `src/api/auth.ts`、`approval.ts`、`attachment.ts` 仍有 `backendPending()`，登录 store 仍使用 mock；`.env.local` 指向 3100。本仓库无权修改这些文件，必须完成 API/认证接线才能进行完整页面验收。正式域名、SSH、正式数据库账号/密码和 HTTPS 配置需要部署方提供。未经过这些检查，不应宣称整个系统已经上线。

## API 文档

- [中文接口文档](API接口文档.md)：65 个 HTTP 操作、参数、返回、权限、流程、业务联动及35模块字段。
- [OpenAPI 3.0 文件](openapi.json)：可导入接口工具；本地服务地址已配置，无需域名。
- [IntelliJ 请求示例](api.http)。
- `./scripts/check-api-docs.py`：核对文档与所有 Controller 路由，验证引用和 JSON 示例。
- `./scripts/export-api-docs.py`：后端启动后重新生成文档与元数据快照，只读查询模块元数据。

## 本地启动

```bash
./scripts/run-dev.sh
curl --fail http://127.0.0.1:8080/health
curl --fail 'http://127.0.0.1:8080/api/modules/engineeringEquipment/list?page=1&pageSize=1'
```

`/health` 的 `data.database` 必须为 `severe_equipment_assets`。默认只在 loopback 8080 监听。环境变量：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://127.0.0.1:55432/severe_equipment_assets}
    username: ${DB_USERNAME:jd_admin}
    password: ${DB_PASSWORD:jd_dev_password}
```

本地模拟身份必须是数据库中启用的账号。例：`X-User-Id: admin`。`X-User-Role` 只能选择该账号已绑定角色，`X-Data-Scope` 只能缩小到该账号授权范围。禁止伪造角色提升权限。开发匿名访问仅保留模块发现与工程设备台账读接口，以兼容现有基础接口。

首次设置账号密码：本地用管理员身份 `POST /api/users/{id}/password`，JSON `{"password":"至少12位强密码"}`。生产只接受 Bearer；密码 BCrypt 存储，token 数据库存 SHA-256，退出/改密立即失效。连续失败锁定账号，IP 失败次数限流。验收使用独立 QA 账号，不重置管理员密码。历史验收设置过的 `QA-Dev-Only-2026!` 管理员密码必须轮换，生产启动会拒绝此密码。

## 统一数据与权限

普通返回：`{"success":true,"data":...,"message":""}`。列表兼容外层 `list/total`，详情兼容外层 `id/cells/payload`，变更兼容 `record`。错误：`{"success":false,"message":"...","code":"FORBIDDEN"}`。无按钮权限返回 403；记录不在数据范围内返回 404，避免泄露记录存在性。非法流转、重复业务编号、库存不足、超额付款返回 409。下载/Excel 返回二进制，不包装 JSON。

`app_users → app_user_roles → app_roles → app_role_permissions → app_permissions` 是唯一授权来源。角色包含超级管理员、内部管理、财务、采购、设备主管、仓库、供应商、司机、操作员、审批人。授权同时覆盖菜单/模块、按钮和记录范围。范围支持 company/project/warehouse/supplier/own/todo/department；另支持 projectWarehouse 交集。本人待办可见申请、待办源单据及源单据关联的设备。

`payload` JSONB 保存完整表单；`cells` 按 `app_module_columns.sort_order` 返回；新增生成编号，编辑保持 UUID，核心记录软删除。不接受普通编辑直接改变流程或设备占用状态。账号、角色、审批模板、审批/我的申请/付款记录/审计/报表列表从真实业务表投影，不能用普通 CRUD 伪造系统记录。

所有 SQL 参数化。写入及审批副作用在 Service 事务内；库存条件更新、设备行锁/占用唯一键、付款余额约束与流水唯一键防止重复执行。失败联动连同审批状态、待办、日志一起回滚；请求失败另写操作日志。

## API 清单

| 类别 | 接口 |
|---|---|
| 基础 | GET `/health`；GET `/api/modules` |
| 记录 | GET `/api/modules/{moduleId}/list?page=1&pageSize=10&keyword=&status=`；GET `/{moduleId}/{id}`；POST `/{moduleId}`；PUT/PATCH/DELETE `/{moduleId}/{id}` |
| 状态 | POST `/api/modules/{moduleId}/{id}/{saveDraft\|submit\|approve\|reject\|void\|archive\|startReview}` |
| 导入导出 | POST `/api/modules/{moduleId}/import`（JSON 数组或 multipart file）；POST `/export`（JSON）；POST `/export/excel`（XLSX） |
| 登录 | POST `/api/auth/login`（account/password）；POST `/auth/logout`；GET `/auth/me`；GET `/auth/permissions` |
| 账号角色 | GET/POST `/api/users`、`/api/roles`；GET/PUT `/users/{id}`、`/roles/{id}`；POST `/users/{id}/password`；GET/POST `/roles/{id}/permissions` |
| 审批 | GET/POST `/api/approval/templates`；PUT `/templates/{id}`；GET `/flow?businessType=&businessId=`；POST `/instances`；POST `/tasks/{id}/{approve\|reject\|return\|transfer\|addSign}` |
| 审计 | GET `/api/audit/logs?moduleId=&recordId=&operator=&action=` |
| 待办 | GET `/api/todos`（管理）；GET `/todos/my`；POST `/todos/{id}/complete`（审批/执行待办须用专用动作） |
| 执行 | POST `/api/modules/{vehicleDispatch\|machineDispatch}/{id}/complete` |
| 附件 | POST `/api/attachments`（moduleId/recordId/file）；GET `/api/attachments?moduleId=&recordId=`；GET `/attachments/{id}/{preview\|download}`；DELETE `/attachments/{id}` |
| 财务 | GET `/api/finance/settlements`；POST `/settlements/{id}/confirm`；GET/POST `/settlements/{id}/payments`；POST `/payments/{id}/reverse` |
| 工作台 | GET `/api/workbench`、`/api/dashboard/stats`；GET `/api/reports/daily-equipment?date=YYYY-MM-DD` |
| 通知 | GET `/api/notifications`；POST `/notifications/{id}/read` |

表中记录相对路径都位于 `/api/modules` 下。账号/角色使用字符串 ID，业务记录/任务/附件使用 UUID。`iot` 仅提供模块外部来源标记，其数据操作返回外部 GPS 提示，不返回原型定位数据。`iotRules` 可存规则配置；本服务没有 GPS 告警采集与执行器。

模板节点：`{nodeName,assignees:[userId],mode:"all"|"any",minAmount?,maxAmount?}`。每模块最多一个启用模板，提交时快照模板。无模板使用数据库中合格审批人单级审批；上线应配置正式模板。转交/加签需要 `assignee`；退回需要 `targetStep`（-1 退回申请人；非负退回更早节点）。会签所有人完成才流转，审批周期防止复用旧审批结果，普通 approve/reject 不能绕过实例。

## 业务闭环与额外字段

通用状态：draft/submitted/reviewing/approved/rejected/voided/archived。规则必须存在于 `app_workflow_transitions`。业务状态如 equipmentStatus、qualified、executionStatus 与单据审批状态分别保存，避免将“设备可用”伪装成“审批通过”。历史非流程状态已保存在 legacyStatus/businessStatus；历史资料需人工复核，系统不自动视为真实审批。

| 模块/链路 | 已实现的联动及关键字段 |
|---|---|
| supplier | 必填证照上传，审批后 qualified=true；后续 supplierId 必须指向 approved 合格供应商 |
| person | 审批校验证照/保险有效期，qualified=true；执行人员 userId 绑定真实账号 |
| inbound / purchaseInbound | assetNo+supplierId；写入/更新台账与入库存档；assetType=vehicle/车辆 写车辆台账，否则工程设备；partNo+warehouseId+quantity 写备件库存 |
| engineeringEquipment / vehicleEquipment | 完整档案、唯一资产号、导入；占用期间禁止直接编辑/删除；设备运行状态由业务动作维护 |
| partsPurchase | 审批生成 purchaseInbound 草稿及结算预留，关联供应商/项目/类别/金额 |
| warehouse / parts | stockAction=inbound/outbound/transfer；partNo/quantity/warehouseId，调拨需 targetWarehouseId；更新双仓余额与备件列表；不足整体回滚 |
| dispatch / assetLife 调拨 | equipmentId+location+custodian；更新地点与保管单位，禁止占用设备调拨 |
| maintenance / workorder | 提交占用设备，审批完成释放；审批 body.equipmentStatus=available/disabled；驳回/作废恢复提交前状态 |
| vehicleDispatch / machineDispatch | equipmentId，driverId+driverUserId 或 operatorId+operatorUserId；人员 approved+未过期，账号匹配且有 complete 权限；只有可用同类设备可派工；完成需 completedAt（含时区）、actualHours/actualDistanceKm，落 usage 并释放占用 |
| rental / contract | equipmentId+amount+startDate+endDate+lessee；生效占用，归档必须 contractEnded=true 才释放；非租赁合同仅生成结算预留 |
| returnPool | equipmentId+contractId；校验该合同当前占用后释放设备 |
| assetLife | 盘点留证；调拨更新位置；减值 carryingValue；报废/处置禁用设备 |
| inspection / standards | inspectionResult=passed/failed；失败禁用设备；标准审批后发布留痕 |
| expenseReimburse / outsourcePayment | 审批形成结算预留，金额/币种存档 |
| expensePaymentRecord | 从真实银行流水登记生成，confirm 应付金额/币种；payments amount/reference/paidAt；防超额/重复；reverse reason/reference，单次冲销并更新付款记录 |
| notice | 审批发布站内信，recipientIds 必须是真实启用账号；真实未读/已读；短信/邮件/APP 未配置服务商会明确拒绝 |
| 审批/账号/审计/报表 | 专用表维护与列表投影；权限接口、任务接口及审计接口直接查询数据库 |

财务实现的是应付预留、支付登记、退款冲销，不包含总账、会计凭证、税务、银行自动划款。API 登记必须使用真实银行流水。本系统不凭审批自动声称钱款已支付。统计工时/里程来自执行回报，不伪造 GPS 采集。

附件上限 5MB，磁盘 UUID 存储键、数据库 SHA-256；上传失败清理未提交文件；预览只开放安全媒体类型；删除保留存档但禁止下载。旧附件 name/storageKey 元数据接口仅兼容历史登记，不等于真实文件上传，不能满足必填附件校验。导入 xls/xlsx/json 上限 500 条，拒绝公式与重复表头，整批失败回滚；导出上限 10000 条。

## 验收

### 每次提交前的自动检查

运行 `bash scripts/check-before-commit.sh`，检查 Git 空白错误、Controller 与接口文档一致性，并执行 Maven `clean verify`（编译、单元测试、打包）。任一检查失败返回非零状态；没有测试也会失败。这组检查不启动服务、不连接数据库，不等同于完整业务验收。

首次在本地启用 Git 钩子：`git config --local core.hooksPath .githooks`。钩子随仓库保存，但每个新克隆都需要执行一次启用命令。IDEA 和终端的普通 Git 提交都会执行钩子。提交前应将准备提交的修改加入暂存区；若已跟踪文件仍有未暂存修改，钩子会阻止提交，避免检查内容与提交内容不一致。新增源码和测试也须加入暂存区。

IDEA 可取消提交检查中的“分析代码”和“检查 TODO”，保留 Git 钩子作为自动门禁。警告仍在编辑器中显示。不要使用 `--no-verify` 绕过失败。首次运行可能需要 Maven 下载测试依赖。

现有 `verify-backend.py`、`verify-release.py` 会调用真实服务并创建 QA 数据；在专用测试数据库/服务中执行，作为发布前的业务验收。当前单元测试覆盖异常响应中的权限状态、参数错误和数据库错误信息保护，业务审批、库存并发、付款上限等仍需要逐步补充隔离测试。

```bash
./scripts/verify-backend.py
./scripts/verify-release.py
PG_JDBC_JAR=/path/to/current/postgresql-driver.jar ./scripts/check-migrations.sh
./scripts/verify-backend.py --status
```

QA 记录通过 X-Test-Data 标记，只有 system:manage 可以创建。测试账号/角色以 QA- 开头；生产排除 QA 业务数据和测试账号。测试数据为可追溯验收记录，禁止作为真实业务或正式账号使用。验收只连接指定数据库的 JDBC 驱动，不使用其他项目的 psql 客户端。构建目录在 java.io.tmpdir，不修改本项目 target。

## 同机生产部署

1. PostgreSQL 建立 `severe_equipment_assets` 和正式用户。首次迁移需要 DDL 权限；后续可由发布迁移用户执行，再给应用用户必需 DML/sequence 权限。迁移按 metadata-v1、business-v1 至 v7 执行，`app_schema_migrations` 确保重启不会覆盖已撤销授权。备份现有库后才能迁移。
2. Java 17+ 构建：`mvn package`。构建产物在 JVM 临时目录 `severe-equipment-assets-build`，把生成的可执行包复制为 `/opt/severe-equipment-assets/app.jar`。部署不要求编辑编译产物。
3. 按 `scripts/production.env.example` 配置 DB_URL（127.0.0.1:5432）、正式用户名/密码、持久附件目录；首次空库可用 APP_BOOTSTRAP_ADMIN_PASSWORD。生产拒绝开发 DB 密码、QA 管理员密码、Header 模拟身份和匿名台账读取。切勿把生产密码提交仓库。
4. 使用 `scripts/severe-equipment-assets.service.example` 安装 systemd 服务。附件目录归 equipment-api，环境文件 600。健康接口只供本机检查。
5. 前端构建环境 `VITE_API_BASE_URL=/api`、`VITE_ENABLE_BACKEND=true`，认证/审批/附件请求须先真实接线；环境变量写入构建过程，不能只改服务器运行环境期待已打包前端改变。
6. `scripts/nginx.conf.example` 把 `/api/` 转发 `127.0.0.1:8080/api/`，传递 Authorization 并清除模拟身份头。正式环境配置有效 TLS 证书与 HTTP→HTTPS 跳转；例文件中的域名需替换。
7. 本机 `curl http://127.0.0.1:8080/health`；正式域名：`API_BASE=https://正式域名 API_TOKEN=真实登录令牌 ./scripts/check-deployment.py`。生产 `/api/modules` 需要 Bearer，匿名 curl 被拒绝是预期授权行为。
8. 定期运行 `backup-db.sh`，使用正式 PostgreSQL 客户端/pgpass；为 DB+附件一致性先暂停写入或使用一致性快照。必须演练数据库及附件恢复。代码回滚不代表可逆数据库迁移；回滚要基于备份及兼容性评估。

发布前须确认：正式模板与角色授权、正式管理员强密码、历史业务资料复核、前端实际操作接线、域名 HTTPS、进程重启恢复、DB/附件备份恢复。缺少上述证据时保持“待正式上线验收”，不要将 localhost API 验收当作服务器上线。
