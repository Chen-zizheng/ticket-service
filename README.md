# 工单管理系统（ticket-service）

一个 Spring Boot + MyBatis 写的工单服务：建单、派单、状态流转、统计、分页筛选，附带一个不依赖构建步骤的网页看板。

服务层用状态机校验流转、用版本号检测并发修改；分页查询批量补全处理人信息，测试覆盖 SQL 数量上限和统一错误响应。

![看板](docs/dashboard.png)

## 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 运行时 | JDK 17 | |
| Web | Spring Boot 3.2.5（spring-boot-starter-web / validation） | |
| 持久层 | MyBatis Spring Boot Starter 3.0.3 + XML 映射 | 手写分页、条件更新和聚合 SQL |
| 数据库 | H2（内存，`MODE=MySQL`） | 启动即建表灌种子数据，克隆下来就能跑 |
| 测试 | JUnit 5 + MockMvc | 17 个接口用例 + 1 个并发用例 |
| 前端 | 原生 HTML/CSS/JS | 没有 node_modules，静态文件由 Spring Boot 直接托管 |

## 快速开始

```bash
mvn test          # 18 个用例
mvn spring-boot:run
# 打开 http://localhost:8080/
```

或者打包成单个 jar：

```bash
mvn -DskipTests package
java -jar target/ticket-service-1.0.0.jar --server.port=8080
```

启动时会执行 [schema.sql](src/main/resources/db/schema.sql) 和 [data.sql](src/main/resources/db/data.sql)，建 3 张表、4 个处理人（其中 1 个已停用，用来测派单校验）。

> Windows 上如果 `java.io.tmpdir` 不可写，Tomcat 建临时目录会抛
> `Unable to create tempDir`，指定一个可写目录即可：`java -Djava.io.tmpdir=D:/tmp -jar ...`。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/tickets` | 建单。`title` 必填且 ≤120 字，`description` ≤2000 字，`priority` 缺省 MEDIUM |
| GET | `/api/tickets/{id}` | 详情 |
| GET | `/api/tickets?status=&priority=&assigneeId=&page=&size=` | 分页列表，`size` 上限 100 |
| POST | `/api/tickets/{id}/assign` | 派单，body `{"assigneeId":2,"note":"..."}` |
| POST | `/api/tickets/{id}/status` | 状态流转，body `{"status":"IN_PROGRESS"}` |
| GET | `/api/stats` | 按状态 / 优先级 / 处理人聚合 |
| GET | `/api/assignees` | 处理人列表 |

```console
$ curl -s -X POST http://localhost:8080/api/tickets \
    -H 'Content-Type: application/json' \
    --data-binary '{"title":"支付回调偶发丢失","priority":"URGENT"}'
{"id":1,"title":"支付回调偶发丢失","description":null,"priority":"URGENT","status":"OPEN",
 "assigneeId":null,"version":0,"createdAt":"2026-11-13T18:40:01.373743",
 "updatedAt":"2026-11-13T18:40:01.373743","resolvedAt":null,"assigneeName":null}
```

> **PowerShell 用户注意**：`curl.exe -d '{"a":1}'` 里的双引号会被 PowerShell 吃掉，
> 结果服务端收到非法 JSON 而返回 400。用 `--data-binary "@body.json"` 从文件读，或者用 `Invoke-RestMethod`。

错误响应统一为：

```json
{"code":"VALIDATION_FAILED","message":"参数校验失败","details":{"title":"标题不能为空"},
 "timestamp":"2026-11-13T17:39:56.552Z"}
```

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 字段缺失 / 超长 / 非法优先级 / 非法状态 |
| 404 | `NOT_FOUND` | 工单或处理人不存在 |
| 409 | `CONFLICT` | 重复派单、派给已停用的处理人、非法状态流转、乐观锁冲突 |

## 状态机

| 当前状态 | 允许转向 |
| --- | --- |
| `OPEN` | `ASSIGNED`、`IN_PROGRESS`、`CLOSED` |
| `ASSIGNED` | `IN_PROGRESS`、`OPEN`、`CLOSED` |
| `IN_PROGRESS` | `RESOLVED`、`ASSIGNED`、`CLOSED` |
| `RESOLVED` | `CLOSED`、`IN_PROGRESS` |
| `CLOSED` | 无（终态） |

- `CLOSED` 是终态，任何流转都会被拒（409）
- 不允许越级（`OPEN` 直接到 `RESOLVED`）
- 从 `RESOLVED` 退回 `IN_PROGRESS` 时会清空 `resolved_at`，避免"已解决时间"残留
- 规则集中在 [TicketStatus.canMoveTo()](src/main/java/com/czz/ticket/domain/TicketStatus.java)，由 service 校验

## 几个实现上的决定

**1. 派单用乐观锁，不用悲观锁。**
`assign` 的 SQL 是：

```sql
UPDATE ticket
   SET assignee_id = #{assigneeId}, status = 'ASSIGNED',
       version = version + 1, updated_at = #{now}
 WHERE id = #{id} AND version = #{expectedVersion} AND status = #{fromStatus}
