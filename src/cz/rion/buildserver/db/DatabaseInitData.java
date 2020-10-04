package cz.rion.buildserver.db;

public class DatabaseInitData {

	public final String DatabaseName;
	private CacheClearer cleaner = null;

	public DatabaseInitData(String name) {
		this.DatabaseName = name;
	}

	public DatabaseInitData(String name, CacheClearer cleaner) {
		this.DatabaseName = name;
		this.cleaner = cleaner;
	}

	public void clearCache() {
		if (cleaner != null) {
			cleaner.clearCache();
		}
	}

	public interface CacheClearer {
		public void clearCache();
	}
}
