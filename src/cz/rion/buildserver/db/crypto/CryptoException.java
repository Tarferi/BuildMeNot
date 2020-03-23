package cz.rion.buildserver.db.crypto;

public class CryptoException extends Exception {

	public CryptoException(String description) {
		super(description);
	}

	public CryptoException(String description, Exception e) {
		super(description, e);
	}

}
