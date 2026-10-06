# Severe Equipment Assets API 接口文档

生成日期：2026-10-05。依据当前 Java Controller/Service 和本地运行数据库元数据。

接口数量：65 个 HTTP 操作，53 个路径模板。动态 moduleId 覆盖35个元数据模块；这不是35套独立Controller。

本地后端：`http://127.0.0.1:8080`；前端：`http://localhost:5173/workbench`；数据库：`severe_equipment_assets`。当前未购买服务器/域名，所有联调可以先在本机完成。GPS接口由外部系统提供。

配套文件：`openapi.json`（可导入接口工具）、`api.http`（IntelliJ请求样例）、`scripts/api-module-schema.json`（本次运行元数据快照）。

## 1. 认证、数据范围与返回约定

推荐所有业务请求发送 `Authorization: Bearer <token>`。先调用 `POST /api/auth/login` 获取令牌。开发阶段也可用 `X-User-Id`，必须对应数据库启用账号；生产禁止模拟身份。`X-User-Role` 只能选已绑定角色，`X-Data-Scope` 只能使用授权范围，不是前端可自行提升的权限。

| 范围值 | 含义 |
|---|---|
| `company` | 全公司 |
| `project` | 所属项目 |
| `warehouse` | 所属仓库 |
| `supplier` | 所属供应商 |
| `own` | 本人创建 |
| `todo` | 本人申请/待办及关联设备 |
| `department` | 本部门 |
| `projectWarehouse` | 所属项目与仓库交集 |

JSON成功常规返回：
```json
{"success":true,"data":{},"message":""}
```

列表保留外层 `list/total`，详情保留外层记录字段，变更保留 `record`，便于现有客户端兼容。删除接口可能仅有 success/message。附件与Excel成功返回二进制。不要假设所有响应只需读取 data。

| HTTP | 含义 |
|---|---|
| 200 | 请求成功；新增当前也使用200，不是201 |
| 400 | 参数/必填/日期/选项/文件格式错误 |
| 401 | 未登录、无效/过期/已撤销令牌 |
| 403 | 按钮权限不足、禁止模拟身份、伪造角色/范围 |
| 404 | 记录不存在或不在范围内；用于隐藏存在性 |
| 409 | 状态、唯一约束、库存不足、占用、超额付款等业务冲突 |
| 413 | 上传请求超过服务端大小限制 |
| 429 | 登录IP失败次数过多 |
| 500 / 503 | 服务端错误/暂时不可用；iot数据接口明确提示外部来源 |

```json
{"success":false,"message":"无权限操作","code":"FORBIDDEN","error":{"code":"FORBIDDEN","message":"无权限操作"}}
```

## 2. 全量接口清单

权限表达式表示后端检查，不是依赖前端隐藏按钮。未单列身份的业务接口也必须识别身份。每条权限还受记录范围及当前状态限制。

| 类别 | 方法 | 路径 | 用途 | 权限 | 请求体模型 |
|---|---|---|---|---|---|
| 基础 | GET | `/health` | 数据库健康检查 | 无需身份 | — |
| 基础 | GET | `/api/modules` | 可见模块、字段、列和流程元数据 | 各模块 view | — |
| 业务记录 | GET | `/api/modules/{moduleId}/list` | 分页列表 | {moduleId}:view | — |
| 业务记录 | GET | `/api/modules/{moduleId}/{id}` | 记录详情 | {moduleId}:view + 范围 | — |
| 业务记录 | POST | `/api/modules/{moduleId}` | 新增草稿 | {moduleId}:create | RecordInput |
| 业务记录 | PUT | `/api/modules/{moduleId}/{id}` | 合并编辑 | {moduleId}:edit | RecordInput |
| 业务记录 | PATCH | `/api/modules/{moduleId}/{id}` | 合并编辑 | {moduleId}:edit | RecordInput |
| 业务记录 | DELETE | `/api/modules/{moduleId}/{id}` | 软删除 | {moduleId}:delete | — |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/saveDraft` | 保存草稿 | {moduleId}:edit | RecordInput |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/submit` | 提交并创建审批实例 | {moduleId}:submit | — |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/approve` | 审批当前任务 | {moduleId}:approve | Action |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/reject` | 驳回当前任务 | {moduleId}:reject | Action |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/void` | 作废并取消审批 | {moduleId}:void | Action |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/archive` | 归档 | {moduleId}:archive | Action |
| 状态流转 | POST | `/api/modules/{moduleId}/{id}/startReview` | 进入审核中 | {moduleId}:approve | Action |
| 导入导出 | POST | `/api/modules/{moduleId}/import` | 整批导入 | import + create + view | — |
| 导入导出 | POST | `/api/modules/{moduleId}/export` | JSON导出 | {moduleId}:export + view | Filter |
| 导入导出 | POST | `/api/modules/{moduleId}/export/excel` | XLSX导出 | {moduleId}:export + view | Filter |
| 认证 | POST | `/api/auth/login` | 密码登录 | 无需已登录身份 | Login |
| 认证 | POST | `/api/auth/logout` | 退出并撤销当前令牌 | 已认证 | — |
| 认证 | GET | `/api/auth/me` | 当前身份、角色和范围 | 已认证 | — |
| 认证 | GET | `/api/auth/permissions` | 当前账号权限点 | 已认证 | — |
| 账号角色 | GET | `/api/users` | 账号列表 | system:manage | — |
| 账号角色 | POST | `/api/users` | 账号创建 | system:manage | UserInput |
| 账号角色 | GET | `/api/users/{id}` | 账号详情 | system:manage | — |
| 账号角色 | PUT | `/api/users/{id}` | 账号更新 | system:manage | UserInput |
| 账号角色 | GET | `/api/roles` | 角色列表 | system:manage | — |
| 账号角色 | POST | `/api/roles` | 角色创建 | system:manage | RoleInput |
| 账号角色 | GET | `/api/roles/{id}` | 角色详情 | system:manage | — |
| 账号角色 | PUT | `/api/roles/{id}` | 角色更新 | system:manage | RoleInput |
| 账号角色 | POST | `/api/users/{id}/password` | 设置或修改密码 | 本人正确原密码，或 system:manage | Password |
| 账号角色 | GET | `/api/roles/{id}/permissions` | 角色权限点 | system:manage | — |
| 账号角色 | POST | `/api/roles/{id}/permissions` | 替换角色全部权限 | system:manage | Permissions |
| 审批 | GET | `/api/approval/templates` | 模板列表 | approvalTemplate:view | — |
| 审批 | POST | `/api/approval/templates` | 创建模板 | approvalTemplate:edit | Template |
| 审批 | PUT | `/api/approval/templates/{id}` | 更新模板 | approvalTemplate:edit | Template |
| 审批 | GET | `/api/approval/flow` | 查看最新审批实例 | 源模块 view + 创建者/参与者或记录范围 | — |
| 审批 | POST | `/api/approval/instances` | 提交源单据并创建审批实例 | 源模块 submit + view | Instance |
| 审批 | POST | `/api/approval/tasks/{id}/approve` | 审批通过 | 源模块 approve + 本人当前待办 | Action |
| 审批 | POST | `/api/approval/tasks/{id}/reject` | 驳回 | 源模块 reject + 本人当前待办 | Action |
| 审批 | POST | `/api/approval/tasks/{id}/return` | 退回 | 源模块 reject + 本人当前待办 | Action |
| 审批 | POST | `/api/approval/tasks/{id}/transfer` | 转交 | 源模块 approve + 本人当前待办 | Action |
| 审批 | POST | `/api/approval/tasks/{id}/addSign` | 加签 | 源模块 approve + 本人当前待办 | Action |
| 审计待办 | GET | `/api/audit/logs` | 查询审计 | system:audit | — |
| 审计待办 | GET | `/api/todos` | 全部待办 | system:manage | — |
| 审计待办 | GET | `/api/todos/my` | 本人待办 | 已认证 | — |
| 审计待办 | POST | `/api/todos/{id}/complete` | 完成普通待办 | 本人任务 | — |
| 附件 | POST | `/api/attachments` | 真实文件上传 | 源模块 edit + view + 范围 | — |
| 附件 | GET | `/api/attachments` | 附件元数据列表 | 源模块 attachments + view + 范围 | — |
| 附件 | GET | `/api/attachments/{id}/preview` | 预览 | 源模块 attachments + view + 范围 | — |
| 附件 | GET | `/api/attachments/{id}/download` | 下载 | 源模块 attachments + view + 范围 | — |
| 附件 | DELETE | `/api/attachments/{id}` | 软删除附件 | 源模块 edit + view + 范围 | — |
| 附件 | GET | `/api/modules/{moduleId}/{id}/attachments` | 兼容附件列表 | {moduleId}:attachments + view | — |
| 附件 | POST | `/api/modules/{moduleId}/{id}/attachments` | 兼容附件元数据登记 | {moduleId}:edit + view | AttachmentMetadata |
| 执行任务 | POST | `/api/modules/{moduleId}/{id}/complete` | 执行任务完成 | {moduleId}:complete + view + 范围 + 实际执行账号或 system:manage | Execution |
| 财务 | GET | `/api/finance/settlements` | 结算预留列表 | expensePaymentRecord:view + 来源记录范围 | — |
| 财务 | POST | `/api/finance/settlements/{id}/confirm` | 确认应付金额 | expensePaymentRecord:approve + 源模块 view/范围 | Confirm |
| 财务 | POST | `/api/finance/settlements/{id}/payments` | 登记银行付款 | expensePaymentRecord:approve + 源模块 view/范围 | Payment |
| 财务 | GET | `/api/finance/settlements/{id}/payments` | 付款与冲销记录 | expensePaymentRecord:view + 源模块 view/范围 | — |
| 财务 | POST | `/api/finance/payments/{id}/reverse` | 冲销一笔付款 | expensePaymentRecord:void + 源模块 view/范围 | Reverse |
| 统计通知 | GET | `/api/workbench` | 工作台真实统计 | dashboard:view | — |
| 统计通知 | GET | `/api/dashboard/stats` | 工作台真实统计 | dashboard:view | — |
| 统计通知 | GET | `/api/reports/daily-equipment` | 指定日期设备分析 | dailyReport:view + 授权范围 | — |
| 统计通知 | GET | `/api/notifications` | 本人通知 | notice:view | — |
| 统计通知 | POST | `/api/notifications/{id}/read` | 本人通知已读 | notice:view + 本人接收 | — |

