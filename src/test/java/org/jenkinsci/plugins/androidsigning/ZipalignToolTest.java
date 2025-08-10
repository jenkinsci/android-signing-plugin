package org.jenkinsci.plugins.androidsigning;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.util.ArgumentListBuilder;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;


public class ZipalignToolTest {

    FilePath workspace;
    FilePath androidHome;
    FilePath androidHomeZipalign;
    FilePath altZipalign;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void copyWorkspace() throws Exception {
        FilePath tempDirPath = new FilePath(tempDir.getRoot());

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
    public void findsZipalignInAndroidHomeEnvVar() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    public void findsZipalignInAndroidZipalignEnvVar() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    public void findsZipalignInPathEnvVarWithToolsDir() throws Exception {
        FilePath toolsDir = androidHome.child("tools");
        toolsDir.mkdirs();
        FilePath androidTool = toolsDir.child("android");
        androidTool.write(getClass().getSimpleName(), "utf-8");

        EnvVars envVars = new EnvVars();

        String path = String.join(File.pathSeparator, toolsDir.getRemote(), "/other/tools", "/other/bin");
        envVars.put(ZipalignTool.ENV_PATH, path);
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, "/other/tools", toolsDir.getRemote(), "/other/bin");
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, "/other/tools", "/other/bin", toolsDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, toolsDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    public void findsZipalignInPathEnvVarWithToolsBinDir() throws Exception {
        FilePath toolsBinDir = androidHome.child("tools").child("bin");
        toolsBinDir.mkdirs();
        FilePath sdkmanagerTool = toolsBinDir.child("sdkmanager");
        sdkmanagerTool.write(getClass().getSimpleName(), "utf-8");

        EnvVars envVars = new EnvVars();

        String path = String.join(File.pathSeparator, toolsBinDir.getRemote(), "/other/tools", "/other/bin");
        envVars.put(ZipalignTool.ENV_PATH, path);
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, "/other/tools", toolsBinDir.getRemote(), "/other/bin");
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, "/other/tools", "/other/bin", toolsBinDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));

        path = String.join(File.pathSeparator, toolsBinDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }
    
    @Test
    public void findsZipalignInPathEnvVarWithZipalignParentDir() throws Exception {
        FilePath zipalignDir = altZipalign.getParent();

        EnvVars envVars = new EnvVars();

        String path = String.join(File.pathSeparator, zipalignDir.getRemote(), "/other/tools", "/other/bin");
        envVars.put(ZipalignTool.ENV_PATH, path);
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        path = String.join(File.pathSeparator, "/other/tools", zipalignDir.getRemote(), "/other/bin");
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        path = String.join(File.pathSeparator, "/other/tools", "/other/bin", zipalignDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        path = String.join(File.pathSeparator, zipalignDir.getRemote());
        envVars.put(ZipalignTool.ENV_PATH, path);
        zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        cmd = zipalign.commandFor("path-test.apk", "path-test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    public void androidZiplignOverridesAndroidHome() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    public void usesLatestZipalignFromAndroidHome() throws IOException, InterruptedException {
        FilePath newerBuildTools = androidHome.child("build-tools").child("1.1");
        newerBuildTools.mkdirs();
        FilePath newerZipalign = newerBuildTools.child("zipalign");
        newerZipalign.write("# fake zipalign", "utf-8");

        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(newerZipalign.getRemote()));

        newerBuildTools.deleteRecursive();
    }

    @Test
    public void explicitAndroidHomeOverridesEnvVars() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitAndroidHome = workspace.createTempDir("my-android-home", "");
        androidHome.copyRecursiveTo(explicitAndroidHome);

        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, explicitAndroidHome.getRemote(), null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitAndroidHome.getRemote()));

        explicitAndroidHome.deleteRecursive();
    }

    @Test
    public void explicitZipalignOverridesEnvZipaligns() throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitZipalign = workspace.createTempDir("my-zipalign", "").child("zipalign");
        explicitZipalign.write("# fake zipalign", "utf-8");
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, explicitZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitZipalign.getRemote()));

        explicitZipalign.getParent().deleteRecursive();
    }

    @Test
    public void explicitZipalignOverridesEverything() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitAndroidHome = workspace.createTempDir("my-android-home", "");
        androidHome.copyRecursiveTo(explicitAndroidHome);

        FilePath explicitZipalign = workspace.createTempDir("my-zipalign", "").child("zipalign");
        explicitZipalign.write("# fake zipalign", "utf-8");

        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, explicitAndroidHome.getRemote(), explicitZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitZipalign.getRemote()));

        explicitZipalign.getParent().deleteRecursive();
        explicitAndroidHome.deleteRecursive();
    }

    @Test
    public void triesWindowsExeIfEnvAndroidHomeZipalignDoesNotExist() throws IOException, InterruptedException, URISyntaxException {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath winAndroidHomeZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");

        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, winAndroidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHomeZipalign.getRemote()));
    }

    @Test
    public void triesWindowsExeIfEnvZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath suffixedZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");
        FilePath unsuffixedZipalign = suffixedZipalign.getParent().child("zipalign");

        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, unsuffixedZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(suffixedZipalign.getRemote()));
    }

    @Test
    public void triesWindowsExeIfExplicitAndroidHomeZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath winAndroidHomeZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");

        EnvVars envVars = new EnvVars();
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, winAndroidHome.getRemote(), null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHomeZipalign.getRemote()));
    }

    @Test
    public void triesWindowsExeIfExplicitZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath suffixedZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");
        FilePath unsuffixedZipalign = suffixedZipalign.getParent().child("zipalign");

        EnvVars envVars = new EnvVars();
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, unsuffixedZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(suffixedZipalign.getRemote()));
    }

    @Test
    public void resolvesVariableReferencesInExplicitParameters() throws Exception {
        EnvVars env = new EnvVars();
        env.put("ALT_ZIPALIGN", altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(env, workspace, System.out, null, "${ALT_ZIPALIGN}");
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));

        env.clear();
        env.put("ALT_ANDROID_HOME", androidHome.getRemote());
        zipalign = new ZipalignTool(env, workspace, System.out, "${ALT_ANDROID_HOME}", null);
        cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    public void findsWindowsZipalignFromEnvPath() throws Exception {
        URL url = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(url.toURI()));
        EnvVars env = new EnvVars();
        env.put("PATH", winAndroidHome.getRemote());

        ZipalignTool zipalign = new ZipalignTool(env, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHome.getRemote()));
    }
}
