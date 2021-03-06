package org.whispersystems.contactdiscovery;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URLDecoder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

public class SigningCertificate {

  private final CertPath path;

  public SigningCertificate(String certificateChain, KeyStore trustStore)
      throws CertificateException, CertPathValidatorException
  {
    try {
      StringReader          stringReader   = new StringReader(URLDecoder.decode(certificateChain, "UTF-8"));
      PEMParser             pemReader      = new PEMParser(stringReader);
      CertificateFactory    factory        = CertificateFactory.getInstance("X.509");
      List<X509Certificate> certificates   = new LinkedList<>();
      PKIXParameters        pkixParameters = new PKIXParameters(trustStore);
      CertPathValidator     validator      = CertPathValidator.getInstance ("PKIX" );

      X509CertificateHolder certificate;

      while ((certificate = (X509CertificateHolder)pemReader.readObject()) != null) {
        X509Certificate x509Certificate = new JcaX509CertificateConverter().getCertificate(certificate);
        certificates.add(x509Certificate);
      }

      this.path = factory.generateCertPath(certificates);

      pkixParameters.setRevocationEnabled(false);
      validator.validate(path, pkixParameters);
      verifyDistinguishedName(path);
    } catch (KeyStoreException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | IOException e) {
      throw new AssertionError(e);
    }
  }

  public void verifySignature(String body, String encodedSignature)
      throws SignatureException
  {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(path.getCertificates().get(0));
      signature.update(body.getBytes());
      signature.verify(Base64.decodeBase64(encodedSignature));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private void verifyDistinguishedName(CertPath path) throws CertificateException {
    X509Certificate leaf              = (X509Certificate) path.getCertificates().get(0);
    String          distinguishedName = leaf.getSubjectX500Principal().getName();

    if (!"CN=Intel SGX Attestation Report Signing,O=Intel Corporation,L=Santa Clara,ST=CA,C=US".equals(distinguishedName)) {
      throw new CertificateException("Bad DN: " + distinguishedName);
    }
  }

}