## 3. 请求模型与示例

模型中的JSON示例是调用结构，示例ID必须替换为真实ID。动态表单字段与选项以运行时 `/api/modules` 为准。

### Login

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `account` | string | 是 |  |
| `password` | string | 是 |  |

```json
{
  "account": "your-account",
  "password": "your-real-password"
}
```

### RecordInput

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 否 |  |
| `projectName` | string | 否 |  |
| `payload` | object | 否 |  |
| `status` | string | 否 | 新增仅 draft；编辑不得改变当前状态 |

```json
{
  "title": "设备草稿",
  "projectName": "PROJECT-001",
  "payload": {
    "资产编号": "EQ-202610-001",
    "生产厂家": "设备制造商",
    "设备型号": "MODEL-A",
    "设备规格": "规格说明",
    "采购单位": "采购单位",
    "管理单位": "保管单位",
    "现设备地点": "仓库A",
    "采购/到场日期": "2026-10-05",
    "在用/闲置": "闲置",
    "完好状态": "正常"
  }
}
```

### UserInput

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 否 | 创建必填；更新以路径 ID 为准 |
| `name` | string | 是 |  |
| `enabled` | boolean | 否 |  默认：True |
| `projectName` | string | 否 |  |
| `warehouseId` | string | 否 |  |
| `supplierId` | string | 否 |  |
| `departmentId` | string | 否 |  |
| `roles` | array | 否 | 提供时替换全部角色绑定 |
| `dataScope` | string | 否 |  可选：company, project, warehouse, supplier, own, todo, department, projectWarehouse |

```json
{
  "id": "operator-001",
  "name": "操作员姓名",
  "enabled": true,
  "roles": [
    "operator"
  ],
  "projectName": "PROJECT-001",
  "warehouseId": "WH-001",
  "supplierId": null,
  "departmentId": "DEPT-001",
  "dataScope": "todo"
}
```

### RoleInput

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 否 | 创建必填 |
| `name` | string | 是 |  |
| `dataScope` | string | 否 |  可选：company, project, warehouse, supplier, own, todo, department, projectWarehouse 默认：own |

```json
{
  "id": "project-reviewer",
  "name": "项目审批人",
  "dataScope": "project"
}
```

### Password

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `password` | string | 是 | 最多72 UTF-8字节 |
| `currentPassword` | string | 否 | 普通账号修改本人密码必填；system:manage 可重置 |

```json
{
  "currentPassword": "original-password",
  "password": "new-strong-password"
}
```

### Permissions

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `permissions` | array | 否 | 完整替换；空数组或省略 permissions 会清空授权 |

```json
{
  "permissions": [
    "engineeringEquipment:view",
    "engineeringEquipment:create",
    "engineeringEquipment:submit"
  ]
}
```

### Template

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `moduleId` | string | 是 |  |
| `name` | string | 否 |  默认：审批模板 |
| `enabled` | boolean | 否 |  默认：True |
| `nodes` | array | 是 |  |

```json
{
  "moduleId": "engineeringEquipment",
  "name": "设备审批",
  "enabled": true,
  "nodes": [
    {
      "nodeName": "设备主管初审",
      "assignees": [
        "reviewer-001"
      ],
      "mode": "any"
    },
    {
      "nodeName": "终审",
      "assignees": [
        "reviewer-002",
        "reviewer-003"
      ],
      "mode": "all",
      "minAmount": 0
    }
  ]
}
```

### Instance

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `businessType` | string | 是 |  |
| `businessId` | string | 是 |  |

```json
{
  "businessType": "engineeringEquipment",
  "businessId": "00000000-0000-0000-0000-000000000001"
}
```

### Action

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `comment` | string | 否 |  |
| `assignee` | string | 否 |  |
| `targetStep` | integer | 否 |  默认：-1 |
| `equipmentStatus` | string | 否 |  可选：available, disabled |
| `contractEnded` | boolean | 否 |  |

```json
{
  "comment": "审核说明"
}
```

### Filter

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 否 |  |
| `status` | string | 否 |  |

```json
{
  "keyword": "EQ-",
  "status": "approved"
}
```

### Execution

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `completedAt` | string | 是 |  |
| `actualHours` | number | 否 |  默认：0 |
| `actualDistanceKm` | number | 否 |  默认：0 |
| `comment` | string | 否 |  |

```json
{
  "completedAt": "2026-10-05T10:30:00+04:00",
  "actualHours": 2.5,
  "actualDistanceKm": 25,
  "comment": "实际完成"
}
```

### Confirm

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `amount` | string | 是 |  |
| `currency` | string | 否 | 省略沿用当前币种 可选：AED, SAR, USD, CNY |

```json
{
  "amount": "100.00",
  "currency": "AED"
}
```

### Payment

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `amount` | string | 是 | 必须大于0 |
| `reference` | string | 是 |  |
| `paidAt` | string | 是 |  |

```json
{
  "amount": "40.00",
  "reference": "REAL-BANK-REFERENCE",
  "paidAt": "2026-10-05T10:30:00+04:00"
}
```

### Reverse

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `reason` | string | 是 |  |
| `reference` | string | 是 |  |

```json
{
  "reason": "退款原因",
  "reference": "REAL-REFUND-REFERENCE"
}
```

### AttachmentMetadata

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 |  |
| `storageKey` | string | 是 |  |

```json
{
  "name": "历史证照.pdf",
  "storageKey": "legacy-storage-key"
}
```

