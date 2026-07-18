# Driftwood

`Driftwood` 是一个 Java 11 命令行工具，用于比较不同环境中的应用配置。它支持
Java properties、YAML、JSON 和实用的 `.env` 语法，会保留嵌套结构和标量类型，
并可输出文本、JSON 或 Markdown 报告。

当前版本：`v1.1.0`。版本历史见 [CHANGELOG.md](CHANGELOG.md)。

比较引擎支持：

- 可识别类型的对象、数组、标量和 null 值
- `server.ports[0]` 这类点分和括号路径
- `services.*.url` 这类路径 glob
- 有序数组和无序数组比较
- 忽略、脱敏和数组顺序规则文件
- include 和 exclude 选择器
- profile 和 overlay 比较
- 可选的 schema 校验：必填路径和值类型
- 每个变更附近的上下文值

## 构建

```bash
mvn clean package
```

构建会生成可执行 jar：

```text
target/driftwood-1.1.0.jar
```

## 快速开始

```bash
java -jar target/driftwood-1.1.0.jar \
  --left samples/left/application.yml \
  --right samples/right/application.json
```

默认根据文件扩展名判断格式，也可以按输入单独覆盖：

```bash
java -jar target/driftwood-1.1.0.jar \
  --left config/application.conf --left-format YAML \
  --right config/runtime.env --right-format ENV
```

支持的格式为 `AUTO`、`PROPERTIES`、`YAML`、`JSON` 和 `ENV`。`.env` 条目按
扁平键读取。properties 和 `.env` 文件中的点分键会展开为嵌套路径，因此
`server.port=8080` 会按 `server.port` 比较。

## 输出

默认报告为文本：

```text
Added keys (1):
  + feature.newFlag

Removed keys (1):
  - feature.oldFlag

Changed keys (1):
  ~ server.port: "8080" -> "9090"
```

使用 `--format` 选择其他报告：

```bash
java -jar target/driftwood-1.1.0.jar \
  --left dev.yml --right prod.yml \
  --format MARKDOWN --output build/driftwood.md
```

JSON 报告包含类型化值、显示值、路径、汇总和上下文。Markdown 报告适合写入
Pull Request 评论或构建产物。

使用 `--context 3` 可以为每个变更附加最多三个相邻的同级值：

```text
Changed keys (1):
  ~ server.port: "8080" -> "9090"
    context: server.host = "localhost"
    context: server.timeout = "30"
```

## 选择器

`--include` 将比较范围限制到某个路径及其后代，`--exclude` 跳过某个路径及其
后代。两个参数都可以重复传入。

```bash
java -jar target/driftwood-1.1.0.jar \
  --left dev.yml --right prod.yml \
  --include 'server' \
  --exclude 'server.tls.keystore' \
  --include 'feature.flags'
```

路径语法：

| 路径 | 含义 |
| --- | --- |
| `server.port` | 一个字段 |
| `servers[0].host` | 一个数组元素 |
| `servers[].host` | 每个数组元素 |
| `servers[*].host` | 每个数组元素 |
| `services.*.url` | 每个对象下的一个字段 |
| `logging.**` | 一个字段及其全部后代 |

路径在 overlay 和 profile 选择之后匹配。数组在报告路径中保留自身索引。

## 数组

默认数组策略为有序。使用 `--array-order UNORDERED` 可以把所有数组按多重集合
比较，也可以限定范围：

```bash
java -jar target/driftwood-1.1.0.jar \
  --left dev.yml --right prod.yml \
  --array-order ORDERED \
  --unordered-path 'service.instances' \
  --ordered-path 'deployment.steps'
```

无序数组会优先匹配相等元素。未匹配元素会带原始索引输出。

## 规则

规则文件是 UTF-8 文本文件，每行一条指令。空行和以 `#` 开头的注释会被忽略。
包含空格的值可以使用引号。

```text
# 稳定的运行时值
ignore metrics.**

# 在报告中隐藏值，但仍检测变更
mask *.password "<hidden>"
mask *.token

# 按路径选择数组语义
unordered service.instances
ordered deployment.steps
array-order feature.rollout ordered
```

对应的命令行参数为 `--ignore`、`--mask`、`--ordered-path` 和
`--unordered-path`。`--rules FILE` 会一次加载全部指令。

