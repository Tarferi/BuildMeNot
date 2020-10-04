package cz.rion.buildserver.db.layers.staticDB;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonNumber;

public abstract class LayeredSSLDB extends LayeredDBFileWrapperDB {

	private static final int KEY_SIZE = 2048;

	private Certificate root;

	private final VirtualFile vfPrehled = new VirtualFile() {

		private String getHeader() {
			StringBuilder sb = new StringBuilder();
			sb.append("# JSON objekt, kde klíèem je doména a kde každý prvek obsahuje:\n");
			sb.append("#\tDomain - název domény, pro který je certifikát vydán\n");
			sb.append("#\tPublicKey - surová data certifikátu. Pokud není obsaženo, spustit gen.exe a vytvoøí se\n");
			sb.append("#\tPrivateKey - surová data certifikátu. Pokud není obsaženo, spustit gen.exe a vytvoøí se\n");
			sb.append("#\tCreation - timestamp v ms kdy byl certifikát vydán\n");
			sb.append("#\tExpiration - timestamp v ms kdy certifikát expiruje\n");
			return sb.toString();
		}

		private String stripHeader(String data) {
			StringBuilder sb = new StringBuilder();
			for (String line : data.split("\n")) {
				line = line.trim();
				if (line.startsWith("#") || line.isEmpty()) {
					continue;
				}
				sb.append(line + "\n");
			}
			return sb.toString();
		}

		@Override
		public String read() throws DatabaseException {
			JsonObject obj = new JsonObject();
			for (StoredCertificate cert : getCertificates()) {
				JsonObject c = new JsonObject();
				JsonArray domains = new JsonArray();
				for (String domain : cert.Domains) {
					domains.add(new JsonString(domain));
				}
				c.add("Domains", domains);
				if (!cert.PrivateKey.isEmpty() && !cert.PublicKey.isEmpty() && !cert.Certificate.isEmpty()) {
					c.add("PublicKey", new JsonString(cert.PublicKey));
					c.add("PrivateKey", new JsonString(cert.PrivateKey));
					c.add("Certificate", new JsonString(cert.Certificate));
				}
				c.add("Creation", new JsonNumber(0, "" + cert.creation.getTime()));
				c.add("Expiration", new JsonNumber(0, "" + cert.creation.getTime()));
				obj.add(cert.Name, c);
			}
			return getHeader() + JsonValue.getPrettyJsonString(obj);
		}

		private List<Certificate> convert(JsonValue val) {
			List<Certificate> lst = null;
			if (val != null) {
				if (val.isObject()) {
					lst = new ArrayList<>();
					for (Entry<String, JsonValue> entry : val.asObject().getEntries()) {
						JsonValue c = entry.getValue();
						if (c.isObject()) {
							JsonObject obj = c.asObject();
							if (obj.containsArray("Domains") && obj.containsNumber("Creation") && obj.containsNumber("Expiration")) {
								List<JsonValue> domainsL = obj.getArray("Domains").Value;
								String[] domains = new String[domainsL.size()];
								for (int i = 0; i < domains.length; i++) {
									domains[i] = domainsL.get(i).asString().Value;
								}
								String name = entry.getKey();
								String publicKey = "";
								String privateKey = "";
								String certificate = "";
								if (obj.containsString("PublicKey") && obj.containsString("PrivateKey") && obj.containsString("Certificate")) {
									publicKey = obj.getString("PublicKey").Value;
									privateKey = obj.getString("PrivateKey").Value;
									certificate = obj.getString("Certificate").Value;
								}
								long creation = obj.getNumber("Creation").asLong();
								long expiration = obj.getNumber("Expiration").asLong();
								lst.add(new Certificate(name, expiration, creation, publicKey, privateKey, certificate, domains));
							}
						}
					}
				}
			}
			return lst;
		}

		@Override
		public void write(String data) throws DatabaseException {
			JsonValue val = JsonValue.parse(stripHeader(data));
			List<Certificate> nw = convert(val);
			if (nw != null) {
				updateCertificates(nw);
			}
		}

		@Override
		public String getName() {
			return "ssl/config.cfg";
		}

	};

