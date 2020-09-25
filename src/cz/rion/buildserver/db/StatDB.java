package cz.rion.buildserver.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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

	private static class StatsCounts {

		private final int[] good;
		private final int[] all;

		private StatsCounts(int totalSteps) {
			this.good = new int[totalSteps];
			this.all = new int[totalSteps];
		}
	}

	private static class StatsToolchain {
		private final Map<String, StatsCounts> data = new HashMap<>();
		private final int totapSteps;

		private StatsToolchain(int totalSteps) {
			this.totapSteps = totalSteps;
		}

		private StatsCounts get(String toolchain) {
			if (data.containsKey(toolchain)) {
				return data.get(toolchain);
			} else {
				StatsCounts s = new StatsCounts(totapSteps);
				data.put(toolchain, s);
				for (int i = 0; i < s.all.length; i++) {
					s.good[i] = 0;
					s.all[i] = 0;
				}
				return s;
			}
		}

		private void addGood(String toolchain, int i) {
			get(toolchain).good[i]++;
		}

		private void addAll(String toolchain, int i) {
			get(toolchain).all[i]++;
		}
	}

	private JsonArray getByDays(Date d_from, Date d_to, long step, SimpleDateFormat format) {

		long from = d_from.getTime();
		long to = d_to.getTime();

		final String tableName = "compilations";
		try {

			final TableField f_id = db.getField(tableName, "ID");
			final TableField f_code = db.getField(tableName, "code");
			final TableField f_ct = db.getField(tableName, "creation_time");
			final TableField f_tc = db.getField(tableName, "toolchain");

			JsonArray res = db.select("compilations", new TableField[] { f_id, f_code, f_ct, f_tc }, false, new ComparisionField(f_ct, from, FieldComparator.Greater), new ComparisionField(f_ct, to, FieldComparator.Lesser));
			int totalSteps = (int) ((to - from) / step);
			totalSteps += ((to - from) % step) == 0 ? 0 : 1;

			StatsToolchain counters = new StatsToolchain(totalSteps);

			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					long tm = obj.getNumber("creation_time").asLong();
					String toolchain = obj.getString("toolchain").Value;

					// Calculate day index this belongs to
					long off = tm - from;
					int offIndex = (int) (off / step);

					boolean isGood = obj.getNumber("code").Value == 0;

					counters.addAll(toolchain, offIndex);
					if (isGood) {
						counters.addGood(toolchain, offIndex);
					}
				}
			}

			List<JsonValue> data = new ArrayList<>();
			for (Entry<String, StatsCounts> entry : counters.data.entrySet()) {
				String toolchain = entry.getKey();
				int[] good = entry.getValue().good;
				int[] all = entry.getValue().all;
				for (int i = 0; i < good.length; i++) {
					JsonObject obj = new JsonObject();
					Date td = new Date(from + (i * step));
					obj.add("Date", new JsonString(format.format(td)));
					obj.add("CountTotal", new JsonNumber(all[i]));
					obj.add("CountGood", new JsonNumber(good[i]));
					obj.add("ToolChain", new JsonString(toolchain));
					data.add(obj);
				}
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
			return "TEXT(Date), INT(CountTotal), INT(CountGood), TEXT(ToolChain)";
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
			return "TEXT(Date), INT(CountTotal), INT(CountGood), TEXT(ToolChain)";
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
			return "TEXT(Date), INT(CountTotal), INT(CountGood), TEXT(ToolChain)";
		}

	};

	private final VirtualStatFile sf4 = new VirtualStatFile() {

		@Override
		public String getName() {
			return "Total2020";
		}

		@SuppressWarnings("deprecation")
		@Override
		public JsonArray getData() {

			Date first = new Date();
			first.setMinutes(0);
			first.setHours(0);
			first.setSeconds(0);
			first.setDate(1);
			first.setMonth(0);
			first.setYear(2020);

			Date last = new Date(first.getTime());
			last.setYear(2021);

			try {
				JsonArray firstData = db.select_raw("SELECT creation_time FROM compilations ORDER BY id ASC LIMIT 1").getJSON(false, new TableField[0]);
				if (!firstData.Value.isEmpty()) {
					first = new Date(firstData.Value.get(0).asObject().getNumber("creation_time").asLong());
				}
				JsonArray lastData = db.select_raw("SELECT creation_time FROM compilations ORDER BY id DESC LIMIT 1").getJSON(false, new TableField[0]);
				if (!lastData.Value.isEmpty()) {
					last = new Date(lastData.Value.get(0).asObject().getNumber("creation_time").asLong());
				}

			} catch (Exception e) {
				e.printStackTrace();
				return new JsonArray(new ArrayList<JsonValue>());
			}

			long from = first.getTime();
			long to = last.getTime();

			return getByDays(new Date(from), new Date(to), day, dateFormatByDays);

		}

		@Override
		public String getQueryString() {
			return "TEXT(Date), INT(CountTotal), INT(CountGood), TEXT(ToolChain)";
		}

	};

	public StatDB(RuntimeDB runtimeDB) {
		this.db = runtimeDB;
		this.db.registerVirtualStatFile(sf1);
		this.db.registerVirtualStatFile(sf2);
		this.db.registerVirtualStatFile(sf3);
		this.db.registerVirtualStatFile(sf4);
	}

}
