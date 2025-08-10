package org.jenkinsci.plugins.androidsigning.compatibility;

import org.jenkinsci.plugins.androidsigning.SignApksBuilder;
import org.jenkinsci.plugins.androidsigning.SignedApkMappingStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.net.URISyntaxException;

import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.util.DescribableList;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class SignApksBuilderCompatibility_2_0_8_Test {

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    @Test
    @LocalData
    public void converts_v2_0_8_entriesToBuilders() throws URISyntaxException, IOException {

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
    public void doesNotSkipZipalignFor_v2_0_8_builders() throws URISyntaxException, IOException {

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
    public void usesOldSignedApkMappingFor_v2_0_8_builders() throws Exception {

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
