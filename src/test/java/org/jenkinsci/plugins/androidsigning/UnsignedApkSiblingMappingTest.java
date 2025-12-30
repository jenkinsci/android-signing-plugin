package org.jenkinsci.plugins.androidsigning;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;


class UnsignedApkSiblingMappingTest {

    private final FilePath workspace = new FilePath((VirtualChannel) null, "/jenkins/jobs/UnsignedApkMappingTest/workspace");

    @Test
    void doesNotAddSignedSuffixWhenInputHasUnsignedSuffix() {
        SignedApkMappingStrategy.UnsignedApkSiblingMapping mapping = new SignedApkMappingStrategy.UnsignedApkSiblingMapping();
        FilePath inApk = workspace.child("app/build/outputs/app-unsigned.apk");
        FilePath outApk = mapping.destinationForUnsignedApk(inApk, workspace);

        assertThat(outApk, equalTo(workspace.child("app/build/outputs/app.apk")));
    }

    @Test
    void addsSignedSuffixWhenInputApkDoesNotHaveUnsignedSuffix() {
        SignedApkMappingStrategy.UnsignedApkSiblingMapping mapping = new SignedApkMappingStrategy.UnsignedApkSiblingMapping();
        FilePath inApk = workspace.child("app/build/outputs/app-other.apk");
        FilePath outApk = mapping.destinationForUnsignedApk(inApk, workspace);

        assertThat(outApk, equalTo(workspace.child("app/build/outputs/app-other-signed.apk")));
    }
}