```

影响行数为 0 时抛出映射为 HTTP 409 的冲突异常。更新工单与插入派单记录在同一事务中；乐观锁避免先加锁查询，但 UPDATE 仍会取得数据库写锁，锁通常持有到事务结束。
[ConcurrentAssignTest](src/test/java/com/czz/ticket/ConcurrentAssignTest.java) 用 32 个线程直接调用 service 抢派同一张单，断言恰好 1 个成功、31 个抛出 `ConflictException`；HTTP 409 映射由接口测试覆盖。

**2. 列表页最多 3 条 SQL。**
先 `findPage` 拿当页数据（默认 20 条），再用 `findByIds` 一次批量取处理人名字，最后 `count` 拿总数。没有处理人 ID 时会跳过批量查询，只发 2 条 SQL。
如果按行 `selectOne` 补名字，20 行就是 21 条 SQL。为此在测试里挂了一个 MyBatis 拦截器统计 SQL 条数
（[SqlCounter.java](src/main/java/com/czz/ticket/config/SqlCounter.java)），用例直接断言 `count() <= 3`——N+1 这种问题会悄悄回来，靠人眼 review 不可靠。

**3. `size` 在服务端夹到 100。**
传 `size=100000` 返回 100 条，用例覆盖了 `size=1000` → 100 条这条路径。

**4. 状态与派单共用一个"版本号"。**
两者都会改 `ticket` 行，共用 `version` 之后"派单"和"改状态"能互相检测到冲突，不需要额外的锁表；
代价是状态变更会让正在并发的派单失败，客户端要重试一次。

**5. 前端不做构建。**
一个 [index.html](src/main/resources/static/index.html) + 一个 [app.js](src/main/resources/static/app.js)，直接 `fetch` 接口。看板每 3 秒刷新一次列表与统计，
派单/流转成功后立即刷新，不用手动点。

## 测试

以下汇总来自工作区已有的 Maven Surefire 报告：Windows 11、Java 17.0.21，接口类 17 项、并发类 1 项，共 18 项。报告位于本地构建产物 `target/surefire-reports/`，不随 Git 发布；本次文档核对未重新执行 Maven。可在具备 JDK 17 和 Maven 的环境执行上面的 `mvn test` 复核。

```console
$ mvn test
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖的点：

- 建单默认值（status=OPEN、version=0、未派单）
- 空标题 / 超长标题 / 未知优先级 → 400，且 `details` 里带字段名
- 不存在的 id → 404
- 按状态、优先级、处理人过滤；分页 size 上限
- 20 条数据只发 ≤3 条 SQL（N+1 回归）
- 派单成功 / 重复派单 409 / 派给停用处理人 409 / 处理人不存在 404
- 状态机：合法流转、越级 409、`CLOSED` 终态、`RESOLVED` 退回清空 `resolved_at`
- 未知状态字符串 → 400
- 统计聚合结果
- 并发抢派：1 成功 + 31 冲突

## 目录

```
src/main/java/com/czz/ticket/
  TicketApplication.java
  domain/        Ticket、Assignee、AssignmentRecord、TicketStatus、Priority
  dto/           CreateTicketRequest、AssignRequest、StatusChangeRequest、PageResponse、StatsResponse、ErrorResponse
  mapper/        TicketMapper、AssigneeMapper（XML 在 resources/mappers/）
  service/       TicketService、NotFoundException、ConflictException
  web/           TicketController、GlobalExceptionHandler
  config/        SqlCounter（测试用 SQL 计数器）
src/main/resources/
  application.yml、db/schema.sql、db/data.sql、mappers/*.xml、static/{index.html,app.js,style.css}
src/test/java/com/czz/ticket/  TicketApiTest（17 个）、ConcurrentAssignTest
```

## 已知限制

- H2 内存库，进程退出数据就没了；迁移 MySQL 需要增加驱动、修改 [application.yml](src/main/resources/application.yml) 的 datasource 与建表方言，并重新验证 SQL、事务及并发行为。
- 没有登录与权限：谁都能派单给任何人。真实系统里这里要有角色（提单人 / 处理人 / 管理员）。
- 没有通知：派单后不发邮件/IM，`assignment` 表只记录谁在什么时候派给了谁。
- 列表页用 offset 分页，深翻页（`page=10000`）会慢；数据量大了要换游标分页。