UserInput/RoleInput：创建额外要求 id。用户PUT会将未提供的 projectName/warehouseId/supplierId/departmentId 写为NULL，enabled默认true；请先读取用户，带齐需要保留的属性。roles提供时完全替换，省略则保留。角色PUT省略dataScope会使用own。账号读接口使用project_name等下划线字段，不会返回password_hash。普通用户没有system:manage时不能通过账号管理GET读取自己，使用/auth/me。

RecordInput：示例是工程设备提交所需字段的当前形状；其他模块不可套用。首次POST只保存草稿；审批状态必须使用动作接口，不能PUT status=approved。PUT/PATCH都合并，不是整条替换；没有强制乐观版本参数。每次完整提交校验SchemaService和业务联动规则。

Action 的不同用途：

| 操作 | 附加参数 | 行为 |
|---|---|---|
| saveDraft | 平铺业务字段或payload对象 | 合并草稿，rejected可回draft |
| approve/reject | comment可选 | 处理本人当前任务；多级approve不一定使源记录approved |
| return | targetStep，默认-1；comment | -1返回申请人，非负必须小于当前step |
| transfer/addSign | assignee必填；comment可选 | 必须真实启用且有源模块审批权限；加签改为会签 |
| maintenance/workorder最终approve | equipmentStatus=available或disabled，可省略 | 控制设备维修后运行状态 |
| rental/contract archive | contractEnded=true | 确认合同结束，释放该合同设备占用 |
| void | 无必填体 | 按规则取消源单据与待办 |

## 4. 返回数据模型

### 4.1 登录与当前身份

登录：`data.token`、`tokenType=Bearer`、`expiresIn`（默认43200秒）、`user`。`/auth/me` 返回 id/account/name/profile、role/roleName、roles对象数组、dataScope、approvalNodes。`/auth/permissions`返回对象数组，例如：

```json
{"success":true,"data":[{"id":"engineeringEquipment:view","module_id":"engineeringEquipment","action":"view"}],"message":""}
```

### 4.2 模块元数据与记录

模块 data 数组每项包含 module_id/module_name/group/route信息、list_columns、form_schema、record_counts、workflow_states和workflow_transitions。实际列名有module_group、route_path等，不统一转为驼峰。form_schema含field_key、field_label、field_type、db_type、sample_value、options、required、sort_order。sample_value是表单提示，不是真实业务默认值或审批证据。

```json
{
  "success": true,
  "list": [
    {
      "id": "00000000-0000-0000-0000-000000000001",
      "recordNo": "ENGINEER-...",
      "title": "设备草稿",
      "status": "draft",
      "payload": {
        "资产编号": "EQ-202610-001"
      },
      "cells": [
        "按元数据列顺序排列"
      ],
      "createdAt": "2026-10-05T10:00:00+04:00",
      "updatedAt": "2026-10-05T10:00:00+04:00"
    }
  ],
  "total": 1,
  "data": {
    "list": [
      {
        "id": "00000000-0000-0000-0000-000000000001",
        "recordNo": "ENGINEER-...",
        "title": "设备草稿",
        "status": "draft",
        "payload": {
          "资产编号": "EQ-202610-001"
        },
        "cells": [
          "按元数据列顺序排列"
        ],
        "createdAt": "2026-10-05T10:00:00+04:00",
        "updatedAt": "2026-10-05T10:00:00+04:00"
      }
    ],
    "total": 1
  },
  "message": ""
}
```

上例为结构示意；实际data.list与外层list相同，均为记录对象数组。详情的data是记录本身，并兼容外层id/recordNo/title/status/payload/cells。新增/编辑/通用状态操作通常同时有record与data，审批任务接口返回data为审批流。

投影模块会额外有sourceId：users/roleperm的列表UUID是展示ID，账号/角色管理必须使用sourceId字符串。approval/expenseTodo列表id是taskId；源业务记录是payload.record_id，源模块是payload.module_id。expensePaymentRecord列表id是付款存档记录ID，payments操作使用payment_id或payload.paymentId；不要混用。dailyReport展示ID由日期生成，只在该日期对应报表存在时可再取详情。

### 4.3 审批流与待办

审批流data：id（instanceId）、record_id、module_id、template_id、nodes、current_step、current_cycle、status、created_by、created_at、updated_at、tasks。tasks项：id、instance_id、step、cycle、assignee、status、comment、completed_at、created_at、todo_id。判断有效待办需实例pending、任务pending且step/cycle等于当前实例。

任务批准的响应示意：

```json
{"success":true,"data":{"id":"审批实例UUID","record_id":"源记录UUID","module_id":"engineeringEquipment","current_step":1,"current_cycle":1,"status":"pending","nodes":[{"nodeName":"终审","assignees":["reviewer-002"],"mode":"any"}],"tasks":[]},"message":"审批操作已存档"}
```

/todos项：id、record_id、assignee、status、created_at、completed_at。todoId、taskId、recordId是不同ID，审批不能调用/todos/{id}/complete替代。

### 4.4 审计

data日志数组：id（数值）、operator、role、module_id、record_id、action、before_status、after_status、before_data、after_data、created_at、request_no、result、error_message。action参数精确匹配，例如create/edit/submit/approve/reject/void/archive/import/export或approvalTask:approve。后端也记录敏感查询、附件和跨模块动作。

### 4.5 附件与导入导出

上传data：id、name、size；列表data项：id、name、storage_key、created_at。multipart参数名必须为moduleId、recordId、file。必填file表单字段填本单据已上传附件UUID或UUID数组，不填本地路径。下载/预览为二进制，客户端responseType=blob。文件名不能当作上传完成证据。

import返回data.batchId、recordIds；记录为draft。export返回data.records、total，响应为JSON，不能把这个端点的blob直接命名为.xlsx。下载Excel使用/export/excel。Excel表头使用业务字段名/field_key，拒绝公式、空或重复表头；最多200列、500行导入，10000行导出。

### 4.6 财务、执行和通知

settlements data数组或confirm data对象：id、source_id、payload、status、amount、currency、paid_amount、created_at。amount可能NULL，必须先confirm再pay。status为pending/partial/paid。付款data：id、settlement_id、amount、reference、paid_at、created_by、created_at；付款查询还含reversal_id/reversal_reason。reverse返回data.id（冲销UUID）。

complete返回data.completedAt/actualHours/actualDistanceKm/comment?/executionStatus=completed，源单据审批状态仍approved，之后才archive。complete仅支持两种执行调度；不是GPS实时数据上报。

notifications项：id、user_id、title、message、record_id、read_at、created_at。已读动作返回data={}。来源通知发布仍由notice业务草稿提交审批；已配置渠道仅站内信，短信/邮件/APP推送会被明确拒绝。

workbench data：moduleStatusCounts（module_id/status/count）、myPendingTodos、unreadNotifications、source=PostgreSQL。daily data：date、updatedRecordCounts、reportedWorkHours、reportedDistanceKm、maintenanceDocuments、gpsSource（外部API、不采集）。

## 5. 状态规则与模块业务补充

普通业务状态与设备状态分别存储。以模块元数据workflow_transitions为准，当前通用规则：

| 起始 | 动作 | 目标 |
|---|---|---|
| draft/rejected | saveDraft | draft |
| draft/rejected | submit | submitted |
| submitted | startReview | reviewing |
| submitted/reviewing | approve | approved（只在最终审批节点） |
| submitted/reviewing | reject | rejected |
| draft/rejected/submitted/reviewing | void | voided |
| approved/voided | archive | archived |

通用DELETE仅draft/rejected/voided/archived；占用设备禁止。write失败全部回滚；支付、设备、库存联动成功与源状态同事务。

