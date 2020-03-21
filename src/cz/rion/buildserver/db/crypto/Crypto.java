package cz.rion.buildserver.db.crypto;

public interface Crypto {

	public String decrypt(String keyFileName, String encryptedData);
	
}
