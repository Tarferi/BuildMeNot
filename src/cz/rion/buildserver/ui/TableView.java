package cz.rion.buildserver.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import cz.rion.buildserver.ui.utils.FontProvider;
import cz.rion.buildserver.ui.utils.MyTextField;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class TableView extends JPanel {

	protected static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd. MM. yyyy - HH:mm");

	private final DefaultTableCellRenderer renderBigString = new DefaultTableCellRenderer();
	private final DefaultTableCellRenderer renderRight = new DefaultTableCellRenderer();
	private final DefaultTableCellRenderer renderDefault = new DefaultTableCellRenderer();
	private String[] _headers = new String[0];
	private String[][] _data = new String[0][0];
	private Field[] _fields = new Field[0];
	private MyTextField[] _filters = new MyTextField[0];
	private boolean editingTable = false;
	private final JPanel pnlFilters;

	private static final class MyFilter extends RowFilter<TableModel, Integer> {

		private String[] filters;
		private boolean[] fast;

		private MyFilter(String[] filters, boolean[] columnFiltering) {
			this.filters = filters;
			this.fast = columnFiltering;
		}

		@Override
		public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
			for (int i = 0; i < fast.length; i++) {
				if (fast[i]) {
					String value = entry.getValue(i).toString();
					String filter = filters[i];
					if (!value.contains(filter)) {
						return false;
					}
				}
			}
			return true;
		}

	}

	private void executeFilters() {
		final String[] filters = new String[_filters.length];
		final boolean[] filtersBool = new boolean[_filters.length];
		boolean execute = false;
		for (int i = 0; i < filters.length; i++) {
			filters[i] = _filters[i].getText();
			execute |= !filters[i].isEmpty();
			filtersBool[i] = !filters[i].isEmpty();
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

	public static interface ShowDetailsPanelCallback {
		public void showDetails();

		public void closeDetails();
	}

	public TableView(DetailsPanel pnlDetails, ShowDetailsPanelCallback detailsCB) {
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
		for (int i = 0; i < _headers.length; i++) {
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

	private String[][] parse(Field[] fields, JsonArray datajsn) {
		String[][] data = new String[datajsn.Value.size()][fields.length];
		int index = 0;
		for (JsonValue row : datajsn.Value) {
			if (row.isArray()) {
				JsonArray rw = row.asArray();
				for (int colIndex = 0; colIndex < fields.length; colIndex++) {
					JsonValue colVal = rw.Value.get(colIndex);
					String colData = "";
					if (colVal.isNumber()) {
						colData = colVal.asNumber().asLong() + "";
					} else if (colVal.isString()) {
						colData = colVal.asString().Value;
					} else {
						colData = colVal.getJsonString();
					}
					data[index][colIndex] = colData;
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
					data[index][colIndex] = colData;
				}
				index++;
			}
		}
		return data;
	}

	private void parseTable(JsonValue val) {
		String[] headers = new String[0];
		String[][] data = new String[0][0];
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
		this._headers = headers;
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
		String[][] data = new String[0][0];
		Field[] fields = new Field[0];
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
		this._headers = headers;
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
				return _headers.length;
			}

			@Override
			public String getColumnName(int columnIndex) {
				return _headers[columnIndex];
			}

			@Override
			public Class<?> getColumnClass(int columnIndex) {
				return String.class;
			}

			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex) {
				return false;
			}

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (_fields[columnIndex].IsDate()) {
					long val = Long.parseLong(_data[rowIndex][columnIndex]);
					if ((val + "").toString().length() < "1000000000000".length()) { // int -> long
						val *= 1000;
					}
					return dateFormat.format(new Date(val));
				} else if (_fields[columnIndex].IsBigString()) {
					return _data[rowIndex][columnIndex].length() + " bytes";
				}
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

		for (int i = 0; i < this._headers.length; i++) {
			Field f = _fields[i];
			DefaultTableCellRenderer r = renderDefault;
			if (f.IsBigString()) {
				r = renderBigString;
			} else if (f.IsDate()) {
				r = renderRight;
			}
			table.getColumnModel().getColumn(i).setCellRenderer(r);
		}

		_filters = new MyTextField[_headers.length];

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
		_headers = new String[0];
		_data = new String[0][0];
		_fields = new Field[0];
		projectTable();
	}

}