| 场景 | 业务字段及前置条件 |
|---|---|
| 合格供应商 | supplierId=已approved供应商记录UUID；新供应商提交先上传必填证照 |
| 设备入库 | assetNo或资产编号、supplierId；assetType=vehicle/车辆写车辆台账，其他写工程设备；型号/厂家/位置随payload归档 |
| 备件入库 | partNo、warehouseId、quantity>0；写库存及存档 |
| 备件调拨/出库 | stockAction=transfer/outbound、partNo、warehouseId、quantity；transfer另需targetWarehouseId且不能同仓 |
| 普通设备调拨 | equipmentId、location、custodian；同步设备位置与保管单位 |
| 维修/作业 | equipmentId；提交占用，完成审批释放；作业工单workType=operation |
| 出租/租赁合同 | equipmentId、amount、startDate/endDate（YYYY-MM-DD）、lessee；不能占用已被另一单据占用设备 |
| 退租 | equipmentId、contractId；必须匹配该合同当前设备占用 |
| 车辆执行 | equipmentId车辆台账、driverId人员资质UUID、driverUserId账号ID；资质userId与账号匹配、未过期 |
| 工程设备执行 | equipmentId工程台账、operatorId资质UUID、operatorUserId账号ID；资质已approved、未过期 |
| 人员资质 | 人员类型、证照/保险有效期；userId可绑定执行账号 |
| 资产事项 | 事项类型=盘点/调拨/减值/报废/处置；减值需carryingValue，调拨需location/custodian |
| 检查 | equipmentId、inspectionResult=passed/failed；失败停用；占用中的设备须先结束业务 |
| 通知发布 | recipientIds真实账号数组、发送渠道=站内信，其他元数据必填字段完整 |

## 6. 本地联调顺序

1. 运行 ./scripts/run-dev.sh；访问/health确认数据库。
2. 用真实密码登录取得token；查询/auth/me与/auth/permissions。
3. GET /api/modules，取moduleId的form_schema与list_columns。
4. POST业务草稿取得record.id；上传必填附件，PATCH附件UUID及完整表单。
5. POST /modules/{moduleId}/{id}/submit。
6. 审批人GET /approval/flow取得本人有效taskId，再调用tasks动作；多级逐节点完成。
7. 查询源记录、目标台账/库存/结算、audit/logs；确认联动一致。
8. 用受限账号复测403、范围外404、非法状态409。
9. 使用对应执行账号回报完成；真实银行流水登记与冲销使用财务账号。

```bash
curl --fail http://127.0.0.1:8080/health
curl --fail -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/modules
curl --fail -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:8080/api/modules/engineeringEquipment/list?page=1&pageSize=1"
```

localhost与127.0.0.1的5173前端跨域已允许。不需要购买域名即可本地验证。正式部署时再配置DB_URL/DB_USERNAME/DB_PASSWORD、prod认证、持久附件目录、/api反代与HTTPS。正式账户凭据不写入本文档。

## 7. 前端 mock 的准确含义与接线差异

本次只核对 ../WEB_Equipment_Assets，未修改任何前端文件。

| 功能 | 当前源码 | 当前行为 | 接入目标 |
|---|---|---|---|
| 登录 | src/stores/app.ts:1162；src/api/auth.ts | mockApprovalUsers匹配账号密码，localStorage保存静态登录标记；auth函数backendPending | POST/auth/login，保留真实token；GET/auth/me/permissions；logout撤销会话 |
| 审批 | src/api/approval.ts | 查询/approve/reject/return/transfer/addSign返回backendPending，不发真实HTTP | /api/approval/flow、instances、tasks/{id}/动作；响应data按本文处理 |
| 附件 | src/api/attachment.ts | upload/list/preview/download/delete均占位 | multipart真实上传与授权下载，保存真实附件UUID |
| 列表及基础CRUD | src/api/modules.ts | enableBackend=true时已有HTTP分支；false时读pages原型rows | 使用实际Java地址与身份；这些功能并非一概mock |
| API地址与身份请求 | .env.local；src/api/http.ts | baseURL=http://127.0.0.1:3100/api（该端口有Node服务）；axios当前没有Bearer注入 | 核对3100是否为代理；或直连8080/api/代理/api；登录token加入Authorization |

localStorage本身不是错误：真实系统也可缓存语言或客户端登录状态。问题是当前身份来源为前端模拟账号，没有服务端密码验证和会话；关闭页面/刷新后显示“已登录”不等于数据库授权。后端真实接口已经存在，但前端登录、审批、附件还没有发出对应请求，因此仅修改数据库或后端不能让这些占位函数自动工作。接线只需API/认证状态/事件数据绑定层，页面布局、菜单路由、UI样式可以保持。本文没有擅自修改前端范围。

客户端其他差异：auth.permissions占位签名是string[]，后端实际返回权限对象数组；approval.getApprovalFlow占位签名是节点数组，后端实际返回包含nodes/tasks的实例对象；模块导出当前调用/export，返回JSON，Excel应调用/export/excel；approvalType查询参数尚未服务端实现，不能把它当作有效筛选。

## 8. 验收证据与范围

证据文件：scripts/verification-result.json（50项）；scripts/release-verification-result.json（34项）；scripts/security-verification-result.json（6项）。空库8个迁移版本在隔离schema通过验证后回滚。以上是已有本地验收结果，不代表逐个接口每一种组合都被覆盖，也不代表前端页面联调或正式服务器部署已完成。

## 附录：35个模块与动态表单字段

本附录来自本次运行的 /api/modules，数据库新增/修改字段后以运行接口为准。系统投影模块仍保留原元数据表单定义，但不能用普通CRUD维护；应使用专用接口。

### dashboard — 工作台首页

模式：只读投影，写入走专用接口。

cells列顺序：无列表列。

### supplier — 供应商准入

模式：业务草稿/流程/存档。

cells列顺序：供应商 → 统一社会信用代码 → 供货范围 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `supplier_name` | 供应商 | text | 否 | [] |
| `registration_no` | 统一社会信用代码 | text | 否 | [] |
| `supply_scope` | 供货范围 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `supplier_name_2` | 供应商名称 | text | 是 | [] |
| `company_english_name` | 企业英文名称 | text | 是 | [] |
| `company_local_name` | 企业当地语言名称 | language | 否 | [] |
| `supplier_short_name` | 供应商简称 | text | 否 | [] |
| `supplier_type` | 供应商类型 | select | 是 | ["工程设备供应商", "车辆运输供应商", "备件供应商", "维修服务商", "综合供应商"] |
| `company_type` | 企业类型 | select | 是 | ["LLC", "Establishment", "Sole Proprietorship", "Branch", "Free Zone Company", "Joint Stock Company"] |
| `registered_country` | 注册国家 | country | 是 | [] |
| `registered_city` | 注册城市/酋长国 | region | 是 | [] |
| `registered_address` | 企业注册地址 | textarea | 是 | [] |
| `trade_license_no` | 商业登记/营业执照编号 | text | 是 | [] |
| `license_expiry_date` | 证照有效期 | date | 是 | [] |
| `trade_license_file` | 营业执照/商业登记证上传 | file | 是 | [] |
| `tax_registration_no` | 税务登记号 | text | 否 | [] |
| `business_scope` | 企业经营范围 | textarea | 是 | [] |
| `established_date` | 成立日期 | date | 否 | [] |
| `authorized_representative` | 企业负责人/授权代表 | text | 是 | [] |
| `representative_id_type` | 负责人ID类型 | select | 是 | ["National ID", "Iqama", "Passport", "Emirates ID"] |
| `representative_id_no` | 负责人ID号码 | text | 是 | [] |
| `representative_id_file` | 负责人ID上传 | file | 是 | [] |
| `contact_name` | 联系人 | text | 是 | [] |
| `contact_phone` | 联系电话 | text | 是 | [] |
| `contact_email` | 联系邮箱 | email | 是 | [] |
| `website` | 企业网站 | url | 否 | [] |
| `company_profile` | 企业简介 | textarea | 否 | [] |

### person — 人员资质档案

模式：业务草稿/流程/存档。