	private final VirtualFile vfProcess = new VirtualFile() {

		@Override
		public String read() throws DatabaseException {
			try {
				StringBuilder sb = new StringBuilder();
				List<Certificate> lst = new ArrayList<>();
				for (StoredCertificate cert : getCertificates()) {
					if (cert.PublicKey.isEmpty() || cert.PrivateKey.isEmpty()) {
						sb.append("Requesting certificate renewal for " + cert.Name + "\n");
						Certificate nw = ACME.update((StaticDB) LayeredSSLDB.this, root, cert.Name, cert.Domains, sb);
						if (nw == null) {
							sb.append("Requesting certificate for " + cert.Name + " failed\n\n\n");
						} else {
							lst.add(nw);
							sb.append("Requesting certificate for " + cert.Name + " finished\n\n\n");
						}
					}
				}
				updateCertificates(lst);
				return sb.toString();
			} finally {
				clearCache();
			}
		}

		@Override
		public void write(String data) throws DatabaseException {

		}

		@Override
		public String getName() {
			return "ssl/gen.exe";
		}

	};

	public LayeredSSLDB(DatabaseInitData initData) throws DatabaseException {
		super(initData);
		this.makeTable("ssl", KEY("ID"), TEXT("name"), TEXT("domain"), BIGTEXT("public_key"), BIGTEXT("private_key"), BIGTEXT("certificate"), DATE("creation_date"), DATE("expiration"), NUMBER("valid"));
		this.registerVirtualFile(vfPrehled);
		this.registerVirtualFile(vfProcess);
		try {
			initRootCert();
		} catch (IOException e) {
			throw new DatabaseException("Failed to create root certificate", e);
		}
	}

	private static class KeyData {
		public final String Algorithm;
		public final String Format;
		public final byte[] Encoded;

		public KeyData(PublicKey pub) {
			Algorithm = pub.getAlgorithm();
			Format = pub.getFormat();
			Encoded = pub.getEncoded();
		}

		public KeyData(PrivateKey priv) throws IOException {
			Algorithm = priv.getAlgorithm();
			Format = priv.getFormat();
			Encoded = priv.getEncoded();
		}

		public KeyData(String algorithm, String format, byte[] encoded) {
			Algorithm = algorithm;
			Format = format;
			Encoded = encoded;
		}

		@Override
		public boolean equals(Object another) {
			if (another instanceof KeyData) {
				KeyData a = (KeyData) another;
				if (this.Algorithm.equals(a.Algorithm) && this.Format.equals(a.Format) && this.Encoded.length == a.Encoded.length) {
					for (int i = 0; i < this.Encoded.length; i++) {
						if (this.Encoded[i] != a.Encoded[i]) {
							return false;
						}
					}
					return true;
				}
				return false;
			}
			return super.equals(another);
		}
	}

	private static final class CodeUtils {

		private static void writeHex(byte b, StringBuilder sb) {
			byte b1 = (byte) ((b >> 4) & 0b1111);
			byte b2 = (byte) (b & 0b1111);
			sb.append((char) (b1 <= 9 ? '0' + b1 : 'A' + (b1 - 10)));
			sb.append((char) (b2 <= 9 ? '0' + b2 : 'A' + (b2 - 10)));
		}

		private static byte fromHex(char c) {
			byte b = (byte) c;
			if (b >= '0' && b <= '9') {
				return (byte) (b - '0');
			} else if (b >= 'A' && b <= 'F') {
				return (byte) (b + 10 - 'A');
			} else if (b >= 'a' && b <= 'f') {
				return (byte) (b + 10 - 'a');
			} else {
				return 0;
			}
		}

		private static byte readHex(char c1, char c2) {
			byte b1 = fromHex(c1);
			byte b2 = fromHex(c2);
			byte b = (byte) ((b1 << 4) | b2);
			return b;
		}

