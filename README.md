# Dependency Slimmer Maven Plugin

A Maven plugin to reduce application size by filtering dependencies. This plugin is particularly useful for optimizing deployment artifacts by removing unused libraries, especially when dealing with large dependencies that support multiple integrations.

## How it Works

The plugin hooks into the `package` phase of the Maven build. It analyzes the project's dependencies and, based on a flexible configuration, removes specified JAR files from the final packaged application. It intelligently handles transitive dependencies, ensuring that when a library is excluded, its own dependencies are also removed.

## Features

- **Dependency Exclusion**: Exclude specific dependencies by `groupId`, `artifactId`, and `version`. Wildcards are supported.
- **Dependency Inclusion**: Specify which dependencies to keep, automatically excluding all others.
- **Transitive Dependency Analysis**: Correctly identifies and removes transitive dependencies of excluded artifacts.
- **Predefined Profiles**: Comes with built-in profiles for common use cases (e.g., `ollama-only`, `openai-only`, `minimal`).
- **Dry Run Mode**: Analyze which dependencies would be removed without actually modifying the artifact.
- **Verbose Logging**: Get detailed information about the slimming process.

## Configuration

To use the plugin, add it to the `build` section of your `pom.xml`.

### Basic Configuration

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.mulesoft.tools</groupId>
            <artifactId>dependency-slimmer-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>slim</goal>
                    </goals>
                    <configuration>
                        <!-- Configuration goes here -->
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Parameters

- `enabled` (boolean, default: `true`): Enable or disable the plugin.
- `verbose` (boolean, default: `false`): Enable detailed logging.
- `dryRun` (boolean, default: `false`): If true, the plugin will only log what it would remove.
- `profile` (String): Use a predefined configuration profile.
- `configuration` (SlimmingConfiguration): The main configuration block for includes and excludes.

### Excluding Dependencies

To exclude dependencies, add an `<excludes>` block to your configuration.

```xml
<configuration>
    <excludes>
        <exclude>
            <groupId>org.apache.hadoop</groupId>
            <artifactId>*</artifactId>
        </exclude>
        <exclude>
            <groupId>com.microsoft.*</groupId>
            <artifactId>*</artifactId>
        </exclude>
    </excludes>
    <verbose>true</verbose>
</configuration>
```

### Including Dependencies (Include-Only Mode)

To specify which dependencies to keep, use an `<includes>` block. All other dependencies (and their transitives) will be removed.

```xml
<configuration>
    <includes>
        <include>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-core</artifactId>
        </include>
        <include>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-ollama</artifactId>
        </include>
    </includes>
</configuration>
```

### Using Predefined Profiles

Profiles provide a quick way to apply common configurations.

```xml
<configuration>
    <profile>ollama-only</profile>
    <verbose>true</verbose>
</configuration>
```

Available profiles:
- `ollama-only`: Keeps `langchain4j-core` and `langchain4j-ollama`, and excludes common cloud/big data dependencies.
- `openai-only`: Keeps `langchain4j-core` and `langchain4j-open-ai`, and excludes others.
- `minimal`: Excludes a wide range of heavy dependencies like Hadoop, Tika, and Spark.

## Building the Plugin

To build the plugin from source, run:

```bash
mvn clean install
```

## Command Line Usage

You can also control the plugin from the command line using system properties.

```bash
# Use a predefined profile
mvn package -Dslim.profile=ollama-only

# Dry run to see what would be removed
mvn package -Dslim.dryRun=true -Dslim.verbose=true

# Disable slimming temporarily
mvn package -Dslim.enabled=false