cells列顺序：人员 → 岗位 → 证照有效期 → 资质状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `person_name` | 人员 | text | 否 | [] |
| `position_name` | 岗位 | text | 否 | [] |
| `license_expiry_date` | 证照有效期 | date | 是 | [] |
| `qualification_status` | 资质状态 | text | 否 | [] |
| `person_name_2` | 人员姓名 | text | 是 | [] |
| `person_type` | 人员类型 | select | 是 | ["司机", "工程设备操作员", "维修人员", "验收人员"] |
| `identity_no` | 证件号码 | text | 是 | [] |
| `mobile_phone` | 手机号 | text | 是 | [] |
| `certificate_no` | 资质证书编号 | text | 是 | [] |
| `insurance_labor_expiry_date` | 保险/劳务有效期 | date | 否 | [] |
| `expiry_reminder_at` | 到期提醒时间 | datetime-local | 是 | [] |
| `bindable_object` | 可绑定对象 | select | 是 | ["车辆设备", "工程设备", "验收任务", "维修工单"] |
| `qualification_notes` | 资质备注 | textarea | 否 | [] |

### inbound — 设备入库验收

模式：业务草稿/流程/存档。

cells列顺序：入库申请号 → 设备 → 供应商 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `inbound_no` | 入库申请号 | text | 是 | [] |
| `equipment_name` | 设备 | text | 否 | [] |
| `supplier_name` | 供应商 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `equipment_category` | 设备类别 | select | 是 | ["工程设备", "车辆车头", "车辆尾挂", "备件"] |
| `expected_arrival_at` | 预计到货时间 | datetime-local | 是 | [] |
| `actual_arrival_at` | 实际到货时间 | datetime-local | 是 | [] |
| `field_010` | 设备名称/型号 | text | 是 | [] |
| `field_011` | 出厂编号/VIN | text | 是 | [] |
| `field_012` | 库位 | text | 是 | [] |
| `field_013` | 实物验收时间 | datetime-local | 是 | [] |
| `field_014` | 技术验收时间 | datetime-local | 是 | [] |
| `field_015` | Excel导入备注 | textarea | 否 | [] |

### engineeringEquipment — 工程设备台账

模式：业务草稿/流程/存档。

cells列顺序：资产编号 → 生产厂家 → 设备型号 → 设备规格 → 出厂编号 → 采购单位 → 管理单位 → 现设备地点 → 采购/到场日期 → 发动机型号 → 发动机厂家 → 发动机号 → 功率 → 在用/闲置。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `asset_no` | 资产编号 | text | 是 | [] |
| `manufacturer` | 生产厂家 | text | 是 | [] |
| `equipment_model` | 设备型号 | text | 是 | [] |
| `equipment_spec` | 设备规格 | text | 是 | [] |
| `serial_no` | 出厂编号 | text | 否 | [] |
| `purchase_unit` | 采购单位 | text | 是 | [] |
| `management_unit` | 管理单位 | text | 是 | [] |
| `field_008` | 现设备地点 | text | 是 | [] |
| `field_009` | 采购/到场日期 | date | 是 | [] |
| `field_010` | 发动机型号 | text | 否 | [] |
| `field_011` | 发动机厂家 | text | 否 | [] |
| `field_012` | 发动机号 | text | 否 | [] |
| `field_013` | 功率 | text | 否 | [] |
| `field_014` | 在用/闲置 | select | 是 | ["在用", "闲置", "待用", "停用"] |
| `field_029` | 完好状态 | select | 是 | ["一级", "二级", "正常", "需定期保养", "大修", "损坏"] |
| `field_030` | 尺寸 | text | 否 | [] |
| `field_031` | 重量 | text | 否 | [] |

### vehicleEquipment — 车辆设备台账

模式：业务草稿/流程/存档。

cells列顺序：车辆编号 → 车头/尾挂 → 司机或绑定关系 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `vehicle_no` | 车辆编号 | text | 是 | [] |
| `field_002` | 车头/尾挂 | text | 否 | [] |
| `field_003` | 司机或绑定关系 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `vehicle_type` | 车辆类型 | select | 是 | ["牵引车头", "尾挂", "平板车", "油罐车", "工程运输车"] |
| `field_007` | 车牌/VIN | text | 是 | [] |
| `field_008` | 绑定司机 | text | 是 | [] |
| `field_009` | 匹配尾挂 | text | 否 | [] |
| `field_010` | 吨位类型 | select | 是 | ["10t", "18t", "25t", "40t", "超限运输"] |
| `field_011` | GPS设备号 | text | 是 | [] |
| `field_012` | 年检有效期 | date | 是 | [] |
| `field_013` | 保险有效期 | date | 是 | [] |
| `field_014` | 最近定位时间 | datetime-local | 是 | [] |
| `field_015` | 车辆证照与绑定说明 | textarea | 否 | [] |

### assetLife — 资产盘点处置

模式：业务草稿/流程/存档。

cells列顺序：单据号 → 资产 → 事项 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 单据号 | text | 否 | [] |
| `field_002` | 资产 | text | 否 | [] |
| `field_003` | 事项 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 业务单号 | text | 是 | [] |
| `asset_no` | 资产编号 | text | 是 | [] |
| `field_007` | 事项类型 | select | 是 | ["盘点", "调拨", "减值", "报废", "处置"] |
| `field_008` | 发起时间 | datetime-local | 是 | [] |
| `field_009` | 责任人 | text | 是 | [] |
| `field_010` | 账面价值 | number | 是 | [] |
| `field_011` | 财务复核截止时间 | datetime-local | 是 | [] |
| `field_012` | 差异/处置说明 | textarea | 否 | [] |

### workorder — 工单管理

模式：业务草稿/流程/存档。

cells列顺序：工单号 → 设备 → 来源 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `work_order_no` | 工单号 | text | 是 | [] |
| `equipment_name` | 设备 | text | 是 | [] |
| `field_003` | 来源 | select | 是 | ["IoT告警", "检查任务", "保养计划", "人工报修"] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_008` | 故障发生时间 | datetime-local | 是 | [] |
| `field_009` | 派工时间 | datetime-local | 是 | [] |
| `field_010` | 修复截止时间 | datetime-local | 是 | [] |
| `priority` | 优先级 | select | 是 | ["高", "中", "低"] |
| `field_012` | 故障描述 | textarea | 否 | [] |

### maintenance — 保养管理

模式：业务草稿/流程/存档。

cells列顺序：计划号 → 设备 → 保养项目 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 计划号 | text | 否 | [] |
| `equipment_name` | 设备 | text | 是 | [] |
| `field_003` | 保养项目 | textarea | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 保养计划号 | text | 是 | [] |
| `field_007` | 触发方式 | select | 是 | ["固定周期", "运行工时", "行驶里程", "人工计划"] |
| `field_008` | 当前工时/里程 | number | 是 | [] |
| `field_009` | 计划保养时间 | datetime-local | 是 | [] |
| `field_010` | 执行人 | text | 是 | [] |
| `field_011` | 下次保养时间 | datetime-local | 是 | [] |

### inspection — 检查任务

模式：业务草稿/流程/存档。

cells列顺序：检查单 → 对象 → 检查类型 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 检查单 | text | 否 | [] |
| `field_002` | 对象 | text | 否 | [] |
| `field_003` | 检查类型 | select | 是 | ["安全检查", "返场检查", "库存检查", "技术复验"] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 检查单号 | text | 是 | [] |
| `field_006` | 检查对象 | text | 是 | [] |
| `field_008` | 计划检查时间 | datetime-local | 是 | [] |
| `field_009` | 完成时间 | datetime-local | 否 | [] |
| `field_010` | 检查人 | text | 是 | [] |
| `field_011` | 缺陷整改截止时间 | datetime-local | 否 | [] |
| `field_012` | 检查项与缺陷 | textarea | 否 | [] |

### standards — 操作标准库

模式：业务草稿/流程/存档。

cells列顺序：标准编号 → 标准名称 → 适用对象 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 标准编号 | text | 是 | [] |
| `field_002` | 标准名称 | text | 是 | [] |
| `field_003` | 适用对象 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_007` | 标准类型 | select | 是 | ["维保SOP", "检查清单", "故障案例", "安全标准"] |
| `field_009` | 版本号 | text | 是 | [] |
| `field_010` | 复核人 | text | 是 | [] |
| `field_011` | 发布时间 | datetime-local | 是 | [] |
| `field_012` | 标准内容摘要 | textarea | 否 | [] |

