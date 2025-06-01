package org.jenkinsci.plugins.androidsigning;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.ArgumentListBuilder;
import hudson.util.VersionNumber;


class ZipalignTool {

    static final String ENV_ANDROID_HOME = "ANDROID_HOME";
    static final String ENV_ZIPALIGN_PATH = "ANDROID_ZIPALIGN";
    static final String ENV_PATH = "PATH";

    private static FilePath findFromEnv(EnvVars env, FilePath workspace, PrintStream logger) throws AbortException {

        String zipalignPath = env.get(ENV_ZIPALIGN_PATH);
        if (!StringUtils.isEmpty(zipalignPath)) {
            zipalignPath = env.expand(zipalignPath);
            logger.printf("[SignApksBuilder] found zipalign path in env %s=%s%n", ENV_ZIPALIGN_PATH, zipalignPath);
            FilePath zipalign = new FilePath(workspace.getChannel(), zipalignPath);
            return zipalignOrZipalignExe(zipalign, logger);
        }

        String androidHome = env.get(ENV_ANDROID_HOME);
        if (!StringUtils.isEmpty(androidHome)) {
            androidHome = env.expand(androidHome);
            logger.printf("[SignApksBuilder] searching environment variable %s=%s for zipalign...%n", ENV_ANDROID_HOME, androidHome);
            return findInAndroidHome(androidHome, workspace, logger);
        }

        String envPath = env.get(ENV_PATH);
        if (!StringUtils.isEmpty(envPath)) {
            envPath = env.expand(envPath);
            logger.printf("[SignApksBuilder] searching environment %s=%s for zipalign...%n", ENV_PATH, envPath);
            return findInPathEnvVar(envPath, workspace, logger);
        }

        throw new AbortException("failed to find zipalign: no environment variable " + ENV_ZIPALIGN_PATH + " or " + ENV_ANDROID_HOME + " or " + ENV_PATH);
    }

    private static FilePath findInAndroidHome(String androidHome, FilePath workspace, PrintStream logger) throws AbortException {

        FilePath buildTools = workspace.child(androidHome).child("build-tools");
        List<FilePath> versionDirs;
        try {
            versionDirs = buildTools.listDirectories();
        }
        catch (Exception e) {
            e.printStackTrace(logger);
            throw new AbortException(String.format(
                "failed to find zipalign: error listing build-tools versions in %s: %s",
                buildTools.getRemote(), e.getLocalizedMessage()));
        }

        if (versionDirs == null || versionDirs.isEmpty()) {
            throw new AbortException("failed to find zipalign: no build-tools directory in Android home path " + androidHome);
        }

        SortedMap<VersionNumber, FilePath> versions = new TreeMap<>();
        for (FilePath versionDir : versionDirs) {
            String versionName = versionDir.getName();
            VersionNumber version = new VersionNumber(versionName);
            versions.put(version, versionDir);
        }

        if (versions.isEmpty()) {
            throw new AbortException(
                "failed to find zipalign: no build-tools versions in Android home path " + buildTools);
        }

        VersionNumber latest = versions.lastKey();
        buildTools = versions.get(latest);
        FilePath zipalign = zipalignOrZipalignExe(buildTools, logger);
        if (zipalign != null) {
            logger.printf("[SignApksBuilder] found zipalign in Android SDK's latest build tools: %s%n", zipalign.getRemote());
            return zipalign;
        }

        throw new AbortException("failed to find zipalign: no zipalign found in latest Android build tools: " + buildTools);
    }

    private static FilePath findInPathEnvVar(String envPath, FilePath workspace, PrintStream logger) throws AbortException {
        String separator = null;
        try {
            separator = pathSeparatorForWorkspace(workspace);
        }
        catch (Exception e) {
            logger.println("[SignApksBuilder] error determining path separator:");
            e.printStackTrace(logger);
            return null;
        }
        String[] dirs = envPath.split(separator);
        for (String dir : dirs) {
            logger.printf("[SignApksBuilder] checking %s dir %s for zipalign...%n", ENV_PATH, dir);
            FilePath dirPath = workspace.child(dir);
            FilePath zipalign = zipalignOrZipalignExe(dirPath, logger);
            if (zipalign != null) {
                return zipalign;
            }
            try {
                dirPath = androidHomeAncestorOfPath(dirPath, logger);
            }
            catch (Exception e) {
                logger.println("error searching " + ENV_PATH + " environment variable: " + e.getMessage());
                e.printStackTrace(logger);
            }
            if (dirPath != null) {
                logger.printf("[SignApksBuilder] found potential Android home in %s dir %s%n", ENV_PATH, dir);
                try {
                    return findInAndroidHome(dirPath.getRemote(), workspace, logger);
                }
                catch (AbortException e) {
                    logger.printf("error searching Android home found in " + ENV_PATH + ": " + e.getMessage());
                }
            }
        }

        return null;
    }

