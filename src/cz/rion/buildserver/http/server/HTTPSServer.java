package cz.rion.buildserver.http.server;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import cz.rion.buildserver.db.layers.staticDB.LayeredSSLDB.StoredCertificate;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.CompatibleSocketClient;
import cz.rion.buildserver.http.sync.HTTPSyncClientFactory;
import cz.rion.buildserver.wrappers.MyThread;

public class HTTPSServer extends HTTPServer {

	private final ServerData data;

	private MyThread thread = new MyThread("HTTPS server") {

		@Override
		protected void runAsync() {
			try {
				HTTPSServer.this.runAsync();
			} catch (HTTPServerException e) {
				e.printStackTrace();
			}
		}

	};

	private final int port;

	public HTTPSServer(int port, ServerData data) throws DatabaseException, IOException {
		super(0);
		this.data = data;
		this.port = port;
	}

	@Override
	protected ServerData createData() {
		return data;
	}

	private SSLContext getContext() {
		try {
			List<StoredCertificate> certs = data.sdb.getCertificates();
			if (certs.isEmpty()) {
				System.err.println("SSL Certificate not found");
				return null;
			}
			StoredCertificate cert = certs.get(0);

			X509Certificate xcert = cert.getCertificate();
			if (xcert == null) {
				System.err.println("SSL Certificate corrupted or not present");
				return null;
			}

			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);

			KeyStore.PrivateKeyEntry privateKeyEntry = new PrivateKeyEntry(cert.getKeyPair().getPrivate(), new Certificate[] { xcert });
			keyStore.setEntry("rion", privateKeyEntry, new PasswordProtection("password".toCharArray()));

			TrustManagerFactory instance = TrustManagerFactory.getInstance("SunX509");
			instance.init(keyStore);

			KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			km.init(keyStore, "password".toCharArray());

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(km.getKeyManagers(), instance.getTrustManagers(), new SecureRandom());
			return sslContext;
		} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException | KeyManagementException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void run() {
		thread.start();
	}

	private void runAsync() throws HTTPServerException {
		SSLContext context = getContext();
		if (context != null) {
			try {
				SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(port);
				server.setUseClientMode(false);
				server.setWantClientAuth(false);
				while (true) {
					SSLSocket client;
					try {
						client = (SSLSocket) server.accept();
					} catch (IOException e) {
						throw new HTTPServerException("Failed to accept client on port " + port, e);
					}
					try {
						data.clients.put(new HTTPSyncClientFactory(new CompatibleSocketClient(client), true));
					} catch (InterruptedException e) {
						e.printStackTrace();
						try {
							client.close();
						} catch (IOException e1) {
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
