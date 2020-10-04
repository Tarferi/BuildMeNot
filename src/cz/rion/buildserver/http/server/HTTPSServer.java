package cz.rion.buildserver.http.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.CompatibleSocketClient;
import cz.rion.buildserver.http.sync.HTTPSyncClientFactory;
import cz.rion.buildserver.https.SSLServerSocketChannel;
import cz.rion.buildserver.wrappers.MyThread;

public class HTTPSServer extends HTTPServer {

	private final ServerData data;

	private MyThread thread = new MyThread() {

		@Override
		protected void runAsync() {
			this.setName("HTTPS server");
			HTTPSServer.this.runAsync();
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

	private InputStream fromBytes(final byte[] data) {
		return new InputStream() {

			int position = 0;
			int length = data.length;

			@Override
			public int read() throws IOException {
				if (position == length) {
					return -1;
				} else {
					position++;
					return data[position - 1];

				}
			}

		};
	}

	private SSLContext getContext() {
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			InputStream finStream = fromBytes("this is a cert".getBytes(Settings.getDefaultCharset()));

			X509Certificate x509Certificate = (X509Certificate) cf.generateCertificate(finStream);

			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null);
			keyStore.setCertificateEntry("someAlias", x509Certificate);

			TrustManagerFactory instance;
			instance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			instance.init(keyStore);

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, instance.getTrustManagers(), null);
			return sslContext;
		} catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private ExecutorService getExecutor() {
		return Executors.newSingleThreadExecutor();
	}

	private ServerSocketChannel createHTTPSServerSocket() throws HTTPServerException {
		SSLContext context = getContext();
		if (context != null) {
			return new SSLServerSocketChannel(super.createServerSocket(port), context, getExecutor());
		}
		return null;
	}

	@Override
	public void run() {
		thread.start();
	}

	private void runAsync() {
		ServerSocketChannel server;
		try {
			server = createHTTPSServerSocket();
			if (server == null) {
				System.err.println("Failed to create SSL server socket, exiting");
				return;
			}

			while (true) {
				SocketChannel client;
				try {
					client = server.accept();
				} catch (IOException e) {
					throw new HTTPServerException("Failed to accept client on port " + port, e);
				}
				try {
					data.clients.put(new HTTPSyncClientFactory(new CompatibleSocketClient(client)));
				} catch (InterruptedException e) {
					e.printStackTrace();
					try {
						client.close();
					} catch (IOException e1) {
					}
				}
			}
		} catch (HTTPServerException e2) {
			e2.printStackTrace();
			return;
		}
	}
}
