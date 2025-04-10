/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.ejb.ca.sign;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import com.keyfactor.util.CertTools;
import com.keyfactor.util.CryptoProviderTools;
import com.keyfactor.util.certificate.DnComponents;
import com.keyfactor.util.crypto.algorithm.AlgorithmConstants;
import com.keyfactor.util.keys.KeyTools;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.jcajce.interfaces.MLDSAPublicKey;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pqc.jcajce.interfaces.FalconPublicKey;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.certificate.request.PKCS10RequestMessage;
import org.cesecore.certificates.certificate.request.ResponseMessage;
import org.cesecore.certificates.certificate.request.X509ResponseMessage;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.EjbRemoteHelper;
import org.cesecore.util.TraceLogMethodsRule;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests signing with PQC algorithms
 * Falcon-512
 * Falcon-1024
 * ML-DSA-44
 * ML-DSA-65
 * ML-DSA-87
 */
public class SignSessionWithPQCSystemTest extends SignSessionCommon {

    private static final Logger log = Logger.getLogger(SignSessionWithPQCSystemTest.class);

    private static final AuthenticationToken internalAdmin = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(
            "PQCSignSessionTest"));

    private CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    private SignSessionRemote signSession = EjbRemoteHelper.INSTANCE.getRemoteSession(SignSessionRemote.class);
    private EndEntityManagementSessionRemote endEntityManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);

    private static final String RSA_USERNAME = "RsaUser";
    private static final String FALCON512_USERNAME = "Falcon512User";
    private static final String FALCON1024_USERNAME = "Falcon1024User";
    private static final String MLDSA44_USERNAME = "MLDSA44User";
    private static final String MLDSA65_USERNAME = "MLDSA65User";
    private static final String MLDSA87_USERNAME = "MLDSA87User";
    private static final String DEFAULT_EE_PROFILE = "PQCEEPROFILE";
    private static final String DEFAULT_CERTIFICATE_PROFILE = "PQCCERTPROFILE";

    private static KeyPair falcon512keys;
    private static KeyPair mldsa44keys;

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Install BouncyCastle provider
        CryptoProviderTools.installBCProviderIfNotAvailable();

        CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
        createTestCA(); // RSA
        createPQCCa(TEST_FALCON512_CA_NAME, AlgorithmConstants.KEYALGORITHM_FALCON512, AlgorithmConstants.SIGALG_FALCON512);
        createPQCCa(TEST_FALCON1024_CA_NAME, AlgorithmConstants.KEYALGORITHM_FALCON1024, AlgorithmConstants.SIGALG_FALCON1024);
        createPQCCa(TEST_MLDSA44_CA_NAME, AlgorithmConstants.KEYALGORITHM_MLDSA44, AlgorithmConstants.SIGALG_MLDSA44);
        createPQCCa(TEST_MLDSA65_CA_NAME, AlgorithmConstants.KEYALGORITHM_MLDSA65, AlgorithmConstants.SIGALG_MLDSA65);
        createPQCCa(TEST_MLDSA87_CA_NAME, AlgorithmConstants.KEYALGORITHM_MLDSA87, AlgorithmConstants.SIGALG_MLDSA87);
        int rsacaid = caSession.getCAInfo(internalAdmin, getTestCAName()).getCAId();
        createEndEntity(RSA_USERNAME, DEFAULT_EE_PROFILE, DEFAULT_CERTIFICATE_PROFILE, rsacaid);
        createEndEntity(TEST_FALCON512_CA_NAME, FALCON512_USERNAME);
        createEndEntity(TEST_FALCON1024_CA_NAME, FALCON1024_USERNAME);
        createEndEntity(TEST_MLDSA44_CA_NAME, MLDSA44_USERNAME);
        createEndEntity(TEST_MLDSA65_CA_NAME, MLDSA65_USERNAME);
        createEndEntity(TEST_MLDSA87_CA_NAME, MLDSA87_USERNAME);
        // Only use one set of client keys, we test with so many keys in CSRs so it is expected to work
        falcon512keys = KeyTools.genKeys(AlgorithmConstants.KEYALGORITHM_FALCON512, AlgorithmConstants.KEYALGORITHM_FALCON512);
        mldsa44keys = KeyTools.genKeys(AlgorithmConstants.KEYALGORITHM_MLDSA44, AlgorithmConstants.KEYALGORITHM_MLDSA44);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        cleanUpEndEntity(RSA_USERNAME);
        cleanUpEndEntity(FALCON512_USERNAME);
        cleanUpEndEntity(FALCON1024_USERNAME);
        cleanUpEndEntity(MLDSA44_USERNAME);
        cleanUpEndEntity(MLDSA65_USERNAME);
        cleanUpEndEntity(MLDSA87_USERNAME);
        removeTestCA();
        removeTestCA(TEST_FALCON512_CA_NAME);
        removeTestCA(TEST_FALCON1024_CA_NAME);
        removeTestCA(TEST_MLDSA44_CA_NAME);
        removeTestCA(TEST_MLDSA65_CA_NAME);
        removeTestCA(TEST_MLDSA87_CA_NAME);
    }

    @Rule
    public TestRule traceLogMethodsRule = new TraceLogMethodsRule();

    @Test
    public void testSignSessionFalconWithRSACA() throws Exception {
        endEntityManagementSession.setUserStatus(internalAdmin, RSA_USERNAME, EndEntityConstants.STATUS_NEW);
        log.debug("Reset status of " + RSA_USERNAME + " to NEW");
        // user that we know exists...
        X509Certificate selfcert = CertTools.genSelfCert("CN=selfsigned", 1, null, falcon512keys.getPrivate(), falcon512keys.getPublic(),
                AlgorithmConstants.SIGALG_FALCON512, false);
        X509Certificate cert = (X509Certificate) signSession.createCertificate(internalAdmin, RSA_USERNAME, "foo123", selfcert);
        assertNotNull("Failed to create certificate", cert);
        log.debug("Cert=" + cert.toString());
        // We need to convert to BC to support Falcon
        X509Certificate bccert = CertTools.getCertfromByteArray(cert.getEncoded(), X509Certificate.class);
        PublicKey pk = bccert.getPublicKey();
        checkPQCKey(pk);
        try {
            X509Certificate rsacacert = (X509Certificate) caSession.getCAInfo(internalAdmin, getTestCAName()).getCertificateChain().toArray()[0];
            cert.verify(rsacacert.getPublicKey());
        } catch (Exception e) {
            assertTrue("Verify failed: " + e.getMessage(), false);
        }
    }

    @Test
    public void testSignSessionMLDSAWithRSACA() throws Exception {
        endEntityManagementSession.setUserStatus(internalAdmin, RSA_USERNAME, EndEntityConstants.STATUS_NEW);
        log.debug("Reset status of " + RSA_USERNAME + " to NEW");
        // user that we know exists...
        X509Certificate selfcert = CertTools.genSelfCert("CN=selfsigned", 1, null, mldsa44keys.getPrivate(), mldsa44keys.getPublic(),
                AlgorithmConstants.SIGALG_MLDSA44, false);
        X509Certificate cert = (X509Certificate) signSession.createCertificate(internalAdmin, RSA_USERNAME, "foo123", selfcert);
        assertNotNull("Failed to create certificate", cert);
        log.debug("Cert=" + cert.toString());
        // We need to convert to BC to support Falcon
        X509Certificate bccert = CertTools.getCertfromByteArray(cert.getEncoded(), X509Certificate.class);
        PublicKey pk = bccert.getPublicKey();
        checkPQCKey(pk);
        try {
            X509Certificate rsacacert = (X509Certificate) caSession.getCAInfo(internalAdmin, getTestCAName()).getCertificateChain().toArray()[0];
            cert.verify(rsacacert.getPublicKey());
        } catch (Exception e) {
            assertTrue("Verify failed: " + e.getMessage(), false);
        }
    }

    @Test
    public void testSignSessionFalconWithFalcon512CA() throws Exception {
        testSignSessionPQCWithPQCCA(FALCON512_USERNAME, TEST_FALCON512_CA_NAME, falcon512keys, AlgorithmConstants.SIGALG_FALCON512);
    }

    @Test
    public void testSignSessionFalconWithFalcon1024CA() throws Exception {
        testSignSessionPQCWithPQCCA(FALCON1024_USERNAME, TEST_FALCON1024_CA_NAME, falcon512keys, AlgorithmConstants.SIGALG_FALCON512);
    }

    @Test
    public void testSignSessionMLDSAWithMLDSA44CA() throws Exception {
        testSignSessionPQCWithPQCCA(MLDSA44_USERNAME, TEST_MLDSA44_CA_NAME, mldsa44keys, AlgorithmConstants.SIGALG_MLDSA44);
    }

    @Test
    public void testSignSessionMLDSAWithMLDSA65CA() throws Exception {
        testSignSessionPQCWithPQCCA(MLDSA65_USERNAME, TEST_MLDSA65_CA_NAME, mldsa44keys, AlgorithmConstants.SIGALG_MLDSA44);
    }

    @Test
    public void testSignSessionMLDSAWithMLDSA87CA() throws Exception {
        testSignSessionPQCWithPQCCA(MLDSA87_USERNAME, TEST_MLDSA87_CA_NAME, mldsa44keys, AlgorithmConstants.SIGALG_MLDSA44);
    }

    private void testSignSessionPQCWithPQCCA(final String username, final String caname, KeyPair keys, String sigAlg) throws Exception {
        endEntityManagementSession.setUserStatus(internalAdmin, username, EndEntityConstants.STATUS_NEW);
        log.debug("Reset status of '" + username + "' to NEW");
        // user that we know exists...
        X509Certificate requestcert = CertTools.genSelfCert("CN=selfsigned", 1, null, keys.getPrivate(), keys.getPublic(),
                sigAlg, false);
        X509Certificate cert = (X509Certificate) signSession.createCertificate(internalAdmin, username, "foo123", requestcert);
        assertNotNull("Failed to create certificate", cert);
        log.debug("Cert=" + cert.toString());
        // We need to convert to BC to support falcon
        X509Certificate bccert = CertTools.getCertfromByteArray(cert.getEncoded(), X509Certificate.class);
        PublicKey pk = bccert.getPublicKey();
        checkPQCKey(pk);
        X509Certificate cacert = (X509Certificate) caSession.getCAInfo(internalAdmin, caname).getCertificateChain().toArray()[0];
        try {
            cert.verify(cacert.getPublicKey());
        } catch (Exception e) {
            fail("Failed to verify the returned certificate with CAs public key: " + e.getMessage());
        }
    }

    /**
     * Tests PKCS10 to Falcon CA
     */
    @Test
    public void testBCPKCS10FalconWithFalconCA() throws Exception {
        testBCPKCS10PQCWithPQCCA(FALCON512_USERNAME, TEST_FALCON512_CA_NAME, falcon512keys, AlgorithmConstants.SIGALG_FALCON512);

    }

    /**
     * Tests PKCS10 to MLDSA CA
     */
    @Test
    public void testBCPKCS10MLDSAWithMLDSACA() throws Exception {
        testBCPKCS10PQCWithPQCCA(MLDSA44_USERNAME, TEST_MLDSA44_CA_NAME, mldsa44keys, AlgorithmConstants.SIGALG_MLDSA44);

    }

    private void testBCPKCS10PQCWithPQCCA(final String username, final String caname, final KeyPair keys, final String sigAlg) throws Exception {
        endEntityManagementSession.setUserStatus(internalAdmin, username, EndEntityConstants.STATUS_NEW);
        log.debug("Reset status of " + username + " to NEW");
        // Create certificate request
        PKCS10CertificationRequest req = CertTools.genPKCS10CertificationRequest(sigAlg, DnComponents.stringToBcX500Name("C=SE, O=Keyfactor, CN="
                + username), keys.getPublic(), new DERSet(), keys.getPrivate(), null);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ASN1OutputStream dOut = ASN1OutputStream.create(bOut, ASN1Encoding.DER);
        dOut.writeObject(req.toASN1Structure());
        dOut.close();

        PKCS10CertificationRequest req2 = new PKCS10CertificationRequest(bOut.toByteArray());
        ContentVerifierProvider verifier = CertTools.genContentVerifierProvider(keys.getPublic());
        boolean verify = req2.isSignatureValid(verifier);
        log.debug("Verify returned " + verify);
        assertTrue("Can't verify the newly created POP on PKCS#10 CSR", verify);
        log.debug("CertificationRequest generated successfully.");
        byte[] bcp10 = bOut.toByteArray();
        PKCS10RequestMessage p10 = new PKCS10RequestMessage(bcp10);
        p10.setUsername(username);
        p10.setPassword("foo123");
        ResponseMessage resp = signSession.createCertificate(internalAdmin, p10, X509ResponseMessage.class, null);
        Certificate cert = CertTools.getCertfromByteArray(resp.getResponseMessage(), Certificate.class);
        assertNotNull("Failed to create certificate", cert);
        log.debug("Cert=" + cert.toString());
        PublicKey pk = cert.getPublicKey();
        checkPQCKey(pk);
        try {
            X509Certificate cacert = (X509Certificate) caSession.getCAInfo(internalAdmin, caname).getCertificateChain().toArray()[0];
            cert.verify(cacert.getPublicKey());
        } catch (Exception e) {
            fail("Failed to verify the returned certificate with CAs public key: " + e.getMessage());
        }
    }

    @Override
    public String getRoleName() {
        return SignSessionWithPQCSystemTest.class.getSimpleName();
    }

    private void checkPQCKey(final PublicKey pk) {
        if (pk instanceof FalconPublicKey) {
            FalconPublicKey pub = (FalconPublicKey) pk;
            assertEquals(pub.getAlgorithm(), AlgorithmConstants.KEYALGORITHM_FALCON512);
            FalconParameterSpec paramspec = pub.getParameterSpec();
            assertNotNull("Falcon can not have null spec", paramspec);
            assertEquals("Spec was not Falcon-512", FalconParameterSpec.falcon_512, paramspec);
        } else if (pk instanceof MLDSAPublicKey) {
            MLDSAPublicKey pub = (MLDSAPublicKey) pk;
            assertEquals(pub.getAlgorithm(), AlgorithmConstants.KEYALGORITHM_MLDSA44);
            MLDSAParameterSpec paramspec = pub.getParameterSpec();
            assertNotNull("MLDSA spec can not have null spec", paramspec);
            assertEquals("Spec was not MLDSA-44", MLDSAParameterSpec.ml_dsa_44, paramspec);
        } else {
            assertTrue("Public key is not Falcon or MLDSA: " + pk.getClass().getName(), false);
        }
    }

    private static void createEndEntity(final String caname, final String username) throws Exception {
        CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
        CAInfo info = caSession.getCAInfo(internalAdmin, caname);
        assertTrue("No active CA with name " + caname + "! Must have at least one active CA to run tests!", info != null);
        createEndEntity(username, EndEntityConstants.EMPTY_END_ENTITY_PROFILE, CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, info.getCAId());
    }

}
