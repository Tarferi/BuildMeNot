package cz.rion.buildserver.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cz.rion.buildserver.db.SQLiteDB.ComparisionField;
import cz.rion.buildserver.db.SQLiteDB.FieldComparator;
import cz.rion.buildserver.db.SQLiteDB.TableField;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;

public class StatDB {

	private final RuntimeDB db;

	private static final SimpleDateFormat dateFormatByDays = new SimpleDateFormat("dd. MM.");
	private static final SimpleDateFormat dateFormatByHours = new SimpleDateFormat("HH:mm");
	private static final long second = 1000;
	private static final long minute = 60 * second;
	private static final long hour = 60 * minute;
	private static final long day = 24 * hour;

	private JsonArray getByDays(Date d_from, Date d_to, long step, SimpleDateFormat format) {

		long from = d_from.getTime();
		long to = d_to.getTime();

		final String tableName = "compilations";
		try {

			final TableField f_id = db.getField(tableName, "ID");
			final TableField f_code = db.getField(tableName, "code");
			final TableField f_ct = db.getField(tableName, "creation_time");

			JsonArray res = db.select("compilations", new TableField[] { f_id, f_code, f_ct }, false, new ComparisionField(f_ct, from, FieldComparator.Greater), new ComparisionField(f_ct, to, FieldComparator.Lesser));
			int totalSteps = (int) ((to - from) / step);
			totalSteps += ((to - from) % step) == 0 ? 0 : 1;

			int[] good = new int[totalSteps];
			int[] all = new int[totalSteps];

			for (int i = 0; i < good.length; i++) {
				good[i] = 0;
				all[i] = 0;
			}
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					long tm = obj.getNumber("creation_time").asLong();

					// Calculate day index this belongs to
					long off = tm - from;
					int offIndex = (int) (off / step);

					boolean isGood = obj.getNumber("code").Value == 0;

					all[offIndex]++;
					if (isGood) {
						good[offIndex]++;
					}
				}
			}

			List<JsonValue> data = new ArrayList<>();
			for (int i = 0; i < good.length; i++) {
				JsonObject obj = new JsonObject();
				Date td = new Date(from + (i * step));
				obj.add("Date", new JsonString(format.format(td)));
				obj.add("CountTotal", new JsonNumber(all[i]));
				obj.add("CountGood", new JsonNumber(good[i]));
				data.add(obj);
			}
			return new JsonArray(data);
		} catch (DatabaseException e) {
			e.printStackTrace();
			return new JsonArray(new ArrayList<JsonValue>());
		}
	}

	private final VirtualStatFile sf1 = new VirtualStatFile() {

		@Override
		public String getName() {
			return "Last24Hours";
		}

		@SuppressWarnings("deprecation")
		@Override
		public JsonArray getData() {
			Date today = new Date();
			today.setSeconds(0);
			today.setMinutes(0);

			long from = new Date(today.getTime() - (24 * hour)).getTime();
			long to = today.getTime() + hour;

			return getByDays(new Date(from), new Date(to), hour, dateFormatByHours);

		}

		@Override
		public String getQueryString() {
			return "TEXT(Date), INT(CountTotal), INT(CountGood)";
		}

	};

	private final VirtualStatFile sf2 = new VirtualStatFile() {

		@Override
		public String getName() {
			return "PastWeek";
		}

		@SuppressWarnings("deprecation")
		@Override
		public JsonArray getData() {
			Date today = new Date();
			today.setMinutes(0);
			today.setHours(0);
			today.setSeconds(0);

			long from = new Date(today.getTime() - (7 * day)).getTime();
			long to = today.getTime() + day;

			return getByDays(new Date(from), new Date(to), day, dateFormatByDays);

		}

		@Override
		public String getQueryString() {
			return "TEXT(Date), INT(CountTotal), INT(CountGood)";
		}

	};

	private final VirtualStatFile sf3 = new VirtualStatFile() {

		@Override
		public String getName() {
			return "PastMonth";
		}

		@SuppressWarnings("deprecation")
		@Override
		public JsonArray getData() {
			Date today = new Date();
			today.setMinutes(0);
			today.setHours(0);
			today.setSeconds(0);

			long from = new Date(today.getTime() - (30 * day)).getTime();
			long to = today.getTime() + day;

			return getByDays(new Date(from), new Date(to), day, dateFormatByDays);

		}

		@Override
		public String getQueryString() {
			return "TEXT(Date), INT(CountTotal), INT(CountGood)";
		}

	};

	public StatDB(RuntimeDB runtimeDB) {
		this.db = runtimeDB;
		this.db.registerVirtualStatFile(sf1);
		this.db.registerVirtualStatFile(sf2);
		this.db.registerVirtualStatFile(sf3);
	}

}
