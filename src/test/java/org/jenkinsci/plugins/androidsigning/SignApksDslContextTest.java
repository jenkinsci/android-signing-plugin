package org.jenkinsci.plugins.androidsigning;

import hudson.model.FreeStyleProject;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@WithJenkins
class SignApksDslContextTest {

    private JenkinsRule testJenkins;
    
    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        testJenkins = rule;
    }

    @Test
    void parsesSignApksDsl() throws Exception {
        ExecuteDslScripts dslScripts = new ExecuteDslScripts();
        dslScripts.setScriptText(
            """
            job('%s-generated') {
                steps {
            
                    signAndroidApks '**/*-unsigned.apk', {
                        keyStoreId 'my.keyStore'
                        keyAlias 'myKey'
                        archiveSignedApks true
                        archiveUnsignedApks true
                        androidHome '/fake/android-sdk'
                        skipZipalign true
                    }
            
                    signAndroidApks '**/*-other.apk', {
                        keyStoreId 'my.otherKeyStore'
                        keyAlias 'myOtherKey'
                        archiveSignedApks false
                        archiveUnsignedApks false
                        zipalignPath '/fake/android-sdk/zipalign'
                        signedApkMapping unsignedApkNameDir()
                    }
            
                    signAndroidApks '**/*-other.apk', {
                        keyStoreId 'my.otherKeyStore'
                        keyAlias 'myOtherKey'
                        archiveSignedApks false
                        archiveUnsignedApks false
                        zipalignPath '/fake/android-sdk/zipalign'
                        signedApkMapping { unsignedApkSibling() }
                    }
                }
            }
            """.formatted(getClass().getSimpleName()));
        FreeStyleProject job = testJenkins.createFreeStyleProject(getClass().getSimpleName() + "-seed");
        job.getBuildersList().add(dslScripts);
        testJenkins.buildAndAssertSuccess(job);
        job = testJenkins.jenkins.getItemByFullName(getClass().getSimpleName() + "-generated", FreeStyleProject.class);

        assertThat(job.getBuilders().size(), equalTo(3));

        SignApksBuilder signApks = (SignApksBuilder) job.getBuilders().get(0);

        assertThat(signApks.getApksToSign(), equalTo("**/*-unsigned.apk"));
        assertThat(signApks.getKeyStoreId(), equalTo("my.keyStore"));
        assertThat(signApks.getKeyAlias(), equalTo("myKey"));
        assertTrue(signApks.getSkipZipalign());
        assertTrue(signApks.getArchiveSignedApks());
        assertTrue(signApks.getArchiveUnsignedApks());
        assertThat(signApks.getAndroidHome(), equalTo("/fake/android-sdk"));
        assertThat(signApks.getZipalignPath(), nullValue());
        assertThat(signApks.getSignedApkMapping(), instanceOf(SignedApkMappingStrategy.UnsignedApkSiblingMapping.class));

        signApks = (SignApksBuilder) job.getBuilders().get(1);

        assertThat(signApks.getApksToSign(), equalTo("**/*-other.apk"));
        assertThat(signApks.getKeyStoreId(), equalTo("my.otherKeyStore"));
        assertThat(signApks.getKeyAlias(), equalTo("myOtherKey"));
        assertFalse(signApks.getSkipZipalign());
        assertFalse(signApks.getArchiveSignedApks());
        assertFalse(signApks.getArchiveUnsignedApks());
        assertThat(signApks.getAndroidHome(), nullValue());
        assertThat(signApks.getZipalignPath(), equalTo("/fake/android-sdk/zipalign"));
        assertThat(signApks.getSignedApkMapping(), instanceOf(org.jenkinsci.plugins.androidsigning.SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class));

        signApks = (SignApksBuilder) job.getBuilders().get(2);

        assertThat(signApks.getSignedApkMapping(), instanceOf(org.jenkinsci.plugins.androidsigning.SignedApkMappingStrategy.UnsignedApkSiblingMapping.class));
    }
}
