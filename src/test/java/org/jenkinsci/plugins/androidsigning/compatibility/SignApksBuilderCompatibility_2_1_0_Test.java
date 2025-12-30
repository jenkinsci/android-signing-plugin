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
class SignApksBuilderCompatibility_2_1_0_Test {

    private JenkinsRule testJenkins;
    
    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        testJenkins = rule;
    }

    @Test
    @LocalData
    void doesNotSkipZipalignFor_v2_1_0_builders() {

        FreeStyleProject job = (FreeStyleProject) testJenkins.jenkins.getItem(getClass().getSimpleName());
        DescribableList<Builder,?> builders = job.getBuildersList();

        assertThat(builders.size(), equalTo(2));

        SignApksBuilder builder = (SignApksBuilder) builders.get(0);
        assertThat(builder.getKeyStoreId(), equalTo("android-team-1"));
        assertThat(builder.getKeyAlias(), equalTo("android-team-1"));
        assertThat(builder.getApksToSign(), equalTo("build/outputs/apk/*-unsigned.apk"));
        assertThat(builder.getArchiveSignedApks(), is(true));
        assertThat(builder.getArchiveUnsignedApks(), is(true));
        assertThat(builder.getSkipZipalign(), is(false));

        builder = (SignApksBuilder) builders.get(1);
        assertThat(builder.getKeyStoreId(), equalTo("android-team-2"));
        assertThat(builder.getKeyAlias(), equalTo("android-team-2"));
        assertThat(builder.getApksToSign(), equalTo("**/*-unsigned.apk"));
        assertThat(builder.getArchiveSignedApks(), is(true));
        assertThat(builder.getArchiveUnsignedApks(), is(false));
        assertThat(builder.getSkipZipalign(), is(false));
    }

    @Test
    @LocalData
    void usesOldSignedApkMappingFor_v2_1_0_builders() {

        FreeStyleProject job = (FreeStyleProject) testJenkins.jenkins.getItem(getClass().getSimpleName());
        DescribableList<Builder,?> builders = job.getBuildersList();

        assertThat(builders.size(), equalTo(2));

        SignApksBuilder builder = (SignApksBuilder) builders.get(0);
        assertThat(builder.getSignedApkMapping(), instanceOf(SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class));

        builder = (SignApksBuilder) builders.get(1);
        assertThat(builder.getSignedApkMapping(), instanceOf(SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class));
    }

}
