package org.jenkinsci.plugins.androidsigning;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;


class VerifyAabCallable extends MasterToSlaveFileCallable<VerifyAabCallable.VerifyResult> {

    private static final long serialVersionUID = 1;

    public static class VerifyResult implements Serializable {

        private static final long serialVersionUID = 1;

        boolean isSigned;
        X509Certificate[] certs = new X509Certificate[0];

    }

    @Override
    public VerifyResult invoke(File inputAabFile, VirtualChannel channel) throws IOException {

        VerifyResult result = new VerifyResult();
        Set<X509Certificate> certs = new LinkedHashSet<>();

        try (JarFile jar = new JarFile(inputAabFile, true)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                // drain the entry to trigger signature verification
                try (InputStream in = jar.getInputStream(entry)) {
                    byte[] buffer = new byte[4096];
                    while (in.read(buffer) != -1) {
                        // ignore
                    }
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers != null) {
                    for (CodeSigner signer : signers) {
                        List<? extends Certificate> chain = signer.getSignerCertPath().getCertificates();
                        if (!chain.isEmpty()) {
                            certs.add((X509Certificate) chain.get(0));
                        }
                    }
                }
            }
        }
        catch (SecurityException e) {
            // signature failed to verify; leave result.isSigned false
        }

        result.isSigned = !certs.isEmpty();
        result.certs = certs.toArray(new X509Certificate[certs.size()]);
        return result;
    }

}
