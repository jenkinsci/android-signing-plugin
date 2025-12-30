package org.jenkinsci.plugins.androidsigning;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;


@WithJenkins
class SignApksStepTest {

    private JenkinsRule testJenkins;
    private TestKeyStore testKeyStore;

    private String androidHome;
    private PretendSlave slave;
    private FakeZipalign zipalign;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        testJenkins = rule;
        testKeyStore = new TestKeyStore(testJenkins);
        testKeyStore.addCredentials();

        URL androidHomeUrl = getClass().getResource("/android");
        androidHome = new File(androidHomeUrl.toURI()).getAbsolutePath();
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("ANDROID_HOME", androidHome);
        testJenkins.jenkins.getGlobalNodeProperties().add(prop);
        zipalign = new FakeZipalign();
        slave = testJenkins.createPretendSlave(zipalign);
        slave.getComputer().getEnvironment().put("ANDROID_HOME", androidHome);
        slave.setLabelString(slave.getLabelString() + " " + getClass().getSimpleName());
    }

    @AfterEach
    void afterEach() {
        testKeyStore.removeCredentials();
    }

    @Test
    void dslWorks() throws Exception {
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: '*-unsigned.apk, **/*-release-unsigned.apk',%n" +
            "      archiveSignedApks: true,%n" +
            "      archiveUnsignedApks: true,%n" +
            "      androidHome: env.ANDROID_HOME%n" +
            "    )%n" +
            "  }%n" +
            "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS), false));

        WorkflowRun build = testJenkins.buildAndAssertSuccess(job);
        List<String> artifactNames = build.getArtifacts().stream().map(Run.Artifact::getFileName).collect(Collectors.toList());

        assertThat(artifactNames.size(), equalTo(4));
        assertThat(artifactNames, hasItem(endsWith("SignApksBuilderTest-unsigned.apk")));
        assertThat(artifactNames, hasItem(endsWith("SignApksBuilderTest.apk")));
        assertThat(artifactNames, hasItem(endsWith("app-release-unsigned.apk")));
        assertThat(artifactNames, hasItem(endsWith("app-release.apk")));
        assertThat(zipalign.lastProc.cmds().get(0), startsWith(androidHome));
    }

    @Test
    void setsAndroidHomeFromEnvVarsIfNotSpecifiedInScript() throws Exception {
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: '*-unsigned.apk, **/*-release-unsigned.apk'%n" +
            "    )%n" +
            "  }%n" +
            "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS), false));

        WorkflowRun build = testJenkins.buildAndAssertSuccess(job);
        List<String> artifactNames = build.getArtifacts().stream().map(Run.Artifact::getFileName).collect(Collectors.toList());

        assertThat(artifactNames.size(), equalTo(2));
        assertThat(artifactNames, hasItem(endsWith("SignApksBuilderTest.apk")));
        assertThat(artifactNames, hasItem(endsWith("app-release.apk")));
        assertThat(zipalign.lastProc.cmds().get(0), startsWith(androidHome));
    }

    @Test
    void setsAndroidZipalignFromEnvVarsIfNotSpecifiedInScript() throws Exception {
        URL altZipalignUrl = getClass().getResource("/alt-zipalign/zipalign");
        String altZipalign = new File(altZipalignUrl.toURI()).getAbsolutePath();
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("ANDROID_ZIPALIGN", altZipalign);

        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: '**/*-unsigned.apk'%n" +
            "    )%n" +
            "  }%n" +
            "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS), false));

        WorkflowRun build = testJenkins.buildAndAssertSuccess(job);
        List<String> artifactNames = build.getArtifacts().stream().map(Run.Artifact::getFileName).collect(Collectors.toList());

        assertThat(artifactNames.size(), equalTo(3));
        assertThat(artifactNames, hasItem(endsWith("SignApksBuilderTest.apk")));
        assertThat(artifactNames, hasItem(endsWith("app-release.apk")));
        assertThat(artifactNames, hasItem(endsWith("app-debug.apk")));
        assertThat(zipalign.lastProc.cmds().get(0), startsWith(androidHome));
    }

    @Test
    void doesNotUseEnvVarsIfScriptSpecifiesAndroidHomeOrZipalign() throws Exception {
        URL altAndroidHomeUrl = getClass().getResource("/win-android");
        String altAndroidHome = new File(altAndroidHomeUrl.toURI()).getAbsolutePath();

        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("ANDROID_ZIPALIGN", "/fail/zipalign");
        testJenkins.jenkins.getGlobalNodeProperties().add(prop);

        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: '**/*-unsigned.apk',%n" +
            "      androidHome: '%s'%n" +
            "    )%n" +
            "  }%n" +
            "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS, altAndroidHome.replace("\\", "\\\\")), false));

        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalign.lastProc.cmds().get(0), startsWith(altAndroidHome));

        URL altZipalignUrl = getClass().getResource("/alt-zipalign/zipalign");
        String altZipalign = new File(altZipalignUrl.toURI()).getAbsolutePath();

        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: '**/*-unsigned.apk',%n" +
            "      zipalignPath: '%s'%n" +
            "    )%n" +
            "  }%n" +
            "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS, altZipalign.replace("\\", "\\\\")), false));

        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalign.lastProc.cmds().get(0), startsWith(altZipalign));
    }

    @Test
    void skipsZipalign() throws Exception {
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
                "  wrap($class: 'CopyTestWorkspace') {%n" +
                "    signAndroidApks(" +
                "      keyStoreId: '%s',%n" +
                "      keyAlias: '%s',%n" +
                "      apksToSign: '**/*-unsigned.apk',%n" +
                "      skipZipalign: true%n" +
                "    )%n" +
                "  }%n" +
                "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS), false));

        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalign.lastProc, nullValue());
    }

    @Test
    void signedApkMappingDefaultsToUnsignedApkSibling() throws Exception {
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: 'SignApksBuilderTest-unsigned.apk',%n" +
            "      archiveSignedApks: false%n" +
            "    )%n" +
            "    archive includes: 'SignApksBuilderTest.apk'%n" +
            "  }%n" +
            "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS), false));

        WorkflowRun run = testJenkins.buildAndAssertSuccess(job);
        List<WorkflowRun.Artifact> artifacts = run.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        assertThat(artifacts.get(0).getFileName(), equalTo("SignApksBuilderTest.apk"));
    }

    @Test
    void usesSpecifiedSignedApkMapping() throws Exception {
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node('%s') {%n" +
                "  wrap($class: 'CopyTestWorkspace') {%n" +
                "    signAndroidApks(" +
                "      keyStoreId: '%s',%n" +
                "      keyAlias: '%s',%n" +
                "      apksToSign: 'SignApksBuilderTest-unsigned.apk',%n" +
                "      archiveSignedApks: false,%n" +
                "      signedApkMapping: [$class: 'TestSignedApkMapping']%n" +
                "    )%n" +
                "    archive includes: 'TestSignedApkMapping-SignApksBuilderTest-unsigned.apk'%n" +
                "  }%n" +
                "}", getClass().getSimpleName(), TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS), false));

        WorkflowRun run = testJenkins.buildAndAssertSuccess(job);
        List<WorkflowRun.Artifact> artifacts = run.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        assertThat(artifacts.get(0).getFileName(), equalTo("TestSignedApkMapping-SignApksBuilderTest-unsigned.apk"));
    }
}
