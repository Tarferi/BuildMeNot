package cz.rion.buildserver.db.layers.staticDB;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager.ReadVirtualFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;

public abstract class LayeredTestDB extends LayeredCodeModifiersDB {

	public LayeredTestDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
	}

	public static class MallocData {
		private static final String mallocFileBeforeName = "tests/includes/malloc_before_code.c";
		private static final String mallocFileAfterName = "tests/includes/malloc_after_code.c";
		private static final String mallocMainFileName = "tests/includes/malloc_main.c";
		public final String MallocFileBefore;
		public final String MallocFileAfter;
		public final String MallocFile;

		private MallocData(String before, String after, String malloc) {
			this.MallocFileBefore = before;
			this.MallocFileAfter = after;
			this.MallocFile = malloc;
		}

		private static MallocData get(StaticDB db) {
			ReadVirtualFile before = db.loadRootFile(mallocFileBeforeName);
			ReadVirtualFile after = db.loadRootFile(mallocFileAfterName);
			ReadVirtualFile malloc = db.loadRootFile(mallocMainFileName);
			if (before != null && after != null && malloc != null) {
				if (before.Contents != null && after.Contents != null && malloc.Contents != null) {
					return new MallocData(before.Contents, after.Contents, malloc.Contents);
				}
			}
			return null;
		}
	}

	public final CachedData<MallocData> MallocFilesCache = new CachedDataWrapper<MallocData>(30 * 60, new CachedDataGetter<MallocData>() {

		@Override
		public MallocData update() {
			return MallocData.get((StaticDB) LayeredTestDB.this);
		}
	});
	
	@Override
	public void clearCache() {
		super.clearCache();
		MallocFilesCache.clear();
	}
}
