package org.jenkinsci.plugins.androidsigning;

import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;


class VerifyApkCallable extends MasterToSlaveFileCallable<VerifyApkCallable.VerifyResult> {

    @Serial
    private static final long serialVersionUID = 1;

    public static class VerifyResult implements Serializable {

        final boolean isVerified;
        final boolean isVerifiedV1Scheme;
        final boolean isVerifiedV2Scheme;
        final boolean isVerifiedV3Scheme;
        final boolean containsErrors;
        final X509Certificate[] certs;
        String[] warnings = new String[0];
        String[] errors = new String[0];

        public VerifyResult(ApkVerifier.Result result) {
            this.isVerified = result.isVerified();
            this.isVerifiedV1Scheme = result.isVerifiedUsingV1Scheme();
            this.isVerifiedV2Scheme = result.isVerifiedUsingV2Scheme();
            this.isVerifiedV3Scheme = result.isVerifiedUsingV3Scheme();
            this.certs = result.getSignerCertificates().toArray(new X509Certificate[0]);
            this.containsErrors = result.containsErrors();
            List<String> messages = new ArrayList<>();
            for (ApkVerifier.IssueWithParams issue : result.getWarnings()) {
                messages.add(issue.toString());
            }
            this.warnings = messages.toArray(warnings);
            messages.clear();
            for (ApkVerifier.IssueWithParams issue : result.getErrors()) {
                messages.add(issue.toString());
            }
            this.errors = messages.toArray(errors);
        }

    }

    private final TaskListener listener;

    VerifyApkCallable(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public VerifyResult invoke(File inputApkFile, VirtualChannel channel) throws IOException {

        ApkVerifier verifier = new ApkVerifier.Builder(inputApkFile).build();
        try {
            ApkVerifier.Result result = verifier.verify();
            return new VerifyResult(result);
        }
        catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            throw new IOException(e.getMessage());
        }
    }
}
