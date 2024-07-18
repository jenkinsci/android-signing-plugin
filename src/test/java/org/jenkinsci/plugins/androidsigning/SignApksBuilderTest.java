package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlOption;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.WithoutJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.util.VirtualFile;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.jenkinsci.plugins.androidsigning.ApkArtifactIsSignedMatcher.isSignedWith;
import static org.jenkinsci.plugins.androidsigning.TestKeyStore.KEY_ALIAS;
import static org.jenkinsci.plugins.androidsigning.TestKeyStore.KEY_STORE_ID;
import static org.jenkinsci.plugins.androidsigning.TestKeyStore.KEY_STORE_RESOURCE;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("deprecation")
public class SignApksBuilderTest {

    public static class CustomToolTestWrapper extends BuildWrapper {

        private final String[] extraEnv;

        @DataBoundConstructor
        public CustomToolTestWrapper(String... extraEnv) {
            this.extraEnv = extraEnv;
        }

        public Descriptor<BuildWrapper> getDescriptor() {
            return DESCRIPTOR;
        }

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return new BuildWrapper.Environment() {
                @Override
                public void buildEnvVars(Map<String, String> env) {
                    env.remove("ANDROID_HOME");
                }
            };
        }

        @Override
        public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {

            build.getEnvironment(listener);

            return new Launcher.DecoratedLauncher(launcher) {
                @Override
                public Proc launch(ProcStarter starter) throws IOException {
                    return getInner().launch(starter.envs(extraEnv));
                }
            };
        }

        @Extension
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        public static final class DescriptorImpl extends BuildWrapperDescriptor {
            public DescriptorImpl() {
                super(CustomToolTestWrapper.class);
            }
            @Override
            public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    private static BuildArtifact buildArtifact(FreeStyleBuild build, Run.Artifact artifact) {
        return new BuildArtifact(build, artifact);
    }

    private ApkArtifactIsSignedMatcher isSigned() throws KeyStoreException {
        return isSignedWith(KEY_STORE_ID, KEY_ALIAS);
    }

    private FilePath androidHome = null;
    private FakeZipalign zipalignLauncher = null;
    private PretendSlave slave = null;
    private JenkinsRule testJenkins = new JenkinsRule();
    private TestKeyStore keyStoreRule = new TestKeyStore(testJenkins);
    private EnvironmentVariablesNodeProperty androidHomeEnvProp = null;

    @Rule
    public RuleChain jenkinsChain = RuleChain.outerRule(testJenkins).around(keyStoreRule);

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setupEnvironment() throws Exception {
        if (testJenkins.jenkins == null) {
            return;
        }
        androidHomeEnvProp = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = androidHomeEnvProp.getEnvVars();
        URL androidHomeUrl = getClass().getResource("/android");
        String androidHomePath = androidHomeUrl.getPath();
        envVars.put("ANDROID_HOME", androidHomePath);
        testJenkins.jenkins.getGlobalNodeProperties().add(androidHomeEnvProp);
        androidHome = new FilePath(new File(androidHomeUrl.toURI()));

        // add a slave so i can use my fake launcher
        zipalignLauncher = new FakeZipalign();
        slave = testJenkins.createPretendSlave(zipalignLauncher);
        slave.setLabelString(slave.getLabelString() + " " + getClass().getSimpleName());
    }

    private FreeStyleProject createSignApkJob() throws IOException {
        FreeStyleProject job = testJenkins.createFreeStyleProject(currentTestName.getMethodName());
        job.getBuildWrappersList().add(new CopyTestWorkspace());
        job.setAssignedLabel(Label.get(getClass().getSimpleName()));
        return job;
    }

    @Test
    @WithoutJenkins
    public void setsEmptyStringsToNullForAndroidHomeAndZipalignPath() {
        SignApksBuilder builder = new SignApksBuilder();

        builder.setAndroidHome("");
        assertThat(builder.getAndroidHome(), nullValue());
        builder.setAndroidHome(" ");
        assertThat(builder.getAndroidHome(), nullValue());

        builder.setZipalignPath("");
        assertThat(builder.getZipalignPath(), nullValue());
        builder.setZipalignPath(" ");
        assertThat(builder.getZipalignPath(), nullValue());
    }

    @Test
    public void credentailsExist() {
        List<StandardCertificateCredentials> result = CredentialsProvider.lookupCredentials(
            StandardCertificateCredentials.class, testJenkins.jenkins, ACL.SYSTEM, Collections.emptyList());
        StandardCertificateCredentials credentials = CredentialsMatchers.firstOrNull(result, CredentialsMatchers.withId(KEY_STORE_ID));
        assertThat(credentials, sameInstance(keyStoreRule.credentials));
        try {
            assertTrue(credentials.getKeyStore().containsAlias(KEY_ALIAS));
        }
        catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void archivesTheSignedApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
    }

    @Test
    public void archivesTheUnsignedApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(false).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-unsigned.apk"));
    }

