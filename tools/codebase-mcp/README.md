# codebase-mcp

An [MCP](https://modelcontextprotocol.io) server, built with Spring AI, that answers structural
questions about a Spring Boot codebase so an AI agent can find its way around a large layered system
without reading every file.

It parses Java sources with JavaParser and reads `application.yml` / `application.properties`.
It is **read-only, makes no network calls and calls no LLM**: the agent that connects to it does the
reasoning. That makes it safe to point at access-restricted repositories on a developer machine.

## Tools

| Tool | Answers |
|---|---|
| `project_overview` | Modules, `@SpringBootApplication` classes, packages with their Spring stereotypes. Call first. |
| `list_rest_endpoints` | Every endpoint: verb, full path (placeholders resolved), handler, body, params, `file:line`. |
| `list_kafka_topics` | `@KafkaListener` consumers, `KafkaTemplate.send` producers, `TopicBuilder` topics, topic config. Topic names are resolved from config, including `@ConfigurationProperties` accessors such as `props.topics().orderEvents()`. |
| `list_data_models` | MongoDB `@Document`, DynamoDB `@DynamoDbBean` (table + partition/sort keys), JPA `@Entity`; optionally all records. |
| `describe_type` | One type's annotations, supertypes, fields, methods, location, and every type that uses it. |

Everything is syntactic, so the target does not need to compile or have its dependencies available.
Multi-module repositories are supported (every `**/src/main/java` is scanned; each module's config is
used for its own placeholders). Parsed files are cached by modification time.

## Use it

Build once (Java 21, Maven):

```bash
mvn -B -f tools/codebase-mcp/pom.xml package
```

**Claude Code in this repo**: nothing to do. `.mcp.json` at the repo root registers it as `codebase`,
and `run.sh` builds the jar on first use.

**Claude Code against another repo** (for example one of your restricted services):

```bash
claude mcp add codebase -- /path/to/agentic-springboot-demo/tools/codebase-mcp/run.sh /path/to/other-service
```

**Any MCP client** (Claude Desktop, IDE plugins, Spring AI / LangChain4j MCP clients) uses the same stdio command:

```json
{
  "mcpServers": {
    "codebase": {
      "command": "java",
      "args": ["-jar", "/path/to/codebase-mcp.jar", "--codebase.root=/path/to/service"]
    }
  }
}
```

Logs go to `${java.io.tmpdir}/codebase-mcp.log`, never stdout (stdout is the MCP channel).

## Extending

Add a method to `CodebaseTools` annotated with `@Tool` (and `@ToolParam` for arguments) and put the
logic in `CodebaseAnalyzer`. Write the tool description for the calling model: say what it returns and
when to use it. Add a case to `CodebaseAnalyzerTest`, which runs against this repo's order-service.

Ideas that fit enterprise services: Feign/WebClient outbound calls (service-to-service map),
`@Scheduled` jobs, `@Transactional` boundaries, security rules per endpoint, OpenAPI diff.