匹配 `password`、`secret`、`token` 或常见 API key 字段名的值默认会脱敏。底层
值不同时，输出仍会报告该变更。使用 `--no-redact` 可以关闭内置脱敏，使用
`--mask-replacement TEXT` 可以选择其他显示值。

## Overlay 和 Profile

Overlay 会深度合并到基础对象上。标量和数组值会替换基础值，嵌套对象会递归
合并。

```bash
java -jar target/driftwood-1.1.0.jar \
  --left base.yml --left-overlay dev.yml \
  --right base.yml --right-overlay prod.yml
```

使用 `--profile` 可以在比较前选择子树。简单名称会先查找同名顶层路径，然后
尝试 `profiles.NAME`。显式路径会按原样使用。

```bash
java -jar target/driftwood-1.1.0.jar \
  --left application.yml --right application.yml \
  --profile dev
```

## Schema 校验

Schema 文件使用 YAML 或 JSON，支持三个区段：

```yaml
required:
  - server.port
  - service.instances

types:
  server.port: integer
  service.instances: array
  service.enabled: boolean

arrays:
  service.instances: unordered
  deployment.steps: ordered
```

支持的类型为 `object`、`array`、`string`、`number`、`integer`、`boolean`、
`null` 和 `scalar`。Schema 校验在比较之前执行。使用 `--validate` 可以只断言
配置有效，不生成差异报告。

```bash
java -jar target/driftwood-1.1.0.jar \
  --left dev.yml --right prod.yml \
  --schema samples/schema.yml --validate
```

## 退出码

| 退出码 | 含义 |
| ---: | --- |
| 0 | 输入有效且相等，或校验成功。 |
| 1 | 输入有效但存在差异。 |
| 2 | 参数、输入文件、overlay、规则、路径或 schema 无效。 |
| 3 | 出现未预期的运行时故障。 |

当所有差异都被忽略时，`--fail-on-ignored` 会让退出码为 1。

## 示例

`samples` 目录包含：

- `samples/left/application.yml`
- `samples/right/application.json`
- `samples/overlays/dev.yml`
- `samples/rules.conf`
- `samples/schema.yml`
- `samples/runtime.env`

运行完整示例：

```bash
java -jar target/driftwood-1.1.0.jar \
  --left samples/left/application.yml \
  --right samples/right/application.json \
  --rules samples/rules.conf \
  --schema samples/schema.yml \
  --context 2 \
  --format MARKDOWN \
  --output target/sample-report.md
```

## 项目结构

```text
src/main/java/io/github/driftwood/
  cli/       命令行参数和退出码
  compare/   比较引擎、请求和选项
  core/      兼容 API 和格式定义
  model/     类型化配置节点和加载器
  report/    文本、JSON 和 Markdown 报告
  rules/     忽略、脱敏和数组顺序规则
  schema/    校验定义和检查
```

原有的 `ConfigLoader` 和 `ConfigDiffer` API 仍可供直接使用库的调用方使用。
新代码应使用 `ConfigComparisonEngine` 和 `ComparisonRequest`。

## 架构

`driftwood` 使用类型化配置管线：

```text
输入文件
    |
    v
格式检测和解析
    |
    v
类型化配置树
    |
    +--> overlay 合并
    +--> profile 选择
    +--> include/exclude 选择器
    +--> 规则求值
    +--> schema 校验
    |
    v
比较引擎
    |
    v
文本、JSON 或 Markdown 报告
```

配置表示为类型化对象、数组、字符串、数字、整数、布尔值和 null 节点。
Properties 和 `.env` 点分键在比较前展开为嵌套路径。

比较引擎会应用 overlay、profile 选择、路径过滤、忽略和脱敏规则、数组顺序覆盖
以及递归类型比较。它输出包含路径、变更类型、旧值、新值、显示值和可选上下文
的变更列表。

所有报告格式使用同一份比较结果。脱敏只影响显示值，底层差异仍会被检测。该工具
不会修改配置、执行部署模板或获取远端密钥。

## 安全

安全问题请通过 GitHub Security Advisories 报告，不要在公开 Issue 中披露
真实配置、密码或私钥。

## 许可证

MIT
