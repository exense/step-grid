# step-grid

[![License](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE.txt)
[![Maven Central](https://img.shields.io/maven-central/v/ch.exense.step/step-grid)](https://central.sonatype.com/artifact/ch.exense.step/step-grid)

A distributed execution grid framework for the [Step automation platform](https://step.dev). `step-grid` provides the infrastructure to distribute and execute workloads across a pool of remote agents, with built-in token management, JWT-based security, Prometheus metrics, and a pluggable file manager.

For full documentation, architecture details, and getting started guides, see the main repository:
**[github.com/exense/step](https://github.com/exense/step)**

## Overview

`step-grid` follows a **client – server – agent** architecture:

- **Clients** connect to the server via REST to acquire tokens, submit work, and retrieve results.
- A central **server** manages a pool of execution tokens and routes work to available agents.
- An optional **proxy** layer sits between the server and the agents, providing intermediate routing.
- **Agents** run on worker nodes, register with the server (or proxy), and execute tasks within isolated token contexts.

## Modules

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| API | `step-grid-api` | Shared interfaces, data model, token pool abstractions, file manager API, and agent handler framework used across all modules |
| Server Common | `step-grid-server-common` | Shared server infrastructure: Jetty servlet container, Jersey REST, JWT authentication, and Prometheus metrics |
| Server | `step-grid-server` | Main grid server: token lifecycle management, REST endpoints, report generation, and file management |
| Client | `step-grid-client` | Client library for connecting to the grid server, acquiring tokens, and retrieving execution reports |
| Agent | `step-grid-agent` | Worker agent that registers with the server, manages a local token pool, and executes delegated tasks |
| Proxy | `step-grid-proxy` | Optional routing proxy that sits between the server and the agents |
| Tests | `step-grid-tests` | Integration test suite covering the full server–agent–client stack |

## Requirements

- Java 11 or later
- Maven 3.6 or later

## Getting Started

Add the BOM to manage dependency versions across modules:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>ch.exense.step</groupId>
      <artifactId>step-grid</artifactId>
      <version>VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then declare only the modules you need:

```xml
<!-- Shared API (required by all other modules) -->
<dependency>
  <groupId>ch.exense.step</groupId>
  <artifactId>step-grid-api</artifactId>
</dependency>

<!-- Grid server -->
<dependency>
  <groupId>ch.exense.step</groupId>
  <artifactId>step-grid-server</artifactId>
</dependency>

<!-- Grid client -->
<dependency>
  <groupId>ch.exense.step</groupId>
  <artifactId>step-grid-client</artifactId>
</dependency>
```

Replace `VERSION` with the latest release available on [Maven Central](https://central.sonatype.com/artifact/ch.exense.step/step-grid).

## Building from Source

```bash
git clone https://github.com/exense/step-grid.git
cd step-grid
mvn clean install
```

To skip tests during the build:

```bash
mvn clean install -DskipTests
```

The `step-grid-agent` module is assembled as a self-contained fat JAR and can be launched directly:

```bash
java -jar step-grid-agent/target/step-grid-agent-<VERSION>.jar <agent-config.yaml>
```

## Architecture

```
┌─────────────┐   REST / JWT   ┌──────────────────┐   REST / JWT   ┌──────────────────┐
│   Client(s) │ ─────────────► │   Grid Server    │ ─────────────► │    Proxy (opt.)  │
└─────────────┘                │  (token manager) │                └────────┬─────────┘
                                └──────────────────┘                         │
                                                                    ┌────────▼─────────┐
                                                                    │    Agent(s)      │
                                                                    │  (task executor) │
                                                                    └──────────────────┘
```

- **Tokens** are the unit of concurrency. Each agent exposes a configurable pool of tokens, each optionally tagged with attributes for affinity-based scheduling.
- **JWT** is used for server-to-agent and client-to-server authentication.
- **Prometheus** metrics are exposed by agents and the proxy for operational monitoring.
- **File manager** handles artifact distribution from server to agents transparently.

## Step Ecosystem

`step-grid` is part of the [Step](https://step.dev) open-source automation platform. Related repositories:

| Repository | Description |
|------------|-------------|
| [step](https://github.com/exense/step) | Core backend — the Step controller that drives the grid |
| [step-api](https://github.com/exense/step-api) | Java API for writing Keywords that run on grid agents |
| [step-framework](https://github.com/exense/step-framework) | Infrastructure library providing the REST server and persistence layer used by `step-grid-server-common` |

For agent setup and configuration see the [Agents section](https://step.dev/knowledgebase/agents) in the Step knowledgebase.

## Contributing

Contributions are welcome. Please open an issue to discuss a bug or feature request before submitting a pull request. All submissions are expected to include appropriate test coverage.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE.txt).