### partsPurchase — 备件采购

模式：业务草稿/流程/存档。

cells列顺序：采购单 → 备件 → 供应商 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 采购单 | text | 否 | [] |
| `part_name` | 备件 | text | 否 | [] |
| `supplier_name` | 供应商 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 采购单号 | text | 是 | [] |
| `field_006` | 备件名称 | text | 是 | [] |
| `part_no` | 备件编号 | text | 是 | [] |
| `field_008` | 申请数量 | number | 是 | [] |
| `field_009` | 预计单价 | number | 是 | [] |
| `field_011` | 需求到货时间 | datetime-local | 是 | [] |
| `field_012` | 采购原因 | select | 是 | ["低库存", "维修急采", "项目备料", "替换件"] |
| `field_013` | 采购说明 | textarea | 否 | [] |

### dispatch — 调度分配

模式：业务草稿/流程/存档。

cells列顺序：需求单 → 需求项目 → 分派资源/去向 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 需求单 | text | 否 | [] |
| `field_002` | 需求项目 | text | 否 | [] |
| `field_003` | 分派资源/去向 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `dispatch_no` | 调度单号 | text | 是 | [] |
| `field_006` | 需求类型 | select | 是 | ["用车", "用机", "抢修支援", "租赁返场"] |
| `field_007` | 项目/地点 | text | 是 | [] |
| `field_008` | 资源类型 | select | 是 | ["工程设备", "车辆"] |
| `field_009` | 关联资产 | asset-any | 是 | [] |
| `field_010` | 需求开始时间 | datetime-local | 是 | [] |
| `field_011` | 预计返场时间 | datetime-local | 是 | [] |
| `applicant_name` | 申请人 | text | 是 | [] |
| `field_013` | 审批截止时间 | datetime-local | 是 | [] |
| `field_014` | 调度要求 | textarea | 否 | [] |

### vehicleDispatch — 车辆调度

模式：业务草稿/流程/存档。

cells列顺序：派车单 → 车头/尾挂 → 司机 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 派车单 | text | 否 | [] |
| `field_002` | 车头/尾挂 | text | 否 | [] |
| `driver_name` | 司机 | driver | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 派车单号 | text | 是 | [] |
| `field_006` | 车头/车辆 | asset-vehicle | 是 | [] |
| `field_008` | 尾挂 | text | 是 | [] |
| `field_009` | 吨位匹配 | select | 是 | ["10t", "18t", "25t", "40t"] |
| `field_010` | 返程货物 | text | 是 | [] |
| `field_011` | 计划发车时间 | datetime-local | 是 | [] |
| `field_012` | 预计到达时间 | datetime-local | 是 | [] |
| `field_013` | 装卸围栏生效时间 | datetime-local | 是 | [] |
| `field_014` | 运输要求 | textarea | 否 | [] |

### machineDispatch — 工程设备调度

模式：业务草稿/流程/存档。

cells列顺序：派工单 → 工程设备 → 操作员 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 派工单 | text | 否 | [] |
| `field_002` | 工程设备 | asset-engineering | 是 | [] |
| `field_003` | 操作员 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 派工单号 | text | 是 | [] |
| `field_008` | 作业地点 | text | 是 | [] |
| `field_009` | 进场时间 | datetime-local | 是 | [] |
| `field_010` | 计划开工时间 | datetime-local | 是 | [] |
| `field_011` | 计划退场时间 | datetime-local | 是 | [] |
| `field_012` | 预计工时 | number | 是 | [] |
| `field_013` | 现场要求 | textarea | 否 | [] |

### rental — 设备租赁管理

模式：业务草稿/流程/存档。

cells列顺序：租赁单 → 承租方 → 出租设备/车辆/合同 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 租赁单 | text | 否 | [] |
| `field_002` | 承租方 | text | 是 | [] |
| `field_003` | 出租设备/车辆/合同 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 租赁单号 | text | 是 | [] |
| `field_007` | 租赁类型 | select | 是 | ["工程设备租赁", "车辆租赁"] |
| `field_008` | 租赁资产 | asset-rental | 是 | [] |
| `field_009` | 租赁开始日期 | date | 是 | [] |
| `field_010` | 租赁结束日期 | date | 是 | [] |
| `field_011` | 日租金 | number | 是 | [] |
| `field_012` | 押金 | number | 是 | [] |
| `field_013` | 结算周期 | select | 是 | ["日结", "周结", "月结", "项目结"] |
| `field_014` | 合同签署截止时间 | datetime-local | 是 | [] |
| `field_015` | 租赁约定 | textarea | 否 | [] |

### returnPool — 退租回池

模式：业务草稿/流程/存档。

cells列顺序：退租单 → 设备 → 承租方 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 退租单 | text | 否 | [] |
| `equipment_name` | 设备 | text | 否 | [] |
| `field_003` | 承租方 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 退租单号 | text | 是 | [] |
| `field_006` | 关联租赁单 | text | 是 | [] |
| `field_007` | 退租资产 | asset-any | 是 | [] |
| `field_008` | 计划退租时间 | datetime-local | 是 | [] |
| `field_009` | 现场验收时间 | datetime-local | 是 | [] |
| `field_010` | 损耗扣款金额 | number | 是 | [] |
| `field_011` | 回池状态 | select | 是 | ["待验收", "待维修", "可租", "待处置"] |
| `field_012` | 验收说明 | textarea | 否 | [] |

### warehouse — 仓库管理

模式：业务草稿/流程/存档。

cells列顺序：单据号 → 仓库/库位 → 事项 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 单据号 | text | 否 | [] |
| `field_002` | 仓库/库位 | text | 否 | [] |
| `field_003` | 事项 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 仓库单据号 | text | 是 | [] |
| `warehouse_name` | 仓库 | select | 是 | ["A仓", "B仓", "临时仓", "项目现场仓"] |
| `field_007` | 库位 | text | 是 | [] |
| `field_008` | 事项类型 | select | 是 | ["入库", "出库", "调拨", "盘点", "移位"] |
| `field_009` | 关联业务单 | text | 是 | [] |
| `field_010` | 经办人 | text | 是 | [] |
| `field_011` | 业务时间 | datetime-local | 是 | [] |
| `field_012` | 库位与交接说明 | textarea | 否 | [] |

### parts — 备件库存

模式：业务草稿/流程/存档。

cells列顺序：备件编号 → 备件名称 → 可用库存 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `part_no` | 备件编号 | text | 是 | [] |
| `field_002` | 备件名称 | text | 是 | [] |
| `field_003` | 可用库存 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_007` | 备件分类 | select | 是 | ["滤芯", "油液", "电气件", "制动件", "消耗件", "结构件"] |
| `field_008` | 当前库存 | number | 是 | [] |
| `field_009` | 安全库存 | number | 是 | [] |
| `field_010` | 已预留数量 | number | 是 | [] |
| `field_011` | 适用设备 | text | 否 | [] |
| `field_012` | 库存状态 | select | 是 | ["正常", "低库存", "已预留", "停用"] |
| `field_013` | 库存规则说明 | textarea | 否 | [] |

### purchaseInbound — 采购入库

模式：业务草稿/流程/存档。

cells列顺序：到货单 → 采购单 → 供应商 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 到货单 | text | 否 | [] |
| `field_002` | 采购单 | text | 否 | [] |
| `supplier_name` | 供应商 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 到货单号 | text | 是 | [] |
| `field_006` | 采购单号 | text | 是 | [] |
| `field_008` | 到货时间 | datetime-local | 是 | [] |
| `field_009` | 到货数量 | number | 是 | [] |
| `field_010` | 质检结果 | select | 是 | ["待质检", "合格", "不合格", "退换货"] |
| `field_011` | 入库库位 | text | 是 | [] |
| `field_012` | 质检与入库说明 | textarea | 否 | [] |

### contract — 合同管理

模式：业务草稿/流程/存档。

cells列顺序：合同编号 → 合同对象 → 所属项目 → 合同类型 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `contract_no` | 合同编号 | autonumber | 是 | [] |
| `field_002` | 合同对象 | text | 是 | [] |
| `field_003` | 所属项目 | text | 是 | [] |
| `field_004` | 合同类型 | select | 是 | ["设备租赁", "备件采购", "维修服务", "运输服务", "框架协议"] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_010` | 合同金额 | number | 是 | [] |
| `field_011` | 币种 | select | 是 | ["AED", "SAR", "USD", "CNY"] |
| `field_012` | 开始日期 | date | 是 | [] |
| `field_013` | 结束日期 | date | 是 | [] |
| `field_014` | 签署方式 | select | 是 | ["线下签署", "电子签署", "补充协议"] |
| `field_015` | 条款与风险说明 | textarea | 否 | [] |

