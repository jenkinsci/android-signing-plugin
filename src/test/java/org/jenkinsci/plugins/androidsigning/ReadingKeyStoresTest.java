package org.jenkinsci.plugins.androidsigning;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadingKeyStoresTest {

    private static PrivateKey basePemKey;
    private static Certificate basePemCert;

    @BeforeAll
    static void beforeAll() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(
            ReadingKeyStoresTest.class.getResourceAsStream("/SignApksBuilderTest-key-exposed.pkcs8.pem")));
        StringBuilder bareBase64 = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.contains("PRIVATE KEY")) {
                bareBase64.append(line.replaceAll("\\s", ""));
            }
        }
        byte[] keyBytes = Base64.getDecoder().decode(bareBase64.toString());
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory rsaKeys = KeyFactory.getInstance("RSA");
        basePemKey = rsaKeys.generatePrivate(keySpec);

        CertificateFactory certs = CertificateFactory.getInstance("X.509");
        basePemCert = certs.generateCertificate(ReadingKeyStoresTest.class.getResourceAsStream("/SignApksBuilderTest.pem"));
    }

    @Test
    void loadKeyStoreWithPassword() throws Exception {
        InputStream keyStoreIn = getClass().getResourceAsStream("/SignApksBuilderTest.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(keyStoreIn, "SignApksBuilderTest".toCharArray());

        assertTrue(store.containsAlias("SignApksBuilderTest"));

        KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection("SignApksBuilderTest".toCharArray());
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry("SignApksBuilderTest", protection);
        PrivateKey key = (PrivateKey) store.getKey("SignApksBuilderTest", protection.getPassword());
        Certificate[] chain = store.getCertificateChain("SignApksBuilderTest");

        assertThat(entry.getPrivateKey(), equalTo(basePemKey));
        assertThat(key, equalTo(basePemKey));

        assertThat(entry.getCertificateChain(), not(nullValue()));
        assertThat(entry.getCertificateChain().length, equalTo(1));
        assertThat(entry.getCertificateChain()[0], equalTo(basePemCert));

        assertThat(chain.length, equalTo(1));
        assertThat(chain[0], equalTo(basePemCert));
    }

    @Test
    void loadAKeyStoreWithBlankPassword() throws Exception {
        InputStream keyStoreIn = getClass().getResourceAsStream("/SignApksBuilderTest-exposed.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(keyStoreIn, new char[0]);

        assertTrue(store.containsAlias("SignApksBuilderTest-exposed"));

        KeyStore.PasswordProtection prot = new KeyStore.PasswordProtection(new char[0]);
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry("SignApksBuilderTest-exposed", prot);
        Key key = store.getKey("SignApksBuilderTest-exposed", prot.getPassword());
        Certificate[] chain = store.getCertificateChain("SignApksBuilderTest-exposed");

        assertThat(entry.getPrivateKey(), equalTo(basePemKey));
        assertThat(key, equalTo(basePemKey));

        assertThat(entry.getCertificateChain(), not(nullValue()));
        assertThat(entry.getCertificateChain().length, equalTo(1));
        assertThat(entry.getCertificateChain()[0], equalTo(basePemCert));

        assertThat(chain.length, equalTo(1));
        assertThat(chain[0], equalTo(basePemCert));
    }

    @Test
    void doesNotWorkWithoutProvidingAKeyPasswordMatchingStorePassword() throws Exception {
        InputStream keyStoreIn = getClass().getResourceAsStream("/SignApksBuilderTest-noKeyPass.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(keyStoreIn, "SignApksBuilderTest-noKeyPass".toCharArray());

        assertTrue(store.containsAlias("SignApksBuilderTest-noKeyPass"));

        KeyStore.PasswordProtection dummy = new KeyStore.PasswordProtection(new char[0]);

        assertThrows(UnrecoverableEntryException.class, () -> store.getEntry("SignApksBuilderTest-noKeyPass", dummy));
        assertThrows(UnrecoverableEntryException.class, () -> store.getKey("SignApksBuilderTest-noKeyPass", dummy.getPassword()));

        KeyStore.PasswordProtection prot = new KeyStore.PasswordProtection("SignApksBuilderTest-noKeyPass".toCharArray());
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry("SignApksBuilderTest-noKeyPass", prot);
        Key key = store.getKey("SignApksBuilderTest-noKeyPass", prot.getPassword());

        assertThat(entry.getPrivateKey(), equalTo(basePemKey));
        assertThat(key, equalTo(basePemKey));
    }

    @Test
    void loadingPasswordlessKeyStoreWithNullPasswordInsteadOfEmptyDoesNotWork() throws Exception {
        InputStream keyStoreIn = getClass().getResourceAsStream("/SignApksBuilderTest-exposed.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(keyStoreIn, null);
        assertTrue(store.containsAlias("SignApksBuilderTest-exposed"));
        KeyStore.PasswordProtection prot = new KeyStore.PasswordProtection(new char[0]);
        assertThrows(NullPointerException.class, () -> store.getEntry("SignApksBuilderTest-exposed", prot));
        Key key = store.getKey("SignApksBuilderTest-exposed", prot.getPassword());
        Certificate[] chain = store.getCertificateChain("SignApksBuilderTest-exposed");
        assertThat(key, equalTo(basePemKey));
        assertThat(chain, nullValue());
    }

}