    @Test
    public void archivesTheUnsignedAndSignedApks() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(2));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        Run.Artifact unsignedApkArtifact = artifacts.get(1);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-unsigned.apk"));
    }

    @Test
    public void archivesNothing() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk")
            .archiveSignedApks(false).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts, empty());
    }

    @Test
    public void signsTheApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        Run.Artifact signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSigned());
    }

    @Test
    public void supportsApksWithoutUnsignedSuffix() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "SignApksBuilderTest.apk")
            .archiveSignedApks(true).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        Run.Artifact signedApkArtifact = artifacts.get(0);
        Run.Artifact unsignedApkArtifact = artifacts.get(1);

        assertThat(buildArtifact(build, signedApkArtifact), isSigned());
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.getFileName(), equalTo("SignApksBuilderTest.apk"));
    }

    @Test
    public void signsAllMatchingApks() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "SignApksBuilderTest-*.apk")
            .archiveSignedApks(true).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));
        assertThat(artifacts, hasItems(
            hasProperty("fileName", endsWith("SignApksBuilderTest-chocolate_flavor.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-chocolate_flavor-signed.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-unsigned.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-signed.apk"))));

        //noinspection Duplicates
        artifacts.forEach(artifact -> {
            try {
                if (!artifact.getFileName().endsWith("-signed.apk")) {
                    return;
                }
                assertThat(buildArtifact(build, artifact), isSigned());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void multipleBuildersDoNotOverwriteArtifacts() throws Exception {
        SignApksBuilder builder1 = new SignApksBuilder();
        builder1.setKeyStoreId(KEY_STORE_ID);
        builder1.setKeyAlias(KEY_ALIAS);
        builder1.setApksToSign("SignApksBuilderTest.apk");
        builder1.setArchiveSignedApks(true);
        builder1.setArchiveUnsignedApks(true);

        SignApksBuilder builder2 = new SignApksBuilder();
        builder2.setKeyStoreId(KEY_STORE_ID);
        builder2.setKeyAlias(KEY_ALIAS);
        builder2.setApksToSign("SignApksBuilderTest-unsigned.apk");
        builder2.setArchiveSignedApks(true);
        builder2.setArchiveUnsignedApks(true);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder1);
        job.getBuildersList().add(builder2);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));

        VirtualFile archive = build.getArtifactManager().root();
        VirtualFile[] archiveDirs = archive.list();

        assertThat(archiveDirs.length, equalTo(1));

        VirtualFile archiveDir = archiveDirs[0];
        List<String> apkNames = Arrays.asList(archiveDir.list("**/*.apk"));
        assertThat(apkNames.size(), equalTo(4));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest.apk/SignApksBuilderTest.apk"));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest.apk/SignApksBuilderTest-signed.apk"));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest-unsigned.apk/SignApksBuilderTest-unsigned.apk"));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest-unsigned.apk/SignApksBuilderTest.apk"));

        Run.Artifact bigger = artifacts.stream().filter(artifact ->
            artifact.relativePath.endsWith("SignApksBuilderTest.apk/SignApksBuilderTest-signed.apk")).findFirst().get();
        Run.Artifact smaller = artifacts.stream().filter(artifact ->
            artifact.relativePath.endsWith("SignApksBuilderTest-unsigned.apk/SignApksBuilderTest.apk")).findFirst().get();

        assertThat(bigger.getFileSize(), greaterThan(smaller.getFileSize()));
        assertThat(buildArtifact(build, bigger), isSigned());
        assertThat(buildArtifact(build, smaller), isSigned());
    }

    @Test
    public void writesSignedApkToUnsignedApkSibling() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest-unsigned.apk, standard_gradle_proj/**/*-release-unsigned.apk");
        builder.setSignedApkMapping(new SignedApkMappingStrategy.UnsignedApkSiblingMapping());
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(false);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(2));
        List<String> artifactNames = artifacts.stream().map(Run.Artifact::getFileName).collect(Collectors.toList());
        assertThat(artifactNames, everyItem(not(endsWith("-signed.apk"))));

        FilePath workspace = build.getWorkspace();
        assertThat(workspace.child("SignApksBuilderTest.apk").exists(), is(true));
        assertThat(workspace.child("standard_gradle_proj/app/build/outputs/apk/app-release.apk").exists(), is(true));
    }

    @Test
    public void supportsMultipleApkGlobs() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest.apk, *chocolate*.apk, *-unsigned.apk");
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(false);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(3));
    }

    @Test
    public void doesNotMatchTheSameApkMoreThanOnce() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest.apk, *Test.apk");
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(false);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
    }

    @Test
    public void usesAndroidHomeOverride() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FilePath androidHomeOverride = testJenkins.jenkins.getRootPath().createTempDir("android-home-override", null);
        androidHome.copyRecursiveTo(androidHomeOverride);
        builder.setAndroidHome(androidHomeOverride.getRemote());
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc.cmds().get(0), startsWith(androidHomeOverride.getRemote()));
    }

    @Test
    public void usesZipalignPathOverride() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FilePath zipalignOverride = testJenkins.jenkins.getRootPath().createTempDir("zipalign-override", null);
        zipalignOverride = zipalignOverride.createTextTempFile("zipalign-override", ".sh", "echo \"zipalign $@\"");
        builder.setZipalignPath(zipalignOverride.getRemote());
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc.cmds().get(0), startsWith(zipalignOverride.getRemote()));
    }

    @Test
    public void retrievesEnvVarsFromDecoratedLauncherForZipalignCommand() throws Exception {

        testJenkins.jenkins.getGlobalNodeProperties().remove(androidHomeEnvProp);

        FilePath decoratedZipalign = new FilePath(testDir.newFolder("decorated")).child("zipalign");
        decoratedZipalign.getParent().mkdirs();
        decoratedZipalign.touch(0);

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setApksToSign("*-unsigned.apk");
        FreeStyleProject job = createSignApkJob();
        job.getBuildWrappersList().add(new CustomToolTestWrapper("PATH+=" + decoratedZipalign.getParent().getRemote()));
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc.cmds().get(0), startsWith(decoratedZipalign.getRemote()));

        builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setApksToSign("*-unsigned.apk");
        job.getBuildWrappersList().remove(CustomToolTestWrapper.class);
        job.getBuildWrappersList().add(new CustomToolTestWrapper("ANDROID_ZIPALIGN=" + decoratedZipalign.getRemote()));
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc.cmds().get(0), startsWith(decoratedZipalign.getRemote()));
    }

    @Test
    public void abortsIfZipalignIsNotFound() throws Exception {

        for (NodeProperty property : testJenkins.jenkins.getGlobalNodeProperties()) {
            if (property instanceof EnvironmentVariablesNodeProperty) {
                EnvironmentVariablesNodeProperty envProp = (EnvironmentVariablesNodeProperty)property;
                envProp.getEnvVars().override("ANDROID_HOME", "/null_android");
            }
        }

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setApksToSign("*-unsigned.apk");
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);

        Run run = testJenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        testJenkins.assertLogContains("failed to find zipalign", run);
    }

    @Test
    public void skipsZipalign() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setApksToSign("*-unsigned.apk");
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setSkipZipalign(true);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc, nullValue());
    }

    @Test
    public void identitySubmission() throws Exception {
        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(KEY_ALIAS);
        original.setApksToSign("**/*-unsigned.apk");
        original.setSignedApkMapping(new SignedApkMappingStrategy.UnsignedApkSiblingMapping());
        original.setSkipZipalign(true);
        original.setArchiveSignedApks(!original.getArchiveSignedApks());
        original.setArchiveUnsignedApks(!original.getArchiveUnsignedApks());
        original.setAndroidHome(androidHome.getRemote());
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        testJenkins.submit(form);
        SignApksBuilder submitted = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getEntries(), nullValue());
        testJenkins.assertEqualBeans(original, submitted, String.join(",",
            "keyStoreId",
            "keyAlias",
            "apksToSign",
            "skipZipalign",
            "archiveUnsignedApks",
            "archiveSignedApks",
            "androidHome",
            "zipalignPath"
        ));
        assertThat(submitted.getSignedApkMapping(), instanceOf(original.getSignedApkMapping().getClass()));
    }

    @Test
    public void identitySubmissionWithSingleOldSigningEntry() throws Exception {
        Apk entry = new Apk(KEY_STORE_ID, KEY_ALIAS, "**/*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false);
        SignApksBuilder original = new SignApksBuilder(Collections.singletonList(entry));
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        testJenkins.submit(form);
        SignApksBuilder submitted = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getEntries(), nullValue());
        testJenkins.assertEqualBeans(original, submitted, String.join(",",
            "keyStoreId",
            "keyAlias",
            "apksToSign",
            "skipZipalign",
            "archiveUnsignedApks",
            "archiveSignedApks",
            "androidHome",
            "zipalignPath"
        ));
        assertThat(submitted.getSignedApkMapping(), instanceOf(original.getSignedApkMapping().getClass()));
    }

    @Test
    public void descriptorProvidesKeyStoreFillMethod() throws Exception {

        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(KEY_ALIAS);
        original.setApksToSign("**/*-unsigned.apk");
        original.setArchiveSignedApks(!original.getArchiveSignedApks());
        original.setArchiveUnsignedApks(!original.getArchiveUnsignedApks());
        original.setAndroidHome(androidHome.getRemote());
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        // have to do this because Descriptor.calcFillSettings() fails outside the context of a Stapler web request
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        HtmlSelect keyStoreSelect = form.getSelectByName("_.keyStoreId");
        String fillUrl = keyStoreSelect.getAttribute("fillUrl");

        assertThat(fillUrl, not(isEmptyOrNullString()));

        HtmlOption option = keyStoreSelect.getOptionByValue(KEY_STORE_ID);

        assertThat(option, notNullValue());
        assertThat(option.getValueAttribute(), equalTo(KEY_STORE_ID));
    }

    @Test
    public void savesTheKeyStoreIdWithMultipleKeyStoresPresent() throws Exception {
        TestKeyStore otherKey = new TestKeyStore(testJenkins, KEY_STORE_RESOURCE, "otherKey", null, getClass().getSimpleName());
        otherKey.addCredentials();

        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(KEY_ALIAS);
        original.setApksToSign("**/*-unsigned.apk");
        original.setArchiveSignedApks(!original.getArchiveSignedApks());
        original.setArchiveUnsignedApks(!original.getArchiveUnsignedApks());
        original.setAndroidHome(androidHome.getRemote());
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        // have to do this because Descriptor.calcFillSettings() fails outside the context of a Stapler web request
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        HtmlSelect keyStoreSelect = form.getSelectByName("_.keyStoreId");
        String fillUrl = keyStoreSelect.getAttribute("fillUrl");

        assertThat(fillUrl, not(isEmptyOrNullString()));

        HtmlOption option1 = keyStoreSelect.getOptionByValue(KEY_STORE_ID);
        HtmlOption option2 = keyStoreSelect.getOptionByValue(otherKey.credentialsId);

        assertThat(keyStoreSelect.getSelectedOptions().size(), equalTo(1));
        assertThat(keyStoreSelect.getSelectedOptions().get(0), equalTo(option1));

        keyStoreSelect.setSelectedIndex(keyStoreSelect.getOptions().indexOf(option2));

        testJenkins.submit(form);
        configPage = browser.getPage(job, "configure");
        form = configPage.getFormByName("config");
        keyStoreSelect = form.getSelectByName("_.keyStoreId");
        HtmlOption selectedOption = keyStoreSelect.getOptions().get(keyStoreSelect.getSelectedIndex());

        assertThat(selectedOption.getValueAttribute(), equalTo(otherKey.credentialsId));

        job = testJenkins.jenkins.getItemByFullName(job.getFullName(), FreeStyleProject.class);
        original = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getKeyStoreId(), equalTo(otherKey.credentialsId));

        otherKey.removeCredentials();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void doesNotSupportMultipleEntriesAnyMore() {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "ignore_me_1/**"));
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "ignore_me_2/**"));
        SignApksBuilder builder = new SignApksBuilder(entries);
    }

    @Test
    public void validatesAllApksToSignGlobs() throws Exception {

        FreeStyleProject job = createSignApkJob();

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("**/*-unsigned.apk");
        builder.setArchiveSignedApks(!builder.getArchiveSignedApks());
        builder.setArchiveUnsignedApks(!builder.getArchiveUnsignedApks());
        builder.setAndroidHome(androidHome.getRemote());
        job.getBuildersList().add(builder);

        Build build = testJenkins.buildAndAssertSuccess(job);
        SignApksBuilder.SignApksDescriptor desc = (SignApksBuilder.SignApksDescriptor) testJenkins.jenkins.getDescriptor(SignApksBuilder.class);
        String jobUrl = job.getUrl();
        String checkUrl = jobUrl + "/" + desc.getDescriptorUrl() + "/checkApksToSign?value=" + URLEncoder.encode("**/*-unsigned.apk, no_match-*.apk", "utf-8");
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        String pageText = browser.goTo(checkUrl).getWebResponse().getContentAsString();

        FilePath workspace = build.getWorkspace();
        String validationMessage = workspace.validateAntFileMask("no_match-*.apk", FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
        assertThat(pageText, containsString(validationMessage));

        workspace.deleteContents();
        workspace.createTempFile("no_match-", ".apk");

        pageText = browser.goTo(checkUrl).getWebResponse().getContentAsString();

        validationMessage = workspace.validateAntFileMask("**/*-unsigned.apk", FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
        assertThat(pageText, containsString(validationMessage));
    }

    @Test
    public void validatingApksToSignHandlesGlobMatchUpperBoundGracefully() throws Exception {

        FreeStyleProject job = createSignApkJob();

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("**/*-unsigned.apk");
        builder.setArchiveSignedApks(!builder.getArchiveSignedApks());
        builder.setArchiveUnsignedApks(!builder.getArchiveUnsignedApks());
        builder.setAndroidHome(androidHome.getRemote());
        job.getBuildersList().add(builder);

        Build build = testJenkins.buildAndAssertSuccess(job);

        FilePath workspace = build.getWorkspace();
        for (int i = 0; i < 10001; i++) {
            workspace.createTempFile(String.format("%06d-", i), ".tmp");
        }

        SignApksBuilder.SignApksDescriptor desc = (SignApksBuilder.SignApksDescriptor) testJenkins.jenkins.getDescriptor(SignApksBuilder.class);
        String jobUrl = job.getUrl();
        String checkUrl = jobUrl + "/" + desc.getDescriptorUrl() + "/checkApksToSign?value=" + URLEncoder.encode("**/*-unsigned.apk", "utf-8");
        HtmlPage page = testJenkins.createWebClient().goTo(checkUrl);
        String pageText = page.getWebResponse().getContentAsString();

        assertThat(pageText, not(containsString(InterruptedException.class.getSimpleName())));
        assertThat(pageText, containsString(Messages.validation_globSearchLimitReached(FilePath.VALIDATE_ANT_FILE_MASK_BOUND)));
    }

    @Test
    public void usesKeyStoreIdIfDescriptionIsNotPresent() throws Exception {

        TestKeyStore otherKey = new TestKeyStore(testJenkins, KEY_STORE_RESOURCE, "otherKey", null, getClass().getSimpleName());
        otherKey.addCredentials();

        SignApksBuilder original = new SignApksBuilder();
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        // have to do this because Descriptor.calcFillSettings() fails outside the context of a Stapler web request
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        HtmlSelect keyStoreSelect = form.getSelectByName("_.keyStoreId");
        String fillUrl = keyStoreSelect.getAttribute("fillUrl");

        assertThat(fillUrl, not(isEmptyOrNullString()));

        HtmlOption option1 = keyStoreSelect.getOptionByValue(KEY_STORE_ID);
        HtmlOption option2 = keyStoreSelect.getOptionByValue(otherKey.credentialsId);

        assertThat(option1.getText(), equalTo("Main Test Key Store"));
        assertThat(option2.getText(), equalTo(otherKey.credentialsId));
    }

    /**
     * Using either a null password or empty password does not work because
     * the Credentials Plugin's CertificateCredentialsImpl uses hudson.Util.fixeEmpty()
     * on the password, which turns empty strings to null.  The plugin then calls
     * KeyStore.load() with a null password which results in a NullPointerException
     * when calling KeyStore.getEntry(alias).  See also ReadingKeyStoresTest.java.
     */
    @Test
    public void passwordlessKeyStoreDoesNotWork() throws Exception {

        TestKeyStore blankPassword = new TestKeyStore(testJenkins, "/SignApksBuilderTest-exposed.p12", "exposed", null, "");
        blankPassword.addCredentials();

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId("exposed");
        builder.setKeyAlias("SignApksBuilderTest-exposed");
        builder.setApksToSign("*-unsigned.apk");

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);

        FreeStyleBuild build = testJenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        testJenkins.assertLogContains(SigningComponents.NullKeyStorePasswordException.class.getName(), build);

        blankPassword.removeCredentials();
    }

    @Test
    public void usesSingletonKeyEntryWhenAliasIsNullOrEmptyString() throws Exception {

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(null);
        builder.setApksToSign("*-unsigned.apk");

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);

        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        Run.Artifact signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSignedWith(KEY_STORE_ID, KEY_ALIAS));

        builder.setKeyAlias("");
        build = testJenkins.buildAndAssertSuccess(job);
        artifacts = build.getArtifacts();
        signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSignedWith(KEY_STORE_ID, KEY_ALIAS));
    }

    @Test
    public void givesMeaningfulErrorWhenKeyStoreDoesNotContainAlias() throws Exception {

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias("hurdur");
        builder.setApksToSign("*-unsigned.apk");

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);

        FreeStyleBuild build = testJenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        testJenkins.assertLogContains(GeneralSecurityException.class.getName(), build);
        testJenkins.assertLogContains(builder.getKeyAlias(), build);
    }

    @Test
    public void supportsMultipleKeysInKeySotre() throws Exception {

        TestKeyStore multiKeyStore = new TestKeyStore(testJenkins,
            "/SignApksBuilderTestMulti.p12", "multiKey", null, "SignApksBuilderTest");
        multiKeyStore.addCredentials();

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId("multiKey");
        builder.setKeyAlias("SignApksBuilderTest2");
        builder.setApksToSign("*-unsigned.apk");

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);

        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        Run.Artifact signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSignedWith("multiKey", "SignApksBuilderTest2"));

        builder.setKeyAlias("SignApksBuilderTest");
        build = testJenkins.buildAndAssertSuccess(job);
        artifacts = build.getArtifacts();
        signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSignedWith("multiKey", "SignApksBuilderTest"));

        multiKeyStore.removeCredentials();
    }

    @Test
    public void failsWhenAliasIsNullAndMultipleKeysArePresent() throws Exception {

        TestKeyStore multiKeyStore = new TestKeyStore(testJenkins,
            "/SignApksBuilderTestMulti.p12", "multiKey", null, "SignApksBuilderTest");
        multiKeyStore.addCredentials();

        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId("multiKey");
        builder.setKeyAlias(null);
        builder.setApksToSign("*-unsigned.apk");

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        Run build = testJenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));

        testJenkins.assertLogContains(UnrecoverableKeyException.class.getName(), build);
    }

    @Test
    public void keyAliasIsEmptyStringNotNullWhenKeyAliasFieldIsBlank() throws Exception {

        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(null);
        original.setApksToSign("**/*-unsigned.apk");
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        // have to do this because Descriptor.calcFillSettings() fails outside the context of a Stapler web request
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        HtmlInput keyAliasInput = form.getInputByName("_.keyAlias");
        String aliasFromForm = keyAliasInput.getValueAttribute();

        assertThat(aliasFromForm, isEmptyString());

        testJenkins.submit(form);
        configPage = browser.getPage(job, "configure");
        form = configPage.getFormByName("config");
        keyAliasInput = form.getInputByName("_.keyAlias");
        aliasFromForm = keyAliasInput.getValueAttribute();

        assertThat(aliasFromForm, isEmptyString());

        job = testJenkins.jenkins.getItemByFullName(job.getFullName(), FreeStyleProject.class);
        original = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getKeyAlias(), isEmptyString());
    }
}
