package io.quarkus.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.cli.build.ExecuteUtil;
import io.quarkus.cli.build.GradleRunner;
import io.quarkus.devtools.commands.CreateProjectHelper;
import io.quarkus.devtools.testing.RegistryClientTestHelper;
import picocli.CommandLine;

/**
 * Similar to CliProjectMavenTest ..
 */
public class CliProjectGradleTest {
    static final Path testProjectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
            .resolve("target/test-classes/test-project/");
    static final Path workspaceRoot = testProjectRoot.resolve("CliProjectGradleTest");
    static final Path wrapperRoot = testProjectRoot.resolve("gradle-wrapper");

    Path project;
    static File gradle;

    @BeforeAll
    public static void setupTestRegistry() {
        RegistryClientTestHelper.enableRegistryClientTestConfig();
    }

    @AfterAll
    public static void cleanupTestRegistry() {
        RegistryClientTestHelper.disableRegistryClientTestConfig();
    }

    @BeforeEach
    public void setupTestDirectories() throws Exception {
        CliDriver.deleteDir(workspaceRoot);
        project = workspaceRoot.resolve("code-with-quarkus");
    }

    @BeforeAll
    static void startGradleDaemon() throws Exception {
        CliDriver.deleteDir(wrapperRoot);

        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle", "--verbose", "-e", "-B",
                "--no-code",
                "-o", testProjectRoot.toString(),
                "gradle-wrapper");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);
        Assertions.assertTrue(result.stdout.contains("SUCCESS"),
                "Expected confirmation that the project has been created." + result);

        gradle = ExecuteUtil.findWrapper(wrapperRoot, GradleRunner.windowsWrapper, GradleRunner.otherWrapper);

        List<String> args = new ArrayList<>();
        args.add(gradle.getAbsolutePath());
        args.add("--daemon");
        CliDriver.preserveLocalRepoSettings(args);

        result = CliDriver.executeArbitraryCommand(wrapperRoot, args.toArray(new String[0]));
        Assertions.assertEquals(0, result.exitCode, "Gradle daemon should start properly");
    }

    @AfterAll
    static void stopGradleDaemon() throws Exception {
        if (gradle != null) {
            List<String> args = new ArrayList<>();
            args.add(gradle.getAbsolutePath());
            args.add("--stop");
            CliDriver.preserveLocalRepoSettings(args);

            CliDriver.Result result = CliDriver.executeArbitraryCommand(wrapperRoot, args.toArray(new String[0]));
            Assertions.assertEquals(0, result.exitCode, "Gradle daemon should stop properly");
        }
    }

    @Test
    public void testNoCode() throws Exception {
        // Inspect the no-code project created to hold the gradle wrapper
        Assertions.assertTrue(gradle.exists(), "Wrapper should exist");

        Path packagePath = wrapperRoot.resolve("src/main/java/");
        Assertions.assertTrue(packagePath.toFile().isDirectory(),
                "Source directory should exist: " + packagePath.toAbsolutePath());

        String[] files = packagePath.toFile().list();
        Assertions.assertEquals(0, files.length,
                "Source directory should be empty: " + Arrays.toString(files));
    }

    @Test
    public void testCreateAppDefaults() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle", "--verbose", "-e", "-B");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);
        Assertions.assertTrue(result.stdout.contains("SUCCESS"),
                "Expected confirmation that the project has been created." + result);

        Assertions.assertTrue(project.resolve("gradlew").toFile().exists(),
                "Wrapper should exist by default");
        Assertions.assertTrue(Files.exists(project.resolve("src/main/docker")),
                "Docker folder should exist by default");
        String buildGradleContent = validateBasicGradleGroovyIdentifiers(project, CreateProjectHelper.DEFAULT_GROUP_ID,
                CreateProjectHelper.DEFAULT_ARTIFACT_ID,
                CreateProjectHelper.DEFAULT_VERSION);
        Assertions.assertTrue(buildGradleContent.contains("quarkus-rest"),
                "build/gradle should contain quarkus-rest:\n" + buildGradleContent);

        CliDriver.valdiateGeneratedSourcePackage(project, "org/acme");

        CliDriver.invokeValidateBuild(project);
    }

    @Test
    public void testCreateAppWithoutDockerfiles() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--no-dockerfiles", "-e", "-B",
                "--verbose");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);
        Assertions.assertTrue(result.stdout.contains("SUCCESS"),
                "Expected confirmation that the project has been created." + result);
        Assertions.assertFalse(Files.exists(project.resolve("src/main/docker")),
                "Docker folder should not exist");
    }

    @Test
    public void testCreateAppDefaultsWithKotlinDSL() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle-kotlin-dsl", "--verbose", "-e",
                "-B");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);
        Assertions.assertTrue(result.stdout.contains("SUCCESS"),
                "Expected confirmation that the project has been created." + result);

        Assertions.assertTrue(project.resolve("gradlew").toFile().exists(),
                "Wrapper should exist by default");
        String buildGradleContent = validateBasicGradleKotlinIdentifiers(project, CreateProjectHelper.DEFAULT_GROUP_ID,
                CreateProjectHelper.DEFAULT_ARTIFACT_ID,
                CreateProjectHelper.DEFAULT_VERSION);
        Assertions.assertTrue(buildGradleContent.contains("quarkus-rest"),
                "build/gradle should contain quarkus-rest:\n" + buildGradleContent);

        Path packagePath = wrapperRoot.resolve("src/main/java/");
        Assertions.assertTrue(packagePath.toFile().isDirectory(),
                "Java Source directory should exist: " + packagePath.toAbsolutePath());

        CliDriver.valdiateGeneratedSourcePackage(project, "org/acme");

        CliDriver.invokeValidateBuild(project);
    }

    @Test
    public void testCreateAppOverrides() throws Exception {
        Path nested = workspaceRoot.resolve("cli-nested");
        project = nested.resolve("my-project");

        List<String> configs = Arrays.asList("custom.app.config1=val1",
                "custom.app.config2=val2", "lib.config=val3");

        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle", "--verbose", "-e", "-B",
                "--package-name=custom.pkg",
                "--output-directory=" + nested,
                "--app-config=" + String.join(",", configs),
                "-x rest",
                "silly:my-project:0.1.0");

        // TODO: would love a test that doesn't use a wrapper, but CI path..

        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);
        Assertions.assertTrue(result.stdout.contains("SUCCESS"),
                "Expected confirmation that the project has been created." + result);

        Assertions.assertTrue(project.resolve("gradlew").toFile().exists(),
                "Wrapper should exist by default");
        String buildGradleContent = validateBasicGradleGroovyIdentifiers(project, "silly", "my-project", "0.1.0");
        Assertions.assertTrue(buildGradleContent.contains("quarkus-rest"),
                "build.gradle should contain quarkus-rest:\n" + buildGradleContent);

        CliDriver.valdiateGeneratedSourcePackage(project, "custom/pkg");
        CliDriver.validateApplicationProperties(project, configs);

        result = CliDriver.invokeValidateDryRunBuild(project);
        Assertions.assertTrue(result.stdout.contains("-Dproperty=value1 -Dproperty2=value2"),
                "result should contain '-Dproperty=value1 -Dproperty2=value2':\n" + result.stdout);

        CliDriver.invokeValidateBuild(project);
    }

    @Test
    public void testCreateCliDefaults() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "cli", "--gradle", "--verbose", "-e", "-B");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);
        Assertions.assertTrue(result.stdout.contains("SUCCESS"),
                "Expected confirmation that the project has been created." + result);

        Assertions.assertTrue(project.resolve("gradlew").toFile().exists(),
                "Wrapper should exist by default");
        String buildGradleContent = validateBasicGradleGroovyIdentifiers(project, CreateProjectHelper.DEFAULT_GROUP_ID,
                CreateProjectHelper.DEFAULT_ARTIFACT_ID,
                CreateProjectHelper.DEFAULT_VERSION);
        Assertions.assertFalse(buildGradleContent.contains("quarkus-resteasy"),
                "build/gradle should not contain quarkus-resteasy:\n" + buildGradleContent);
        Assertions.assertTrue(buildGradleContent.contains("quarkus-picocli"),
                "build/gradle should contain quarkus-picocli:\n" + buildGradleContent);

        CliDriver.valdiateGeneratedSourcePackage(project, "org/acme");

        CliDriver.invokeValidateBuild(project);
    }

    @Test
    public void testExtensionList() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle", "--verbose", "-e", "-B");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);

        Path buildGradle = project.resolve("build.gradle");
        String buildGradleContent = CliDriver.readFileAsString(buildGradle);
        Assertions.assertFalse(buildGradleContent.contains("quarkus-qute"),
                "Dependencies should not contain qute extension by default. Found:\n" + buildGradleContent);

        CliDriver.invokeExtensionAddQute(project, buildGradle);
        CliDriver.invokeExtensionAddRedundantQute(project);
        CliDriver.invokeExtensionListInstallable(project);
        CliDriver.invokeExtensionAddMultiple(project, buildGradle);
        CliDriver.invokeExtensionRemoveQute(project, buildGradle);
        CliDriver.invokeExtensionRemoveMultiple(project, buildGradle);

        CliDriver.invokeExtensionListInstallableSearch(project);
        CliDriver.invokeExtensionListFormatting(project);

        // TODO: Maven and Gradle give different return codes
        result = CliDriver.invokeExtensionRemoveNonexistent(project);
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);
    }

    @Test
    public void testBuildOptions() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle", "-e", "-B", "--verbose");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);

        // 1 --clean --tests --native --offline
        result = CliDriver.execute(project, "build", "-e", "-B", "--dry-run",
                "--clean", "--tests", "--native", "--offline");

        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);

        Assertions.assertTrue(result.stdout.contains(" clean"),
                "gradle command should specify 'clean'\n" + result);

        Assertions.assertFalse(result.stdout.contains("-x test"),
                "gradle command should not specify '-x test'\n" + result);

        Assertions.assertTrue(result.stdout.contains("-Dquarkus.native.enabled=true"),
                "gradle command should specify -Dquarkus.native.enabled=true\n" + result);

        Assertions.assertTrue(result.stdout.contains("--offline"),
                "gradle command should specify --offline\n" + result);

        // 2 --no-clean --no-tests
        result = CliDriver.execute(project, "build", "-e", "-B", "--dry-run",
                "--no-clean", "--no-tests");

        Assertions.assertFalse(result.stdout.contains(" clean"),
                "gradle command should not specify 'clean'\n" + result);

        Assertions.assertTrue(result.stdout.contains("-x test"),
                "gradle command should specify '-x test'\n" + result);

        Assertions.assertFalse(result.stdout.contains("native"),
                "gradle command should not specify native\n" + result);

        Assertions.assertFalse(result.stdout.contains("offline"),
                "gradle command should not specify offline\n" + result);
    }

    @Test
    public void testDevOptions() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle", "-e", "-B", "--verbose");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);

        // 1 --clean --tests --suspend --offline
        result = CliDriver.execute(project, "dev", "-e", "--dry-run",
                "--clean", "--tests", "--debug", "--suspend", "--debug-mode=listen", "--offline");

        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);
        Assertions.assertTrue(result.stdout.contains("GRADLE"),
                "gradle command should specify 'GRADLE'\n" + result);

        Assertions.assertTrue(result.stdout.contains(" clean"),
                "gradle command should specify 'clean'\n" + result);

        Assertions.assertFalse(result.stdout.contains("-x test"),
                "gradle command should not specify '-x test'\n" + result);

        Assertions.assertFalse(result.stdout.contains("-Ddebug"),
                "gradle command should not specify '-Ddebug'\n" + result);

        Assertions.assertTrue(result.stdout.contains("-Dsuspend"),
                "gradle command should specify '-Dsuspend'\n" + result);

        Assertions.assertTrue(result.stdout.contains("--offline"),
                "gradle command should specify --offline\n" + result);

        // 2 --no-clean --no-tests --no-debug
        result = CliDriver.execute(project, "dev", "-e", "--dry-run",
                "--no-clean", "--no-tests", "--no-debug");

        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);
        Assertions.assertTrue(result.stdout.contains("GRADLE"),
                "gradle command should specify 'GRADLE'\n" + result);

        Assertions.assertFalse(result.stdout.contains(" clean"),
                "gradle command should not specify 'clean'\n" + result);

        Assertions.assertFalse(result.stdout.contains("-x test"),
                "gradle command should not specify '-x test' (ignored)\n" + result);

        Assertions.assertTrue(result.stdout.contains("-Ddebug=false"),
                "gradle command should specify '-Ddebug=false'\n" + result);

        Assertions.assertFalse(result.stdout.contains("-Dsuspend"),
                "gradle command should not specify '-Dsuspend'\n" + result);

        // 3 --no-suspend --debug-host=0.0.0.0 --debug-port=8008 --debug-mode=connect -- arg1 arg2
        result = CliDriver.execute(project, "dev", "-e", "--dry-run",
                "--no-suspend", "--debug-host=0.0.0.0", "--debug-port=8008", "--debug-mode=connect", "--", "arg1", "arg2");

        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);
        Assertions.assertTrue(result.stdout.contains("GRADLE"),
                "gradle command should specify 'GRADLE'\n" + result);

        Assertions.assertTrue(
                result.stdout.contains("-DdebugHost=0.0.0.0 -Ddebug=client -DdebugPort=8008"),
                "gradle command should specify -DdebugHost=0.0.0.0 -Ddebug=client -DdebugPort=8008\n" + result);

        Assertions.assertFalse(result.stdout.contains("-Dsuspend"),
                "gradle command should not specify '-Dsuspend'\n" + result);

        Assertions.assertTrue(result.stdout.contains("-Dquarkus.args=\"arg1\" \"arg2\""),
                "gradle command should not specify -Dquarkus.args=\"arg1\" \"arg2\"\n" + result);

        // 4 TEST MODE: test --clean --debug --suspend --offline
        result = CliDriver.execute(project, "test", "-e", "--dry-run",
                "--clean", "--debug", "--suspend", "--debug-mode=listen", "--offline", "--filter=FooTest");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);
        Assertions.assertTrue(result.stdout.contains("Run current project in continuous test mode"), result.toString());
        Assertions.assertTrue(result.stdout.contains("-Dquarkus.test.include-pattern=FooTest"), result.toString());

        // 5 TEST MODE - run once: test --once --offline
        result = CliDriver.execute(project, "test", "-e", "--dry-run",
                "--once", "--offline", "--filter=FooTest");
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode,
                "Expected OK return code. Result:\n" + result);
        Assertions.assertTrue(result.stdout.contains("Run current project in test mode"), result.toString());
        Assertions.assertTrue(result.stdout.contains("--tests FooTest"), result.toString());

        // 6 TEST MODE: Two word argument
        result = CliDriver.execute(project, "dev", "-e", "--dry-run",
                "--no-suspend", "--debug-host=0.0.0.0", "--debug-port=8008", "--debug-mode=connect", "--", "arg1 arg2");

        Assertions.assertTrue(result.stdout.contains("-Dquarkus.args=\"arg1 arg2\""),
                "mvn command should not specify -Dquarkus.args=\"arg1 arg2\"\n" + result);
    }

    @Test
    public void testCreateArgPassthrough() throws Exception {
        Path nested = workspaceRoot.resolve("cli-nested");
        project = nested.resolve("my-project");

        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "--gradle",
                "--verbose", "-e", "-B",
                "--dryrun", "--no-wrapper", "--package-name=custom.pkg",
                "--output-directory=" + nested,
                "silly:my-project:0.1.0");

        // We don't need to retest this, just need to make sure all the arguments were passed through
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);

        Assertions.assertTrue(result.stdout.contains("Creating an app"),
                "Should contain 'Creating an app', found: " + result.stdout);
        Assertions.assertTrue(result.stdout.contains("GRADLE"),
                "Should contain MAVEN, found: " + result.stdout);

        // strip spaces to avoid fighting with column whitespace
        String noSpaces = result.stdout.replaceAll("[\\s\\p{Z}]", "");
        Assertions.assertTrue(noSpaces.contains("Omitbuildtoolwrappertrue"),
                "Should contain 'Omit build tool wrapper   true', found: " + result.stdout);
        Assertions.assertTrue(noSpaces.contains("PackageNamecustom.pkg"),
                "Should contain 'Package Name   custom.pkg', found: " + result.stdout);
        Assertions.assertTrue(noSpaces.contains("ProjectArtifactIdmy-project"),
                "Output should contain 'Project ArtifactId   my-project', found: " + result.stdout);
        Assertions.assertTrue(noSpaces.contains("ProjectGroupIdsilly"),
                "Output should contain 'Project GroupId   silly', found: " + result.stdout);
    }

    @Test
    public void testCreateArgJava17() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle",
                "-e", "-B", "--verbose",
                "--java", "17");

        // We don't need to retest this, just need to make sure all the arguments were passed through
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);

        Path buildGradle = project.resolve("build.gradle");
        String buildGradleContent = CliDriver.readFileAsString(buildGradle);

        Assertions.assertTrue(buildGradleContent.contains("sourceCompatibility = JavaVersion.VERSION_17"),
                "Java 17 should be used when specified. Found:\n" + buildGradleContent);
    }

    @Test
    public void testCreateArgJava21() throws Exception {
        CliDriver.Result result = CliDriver.execute(workspaceRoot, "create", "app", "--gradle",
                "-e", "-B", "--verbose",
                "--java", "21");

        // We don't need to retest this, just need to make sure all the arguments were passed through
        Assertions.assertEquals(CommandLine.ExitCode.OK, result.exitCode, "Expected OK return code." + result);

        Path buildGradle = project.resolve("build.gradle");
        String buildGradleContent = CliDriver.readFileAsString(buildGradle);

        Assertions.assertTrue(buildGradleContent.contains("sourceCompatibility = JavaVersion.VERSION_21"),
                "Java 21 should be used when specified. Found:\n" + buildGradleContent);
    }

    String validateBasicGradleGroovyIdentifiers(Path project, String group, String artifact, String version) throws Exception {
        Path buildGradle = project.resolve("build.gradle");
        Assertions.assertTrue(buildGradle.toFile().exists(),
                "build.gradle should exist: " + buildGradle.toAbsolutePath().toString());

        String buildContent = CliDriver.readFileAsString(buildGradle);
        Assertions.assertTrue(buildContent.contains("group = '" + group + "'"),
                "build.gradle should include the group id: " + group + " but was:\n" + buildContent);
        Assertions.assertTrue(buildContent.contains("version = '" + version + "'"),
                "build.gradle should include the version: " + version + " but was:\n" + buildContent);

        Path settings = project.resolve("settings.gradle");
        Assertions.assertTrue(settings.toFile().exists(),
                "settings.gradle should exist: " + settings.toAbsolutePath().toString());
        String settingsContent = CliDriver.readFileAsString(settings);
        Assertions.assertTrue(settingsContent.contains(artifact),
                "settings.gradle should include the artifact id: " + artifact + " but was:\n" + settingsContent);

        return buildContent;
    }

    String validateBasicGradleKotlinIdentifiers(Path project, String group, String artifact, String version) throws Exception {
        Path buildGradle = project.resolve("build.gradle.kts");
        Assertions.assertTrue(buildGradle.toFile().exists(),
                "build.gradle.kts should exist: " + buildGradle.toAbsolutePath().toString());

        String buildContent = CliDriver.readFileAsString(buildGradle);
        Assertions.assertTrue(buildContent.contains("group = \"" + group + "\""),
                "build.gradle.kts should include the group id: " + group + " but was:\n" + buildContent);
        Assertions.assertTrue(buildContent.contains("version = \"" + version + "\""),
                "build.gradle.kts should include the version: " + version + " but was:\n" + buildContent);

        Path settings = project.resolve("settings.gradle.kts");
        Assertions.assertTrue(settings.toFile().exists(),
                "settings.gradle.kts should exist: " + settings.toAbsolutePath().toString());
        String settingsContent = CliDriver.readFileAsString(settings);
        Assertions.assertTrue(settingsContent.contains(artifact),
                "settings.gradle.kts should include the artifact id: " + artifact + " but was:\n" + settingsContent);

        return buildContent;
    }
}
