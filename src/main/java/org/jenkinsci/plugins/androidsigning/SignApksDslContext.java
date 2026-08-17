package org.jenkinsci.plugins.androidsigning;


import hudson.Extension;
import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.dsl.helpers.step.StepContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

@Extension(optional = true)
public class SignApksDslContext extends ContextExtensionPoint {

    public static class ConfigureContext implements Context {

        private final SignApksBuilder builder;

        private ConfigureContext(SignApksBuilder builder) {
            this.builder = builder;
        }

        public void keyStoreId(String x) {
            builder.setKeyStoreId(x);
        }

        public void keyAlias(String x) {
            builder.setKeyAlias(x);
        }

        public void aabsToSign(String aabsToSign) {
            builder.setAabsToSign(aabsToSign);
        }

        public void signedApkMapping(SignedApkMappingStrategy x) {
            builder.setSignedApkMapping(x);
        }

        public void signedApkMapping(Runnable configClosure) {
            SignedApkMappingContext context = new SignedApkMappingContext(builder);
            executeInContext(configClosure, context);
        }

        public void skipZipalign(boolean x) {
            builder.setSkipZipalign(x);
        }

        public void archiveSignedApks(boolean x) {
            builder.setArchiveSignedApks(x);
        }

        public void archiveUnsignedApks(boolean x) {
            builder.setArchiveUnsignedApks(x);
        }

        public void androidHome(String x) {
            builder.setAndroidHome(x);
        }

        public void zipalignPath(String x) {
            builder.setZipalignPath(x);
        }

        public SignedApkMappingStrategy.UnsignedApkSiblingMapping unsignedApkSibling() {
            return new SignedApkMappingStrategy.UnsignedApkSiblingMapping();
        }

        public SignedApkMappingStrategy.UnsignedApkBuilderDirMapping unsignedApkNameDir() {
            return new SignedApkMappingStrategy.UnsignedApkBuilderDirMapping();
        }
    }

    public static class SignedApkMappingContext implements Context {
        private final SignApksBuilder builder;
        SignedApkMappingContext(SignApksBuilder builder) {
            this.builder = builder;
        }

        public void unsignedApkSibling() {
            builder.setSignedApkMapping(new SignedApkMappingStrategy.UnsignedApkSiblingMapping());
        }

        public void unsignedApkNameDir() {
            builder.setSignedApkMapping(new SignedApkMappingStrategy.UnsignedApkBuilderDirMapping());
        }
    }

    @DslExtensionMethod(context = StepContext.class)
    public SignApksBuilder signAndroidApks(String apksToSign, Runnable configClosure) {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setApksToSign(apksToSign);
        executeInContext(configClosure, new ConfigureContext(builder));
        return builder;
    }
}
