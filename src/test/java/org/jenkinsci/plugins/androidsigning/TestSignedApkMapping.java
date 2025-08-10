package org.jenkinsci.plugins.androidsigning;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;


public class TestSignedApkMapping extends SignedApkMappingStrategy {

    @DataBoundConstructor
    public TestSignedApkMapping() {
    }

    @Override
    public FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace) {
        return workspace.child(getClass().getSimpleName() + "-" + unsignedApk.getName());
    }

    @Extension
    @Symbol("testSignedApkMapping")
    public static class DescriptorImpl extends Descriptor<SignedApkMappingStrategy> {
        @NonNull
        @Override
        public String getDisplayName() {
            return TestSignedApkMapping.class.getSimpleName();
        }
    }

}
