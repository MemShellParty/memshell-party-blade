# memshell-party Blade plugin

Adds the Blade framework as a supported server for `memshell-party`. The plugin is a drop-in external plugin. It registers one server provider and one injector template, and it reuses the built-in `NettyHandler` shell type.

- Blade version. `2.1.2.RELEASE`.
- Shell tools. Godzilla and Command.
- Shell type. `NettyHandler`.
- Host SDK. `3.2.0-SNAPSHOT`.

The Godzilla and Command providers and Netty itself already ship in the full `memshell-party` distribution. The plugin only adds the Blade server mapping.

## SDK 3.2.0 integration

The plugin targets the 3.2.0 extension points.

- `getShellPackageName` writes shell classes under `com.hellokaton.blade.mvc.handler`, the same package family as Blade's own handlers.
- `getMinimumTargetJreVersion` raises every generation request to Java 8, matching the class file version of the compiled templates.
- The injector template calls `WebContext.get()` directly. A generation-time stub under `com.hellokaton.blade.mvc` satisfies the compile-time link, and the target application class loader supplies the real class at runtime.
- `PluginBootstrap.validate` runs in the integration test and fails on template or SPI contract errors.

## Build and install

The build needs JDK 17 only. The plugin and the range compile with `--release 8`, so the output still runs on Java 8 targets. Dependencies resolve from Maven Central and the Sonatype snapshots repository.

```bash
./gradlew :plugin:jar
```

Copy `plugin/build/libs/memshell-party-blade-plugin-*.jar` into the host `plugins/` directory next to the boot or GUI jar, then restart the host. The host discovers the jar through `ServiceLoader` and the `META-INF/memshell-party-plugin.properties` descriptor. The plugins directory defaults to `plugins` under the launch directory. Set `-Dmemshell.plugins.dir=/path/to/plugins` or `MEMSHELL_PLUGINS_DIR` to use another location.

## Range and tests

Run the end-to-end tests. They build the plugin jar and the range jar, start the range, inject both shells, and execute `id` through Godzilla and Command.

```bash
./gradlew :integration-test:test
```

Run the range alone.

```bash
./gradlew :range:shadowJar
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens jdk.unsupported/sun.misc=ALL-UNNAMED \
     -jar range/build/libs/blade-range.jar 9000
```

The range serves `/`, `/health`, and `/hello`. Its `/loader` route base64-decodes a submitted class, defines it in a child class loader, and instantiates it. It simulates a code execution entry point.

The tests cover four cases. Plugin discovery and validation. Shell package and minimum JRE enforcement. Godzilla `id` execution. Command `id` execution.

The Godzilla test client is a small HTTP/AES/GZIP implementation in the test sources, together with a minimal payload class. The official `io.github.beichendream:godzilla:4.01` artifact is the desktop GUI (`Main-Class: core.ui.MainActivity`), not a headless client, so it cannot drive this test. The test client speaks the same wire protocol and is not a dependency of the plugin.

## Verify a downloaded artifact

The test can load the plugin from an external directory instead of the module output. This is how the CI artifact is checked.

```bash
mkdir -p /tmp/ci-plugin
cp memshell-blade-plugin-3.2.0.jar /tmp/ci-plugin/
./gradlew :integration-test:test -Pplugins.dir=/tmp/ci-plugin
```

## CI

`.github/workflows/ci.yml` runs on every push and pull request. It runs the unit test tasks, the integration tests, builds the plugin jar, and uploads the `memshell-blade-plugin` artifact.

## Injection preconditions

The injection entry point must run on a Blade request thread. The injector reads Blade's `WebContext` thread local to reach the running channel.

The target JVM needs `--add-opens java.base/java.lang=ALL-UNNAMED` for the shell `defineClass` path. The Godzilla shell also needs `--add-opens jdk.unsupported/sun.misc=ALL-UNNAMED`.

## Upstream feedback

Resolved in 3.2.0. `getShellPackageName`, `getMinimumTargetJreVersion`, `getInjectorTemplateClassLoader`, and `PluginBootstrap.validate` cover the gaps this plugin hit in 3.0.0.

Still open.

1. SpringWebFlux, XxlJob, and Blade each hand-roll Netty pipeline discovery. A reusable Netty handler injector base in the SDK would remove the duplicated `ServerBootstrapAcceptor` and pipeline-anchor logic.
2. The Godzilla test client lives in the unpublished `tools` module. The official Maven artifact is the GUI, not a client library. Publish a headless client or a test fixture artifact so downstream plugins do not have to implement the protocol again.
3. `PluginBootstrap.validate` builds its class loader from the plugin jars and the caller's context loader. Host-provided shell tools are not part of the report, so a plugin cannot assert that its advertised tool mappings resolve against the installed distribution.
4. The Netty handler shell path needs `--add-opens java.base/java.lang=ALL-UNNAMED` for `ClassLoader.defineClass`. Document the requirement on the shell type or offer a public channel path.