		private static String encodeKey(KeyData data) throws DatabaseException {
			StringBuilder sb = new StringBuilder();
			sb.append(data.Algorithm + "\n");
			sb.append(data.Format + "\n");
			sb.append(encodeBytes(data.Encoded, 16));
			String str = sb.toString();
			// test decoder
			KeyData test = decodeKey(str);
			if (!test.equals(data)) {
				throw new DatabaseException("Encoding failed");
			}
			return str;
		}

		private static String encodeCert(X509Certificate crt) throws DatabaseException, CertificateEncodingException {
			return encodeBytes(crt.getEncoded(), 16);
		}

		private static X509Certificate decodeCert(String encoded) throws CertificateException {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(decodeString(encoded));
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) certFactory.generateCertificate(inputStream);
			return cert;
		}

		private static boolean isCodedChar(char c) {
			return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
		}

		private static byte[] decodeString(String str) {
			int len = 0;
			char[] chars = str.toCharArray();
			for (int i = 0; i < chars.length; i++) {
				if (isCodedChar(chars[i])) {
					len++;
				}
			}
			byte[] res = new byte[len / 2];
			for (int i = 0, o = 0; i < chars.length; i++) {
				if (isCodedChar(chars[i]) && isCodedChar(chars[i + 1])) {
					res[o] = readHex(chars[i], chars[i + 1]);
					i++;
					o++;
				}
			}
			return res;
		}