### expenseReimburse — 个人报销

模式：业务草稿/流程/存档。

cells列顺序：报销单号 → 申请人/项目 → 费用类型/期间 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 报销单号 | text | 是 | [] |
| `field_002` | 申请人/项目 | text | 否 | [] |
| `field_003` | 费用类型/期间 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_006` | 报销人 | text | 是 | [] |
| `field_007` | 所属公司/法人 | select | 是 | ["JADA UAE", "JADA Saudi", "JADA China", "项目公司"] |
| `field_008` | 所属部门 | select | 是 | ["工程部", "调度部", "财务部", "采购部", "项目部"] |
| `field_009` | 所属项目 | text | 是 | [] |
| `field_010` | 申请主题 | text | 是 | [] |
| `field_011` | 费用开始日期 | date | 是 | [] |
| `field_012` | 费用结束日期 | date | 是 | [] |
| `field_013` | 费用性质 | select | 是 | ["本人工作支出", "团队共同支出", "项目现场支出", "驻地生活支出", "个人垫付采购", "个人垫付劳务"] |
| `field_014` | 费用类型 | select | 是 | ["差旅交通", "车辆费用", "住宿费用", "餐饮生活", "办公通讯", "劳保保障", "驻地后勤", "项目现场零星", "个人垫付采购", "个人垫付劳务"] |
| `field_015` | 币种 | select | 是 | ["CNY", "SAR", "AED", "USD"] |
| `field_016` | 原币合计 | number | 是 | [] |
| `field_017` | 汇率类型 | select | 是 | ["公司月度汇率", "发生日汇率", "手工汇率"] |
| `field_018` | 汇率 | number | 是 | [] |
| `field_019` | 折算本币合计 | number | 是 | [] |
| `field_020` | 票据类型 | select | 是 | ["发票", "电子发票", "收据", "无票"] |
| `field_021` | 票据号码 | text | 否 | [] |
| `field_022` | 发票/收据/支付凭证上传 | file | 是 | [] |
| `field_023` | 收款账户类型 | select | 是 | ["银行卡", "现金", "其他"] |
| `field_024` | 开户名 | text | 是 | [] |
| `field_025` | 银行账号/IBAN | text | 是 | [] |
| `field_026` | 超标/无票/重复风险说明 | textarea | 否 | [] |

### outsourcePayment — 劳务/外包付款

模式：业务草稿/流程/存档。

cells列顺序：付款申请单号 → 服务方/项目 → 服务类型/金额 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 付款申请单号 | text | 是 | [] |
| `field_002` | 服务方/项目 | text | 否 | [] |
| `field_003` | 服务类型/金额 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `applicant_name` | 申请人 | text | 是 | [] |
| `field_007` | 所属公司/法人 | select | 是 | ["JADA UAE", "JADA Saudi", "JADA China", "项目公司"] |
| `field_008` | 所属部门 | select | 是 | ["工程部", "调度部", "采购部", "项目部"] |
| `field_009` | 所属项目 | text | 是 | [] |
| `field_010` | 申请主题 | text | 是 | [] |
| `field_011` | 服务类型 | select | 是 | ["临时劳务", "施工劳务", "搬运装卸", "安装服务", "维修服务", "设备操作", "技术服务", "专业服务", "工程外包", "其他服务"] |
| `field_012` | 服务方类型 | select | 是 | ["个人", "企业"] |
| `field_013` | 服务方/供应商 | text | 是 | [] |
| `field_014` | 合同/订单 | text | 是 | [] |
| `field_015` | 服务开始日期 | date | 是 | [] |
| `field_016` | 服务结束日期 | date | 是 | [] |
| `field_017` | 服务地点 | text | 是 | [] |
| `field_018` | 服务内容 | textarea | 是 | [] |
| `field_019` | 币种 | select | 是 | ["SAR", "AED", "USD", "CNY"] |
| `field_020` | 申请金额 | number | 是 | [] |
| `field_021` | 已付金额 | number | 是 | [] |
| `field_022` | 本次付款金额 | number | 是 | [] |
| `field_023` | 付款性质 | select | 是 | ["预付款", "进度款", "结算款", "尾款", "一次性付款"] |
| `field_024` | 人数/工日/工程量 | number | 是 | [] |
| `field_025` | 计量单位 | select | 是 | ["小时", "天", "工日", "㎡", "m", "项"] |
| `field_026` | 单价 | number | 是 | [] |
| `field_027` | 合同/验收/发票/工时表上传 | file | 是 | [] |
| `field_028` | 收款户名 | text | 是 | [] |
| `field_029` | 银行账号/IBAN | text | 是 | [] |
| `field_030` | 异常说明 | textarea | 否 | [] |

### expenseMyApply — 我的申请

模式：只读投影，写入走专用接口。

cells列顺序：单据类型 → 单据编号/主题 → 金额/币种 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 单据类型 | select | 是 | ["个人报销", "劳务/外包付款"] |
| `field_002` | 单据编号/主题 | text | 否 | [] |
| `field_003` | 金额/币种 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_006` | 单据编号 | text | 是 | [] |
| `field_007` | 申请主题 | text | 是 | [] |
| `field_008` | 所属项目 | text | 否 | [] |
| `field_009` | 状态 | select | 是 | ["草稿", "审批中", "退回修改", "财务审核中", "待付款", "已付款", "已完成", "已驳回"] |
| `field_010` | 申请日期 | date | 是 | [] |
| `amount` | 金额 | number | 是 | [] |
| `field_012` | 币种 | select | 是 | ["CNY", "SAR", "AED", "USD"] |
| `field_013` | 收款对象 | text | 是 | [] |
| `field_014` | 可用操作 | textarea | 否 | [] |

### expensePaymentRecord — 付款记录

模式：只读投影，写入走专用接口。