    private static String pathSeparatorForWorkspace(FilePath workspace) throws IOException, InterruptedException {
        return workspace.act(new GetPathSeparator());
    }

    private static FilePath androidHomeAncestorOfPath(FilePath path, PrintStream logger) throws Exception {
        if ("bin".equals(path.getName())) {
            FilePath sdkmanager = path.child("sdkmanager");
            if (commandOrWinCommandAtPath(sdkmanager, logger) != null) {
                path = path.getParent();
                if (path != null && "tools".equals(path.getName())) {
                    return path.getParent();
                }
            }
        }
        else if ("tools".equals(path.getName())) {
            FilePath androidTool = path.child("android");
            if (commandOrWinCommandAtPath(androidTool, logger) != null) {
                return path.getParent();
            }
        }
        else {
            FilePath androidTool = path.child("tools").child("android");
            if (commandOrWinCommandAtPath(androidTool, logger) != null) {
                return path;
            }
        }

        return null;
    }

    private static FilePath zipalignOrZipalignExe(FilePath zipalignOrDir, PrintStream logger) {
        FilePath parent = zipalignOrDir.getParent();
        try {
            if (zipalignOrDir.isDirectory()) {
                parent = zipalignOrDir;
                zipalignOrDir = zipalignOrDir.child("zipalign");
            }
        }
        catch (Exception e) {
            logger.println("[SignApksBuilder] error checking for zipalign at path " + zipalignOrDir);
            e.printStackTrace(logger);
        }
        zipalignOrDir = commandOrWinCommandAtPath(zipalignOrDir, logger);
        if (zipalignOrDir != null) {
            return zipalignOrDir;
        }

        logger.println("[SignApksBuilder] no zipalign or zipalign.exe found in path " + parent);
        return null;
    }

    private static FilePath commandOrWinCommandAtPath(FilePath path, PrintStream logger) {
        try {
            if (path.isDirectory()) {
                return null;
            }
            if (path.exists()) {
                return path;
            }
            FilePath parent = path.getParent();
            if (parent == null) {
                return null;
            }
            String name = path.getName();
            String winCommand = name + ".exe";
            path = parent.child(winCommand);
            if (path.exists()) {
                return path;
            }
            winCommand = name + ".bat";
            path = parent.child(winCommand);
            if (path.exists()) {
                return path;
            }
        }
        catch (Exception e) {
            logger.println("[SignApksBuilder] error checking path " + path);
            e.printStackTrace(logger);
        }

        return null;
    }

    private final EnvVars buildEnv;
    private final FilePath workspace;
    private final PrintStream logger;
    private final String overrideAndroidHome;
    private final String overrideZipalignPath;
    private FilePath zipalign;

    ZipalignTool(@Nonnull EnvVars buildEnv, @Nonnull FilePath workspace, @Nonnull PrintStream logger, @Nullable String overrideAndroidHome, @Nullable String overrideZipalignPath) {
        this.buildEnv = buildEnv;
        this.workspace = workspace;
        this.logger = logger;
        this.overrideAndroidHome = overrideAndroidHome;
        this.overrideZipalignPath = overrideZipalignPath;
    }

    ArgumentListBuilder commandFor(String unsignedApk, String outputApk) throws AbortException {
        if (zipalign == null) {
            if (!StringUtils.isEmpty(overrideZipalignPath)) {
                logger.printf("[SignApksBuilder] zipalign path explicitly set to %s%n", overrideZipalignPath);
                zipalign = zipalignOrZipalignExe(workspace.child(buildEnv.expand(overrideZipalignPath)), logger);
            }
            else if (!StringUtils.isEmpty(overrideAndroidHome)) {
                logger.printf("[SignApksBuilder] zipalign %s explicitly set to %s%n", ENV_ANDROID_HOME, overrideAndroidHome);
                String expandedAndroidHome = buildEnv.expand(overrideAndroidHome);
                zipalign = findInAndroidHome(expandedAndroidHome, workspace, this.logger);
            }
            else {
                zipalign = findFromEnv(buildEnv, workspace, logger);
            }

            if (zipalign == null) {
                throw new AbortException("failed to find zipalign path in parameters or environment");
            }
        }

        return new ArgumentListBuilder()
            .add(zipalign.getRemote())
            .add("-f")
            .add("-p").add("4")
            .add(unsignedApk)
            .add(outputApk);
    }
}
