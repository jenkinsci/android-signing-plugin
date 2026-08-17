package org.jenkinsci.plugins.androidsigning;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;


public abstract class SignedApkMappingStrategy extends AbstractDescribableImpl<SignedApkMappingStrategy> implements ExtensionPoint {

    public abstract FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace);

    /**
     * Return the destination for the signed AAB given its unsigned input. The default
     * implementation writes the signed AAB as a sibling of the unsigned AAB, mirroring
     * {@link UnsignedApkSiblingMapping} but with a {@code .aab} extension. A concrete
     * (non-abstract) default keeps existing third-party {@link SignedApkMappingStrategy}
     * implementations source and binary compatible.
     * @param unsignedAab the unsigned AAB to sign
     * @param workspace the build workspace
     * @return the destination {@link FilePath} for the signed AAB
     */
    public FilePath destinationForUnsignedAab(FilePath unsignedAab, FilePath workspace) {
        String strippedName = unqualifiedNameOfUnsignedApk(unsignedAab);
        if (!unsignedAab.getBaseName().endsWith("-unsigned")) {
            strippedName += "-signed";
        }
        FilePath parent = unsignedAab.getParent();
        if (parent == null) {
            return null;
        }
        return parent.child(strippedName + ".aab");
    }

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

        @Override
        public FilePath destinationForUnsignedAab(FilePath unsignedAab, FilePath workspace) {
            String strippedName = unqualifiedNameOfUnsignedApk(unsignedAab);
            return workspace.child(SignApksBuilder.BUILDER_DIR).child(unsignedAab.getName()).child(strippedName + "-signed.aab");
        }

        @Extension
        @Symbol("unsignedApkNameDir")
        public static class DescriptorImpl extends Descriptor<SignedApkMappingStrategy> {
            @NonNull
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
            FilePath file = unsignedApk.getParent();
            if (file == null) {
                return null;
            }
            return file.child(strippedName + ".apk");
        }

        @Extension
        @Symbol("unsignedApkSibling")
        public static class DescriptorImpl extends Descriptor<SignedApkMappingStrategy> {
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.signedApkMapping_unsignedSibling_displayName();
            }
        }
    }

}
