package cz.rion.buildserver.db.crypto;

public class CryptoException extends Exception {

	private static final long serialVersionUID = 1L;
	public final String Description;

	public CryptoException(String description) {
		super(description);
		this.Description = description;
	}

	public CryptoException(String description, Exception e) {
		super(description, e);
		this.Description = description;
	}

}