cells列顺序：付款单号 → 来源单据/收款对象 → 应付/已付/未付 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 付款单号 | text | 是 | [] |
| `field_002` | 来源单据/收款对象 | text | 否 | [] |
| `field_003` | 应付/已付/未付 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_006` | 来源单据类型 | select | 是 | ["个人报销", "劳务/外包付款"] |
| `field_007` | 来源单据号 | text | 是 | [] |
| `field_008` | 收款对象 | text | 是 | [] |
| `field_009` | 收款账户 | text | 是 | [] |
| `field_010` | 付款币种 | select | 是 | ["CNY", "SAR", "AED", "USD"] |
| `field_011` | 应付金额 | number | 是 | [] |
| `field_012` | 本次付款金额 | number | 是 | [] |
| `field_013` | 付款方式 | select | 是 | ["银行转账", "现金", "支票", "其他"] |
| `field_014` | 付款账户 | text | 是 | [] |
| `field_015` | 计划付款日期 | date | 否 | [] |
| `field_016` | 实际付款日期 | date | 是 | [] |
| `field_017` | 银行流水号 | text | 否 | [] |
| `field_018` | 付款凭证上传 | file | 是 | [] |
| `payment_status` | 付款状态 | select | 是 | ["待付款", "部分付款", "已付款", "付款失败", "已冲销"] |
| `field_020` | 付款备注/冲销原因 | textarea | 否 | [] |

### approval — 审批中心

模式：只读投影，写入走专用接口。

cells列顺序：审批单 → 类型 → 申请人 → 当前节点。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 审批单 | text | 否 | [] |
| `field_002` | 类型 | text | 是 | [] |
| `applicant_name` | 申请人 | text | 是 | [] |
| `field_004` | 当前节点 | text | 是 | [] |
| `field_005` | 审批单号 | text | 是 | [] |
| `field_009` | 审批动作 | select | 是 | ["同意", "拒绝", "退回", "转交", "撤回", "加签", "催办"] |
| `field_010` | 审批意见 | textarea | 是 | [] |
| `field_011` | 节点到期时间 | datetime-local | 是 | [] |
| `field_012` | 回写规则 | textarea | 否 | [] |

### expenseTodo — 待办审批

模式：只读投影，写入走专用接口。

cells列顺序：待办单据 → 申请人/项目 → 金额/风险提示 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 待办单据 | text | 是 | [] |
| `field_002` | 申请人/项目 | text | 否 | [] |
| `field_003` | 金额/风险提示 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 待办类型 | select | 是 | ["个人报销", "劳务/外包付款"] |
| `applicant_name` | 申请人 | text | 是 | [] |
| `field_008` | 所属部门/项目 | text | 是 | [] |
| `field_009` | 申请金额 | number | 是 | [] |
| `field_010` | 币种 | select | 是 | ["CNY", "SAR", "AED", "USD"] |
| `field_011` | 提交时间 | datetime-local | 是 | [] |
| `field_012` | 停留时长 | text | 是 | [] |
| `field_013` | 审批动作 | select | 是 | ["通过", "退回修改", "驳回", "转交", "加签", "批量通过"] |
| `field_014` | 审批意见 | textarea | 是 | [] |

### approvalTemplate — 审批模板配置

模式：只读投影，写入走专用接口。

cells列顺序：配置编号 → 审批账号 → 可审批功能模块 → 可审批节点 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 配置编号 | autonumber | 是 | [] |
| `field_002` | 审批账号 | text | 否 | [] |
| `field_003` | 可审批功能模块 | text | 否 | [] |
| `field_004` | 可审批节点 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_007` | 配置名称 | text | 是 | [] |
| `field_008` | 配置说明 | textarea | 是 | [] |
| `field_009` | 状态回写规则 | textarea | 是 | [] |
| `field_010` | 版本号 | text | 是 | [] |
| `field_011` | 发布策略 | select | 是 | ["草稿", "测试中", "已发布", "已冻结", "已停用"] |

### iot — GPS实时监测

模式：外部GPS，数据操作503。

cells列顺序：无列表列。

### iotRules — IoT告警规则

模式：业务草稿/流程/存档。

cells列顺序：规则名称 → 适用对象 → 阈值 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 规则名称 | text | 是 | [] |
| `field_002` | 适用对象 | select | 是 | ["车辆设备", "工程设备", "司机", "项目围栏"] |
| `field_003` | 阈值 | number | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_008` | 时间单位 | select | 是 | ["分钟", "小时", "公里", "米"] |
| `field_009` | 生效时间 | datetime-local | 是 | [] |
| `field_010` | 失效时间 | datetime-local | 否 | [] |
| `field_011` | 触发后动作 | select | 是 | ["通知", "生成工单", "标记调度异常", "进入日报"] |
| `field_012` | 规则说明 | textarea | 否 | [] |

### dailyReport — 每日设备分析报告

模式：只读投影，写入走专用接口。

cells列顺序：报告日期 → 分析对象 → 核心指标 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 报告日期 | date | 是 | [] |
| `field_002` | 分析对象 | text | 否 | [] |
| `field_003` | 核心指标 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_006` | 数据开始时间 | datetime-local | 是 | [] |
| `field_007` | 数据截止时间 | datetime-local | 是 | [] |
| `field_008` | 车辆运行台数 | number | 是 | [] |
| `field_009` | 车辆累计里程km | number | 是 | [] |
| `field_010` | 工程设备作业台数 | number | 是 | [] |
| `field_011` | 工程设备累计工时 | number | 是 | [] |
| `field_012` | 异常告警数 | number | 是 | [] |
| `field_013` | 维保影响台数 | number | 是 | [] |
| `field_014` | 复核截止时间 | datetime-local | 是 | [] |
| `field_015` | 日报摘要 | textarea | 否 | [] |

### users — 用户管理

模式：只读投影，写入走专用接口。

cells列顺序：账号 → 账号类型 → 所属主体 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 账号 | text | 否 | [] |
| `field_002` | 账号类型 | select | 是 | ["供应商账号", "内部用户", "承租方账号", "移动端账号"] |
| `field_003` | 所属主体 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 登录账号 | text | 是 | [] |
| `field_006` | 用户姓名 | text | 是 | [] |
| `mobile_phone` | 手机号 | text | 是 | [] |
| `field_010` | 绑定主角色 | select | 是 | ["供应商用户", "超级管理员", "财务人员", "采购人员", "设备主管", "司机移动端", "工程设备操作员"] |
| `field_011` | 数据范围 | select | 是 | ["所属供应商", "全公司", "项目+仓库", "本人任务"] |
| `field_012` | 可见设备范围 | select | 是 | ["仅本供应商设备信息和状态", "全部工程设备", "全部车辆设备", "全部设备"] |
| `field_013` | 账号有效期 | date | 是 | [] |
| `field_014` | 账号状态 | select | 是 | ["启用", "停用", "锁定"] |

### roleperm — 角色权限

模式：只读投影，写入走专用接口。

cells列顺序：角色模板 → 功能模块权限 → 数据范围 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 角色模板 | text | 否 | [] |
| `field_002` | 功能模块权限 | text | 否 | [] |
| `field_003` | 数据范围 | text | 否 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_005` | 角色名称 | text | 是 | [] |
| `field_006` | 账号选择 | select | 是 | ["super_admin", "finance_lina", "buyer_chen", "supplier_ae_01", "driver_ahmed"] |
| `field_007` | 角色类型 | select | 是 | ["供应商角色", "管理端角色", "财务角色", "采购角色", "移动端角色"] |
| `field_008` | 默认数据范围 | select | 是 | ["所属供应商", "全公司", "项目+仓库", "本人任务"] |
| `field_009` | 授权有效期 | date | 是 | [] |
| `field_010` | 角色说明 | textarea | 否 | [] |

### notice — 通知中心

模式：业务草稿/流程/存档。

cells列顺序：通知标题 → 来源 → 接收人 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `field_001` | 通知标题 | text | 是 | [] |
| `field_002` | 来源 | text | 否 | [] |
| `field_003` | 接收人 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `field_006` | 通知类型 | select | 是 | ["审批待办", "IoT告警", "合同到期", "库存预警", "报表提醒", "系统消息"] |
| `field_007` | 来源模块 | text | 是 | [] |
| `field_009` | 发送渠道 | select | 是 | ["站内信", "短信", "邮件", "APP推送"] |
| `field_010` | 发送时间 | datetime-local | 是 | [] |
| `field_011` | 阅读状态 | select | 是 | ["未读", "已读", "已处理"] |
| `field_012` | 跳转与重试规则 | textarea | 否 | [] |

### audit — 审计日志

模式：只读投影，写入走专用接口。

cells列顺序：日志ID → 操作对象 → 操作人 → 当前状态。

| field_key | 标签 | 类型 | 提交必填 | 选项 |
|---|---|---|---|---|
| `log_no` | 日志ID | text | 是 | [] |
| `target_object` | 操作对象 | text | 是 | [] |
| `operator_name` | 操作人 | text | 是 | [] |
| `current_status` | 当前状态 | text | 否 | [] |
| `operation_type` | 操作类型 | select | 是 | ["新增", "编辑", "审批", "上传", "导入", "导出", "回写", "登录", "越权访问"] |
| `operated_at` | 操作时间 | datetime-local | 是 | [] |
| `request_no` | 请求号 | text | 是 | [] |
| `record_status` | 记录状态 | select | 是 | ["已记录", "异常", "待归档"] |
| `change_evidence` | 前后值与证据 | textarea | 否 | [] |
