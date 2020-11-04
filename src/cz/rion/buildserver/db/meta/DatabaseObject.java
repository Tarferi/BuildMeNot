package cz.rion.buildserver.db.meta;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.db.SQLiteDB.FieldType;
import cz.rion.buildserver.db.SQLiteDB.TableField;
import cz.rion.buildserver.db.SQLiteDB.ValuedField;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

@Retention(RetentionPolicy.RUNTIME)
public @interface DatabaseObject {

	public String TableName();

	@Retention(RetentionPolicy.RUNTIME)
	public @interface BIGTEXT {

	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface KEY {

	}

	public static class MetaTable<T> {
		private Class<?> cls;

		private final MetaTableField[] fields;

		private LayeredMetaDB db;

		private boolean isPrimitiveInt(Class<?> c) {
			return c.isPrimitive() && c.getName().equals("int");
		}

		private boolean isPrimitiveLong(Class<?> c) {
			return c.isPrimitive() && c.getName().equals("long");
		}

		private FieldType getType(Field f) {
			Class<?> cls = f.getType();
			if (cls == Integer.class || isPrimitiveInt(cls)) {
				return FieldType.INT;
			} else if (cls == Long.class || isPrimitiveLong(cls)) {
				return FieldType.DATE;
			} else if (cls == String.class) {
				for (Annotation an : f.getAnnotations()) {
					if (an.annotationType() == DatabaseObject.class) {
						return FieldType.BIGSTRING;
					}
				}
				return FieldType.STRING;
			}
			throw new RuntimeException("Failed to determine field type for " + cls.getCanonicalName());
		}

		public MetaTable(Class<T> cls, LayeredMetaDB db) throws DatabaseException {
			try {
				this.cls = cls;
				this.db = db;

				Field[] f = this.cls.getDeclaredFields();

				fields = new MetaTableField[f.length];
				for (int i = 0; i < f.length; i++) {
					boolean key = false;
					Annotation a = f[i].getClass().getAnnotation(DatabaseObject.KEY.class);
					if (a != null || f[i].getName().toLowerCase().equals("id")) {
						key = true;
					}
					fields[i] = new MetaTableField(f[i].getName(), key, getType(f[i]));
				}
			} catch (Exception e) {
				throw new DatabaseException("Failed to construct database object", e);
			}
		}

		public boolean insert(T entry) throws DatabaseException {
			try {

				final String tableName = getTableName();

				int fieldsLength = 0;
				for (int i = 0; i < fields.length; i++) {
					if (!fields[i].IsKey) {
						fieldsLength++;
					}
				}

				final ValuedField[] tfields = new ValuedField[fieldsLength];
				int o = 0;
				for (int i = 0; i < fields.length; i++) {
					if (!fields[i].IsKey) {
						tfields[o] = new ValuedField(db.getField(tableName, fields[i].Name), fields[i].get(entry));
						o++;
					}
				}

				return db.insert(tableName, tfields);

			} catch (Exception e) {
				throw new DatabaseException("Failed to construct database object", e);
			}
		}

		@SuppressWarnings("unchecked")
		private T get(JsonValue val) {
			if (val.isObject()) {
				Object inst;
				try {
					inst = cls.getConstructor().newInstance();
					for (MetaTableField f : fields) {
						if (!f.isIn(val.asObject())) {
							throw new RuntimeException("Failed to create instance of database object. Missing field: " + f.Name);
						}
						Object value = f.get(val.asObject());
						Field df = cls.getDeclaredField(f.Name);
						boolean acc = df.canAccess(inst);
						df.setAccessible(true);
						df.set(inst, value);
						df.setAccessible(acc);
					}
					return (T) inst;
				} catch (Exception e) {
					throw new RuntimeException("Failed to create instance of database object.", e);
				}
			}
			throw new RuntimeException("Failed to create instance of database object");
		}

		private String getTableName() {
			Annotation a = cls.getAnnotation(DatabaseObject.class);
			if (a != null) {
				return ((DatabaseObject) a).TableName();
			}
			throw new RuntimeException("Missing annotation for database object class");
		}

		public List<T> get() throws DatabaseException {
			try {
				final String tableName = getTableName();
				final TableField[] tfields = new TableField[fields.length];
				for (int i = 0; i < fields.length; i++) {
					tfields[i] = db.getField(tableName, fields[i].Name);
				}
				JsonArray value = db.select(tableName, tfields, true);
				List<T> lst = new ArrayList<>();
				for (JsonValue val : value.Value) {
					T v = get(val);
					lst.add(v);
				}
				return lst;
			} catch (Exception e) {
				throw new DatabaseException("Failed to construct database object data", e);
			}
		}
	}

	public static class MetaTableField {
		public final boolean IsKey;
		public final String Name;
		public final FieldType Type;

		private MetaTableField(String name, boolean isKey, FieldType type) {
			this.Name = name;
			this.IsKey = isKey;
			this.Type = type;
		}

		public Object get(Object instance) {
			try {
				Object value = null;
				Field f = instance.getClass().getDeclaredField(Name);
				boolean acc = f.canAccess(instance);
				f.setAccessible(true);
				value = f.get(instance);
				f.setAccessible(acc);
				return value;
			} catch (Exception e) {
				throw new RuntimeException("Failed to access value", e);
			}
		}

		public boolean isIn(JsonObject obj) {
			if (Type == FieldType.BIGSTRING || Type == FieldType.STRING) {
				return obj.containsString(Name);
			} else if (Type == FieldType.DATE || Type == FieldType.INT) {
				return obj.containsNumber(Name);
			}
			return false;
		}

		public Object get(JsonObject obj) {
			if (Type == FieldType.BIGSTRING || Type == FieldType.STRING) {
				return obj.getString(Name).Value;
			} else if (Type == FieldType.DATE) {
				return obj.getNumber(Name).asLong();
			} else if (Type == FieldType.INT) {
				return obj.getNumber(Name).Value;
			}
			return false;
		}
	}

}
