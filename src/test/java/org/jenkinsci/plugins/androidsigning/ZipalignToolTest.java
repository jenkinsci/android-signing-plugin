package org.jenkinsci.plugins.androidsigning;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.StreamBuildListener;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;


class ZipalignToolTest {

    private FilePath workspace;
    private FilePath androidHome;
    private FilePath androidHomeZipalign;
    private FilePath altZipalign;

    @TempDir
    private File tempDir;

    @BeforeEach
    void beforeEach() throws Exception {
        FilePath tempDirPath = new FilePath(tempDir);

        URL workspaceUrl = getClass().getResource("/workspace");
        FilePath workspace = new FilePath(new File(workspaceUrl.toURI()));
        this.workspace = tempDirPath.child("workspace");
        workspace.copyRecursiveTo(this.workspace);

        URL androidHomeUrl = getClass().getResource("/android");
        FilePath androidHome = new FilePath(new File(androidHomeUrl.toURI()));
        this.androidHome = tempDirPath.child("android-sdk");
        androidHome.copyRecursiveTo(this.androidHome);
        androidHomeZipalign = this.androidHome.child("build-tools").child("1.0").child("zipalign");

        URL altZipalignUrl = getClass().getResource("/alt-zipalign");
        FilePath altZipalign = new FilePath(new File(altZipalignUrl.toURI()));
        this.altZipalign = tempDirPath.child("alt-zipalign");
        altZipalign.copyRecursiveTo(this.altZipalign);
        this.altZipalign = this.altZipalign.child("zipalign");
    }

    @Test
    void findsZipalignInAndroidHomeEnvVar() throws Exception {
        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    void findsZipalignInAndroidZipalignEnvVar() throws Exception {
        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    void findsZipalignInPathEnvVarWithToolsDir() throws Exception {
        FilePath toolsDir = androidHome.child("tools");
        toolsDir.mkdirs();
        FilePath androidTool = toolsDir.child("android");
        androidTool.write(getClass().getSimpleName(), "utf-8");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        String otherTools = launcher.isUnix() ? "/other/tools" : "\\other\\tools";
        String otherBin = launcher.isUnix() ? "/other/bin" : "\\other\\bin";

        String path = String.join(File.pathSeparator, toolsDir.getRemote(), otherTools, otherBin);
        envVars.put(ZipalignTool.ENV_PATH, path);
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, otherTools, toolsDir.getRemote(), otherBin);
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, otherTools, otherBin, toolsDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, toolsDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    void findsZipalignInPathEnvVarWithToolsBinDir() throws Exception {
        FilePath toolsBinDir = androidHome.child("tools").child("bin");
        toolsBinDir.mkdirs();
        FilePath sdkmanagerTool = toolsBinDir.child("sdkmanager");
        sdkmanagerTool.write(getClass().getSimpleName(), "utf-8");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        String otherTools = launcher.isUnix() ? "/other/tools" : "\\other\\tools";
        String otherBin = launcher.isUnix() ? "/other/bin" : "\\other\\bin";

        String path = String.join(File.pathSeparator, toolsBinDir.getRemote(), otherTools, otherBin);
        envVars.put(ZipalignTool.ENV_PATH, path);
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, otherTools, toolsBinDir.getRemote(), otherBin);
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, otherTools, otherBin, toolsBinDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, toolsBinDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    void findsZipalignInPathEnvVarWithZipalignParentDir() throws Exception {
        FilePath zipalignDir = altZipalign.getParent();

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        String otherTools = launcher.isUnix() ? "/other/tools" : "\\other\\tools";
        String otherBin = launcher.isUnix() ? "/other/bin" : "\\other\\bin";

        String path = String.join(File.pathSeparator, zipalignDir.getRemote(), otherTools, otherBin);
        envVars.put(ZipalignTool.ENV_PATH, path);
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        path = String.join(File.pathSeparator, otherTools, zipalignDir.getRemote(), otherBin);
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        path = String.join(File.pathSeparator, otherTools, otherBin, zipalignDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        path = String.join(File.pathSeparator, zipalignDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    void androidZiplignOverridesAndroidHome() throws Exception {
        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    void usesLatestZipalignFromAndroidHome() throws IOException, InterruptedException {
        FilePath newerBuildTools = androidHome.child("build-tools").child("1.1");
        newerBuildTools.mkdirs();
        FilePath newerZipalign = newerBuildTools.child("zipalign");
        newerZipalign.write("# fake zipalign", "utf-8");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(newerZipalign.getRemote()));

        newerBuildTools.deleteRecursive();
    }

    @Test
    void explicitAndroidHomeOverridesEnvVars() throws Exception {
        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitAndroidHome = workspace.createTempDir("my-android-home", "");
        androidHome.copyRecursiveTo(explicitAndroidHome);

        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, explicitAndroidHome.getRemote(), null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitAndroidHome.getRemote()));

        explicitAndroidHome.deleteRecursive();
    }

    @Test
    void explicitZipalignOverridesEnvZipaligns() throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitZipalign = workspace.createTempDir("my-zipalign", "").child("zipalign");
        explicitZipalign.write("# fake zipalign", "utf-8");
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, explicitZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitZipalign.getRemote()));

        explicitZipalign.getParent().deleteRecursive();
    }

