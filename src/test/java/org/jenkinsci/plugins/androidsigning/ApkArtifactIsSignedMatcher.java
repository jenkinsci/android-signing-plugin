package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;


class ApkArtifactIsSignedMatcher extends BaseMatcher<BuildArtifact> {
    private final StandardCertificateCredentials signer;
    private final X509Certificate expectedCert;
    private final StringBuilder descText = new StringBuilder();

    private ApkArtifactIsSignedMatcher(String keyStoreId, String keyAlias) throws KeyStoreException {
        List<StandardCertificateCredentials> result = CredentialsProvider.lookupCredentials(
            StandardCertificateCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList());
        signer = CredentialsMatchers.firstOrNull(result, CredentialsMatchers.withId(keyStoreId));
        expectedCert = (X509Certificate) signer.getKeyStore().getCertificate(keyAlias);
    }

    static ApkArtifactIsSignedMatcher isSignedWith(String keyStoreId, String keyAlias) throws KeyStoreException {
        return new ApkArtifactIsSignedMatcher(keyStoreId, keyAlias);
    }

    @Override
    public boolean matches(Object item) {
        BuildArtifact actual = (BuildArtifact) item;
        descText.append(actual.artifact.getFileName());
        try {
            VirtualFile virtualSignedApk = actual.build.getArtifactManager().root().child(actual.artifact.relativePath);
            FilePath signedApkPath = actual.build.getWorkspace().createTempFile(actual.artifact.getFileName().replace(".apk", ""), ".apk");
            signedApkPath.copyFrom(virtualSignedApk.open());
            VerifyApkCallable.VerifyResult result = signedApkPath.act(new VerifyApkCallable(TaskListener.NULL));
            if (!result.isVerified) {
                descText.append(" not verified;");
            }
            if (!result.isVerifiedV3Scheme) {
                descText.append(" not verified v3;");
            }
            if (!result.isVerifiedV2Scheme) {
                descText.append(" not verified v2;");
            }
            if (!result.isVerifiedV1Scheme) {
                descText.append(" not verified v1;");
            }
            if (result.certs.length != 1) {
                descText.append(" signer cert chain length should be 1, was ").append(result.certs.length);
            }
            else if (!result.certs[0].equals(expectedCert)) {
                descText.append(" signer cert differs from expected cert");
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return descText.length() == actual.artifact.getFileName().length();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(descText.toString());
    }
}
