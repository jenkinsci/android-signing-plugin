package org.jenkinsci.plugins.androidsigning;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;


public abstract class SignedApkMappingStrategy extends AbstractDescribableImpl<SignedApkMappingStrategy> implements ExtensionPoint {

    public abstract FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace);

    public static ExtensionList<SignedApkMappingStrategy> all() {
        return Jenkins.getActiveInstance().getExtensionList(SignedApkMappingStrategy.class);
    }

    /**
     * Return the name of the given APK without the .apk extension and without any -unsigned suffix, if present.
     * For example, {@code}myApp-unsigned.apk{@code} returns {@code}myApp{@code}, and
     * {@code}myApp-someFlavor.apk{@code} returns {@code}myApp-someFlavor{@code}.
     * @param unsignedApk
     * @return
     */
    public static String unqualifiedNameOfUnsignedApk(FilePath unsignedApk) {
        Pattern stripUnsignedPattern = Pattern.compile("(-?unsigned)?$", Pattern.CASE_INSENSITIVE);
        Matcher stripUnsigned = stripUnsignedPattern.matcher(unsignedApk.getBaseName());
        return stripUnsigned.replaceFirst("");
    }

    public static class UnsignedApkBuilderDirMapping extends SignedApkMappingStrategy {

        @DataBoundConstructor
        public UnsignedApkBuilderDirMapping() {
        }

        @Override
        public FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace) {
            String strippedName = unqualifiedNameOfUnsignedApk(unsignedApk);
            return workspace.child(SignApksBuilder.BUILDER_DIR).child(unsignedApk.getName()).child(strippedName + "-signed.apk");
        }

        @Extension
        @Symbol("unsignedApkNameDir")
        public static class DescriptorImpl extends Descriptor<SignedApkMappingStrategy> {
            @Nonnull
            @Override
            public String getDisplayName() {
                return Messages.signedApkMapping_builderDir_displayName();
            }
        }
    }

    public static class UnsignedApkSiblingMapping extends SignedApkMappingStrategy {

        @DataBoundConstructor
        public UnsignedApkSiblingMapping() {
        }

        @Override
        public FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace) {
            String strippedName = unqualifiedNameOfUnsignedApk(unsignedApk);
            if (!unsignedApk.getBaseName().endsWith("-unsigned")) {
                strippedName += "-signed";
            }
            return unsignedApk.getParent().child(strippedName + ".apk");
        }

        @Extension
        @Symbol("unsignedApkSibling")
        public static class DescriptorImpl extends Descriptor<SignedApkMappingStrategy> {
            @Nonnull
            @Override
            public String getDisplayName() {
                return Messages.signedApkMapping_unsignedSibling_displayName();
            }
        }
    }

}
