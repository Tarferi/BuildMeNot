package cz.rion.buildserver.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public abstract class JsonValue {

	@Override
	public String toString() {
		return this.getJsonString();
	}

	public boolean isString() {
		return false;
	}

	public boolean isNumber() {
		return false;
	}

	public boolean isBoolean() {
		return false;
	}

	public boolean isArray() {
		return false;
	}

	public boolean isObject() {
		return false;
	}

	public JsonString asString() {
		return (JsonString) this;
	}

	public JsonNumber asNumber() {
		return (JsonNumber) this;
	}

	public JsonBoolean asBoolean() {
		return (JsonBoolean) this;
	}

	public JsonArray asArray() {
		return (JsonArray) this;
	}

	public JsonObject asObject() {
		return (JsonObject) this;
	}

	public abstract String getJsonString();

	private static List<String> print(String currentPadding, String singlePadding, JsonValue val) {
		List<String> lines = new ArrayList<>();
		if (val.isArray()) {
			if (val.asArray().Value.isEmpty()) {
				lines.add(currentPadding + "[]");
			} else {
				lines.add(currentPadding + "[");
				int valuesLeft = val.asArray().Value.size();
				for (JsonValue v : val.asArray().Value) {
					List<String> nestedLines = print(currentPadding + singlePadding, singlePadding, v);
					if (valuesLeft > 1) {
						String lastLine = nestedLines.remove(nestedLines.size() - 1);
						nestedLines.add(lastLine + ",");
						valuesLeft--;
					}
					lines.addAll(nestedLines);
				}
				lines.add(currentPadding + "]");
			}
		} else if (val.isObject()) {
			if (val.asObject().getEntries().isEmpty()) {
				lines.add(currentPadding + "{}");
			} else {
				lines.add(currentPadding + "{");
				int valuesLeft = val.asObject().getEntries().size();
				for (Entry<String, JsonValue> x : val.asObject().getEntries()) {
					List<String> nestedLines = print(currentPadding + singlePadding, singlePadding, x.getValue());
					if (valuesLeft > 1) {
						String lastLine = nestedLines.remove(nestedLines.size() - 1);
						nestedLines.add(lastLine + ",");
						valuesLeft--;
					}
					String firstLine = nestedLines.remove(0);
					firstLine = currentPadding + singlePadding + "\"" + x.getKey() + "\": " + firstLine.trim();
					nestedLines.add(0, firstLine);
					lines.addAll(nestedLines);
				}
				lines.add(currentPadding + "}");
			}
		} else {
			lines.add(currentPadding + val.getJsonString());
		}
		return lines;
	}

	public static String getPrettyJsonString(String data) {
		JsonValue val = JsonValue.parse(data);
		if (val != null) {
			return getPrettyJsonString(val);
		}
		return data;
	}

	public static String getPrettyJsonString(JsonValue val) {
		List<String> lines = print("", "    ", val);
		boolean first = true;
		StringBuilder sb = new StringBuilder();
		for (String l : lines) {
			if (!first) {
				sb.append("\n");
			}
			sb.append(l);
			first = false;
		}
		return sb.toString();
	}

	private static class JsonParser {

		private String data;
		private int position;

		public JsonParser(String data) {
			this.data = data;
			this.position = 0;
		}

		public char getCurrentChar() {
			return data.charAt(position);
		}

		public boolean isCurrentWhiteSpace() {
			char c = getCurrentChar();
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
				return true;
			}
			return false;
		}

		public void nextChar(boolean skipWhiteSpace) {
			while (position + 1 < data.length()) {
				position++;
				if (skipWhiteSpace) {
					if (isCurrentWhiteSpace()) {
						continue;
					}
				}
				break;
			}
		}

		public boolean isDone() {
			return position == data.length();
		}
	}

	public static JsonValue parse(String data) {
		return parse(new JsonParser(data));
	}

	public static JsonValue parse(JsonParser inst) {
		if (inst.isDone()) {
			return null;
		} else {
			if (inst.isCurrentWhiteSpace()) {
				inst.nextChar(true);
			}
			char c = inst.getCurrentChar();
			if (c == '"') {
				StringBuilder sb = new StringBuilder();
				char previous = 0;
				while (!inst.isDone()) {
					inst.nextChar(false);
					char next = inst.getCurrentChar();
					if (next == '\\') {
						inst.nextChar(false);
						next = inst.getCurrentChar();
						if (next == 'u' || next == 'U') {
							int value = 0;
							for (int i = 0; i < 4; i++) {
								inst.nextChar(false);
								next = inst.getCurrentChar();
								int n = 0;
								if (next >= '0' && next <= '9') {
									n = (int) (next - '0');
								} else if (next >= 'a' && next <= 'f') {
									n = 10 + ((int) (next - 'a'));
								} else if (next >= 'A' && next <= 'F') {
									n = 10 + ((int) (next - 'A'));
								}
								value <<= 4;
								value += n;
							}
							char[] chars = Character.toChars(value);
							sb.append(chars);
						} else {
							next = next == 'r' ? '\r' : next;
							next = next == 'n' ? '\n' : next;
							next = next == 't' ? '\t' : next;
							next = next == '\\' ? '\\' : next;
							sb.append(next);
						}
						continue;
					}
					if (next == '"' && previous != '\\') {
						break;
					} else {
						sb.append(next);
						previous = next;
					}
				}
				inst.nextChar(true);
				return new JsonString(sb.toString());
			} else if ((c >= '0' && c <= '9') || c == '-') {
				boolean minus = c == '-';
				int num = minus ? 0 : c - '0';
				StringBuilder sb = new StringBuilder();
				sb.append(c);
				while (!inst.isDone()) {
					inst.nextChar(false);
					char next = inst.getCurrentChar();
					if (next >= '0' && next <= '9') {
						sb.append(next);
						num *= 10;
						num += next - '0';
					} else {
						break;
					}
				}
				return new JsonNumber(num, sb.toString());
			} else if (c == '{') {
				inst.nextChar(true);
				boolean foundEnd = false;
				if (inst.isCurrentWhiteSpace()) {
					inst.nextChar(true);
				}
				Map<String, JsonValue> data = new HashMap<>();
				if (inst.getCurrentChar() != '}') { // At least 1 element
					while (!inst.isDone()) {
						JsonValue key = parse(inst);
						if (key == null) {
							return null;
						}
						if (!key.isString()) {
							return null;
						}

						if (inst.isCurrentWhiteSpace()) {
							inst.nextChar(true);
						}
						if (inst.getCurrentChar() != ':') {
							return null;
						}
						inst.nextChar(true);

						JsonValue value = parse(inst);
						if (value == null) {
							return null;
						}
						data.put(key.asString().Value, value);
						if (inst.isCurrentWhiteSpace()) {
							inst.nextChar(true);
						}
						if (inst.getCurrentChar() == ',') {
							inst.nextChar(true);
						} else if (inst.getCurrentChar() == '}') {
							foundEnd = true;
							break;
						} else {
							return null;
						}
					}
					if (!foundEnd) {
						return null;
					}
				}
				inst.nextChar(true);
				return new JsonObject(data);
			} else if (c == '[') {
				inst.nextChar(true);
				List<JsonValue> values = new ArrayList<>();
				boolean foundEnd = false;
				if (inst.isCurrentWhiteSpace()) {
					inst.nextChar(true);
				}
				if (inst.getCurrentChar() != ']') {
					while (!inst.isDone()) {
						JsonValue val = parse(inst);
						if (val == null) {
							return null;
						}
						values.add(val);
						if (inst.isCurrentWhiteSpace()) {
							inst.nextChar(true);
						}
						if (inst.getCurrentChar() == ',') {
							inst.nextChar(true);
						} else if (inst.getCurrentChar() == ']') {
							foundEnd = true;
							break;
						}
					}
					if (!foundEnd) {
						return null;
					}
				}
				inst.nextChar(true);
				return new JsonArray(values);
			} else if (c == 't' || c == 'T') {
				inst.nextChar(false);
				char next = inst.getCurrentChar();
				if (next != 'r' && next != 'R') {
					return null;
				}
				inst.nextChar(false);
				next = inst.getCurrentChar();
				if (next != 'u' && next != 'U') {
					return null;
				}
				inst.nextChar(false);
				next = inst.getCurrentChar();
				if (next != 'e' && next != 'E') {
					return null;
				}
				inst.nextChar(false);
				return new JsonBoolean(true);
			} else if (c == 'f' || c == 'F') {
				inst.nextChar(false);
				char next = inst.getCurrentChar();
				if (next != 'a' && next != 'A') {
					return null;
				}
				inst.nextChar(false);
				next = inst.getCurrentChar();
				if (next != 'l' && next != 'L') {
					return null;
				}
				inst.nextChar(false);
				next = inst.getCurrentChar();
				if (next != 's' && next != 'S') {
					return null;
				}
				inst.nextChar(false);
				next = inst.getCurrentChar();
				if (next != 'e' && next != 'E') {
					return null;
				}
				inst.nextChar(false);
				return new JsonBoolean(false);
			} else {
				return null;
			}
		}
	}

	public static final class JsonString extends JsonValue {

		public final String Value;

		@Override
		public boolean isString() {
			return true;
		}

		public JsonString(String value) {
			this.Value = value;
		}

		private String getHexEscape(int codePoint) {
			String s = "0000" + Integer.toHexString(codePoint);
			return s.substring(s.length() - 4, s.length());
		}

		@Override
		public String getJsonString() {
			StringBuilder nsb = new StringBuilder();
			nsb.append('"');
			String newValue = Value == null ? "null" : Value;
			char[] chars = newValue.toCharArray();
			for (int i = 0; i < chars.length; i++) {
				int cp = Character.codePointAt(chars, i);
				char c = chars[i];
				if (c == '\n') {
					nsb.append("\\n");
				} else if (c == '\t') {
					nsb.append("\\t");
				} else if (c == '\r') {
					nsb.append("\\r");
				} else if (c == '\f') {
					nsb.append("\\f");
				} else if (c == '\b') {
					nsb.append("\\b");
				} else if (c == '\\') {
					nsb.append("\\\\");
				} else if (c == '"') {
					nsb.append("\\\"");
				} else if (cp >= 32 && cp <= 126) {
					nsb.append(c);
				} else {
					nsb.append("\\u" + getHexEscape(chars[i]));
				}
			}
			nsb.append('"');
			return nsb.toString();
		}
	}

	public static final class JsonNumber extends JsonValue {

		@Override
		public boolean isNumber() {
			return true;
		}

		public final int Value;
		private final String txtValue;

		public JsonNumber(int value) {
			this(value, value + "");
		}

		public JsonNumber(int value, String txtValue) {
			this.Value = value;
			this.txtValue = txtValue;
		}

		public long asLong() {
			return Long.parseLong(txtValue);
		}

		@Override
		public String getJsonString() {
			return txtValue;
		}

	}

	public static final class JsonBoolean extends JsonValue {

		@Override
		public boolean isBoolean() {
			return true;
		}

		public final boolean Value;

		public JsonBoolean(boolean value) {
			this.Value = value;
		}

		@Override
		public String getJsonString() {
			return Value ? "true" : "false";
		}

	}

	public static final class JsonArray extends JsonValue {

		public final List<JsonValue> Value;

		@Override
		public boolean isArray() {
			return true;
		}

		public void add(JsonValue value) {
			this.Value.add(value);
		}

		public JsonArray() {
			this(new ArrayList<JsonValue>());
		}

		public JsonArray(List<JsonValue> values) {
			this.Value = values;
		}

		@Override
		public String getJsonString() {
			StringBuilder sb = new StringBuilder();
			if (Value.size() == 0) {
				return "[]";
			} else if (Value.size() == 1) {
				return "[" + Value.get(0).getJsonString() + "]";
			} else {
				sb.append("[");
				for (JsonValue val : Value) {
					sb.append(val.getJsonString());
					sb.append(",");
				}
				sb.setLength(sb.length() - 1);
				sb.append("]");
				return sb.toString();
			}
		}

		public void add(int val) {
			add(new JsonNumber(val));
		}

		public void add(String val) {
			add(new JsonString(val));
		}
	}

	public static final class JsonObject extends JsonValue {

		private final Map<String, JsonValue> Value;

		public void add(String key, JsonValue value) {
			this.Value.put(key, value);
		}

		public JsonString getString(String key) {
			if (Value.containsKey(key)) {
				return Value.get(key).asString();
			}
			return null;
		}

		public JsonNumber getNumber(String key) {
			if (Value.containsKey(key)) {
				return Value.get(key).asNumber();
			}
			return null;
		}

		public JsonBoolean getBoolean(String key) {
			if (Value.containsKey(key)) {
				return Value.get(key).asBoolean();
			}
			return null;
		}

		public JsonArray getArray(String key) {
			if (Value.containsKey(key)) {
				return Value.get(key).asArray();
			}
			return null;
		}

		public JsonObject getObject(String key) {
			if (Value.containsKey(key)) {
				return Value.get(key).asObject();
			}
			return null;
		}

		public boolean containsString(String key) {
			if (Value.containsKey(key)) {
				JsonValue val = Value.get(key);
				if (val.isString()) {
					return true;
				}
			}
			return false;
		}

		public boolean containsObject(String key) {
			if (Value.containsKey(key)) {
				JsonValue val = Value.get(key);
				if (val.isObject()) {
					return true;
				}
			}
			return false;
		}

		public boolean containsArray(String key) {
			if (Value.containsKey(key)) {
				JsonValue val = Value.get(key);
				if (val.isArray()) {
					return true;
				}
			}
			return false;
		}

		public boolean containsBoolean(String key) {
			if (Value.containsKey(key)) {
				JsonValue val = Value.get(key);
				if (val.isBoolean()) {
					return true;
				}
			}
			return false;
		}

		public boolean containsNumber(String key) {
			if (Value.containsKey(key)) {
				JsonValue val = Value.get(key);
				if (val.isNumber()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean isObject() {
			return true;
		}

		public JsonObject(Map<String, JsonValue> values) {
			this.Value = values;
		}

		public JsonObject() {
			this(new HashMap<String, JsonValue>());
		}

		@Override
		public String getJsonString() {
			StringBuilder sb = new StringBuilder();
			if (Value.size() == 0) {
				return "{}";
			} else {
				sb.append("{");
				for (Entry<String, JsonValue> entry : Value.entrySet()) {
					sb.append("\"" + entry.getKey() + "\":");
					sb.append(entry.getValue().getJsonString());
					sb.append(",");
				}
				sb.setLength(sb.length() - 1);
				sb.append("}");
				return sb.toString();
			}
		}

		public void remove(String key) {
			if (Value.containsKey(key)) {
				Value.remove(key);
			}
		}

		public Set<Entry<String, JsonValue>> getEntries() {
			return Value.entrySet();
		}

		public boolean contains(String col) {
			return Value.containsKey(col);
		}

		public JsonValue get(String col) {
			return Value.get(col);
		}

		public void add(String key, String value) {
			add(key, new JsonString(value));
		}

		public void add(String key, int value) {
			add(key, new JsonNumber(value));
		}

		public void add(String key, boolean value) {
			add(key, new JsonBoolean(value));
		}

		public void add(String name, long value) {
			add(name, new JsonNumber(0, "" + value));
		}
	}
}
