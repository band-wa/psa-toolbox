package com.bandwa.openadb.util.apktools;

import android.content.Context;

import com.android.apksig.ApkSigner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class ApkSignUtil {
	public static ApkSignUtil loadKey(Context context) {
		String keystoreFileName = "keystore.jks";
		String cert_or_alias = "bandwa";
		String store_pass = "musichelper";
		String key_pass = "musichelper";

		try {
			KeyStore ks = KeyStore.getInstance("PKCS12");
			char[] storePass = store_pass.toCharArray();
			char[] keyPass = key_pass.toCharArray();

			InputStream i = context.getAssets().open(keystoreFileName);

			ks.load(i, storePass);
			PrivateKey prk = (PrivateKey) ks.getKey(cert_or_alias, keyPass);
			X509Certificate cert = (X509Certificate) ks.getCertificate(cert_or_alias);

			if (prk == null || cert == null) {
				return null;
			}

			ApkSignUtil st = new ApkSignUtil();
			st.privateKey = prk;
			st.certificate = cert;
			return st;
		} catch (Exception e) {
			return null;
		}
	}

	private PrivateKey privateKey;
	private X509Certificate certificate;
	private ApkSignUtil() {}

	public boolean sign(File in, File out, int minSdk) {
		try {
			ApkSigner.SignerConfig.Builder builder = new ApkSigner.SignerConfig.Builder("CERT", privateKey, ImmutableList.of(certificate));
			ApkSigner.SignerConfig signerConfig = builder.build();
			ApkSigner.Builder builder1 = new ApkSigner.Builder(ImmutableList.of(signerConfig));
			builder1.setInputApk(in);
			builder1.setOutputApk(out);
			builder1.setCreatedBy("Apktool");
			builder1.setMinSdkVersion(minSdk);
			builder1.setV1SigningEnabled(false);
			builder1.setV2SigningEnabled(true);
			builder1.setV3SigningEnabled(false);
			ApkSigner signer = builder1.build();
			signer.sign();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
