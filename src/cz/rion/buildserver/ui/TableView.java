package cz.rion.buildserver.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import cz.rion.buildserver.db.SQLiteDB.Field;
import cz.rion.buildserver.db.SQLiteDB.FieldType;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.DetailsPanel.DetailsPanelCloseListener;
import cz.rion.buildserver.ui.events.UsersLoadedEvent;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserInfo;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserListLoadedListener;
import cz.rion.buildserver.ui.utils.FontProvider;
import cz.rion.buildserver.ui.utils.MyTextField;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class TableView extends JPanel implements UserListLoadedListener {

	protected static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd. MM. yyyy - HH:mm");

	private final DefaultTableCellRenderer renderBigString = new DefaultTableCellRenderer();
	private final DefaultTableCellRenderer renderRight = new DefaultTableCellRenderer();
	private final DefaultTableCellRenderer renderDefault = new DefaultTableCellRenderer();
	private TableValue[][] _data = new TableValue[0][0];
	private Field[] _fields = new Field[0];
	private MyTextField[] _filters = new MyTextField[0];
	private boolean editingTable = false;
	private final JPanel pnlFilters;

	private static final class MyFilter extends RowFilter<TableModel, Integer> {

		private Object[] filters;
		private boolean[] fast;

		private MyFilter(Object[] filters, boolean[] columnFiltering) {
			this.filters = filters;
			this.fast = columnFiltering;
		}

		@Override
		public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
			for (int i = 0; i < fast.length; i++) {
				if (fast[i]) {
					Object value = entry.getValue(i);
					Object filter = filters[i];
					if (value instanceof TableValue && filter instanceof String) {
						TableValue tvalue = (TableValue) value;
						if (!tvalue.coveredByFilter((String) filter)) {
							return false;
						}
					}
				}
			}
			return true;
		}

	}

	abstract static class TableValue implements Comparable<TableValue> {
		public final String DisplayedValue;
		public final String EditValue;

		private TableValue(String displayedValue, String editValue) {
			this.DisplayedValue = displayedValue;
			this.EditValue = editValue;
		}

		@Override
		public String toString() {
			return DisplayedValue;
		}

		public boolean coveredByFilter(String filter) {
			return DisplayedValue.toString().toLowerCase().contains(filter.toLowerCase());
		}

		protected int baseCompareTo(TableValue another) {
			return DisplayedValue.compareTo(another.DisplayedValue);
		}
	}

	private static class BigStringTableValue extends TableValue {
		public final String BigString;

		private BigStringTableValue(String displayedValue) {
			super(displayedValue.length() + " bytes", displayedValue);
			this.BigString = displayedValue;
		}

		@Override
		public int compareTo(TableValue o) {
			if (o instanceof BigStringTableValue) {
				int myLength = BigString.length();
				int otherLength = ((BigStringTableValue) o).BigString.length();
				return Integer.compare(myLength, otherLength);
			}
			return super.baseCompareTo(o);
		}
	}

	private static class StringTableValue extends TableValue {
		public final String String;

		private StringTableValue(String displayedValue) {
			super(displayedValue, displayedValue);
			this.String = displayedValue;
		}

		@Override
		public int compareTo(TableValue o) {
			if (o instanceof StringTableValue) {
				return String.compareTo(((StringTableValue) o).String);
			}
			return super.baseCompareTo(o);
		}
	}

	private static class DateTableValue extends TableValue {
		public final Date Date;

		public DateTableValue(Date date) {
			super(dateFormat.format(date), dateFormat.format(date));
			this.Date = date;
		}

		@Override
		public int compareTo(TableValue o) {
			if (o instanceof DateTableValue) {
				long myTime = Date.getTime();
				long otherTime = ((DateTableValue) o).Date.getTime();
				return Long.compare(myTime, otherTime);
			}
			return super.baseCompareTo(o);
		}
	}

	private static class NumberTableValue extends TableValue {
		public final long Value;

		private NumberTableValue(long value) {
			super(value + "", value + "");
			this.Value = value;
		}

		@Override
		public boolean coveredByFilter(String filter) {
			if (filter.startsWith(">")) {
				int val = Integer.parseInt(filter.substring(1).trim());
				return Value > val;
			} else if (filter.startsWith("<")) {
				int val = Integer.parseInt(filter.substring(1).trim());
				return Value < val;
			} else if (filter.startsWith("<")) {
				int val = Integer.parseInt(filter.substring(1).trim());
				return Value < val;
			} else if (filter.startsWith("!=")) {
				int val = Integer.parseInt(filter.substring(2).trim());
				return Value != val;

			} else {
				return DisplayedValue.toString().equals(filter);
			}
		}

		@Override
		public int compareTo(TableValue o) {
			if (o instanceof NumberTableValue) {
				long myValue = Value;
				long otherValue = ((NumberTableValue) o).Value;
				return Long.compare(myValue, otherValue);
			}
			return 0;
		}
	}

	private void executeFilters() {
		final String[] filters = new String[_filters.length];
		final boolean[] filtersBool = new boolean[_filters.length];
		boolean execute = false;
		for (int i = 0; i < filters.length; i++) {
			String value = _filters[i].getText();
			execute |= !value.isEmpty();
			filtersBool[i] = !value.isEmpty();
			filters[i] = value;
		}
		TableRowSorter<? extends TableModel> sorter = (TableRowSorter<? extends TableModel>) table.getRowSorter();
		if (sorter == null) {
			sorter = new TableRowSorter<TableModel>(table.getModel());
			table.setRowSorter(sorter);
		}
		if (execute) {
			sorter.setRowFilter(new MyFilter(filters, filtersBool));
		} else {
			sorter.setRowFilter(null);
		}
	}

	private final JTable table;

	private ShowDetailsPanelCallback detailsCB;

	private DetailsPanel pnlDetails;

	private final Map<String, UserInfo> userData = new HashMap<>();

	public static interface ShowDetailsPanelCallback {
		public void showDetails();

		public void closeDetails();
	}

	public TableView(DetailsPanel pnlDetails, ShowDetailsPanelCallback detailsCB, UIDriver driver) {
		UsersLoadedEvent.addStatusChangeListener(driver.EventManager, this);
		this.pnlDetails = pnlDetails;
		this.detailsCB = detailsCB;

		this.setLayout(new BorderLayout());
		table = new JTable();
		table.getTableHeader().setFont(FontProvider.LabelFont);

		table.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				updateFiltersLocation();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				updateFiltersLocation();
			}
		});

		JPanel tblPnl = new JPanel();
		tblPnl.setLayout(new BorderLayout());
		JScrollPane tblScroll = new JScrollPane(table);
		tblScroll.getVerticalScrollBar().getUnitIncrement(16);
		tblPnl.add(table.getTableHeader(), BorderLayout.NORTH);
		tblPnl.add(tblScroll, BorderLayout.CENTER);

		this.add(tblPnl, BorderLayout.CENTER);

		pnlFilters = new JPanel();
		Dimension dim = new Dimension(640, 32); // Only height really matters
		pnlFilters.setPreferredSize(dim);
		this.add(pnlFilters, BorderLayout.NORTH);

		table.setFont(FontProvider.LabelFont);
		// table.getTableHeader().setFont(FontProvider.LabelFont);
		renderRight.setHorizontalAlignment(SwingConstants.RIGHT);
		renderBigString.setHorizontalAlignment(SwingConstants.CENTER);
		((DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

		table.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent mouseEvent) {
				if (mouseEvent.getClickCount() == 2 && table.getSelectedRow() != -1) {
					int row = table.rowAtPoint(mouseEvent.getPoint());
					row = table.convertRowIndexToModel(row);
					if (row >= 0 && row < _data.length) {
						showDetails(row);
					}
				}
			}
		});
	}

	protected void updateFiltersLocation() {
		pnlFilters.setLayout(null);
		int pos = 0;
		for (int i = 0; i < _fields.length; i++) {
			int width = table.getColumnModel().getColumn(i).getWidth();
			_filters[i].setLocation(pos, 0);
			_filters[i].setSize(width + 2, 30);
			pos += width;
		}
		Dimension dim = new Dimension(pos, 30);
		pnlFilters.setSize(dim);
		pnlFilters.setPreferredSize(dim);
		pnlFilters.setVisible(_data.length > 0);
	}

	private void showDetails(int row) {
		pnlDetails.reset(_fields, _data[row], new DetailsPanelCloseListener() {

			@Override
			public void close() {
				showData();
			}
		}, dateFormat, editingTable);
		detailsCB.showDetails();
		redraw();
	}

	public void showData() {
		detailsCB.closeDetails();
		redraw();
	}

	private void redraw() {
		this.invalidate();
		this.revalidate();
		this.repaint();
	}

	private TableValue fromString(String colData, Field f) {
		if (f.IsDate()) {
			long val = Long.parseLong(colData.toString());
			if ((val + "").toString().length() < "1000000000000".length()) { // int -> long
				val *= 1000;
			}
			return new DateTableValue(new Date(val));
		} else if (f.IsBigString()) {
			return new BigStringTableValue(colData);
		} else if (f.type == FieldType.INT) {
			return new NumberTableValue(Long.parseLong(colData));
		} else if (f.type == FieldType.STRING) {
			return new StringTableValue(colData);
		} else {
			return new StringTableValue("Unknown: " + colData); // Should never happen
		}
	}

	private TableValue[][] parse(Field[] fields, JsonArray datajsn) {
		TableValue[][] data = new TableValue[datajsn.Value.size()][fields.length];
		int index = 0;
		for (JsonValue row : datajsn.Value) {
			if (row.isArray()) {
				JsonArray rw = row.asArray();
				for (int colIndex = 0; colIndex < fields.length; colIndex++) {
					Field f = fields[colIndex];
					JsonValue colVal = rw.Value.get(colIndex);
					String colData = "";
					if (colVal.isNumber()) {
						colData = colVal.asNumber().asLong() + "";
					} else if (colVal.isString()) {
						colData = colVal.asString().Value;
					} else {
						colData = colVal.getJsonString();
					}
					data[index][colIndex] = fromString(colData, f);
				}
				index++;
			} else if (row.isObject()) {
				JsonObject rw = row.asObject();
				for (int colIndex = 0; colIndex < fields.length; colIndex++) {
					Field f = fields[colIndex];
					JsonValue colVal = rw.get(f.name);
					if (colVal == null) { // Shuold never happen
						return null;
					}
					String colData = "";
					if (colVal.isNumber()) {
						colData = colVal.asNumber().asLong() + "";
					} else if (colVal.isString()) {
						colData = colVal.asString().Value;
					} else {
						colData = colVal.getJsonString();
					}
					data[index][colIndex] = fromString(colData, f);
				}
				index++;
			}
		}
		return data;
	}

	private void parseTable(JsonValue val) {
		String[] headers = new String[0];
		TableValue[][] data = new TableValue[0][0];
		Field[] fields = new Field[0];
		if (val.isObject()) {
			JsonObject vobj = val.asObject();
			if (vobj.containsArray("columns") && vobj.containsArray("data")) {
				JsonArray cols = vobj.getArray("columns");
				JsonArray datajsn = vobj.getArray("data");

				headers = new String[cols.Value.size()];
				fields = new Field[cols.Value.size()];
				int totalCols = cols.Value.size();
				for (int colIndex = 0; colIndex < totalCols; colIndex++) {
					fields[colIndex] = Field.fromDecodableRepresentation(cols.Value.get(colIndex).asString().Value);
					headers[colIndex] = fields[colIndex].name;
				}
				data = parse(fields, datajsn);
				if (data == null) {
					return;
				}
			}
		}
		this._data = data;
		this._fields = fields;
	}

	private final class PositionedField {
		public final Field Field;
		public final int Position;

		private PositionedField(Field f, int pos) {
			this.Field = f;
			this.Position = pos;
		}
	}

	private void parseView(JsonValue val) {
		String[] headers = new String[0];
		TableValue[][] data = new TableValue[0][0];
		Field[] fields = new Field[0];
		List<Field> loginFields = new ArrayList<>();
		if (val.isObject()) {
			JsonObject vobj = val.asObject();
			if (vobj.containsArray("result") && vobj.containsString("SQL") && vobj.containsNumber("code")) {
				int code = vobj.getNumber("code").Value;
				if (code == 0) {
					JsonArray result = vobj.getArray("result");
					String sql = vobj.getString("SQL").Value;

					// Extract fields
					List<PositionedField> flds = new ArrayList<>();
					Matcher matcher = LayeredDBFileWrapperDB.FreeSQLSyntaxMatcher.matcher(sql);
					while (matcher.find()) {
						String fn = matcher.group(1);
						String field = matcher.group(2);
						int position = sql.indexOf(fn + "(" + field + ")");
						Field f = null;
						if (fn.equals("TEXT")) {
							f = new Field(field, "", FieldType.STRING);
						} else if (fn.equals("BIGTEXT")) {
							f = new Field(field, "", FieldType.BIGSTRING);
						} else if (fn.equals("INT")) {
							f = new Field(field, "", FieldType.INT);
						} else if (fn.equals("DATE")) {
							f = new Field(field, "", FieldType.DATE);
						} else if (fn.equals("LOGIN")) {
							f = new Field(field, "", FieldType.STRING);
							loginFields.add(f);
						} else {
							return;
						}
						flds.add(new PositionedField(f, position));
					}
					flds.sort(new Comparator<PositionedField>() {

						@Override
						public int compare(PositionedField o1, PositionedField o2) {
							return Integer.compare(o1.Position, o2.Position);
						}

					});
					fields = new Field[flds.size()];
					headers = new String[flds.size()];
					for (int i = 0; i < fields.length; i++) {
						fields[i] = flds.get(i).Field;
						headers[i] = fields[i].name;
					}
					data = parse(fields, result);
					if (data == null) {
						return;
					}
				}
			}
		}
		this._data = data;
		this._fields = fields;
		if (!loginFields.isEmpty()) {
			addLogins(loginFields);
		}
	}

	private void addLogins(List<Field> loginFields) {
		if (userData.isEmpty()) { // Only works with loaded user list
			return;
		}
		int totalLoginsToAdd = loginFields.size();
		int additionalFieldsPerLogin = 3;
		Field[] fields = new Field[this._fields.length + (totalLoginsToAdd * additionalFieldsPerLogin)];
		TableValue[][] data = new TableValue[_data.length][fields.length];

		// Find login fields in new header indexes
		int remapping[] = new int[this._fields.length]; // Index is current index, value is new index
		int[] newLoginIndexes = new int[totalLoginsToAdd];

		int foundIndexes = 0;
		for (int i = 0; i < this._fields.length; i++) {
			Field f = _fields[i];
			remapping[i] = i + (foundIndexes * additionalFieldsPerLogin); // Remapping, done always
			if (loginFields.contains(f)) { // is Login field
				newLoginIndexes[foundIndexes] = i;
				foundIndexes++;
			}
		}

		// Update data
		for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {

			// Move existing data -> make space for login fields
			for (int colIndex = 0; colIndex < _fields.length; colIndex++) {
				int newColIndex = remapping[colIndex];
				data[rowIndex][newColIndex] = _data[rowIndex][colIndex];
			}

			// Add login fields
			for (int loginFieldIndex : newLoginIndexes) {
				String name = "???";
				String group = "???";
				String permGroup = "???";

				TableValue loginValue = data[rowIndex][loginFieldIndex];
				if (loginValue instanceof StringTableValue) {
					String login = loginValue.DisplayedValue;
					if (userData.containsKey(login.toLowerCase())) {
						UserInfo user = userData.get(login.toLowerCase());
						name = user.FullName;
						group = user.Group;
						permGroup = user.PermissionGroup;
						String[] pd = permGroup.split("\\.");
						permGroup = pd[pd.length == 0 ? 0 : pd.length - 1];
					}
				}

				data[rowIndex][loginFieldIndex + 1] = new StringTableValue(name);
				data[rowIndex][loginFieldIndex + 2] = new StringTableValue(group);
				data[rowIndex][loginFieldIndex + 3] = new StringTableValue(permGroup);
			}
		}

		// Update fields
		for (int colIndex = 0; colIndex < _fields.length; colIndex++) {
			int newColIndex = remapping[colIndex];
			fields[newColIndex] = _fields[colIndex];
		}

		int loginIndex = 0;
		for (int loginFieldIndex : newLoginIndexes) {
			Field loginField = loginFields.get(loginIndex);
			fields[loginFieldIndex + 1] = new Field(loginField.name + "_FullName", "", FieldType.STRING);
			fields[loginFieldIndex + 2] = new Field(loginField.name + "_Group", "", FieldType.STRING);
			fields[loginFieldIndex + 3] = new Field(loginField.name + "_PermGroup", "", FieldType.STRING);
			loginIndex++;
		}

		this._data = data;
		this._fields = fields;
	}

	private void projectTable() {

		table.setRowHeight(32);

		table.setModel(new TableModel() {

			@Override
			public int getRowCount() {
				return _data.length;
			}

			@Override
			public int getColumnCount() {
				return _fields.length;
			}

			@Override
			public String getColumnName(int columnIndex) {
				return _fields[columnIndex].name;
			}

			@Override
			public Class<?> getColumnClass(int columnIndex) {
				return TableValue.class;
			}

			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex) {
				return false;
			}

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				return _data[rowIndex][columnIndex];
			}

			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			}

			@Override
			public void addTableModelListener(TableModelListener l) {
			}

			@Override
			public void removeTableModelListener(TableModelListener l) {
			}

		});

		TableRowSorter<? extends TableModel> sorter = (TableRowSorter<? extends TableModel>) table.getRowSorter();

		for (int i = 0; i < this._fields.length; i++) {
			Field f = _fields[i];
			DefaultTableCellRenderer r = renderDefault;
			if (f.IsBigString()) {
				r = renderBigString;
			} else if (f.IsDate()) {
				r = renderRight;
			}
			table.getColumnModel().getColumn(i).setCellRenderer(r);
			sorter.setComparator(i, new Comparator<TableValue>() {

				@Override
				public int compare(TableValue o1, TableValue o2) {
					return o1.compareTo(o2);
				}
			});
		}

		_filters = new MyTextField[_fields.length];

		pnlFilters.removeAll();
		for (int i = 0; i < _filters.length; i++) {
			_filters[i] = new MyTextField();
			pnlFilters.add(_filters[i]);
			_filters[i].getDocument().addDocumentListener(new DocumentListener() {

				@Override
				public void insertUpdate(DocumentEvent e) {
					executeFilters();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					executeFilters();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					executeFilters();
				}
			});
		}

		updateFiltersLocation();
		executeFilters();
	}

	public void setData(String contents, boolean isView) {
		editingTable = !isView;
		showData();
		JsonValue val = JsonValue.parse(contents);

		if (val != null) {
			if (isView) {
				parseView(val);
			} else {
				parseTable(val);
			}
		}
		projectTable();

	}

	public void clearData() {
		_data = new TableValue[0][0];
		_fields = new Field[0];
		projectTable();
	}

	@Override
	public void userListLoaded(List<UserInfo> users) {
		userData.clear();
		for (UserInfo user : users) {
			userData.put(user.Login.toLowerCase(), user);
		}
	}

}
