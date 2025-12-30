package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;

import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public class TestKeyStore {

    public static final String KEY_STORE_RESOURCE = "/" + SignApksBuilderTest.class.getSimpleName() + ".p12";
    public static final String KEY_STORE_ID = SignApksBuilderTest.class.getSimpleName() + ".keyStore";
    public static final String KEY_ALIAS = SignApksBuilderTest.class.getSimpleName();

    public final JenkinsRule testJenkins;
    public final String resourceName;
    public final String credentialsId;
    public final String description;
    public final String password;
    public StandardCertificateCredentials credentials;

    TestKeyStore(JenkinsRule testJenkins) {
        this(testJenkins, KEY_STORE_RESOURCE, KEY_STORE_ID, "Main Test Key Store", SignApksBuilderTest.class.getSimpleName());
    }

    TestKeyStore(JenkinsRule testJenkins, String resourceName, String credentialsId, String description, String password) {
        this.testJenkins = testJenkins;
        this.resourceName = resourceName;
        this.credentialsId = credentialsId;
        this.description = description;
        this.password = password;
    }

    void addCredentials() {
        if (testJenkins.jenkins == null) {
            return;
        }
        try {
            InputStream keyStoreIn = SignApksBuilderTest.class.getResourceAsStream(resourceName);
            byte[] keyStoreBytes = new byte[keyStoreIn.available()];
            keyStoreIn.read(keyStoreBytes);
            String keyStore = new String(Base64.getEncoder().encode(keyStoreBytes), StandardCharsets.UTF_8);
            credentials = new CertificateCredentialsImpl(
                CredentialsScope.GLOBAL, credentialsId, description, password,
                new CertificateCredentialsImpl.UploadedKeyStoreSource(keyStore));
            CredentialsStore store = CredentialsProvider.lookupStores(testJenkins.jenkins).iterator().next();
            store.addCredentials(Domain.global(), credentials);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void removeCredentials() {
        if (testJenkins.jenkins == null) {
            return;
        }
        CredentialsStore store = CredentialsProvider.lookupStores(testJenkins.jenkins).iterator().next();
        try {
            store.removeCredentials(Domain.global(), credentials);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        credentials = null;
    }

}
