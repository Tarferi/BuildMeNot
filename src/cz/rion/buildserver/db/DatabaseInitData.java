package cz.rion.buildserver.db;

public class DatabaseInitData {

	public final String DatabaseName;
	private CacheClearer cleaner = null;
	public final VirtualFileManager Files;

	public DatabaseInitData(String name, VirtualFileManager files) {
		this.DatabaseName = name;
		this.Files = files;
	}

	public DatabaseInitData(String name, CacheClearer cleaner, VirtualFileManager files) {
		this.DatabaseName = name;
		this.cleaner = cleaner;
		this.Files = files;
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
