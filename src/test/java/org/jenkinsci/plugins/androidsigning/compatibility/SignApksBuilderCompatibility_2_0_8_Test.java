package org.jenkinsci.plugins.androidsigning.compatibility;

import org.jenkinsci.plugins.androidsigning.SignApksBuilder;
import org.jenkinsci.plugins.androidsigning.SignedApkMappingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.util.DescribableList;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


@WithJenkins
class SignApksBuilderCompatibility_2_0_8_Test {

    private JenkinsRule testJenkins;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        testJenkins = rule;
    }

    @Test
    @LocalData
    void converts_v2_0_8_entriesToBuilders() {

        FreeStyleProject job = (FreeStyleProject) testJenkins.jenkins.getItem(getClass().getSimpleName());
        DescribableList<Builder,?> builders = job.getBuildersList();

        assertThat(builders.size(), equalTo(3));

        SignApksBuilder builder = (SignApksBuilder) builders.get(0);
        assertThat(builder.getKeyStoreId(), equalTo("android-signing-1"));
        assertThat(builder.getKeyAlias(), equalTo("key1"));
        assertThat(builder.getApksToSign(), equalTo("build/outputs/apk/*-unsigned.apk"));
        assertThat(builder.getArchiveUnsignedApks(), is(true));
        assertThat(builder.getArchiveSignedApks(), is(true));

        builder = (SignApksBuilder) builders.get(1);
        assertThat(builder.getKeyStoreId(), equalTo("android-signing-1"));
        assertThat(builder.getKeyAlias(), equalTo("key2"));
        assertThat(builder.getApksToSign(), equalTo("SignApksBuilderTest.apk, SignApksBuilderTest-choc*.apk"));
        assertThat(builder.getArchiveUnsignedApks(), is(false));
        assertThat(builder.getArchiveSignedApks(), is(true));

        builder = (SignApksBuilder) builders.get(2);
        assertThat(builder.getKeyStoreId(), equalTo("android-signing-2"));
        assertThat(builder.getKeyAlias(), equalTo("key1"));
        assertThat(builder.getApksToSign(), equalTo("**/*.apk"));
        assertThat(builder.getArchiveUnsignedApks(), is(false));
        assertThat(builder.getArchiveSignedApks(), is(false));
    }

    @Test
    @LocalData
    void doesNotSkipZipalignFor_v2_0_8_builders() {

        FreeStyleProject job = (FreeStyleProject) testJenkins.jenkins.getItem(getClass().getSimpleName());
        DescribableList<Builder,?> builders = job.getBuildersList();

        assertThat(builders.size(), equalTo(3));

        SignApksBuilder builder = (SignApksBuilder) builders.get(0);
        assertThat(builder.getSkipZipalign(), is(false));

        builder = (SignApksBuilder) builders.get(1);
        assertThat(builder.getSkipZipalign(), is(false));

        builder = (SignApksBuilder) builders.get(2);
        assertThat(builder.getSkipZipalign(), is(false));
    }

    @Test
    @LocalData
    void usesOldSignedApkMappingFor_v2_0_8_builders() {

        FreeStyleProject job = (FreeStyleProject) testJenkins.jenkins.getItem(getClass().getSimpleName());
        DescribableList<Builder,?> builders = job.getBuildersList();

        SignApksBuilder builder = (SignApksBuilder) builders.get(0);
        assertThat(builder.getSignedApkMapping(), instanceOf(SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class));

        builder = (SignApksBuilder) builders.get(1);
        assertThat(builder.getSignedApkMapping(), instanceOf(SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class));

        builder = (SignApksBuilder) builders.get(2);
        assertThat(builder.getSignedApkMapping(), instanceOf(SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class));
    }
}