		private static String encodeBytes(byte[] raw, int width) throws DatabaseException {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < raw.length; i++) {
				if (i % width == 0 && i > 0) {
					sb.append("\n");
				}
				writeHex(raw[i], sb);
			}
			String str = sb.toString();
			// test decoder
			byte[] test = decodeString(str);
			if (test.length != raw.length) {
				throw new DatabaseException("Encoding failed");
			}
			for (int i = 0; i < test.length; i++) {
				if (test[i] != raw[i]) {
					throw new DatabaseException("Encoding failed");
				}
			}
			return str;
		}

		private static KeyData decodeKey(String data) {
			String[] parts = data.split("\n", 3);
			if (parts.length == 3) {
				String algorithm = parts[0].trim();
				String format = parts[1].trim();
				data = parts[2].trim();
				return new KeyData(algorithm, format, decodeString(data));
			}
			return null;
		}

	}

	private void initRootCert() throws DatabaseException, IOException {
		List<Certificate> lst = new ArrayList<>();
		for (StoredCertificate cert : getCertificates(true)) {
			lst.add(cert);
			if (cert.isRoot()) {
				root = cert;
				return;
			}
		}
		long now = System.currentTimeMillis();
		KeyPair kwp;
		try {
			kwp = ACME.createKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new DatabaseException("Failed to create root key", e);
		}
		String pub = CodeUtils.encodeKey(new KeyData(kwp.getPublic()));
		String priv = CodeUtils.encodeKey(new KeyData(kwp.getPrivate()));

		root = new Certificate("root", 0, now, pub, priv, "", new String[] { "root" });
		lst.add(root);
		updateCertificates(lst);
	}

	public static class Certificate {
		public final Date expiration;
		public final Date creation;
		public final String PublicKey;
		public final String PrivateKey;
		public final String Certificate;
		public final String[] Domains;
		public final String Name;

		private KeyPair cachedKey = null;
		private X509Certificate cachedCert = null;

		private Certificate(String name, long exp, long crea, String publicKey, String privateKey, String certificate, String[] domains) {
			this.Name = name;
			this.expiration = new Date(exp);
			this.creation = new Date(crea);
			this.PublicKey = publicKey;
			this.PrivateKey = privateKey;
			this.Certificate = certificate;
			this.Domains = domains;
		}

		public boolean isRoot() {
			for (String domain : Domains) {
				if (domain.equals("root")) {
					return true;
				}
			}
			return false;
		}

		public X509Certificate getCertificate() {
			if (!PublicKey.isEmpty() && !PrivateKey.isEmpty() && !Certificate.isEmpty()) {
				if (cachedCert == null) {
					try {
						cachedCert = CodeUtils.decodeCert(Certificate);
					} catch (CertificateException e) {
						e.printStackTrace();
					}
				}
				return cachedCert;
			}
			return null;
		}

		public KeyPair getKeyPair() {
			if (!PublicKey.isEmpty() && !PrivateKey.isEmpty() && !Certificate.isEmpty()) {
				if (cachedKey == null) {
					try {
						cachedKey = ACME.createKeyPair(this);
					} catch (DatabaseException | NoSuchAlgorithmException | InvalidKeySpecException e) {
						e.printStackTrace();
					}
				}
				return cachedKey;
			}
			return null;
		}
	}

	public static class StoredCertificate extends Certificate {
		public final int ID;

		private StoredCertificate(String name, int id, long exp, long crea, String publicKey, String privateKey, String certificate, String[] domains) {
			super(name, exp, crea, publicKey, privateKey, certificate, domains);
			this.ID = id;
		}
	}

	public void updateCertificates(List<Certificate> nw) throws DatabaseException {
		try {
			Map<String, StoredCertificate> existing = new HashMap<>();
			for (StoredCertificate ex : getCertificates()) {
				if (ex.isRoot()) { // Ignore root certificate
					continue;
				}
				existing.put(ex.Name, ex);
			}
			for (Certificate ep : nw) {
				if (existing.containsKey(ep.Name)) {
					StoredCertificate exi = existing.get(ep.Name);
					if (!exi.PublicKey.equals(ep.PublicKey) || !exi.PrivateKey.equals(ep.PrivateKey) || exi.expiration != ep.expiration) {
						update(exi.ID, ep);
					}
					existing.remove(ep.Name);
				} else {
					create(ep);
				}
			}
			for (Entry<String, StoredCertificate> entry : existing.entrySet()) {
				delete(entry.getValue().ID);
			}
		} finally {
			clearCache();
		}
	}

	private void delete(int id) throws DatabaseException {
		final String tableName = "ssl";
		update(tableName, id, new ValuedField(getField(tableName, "valid"), 0));
	}

	private String[] unserialize(String data) {
		if (data.isEmpty()) {
			return new String[0];
		}
		String[] s = data.split(",");
		for (int i = 0; i < s.length; i++) {
			s[i] = s[i].trim();
		}
		return s;
	}

	private String serialize(String[] data) {
		if (data.length == 0) {
			return "";
		} else if (data.length == 1) {
			return data[0];
		} else {
			StringBuilder sb = new StringBuilder(data[0]);
			for (int i = 1; i < data.length; i++) {
				sb.append(", " + data[i]);
			}
			return sb.toString();
		}
	}

	private void create(Certificate ep) throws DatabaseException {
		final String tableName = "ssl";
		insert(tableName, new ValuedField(getField(tableName, "domain"), serialize(ep.Domains)), new ValuedField(getField(tableName, "name"), ep.Name), new ValuedField(getField(tableName, "certificate"), ep.Certificate), new ValuedField(getField(tableName, "public_key"), ep.PublicKey), new ValuedField(getField(tableName, "private_key"), ep.PrivateKey), new ValuedField(getField(tableName, "creation_date"), ep.creation.getTime()), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "expiration"), ep.expiration.getTime()));
	}

	private void update(int id, Certificate ep) throws DatabaseException {
		final String tableName = "ssl";
		update(tableName, id, new ValuedField(getField(tableName, "domain"), serialize(ep.Domains)), new ValuedField(getField(tableName, "name"), ep.Name), new ValuedField(getField(tableName, "certificate"), ep.Certificate), new ValuedField(getField(tableName, "public_key"), ep.PublicKey), new ValuedField(getField(tableName, "private_key"), ep.PrivateKey), new ValuedField(getField(tableName, "creation_date"), ep.creation.getTime()), new ValuedField(getField(tableName, "expiration"), ep.expiration.getTime()));
	}

	public List<StoredCertificate> getCertificates() {
		return getCertificates(false);
	}

	private List<StoredCertificate> getCertificates(boolean includeRoot) {
		List<StoredCertificate> lst = new ArrayList<>();
		final String tableName = "ssl";
		try {
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "valid"), getField(tableName, "domain"), getField(tableName, "public_key"), getField(tableName, "private_key"), getField(tableName, "certificate"), getField(tableName, "creation_date"), getField(tableName, "expiration") }, true, new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("domain") && obj.containsString("public_key") && obj.containsString("private_key") && obj.containsString("certificate") && obj.containsNumber("creation_date") && obj.containsNumber("expiration")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String[] domains = unserialize(obj.getString("domain").Value);
						String pub = obj.getString("public_key").Value;
						String priv = obj.getString("private_key").Value;
						String certificate = obj.getString("certificate").Value;
						long crea = obj.getNumber("creation_date").asLong();
						long exp = obj.getNumber("expiration").asLong();
						StoredCertificate cert = new StoredCertificate(name, id, exp, crea, pub, priv, certificate, domains);
						if (!cert.isRoot() || includeRoot) { // Ignore root certificates
							lst.add(cert);
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return lst;

	}

	private static final class ACME {

		public static Certificate update(StaticDB sdb, Certificate root, String name, String[] domains, StringBuilder log) {
			List<String> allocatedPaths = new ArrayList<>();
			try {
				// Create key pair
				log.append("Getting root keypair\n");
				KeyPair keyPair = createKeyPair(root);
				Session session = new Session("acme://letsencrypt.org");

				log.append("Getting account\n");
				Account account = findOrRegisterAccount(session, keyPair);

				log.append("Creating order\n");
				Order order = account.newOrder().domains(domains).create();

				log.append("Performing authorizations\n");
				// Perform all required authorizations
				for (Authorization auth : order.getAuthorizations()) {
					authorize(sdb, auth, log);
				}

				log.append("Generating domain key pairs\n");
				KeyPair domainKeyPair = createKeyPair();

				// Generate a CSR for all of the domains, and sign it with the domain key pair.
				log.append("Getting order data\n");
				CSRBuilder csrb = new CSRBuilder();
				csrb.addDomains(domains);
				csrb.sign(domainKeyPair);

				// Order the certificate
				log.append("Executing order\n");
				order.execute(csrb.getEncoded());

				log.append("Waiting for order to complete\n");
				// Wait for the order to complete
				try {
					log.append("Polling order status\n");
					int attempts = 10;
					while (order.getStatus() != Status.VALID && attempts-- > 0) {
						// Did the order fail?
						if (order.getStatus() == Status.INVALID) {
							log.append("Order is now invalid, failed\n");
							throw new AcmeException("Order failed... Giving up.");
						}

						// Wait for a few seconds
						Thread.sleep(3000L);
						// Then update the status
						order.update();
					}
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					return null;
				}

				// Get the certificate
				log.append("Certificate generated, encoding and returning\n");
				org.shredzone.acme4j.Certificate certificate = order.getCertificate();
				X509Certificate crt = certificate.getCertificate();

				String cert = CodeUtils.encodeCert(crt);

				String pubKey = CodeUtils.encodeKey(new KeyData(domainKeyPair.getPublic()));
				String privKey = CodeUtils.encodeKey(new KeyData(domainKeyPair.getPrivate()));
				long now = System.currentTimeMillis();
				return new Certificate(name, 0, now, pubKey, privKey, cert, domains);
			} catch (Throwable t) { // We must never fail
				t.printStackTrace();
			} finally {
				clearPaths(allocatedPaths);
			}
			return null;
		}

		private static void clearPaths(List<String> allocatedPaths) {

		}

		private static Challenge httpChallenge(StaticDB sdb, Authorization auth, StringBuilder logger) throws AcmeException, DatabaseException {
			// Find a single http-01 challenge
			Http01Challenge challenge = auth.findChallenge(Http01Challenge.class);
			if (challenge == null) {
				throw new AcmeException("Found no " + Http01Challenge.TYPE + " challenge, don't know what to do...");
			}

			String path = "/.well-known/acme-challenge/" + challenge.getToken();
			String content = challenge.getAuthorization();

			sdb.addStaticEndpoint(path, content);
			logger.append("Passing challenge for domain " + auth.getIdentifier().getDomain());

			if (Settings.SSLRequiresRemotePart()) { // Notify remote part
				Socket s = null;
				try {
					s = new Socket(auth.getIdentifier().getDomain(), 80);
					StringBuilder sb = new StringBuilder();
					sb.append("GET /query/" + Settings.getPasscode() + path + " HTTP/1.1\r\n");
					sb.append("Host: " + auth.getIdentifier().getDomain() + "\r\n");
					sb.append("Connection: close\r\n");
					sb.append("Content-Length: " + content.length() + "\r\n");
					sb.append("\r\n");
					sb.append(content);
					s.getOutputStream().write(sb.toString().getBytes());
					synchronized (s) {
						s.wait(1000);
					}
					s.close();
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				} finally {
					try {
						s.close();
					} catch (Throwable t) {
					}
				}
			}

			return challenge;
		}

		private static void authorize(StaticDB sdb, Authorization auth, StringBuilder logger) throws AcmeException, DatabaseException {
			// The authorization is already valid. No need to process a challenge.
			if (auth.getStatus() == Status.VALID) {
				return;
			}

			// Find the desired challenge and prepare it.
			Challenge challenge = httpChallenge(sdb, auth, logger);
			if (challenge == null) {
				throw new AcmeException("No challenge found");
			}

			// If the challenge is already verified, there's no need to execute it again.
			if (challenge.getStatus() == Status.VALID) {
				return;
			}

			// Now trigger the challenge.
			challenge.trigger();

			// Poll for the challenge to complete.
			try {
				int attempts = 10;
				while (challenge.getStatus() != Status.VALID && attempts-- > 0) {
					// Did the authorization fail?
					if (challenge.getStatus() == Status.INVALID) {
						throw new AcmeException("Challenge failed... Giving up.");
					}

					// Wait for a few seconds
					Thread.sleep(3000L);

					// Then update the status
					challenge.update();
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			// All reattempts are used up and there is still no valid authorization?
			if (challenge.getStatus() != Status.VALID) {
				throw new AcmeException("Failed to pass the challenge for domain " + auth.getIdentifier().getDomain() + ", ... Giving up.");
			}
		}

		private static KeySpec getKeySpec(KeyData data) throws InvalidKeySpecException {
			if (data.Format.equals("X.509")) {
				return new X509EncodedKeySpec(data.Encoded);
			} else if (data.Format.equals("PKCS#8")) {
				return new PKCS8EncodedKeySpec(data.Encoded);
			} else {
				throw new InvalidKeySpecException();
			}
		}

		private static KeyPair createKeyPair(Certificate root) throws DatabaseException, NoSuchAlgorithmException, InvalidKeySpecException {
			final KeyData pub = CodeUtils.decodeKey(root.PublicKey);
			final KeyData priv = CodeUtils.decodeKey(root.PrivateKey);
			if (pub == null || priv == null) {
				throw new DatabaseException("Failed to load stored certificate");
			}
			KeyFactory pubKf = KeyFactory.getInstance(pub.Algorithm);
			KeyFactory privKf = KeyFactory.getInstance(priv.Algorithm);
			PublicKey pubKey = pubKf.generatePublic(getKeySpec(pub));
			PrivateKey privKey = privKf.generatePrivate(getKeySpec(priv));
			return new KeyPair(pubKey, privKey);
		}

		private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			return keyPair;
		}

		private static Account findOrRegisterAccount(Session session, KeyPair accountKey) throws AcmeException {
			session.getMetadata().getTermsOfService();
			Account account = new AccountBuilder().addContact("mailto:" + Settings.getSSLEmail()).agreeToTermsOfService().useKeyPair(accountKey).create(session);
			return account;
		}
	}
}
