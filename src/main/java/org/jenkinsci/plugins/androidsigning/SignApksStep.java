package org.jenkinsci.plugins.androidsigning;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;


public class SignApksStep extends AbstractStepImpl {

    @CheckForNull
    private String keyStoreId;
    @CheckForNull
    private String keyAlias;
    @CheckForNull
    private String apksToSign;
    private SignedApkMappingStrategy signedApkMapping;
    private String androidHome;
    private String zipalignPath;
    private boolean skipZipalign = false;
    private boolean archiveSignedApks = true;
    private boolean archiveUnsignedApks = false;

    @DataBoundConstructor
    public SignApksStep() {
    }

    @DataBoundSetter
    public void setKeyStoreId(String x) {
        keyStoreId = x;
    }

    @DataBoundSetter
    public void setKeyAlias(String x) {
        keyAlias = x;
    }

    @DataBoundSetter
    public void setApksToSign(String x) {
        apksToSign = x;
    }

    @DataBoundSetter
    public void setSignedApkMapping(SignedApkMappingStrategy x) {
        signedApkMapping = x;
    }

    @DataBoundSetter
    public void setSkipZipalign(boolean x) {
        skipZipalign = x;
    }

    @DataBoundSetter
    public void setArchiveSignedApks(boolean x) {
        archiveSignedApks = x;
    }

    @DataBoundSetter
    public void setArchiveUnsignedApks(boolean x) {
        archiveUnsignedApks = x;
    }

    @DataBoundSetter
    public void setAndroidHome(String x) {
        androidHome = x;
    }

    @DataBoundSetter
    public void setZipalignPath(String x) {
        zipalignPath = x;
    }

    public String getKeyStoreId() {
        return keyStoreId;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getApksToSign() {
        return apksToSign;
    }

    public SignedApkMappingStrategy getSignedApkMapping() {
        return signedApkMapping;
    }

    public boolean getSkipZipalign() {
        return skipZipalign;
    }

    public boolean getArchiveSignedApks() {
        return archiveSignedApks;
    }

    public boolean getArchiveUnsignedApks() {
        return archiveUnsignedApks;
    }

    public String getAndroidHome() {
        return androidHome;
    }

    public String getZipalignPath() {
        return zipalignPath;
    }


    private static class SignApksStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient SignApksStep step;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient Run build;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient FilePath workspace;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient Launcher launcher;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient TaskListener listener;

        @StepContextParameter
        private transient EnvVars env;

        @Override
        protected Void run() throws Exception {
            String androidHome = step.getAndroidHome();
            String zipalignPath = step.getZipalignPath();
            if (StringUtils.isEmpty(androidHome) && StringUtils.isEmpty(zipalignPath)) {
                if (StringUtils.isEmpty(androidHome)) {
                    androidHome = env.get(ZipalignTool.ENV_ANDROID_HOME);
                }
                if (StringUtils.isEmpty(zipalignPath)) {
                    zipalignPath = env.get(ZipalignTool.ENV_ZIPALIGN_PATH);
                }
            }
            SignApksBuilder builder = new SignApksBuilder();
            builder.setKeyStoreId(step.getKeyStoreId());
            builder.setKeyAlias(step.getKeyAlias());
            builder.setApksToSign(step.getApksToSign());
            builder.setSignedApkMapping(step.getSignedApkMapping());
            builder.setSkipZipalign(step.getSkipZipalign());
            builder.setArchiveSignedApks(step.getArchiveSignedApks());
            builder.setArchiveUnsignedApks(step.getArchiveUnsignedApks());
            builder.setAndroidHome(androidHome);
            builder.setZipalignPath(zipalignPath);
            builder.perform(build, workspace, launcher, listener);
            return null;
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(SignApksStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "signAndroidApks";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.builderDisplayName();
        }
    }
}
