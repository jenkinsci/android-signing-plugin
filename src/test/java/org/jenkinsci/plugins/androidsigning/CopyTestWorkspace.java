package org.jenkinsci.plugins.androidsigning;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.tasks.SimpleBuildWrapper;


public class CopyTestWorkspace extends SimpleBuildWrapper {

    @DataBoundConstructor
    public CopyTestWorkspace() {
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        URL workspaceUrl = getClass().getResource("/workspace");
        FilePath sourceWorkspace;
        try {
            sourceWorkspace = new FilePath(new File(workspaceUrl.toURI()));
        }
        catch (URISyntaxException e) {
            e.printStackTrace(listener.getLogger());
            throw new AbortException(e.getMessage());
        }
        sourceWorkspace.copyRecursiveTo("*/**", workspace);
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return getClass().getSimpleName();
        }
    }

}