    @Test
    void explicitZipalignOverridesEverything() throws Exception {
        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitAndroidHome = workspace.createTempDir("my-android-home", "");
        androidHome.copyRecursiveTo(explicitAndroidHome);

        FilePath explicitZipalign = workspace.createTempDir("my-zipalign", "").child("zipalign");
        explicitZipalign.write("# fake zipalign", "utf-8");

        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, explicitAndroidHome.getRemote(), explicitZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitZipalign.getRemote()));

        explicitZipalign.getParent().deleteRecursive();
        explicitAndroidHome.deleteRecursive();
    }

    @Test
    void triesWindowsExeIfEnvAndroidHomeZipalignDoesNotExist() throws IOException, URISyntaxException {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath winAndroidHomeZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ANDROID_HOME, winAndroidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHomeZipalign.getRemote()));
    }

    @Test
    void triesWindowsExeIfEnvZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath suffixedZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");
        FilePath unsuffixedZipalign = suffixedZipalign.getParent().child("zipalign");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, unsuffixedZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(suffixedZipalign.getRemote()));
    }

    @Test
    void triesWindowsExeIfExplicitAndroidHomeZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath winAndroidHomeZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, winAndroidHome.getRemote(), null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHomeZipalign.getRemote()));
    }

    @Test
    void triesWindowsExeIfExplicitZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath suffixedZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");
        FilePath unsuffixedZipalign = suffixedZipalign.getParent().child("zipalign");

        EnvVars envVars = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, envVars, workspace, System.out, null, unsuffixedZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(suffixedZipalign.getRemote()));
    }

    @Test
    void resolvesVariableReferencesInExplicitParameters() throws Exception {
        EnvVars env = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        env.put("ALT_ZIPALIGN", altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, env, workspace, System.out, null, "${ALT_ZIPALIGN}");
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        env.clear();
        env.put("ALT_ANDROID_HOME", androidHome.getRemote());
        zipalign = new ZipalignTool(launcher, bytes, env, workspace, System.out, "${ALT_ANDROID_HOME}", null);
        cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    void findsWindowsZipalignFromEnvPath() throws Exception {
        URL url = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(url.toURI()));
        EnvVars env = new EnvVars();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TaskListener taskListener = new StreamBuildListener(bytes, Charset.defaultCharset());
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        env.put("PATH", winAndroidHome.getRemote());

        ZipalignTool zipalign = new ZipalignTool(launcher, bytes, env, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHome.getRemote()));
    }
}
