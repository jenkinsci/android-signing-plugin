package org.jenkinsci.plugins.androidsigning;


import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.Enumeration;


public class SigningComponents implements Serializable {

    private static final long serialVersionUID = 1L;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="DCN_NULLPOINTER_EXCEPTION",
                                                        justification="getEntry can generate this exeception")
    public static SigningComponents fromCredentials(StandardCertificateCredentials creds, String keyAlias) throws GeneralSecurityException {
        KeyStore keyStore = creds.getKeyStore();
        if (StringUtils.isEmpty(keyAlias)) {
            keyAlias = null;
            Enumeration<String> aliases = keyStore.aliases();
            if (aliases != null) {
                while (aliases.hasMoreElements()) {
                    String entryAlias = aliases.nextElement();
                    if (keyStore.isKeyEntry(entryAlias)) {
                        if (keyAlias != null) {
                            throw new UnrecoverableKeyException("no key alias was given and there is more than one entry in key store");
                        }
                        keyAlias = entryAlias;
                    }
                }
            }
        }
        if (keyAlias == null) {
            throw new UnrecoverableKeyException("no key alias was given and no key entries were found in key store");
        }

        String password = creds.getPassword().getPlainText();
        char[] passwordChars = new char[0];
        if (password != null) {
            passwordChars = password.toCharArray();
        }
        KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection(passwordChars);
        KeyStore.PrivateKeyEntry entry;
        try {
            entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(keyAlias, protection);
        }
        catch(NullPointerException e) {
            // empty passwords could be pessimistically handled, but this way if Credentials Plugin
            // changes to load key stores (CertificateCredentialsImpl) with empty password instead
            // of null, this should still work
            if (StringUtils.isEmpty(password)) {
                throw new NullKeyStorePasswordException(
                    "the password for key store credential " + creds.getId() + " is null - use the Credentials Plugin to configure a non-empty password", e);
            }
            throw e;
        }
        if (entry == null) {
            throw new GeneralSecurityException("key store credential " + creds.getId() + " has no entry named " + keyAlias);
        }
        PrivateKey key = entry.getPrivateKey();
        Certificate[] certChain = entry.getCertificateChain();

        return new SigningComponents(key, certChain, keyAlias, keyAlias);
    }

    public final PrivateKey key;
    public final Certificate[] certChain;
    public final String alias;
    public final String v1SigName;

    private SigningComponents(PrivateKey key, Certificate[] certChain, String alias, String v1SigName) {
        this.key = key;
        this.certChain = certChain;
        this.alias = alias;
        this.v1SigName = v1SigName;
    }

    /**
     * Using either a null password or empty password does not work because
     * the Credentials Plugin's CertificateCredentialsImpl uses hudson.Util.fixeEmpty()
     * on the password, which turns empty strings to null.  The plugin then calls
     * KeyStore.load() with a null password which results in a NullPointerException
     * when calling KeyStore.getEntry(alias).  See also ReadingKeyStoresTest.java.
     */
    static class NullKeyStorePasswordException extends GeneralSecurityException {
        NullKeyStorePasswordException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
