package cz.rion.buildserver.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import cz.rion.buildserver.db.SQLiteDB.Field;
import cz.rion.buildserver.db.layers.LayeredDBFileWrapperDB;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.DetailsPanel.DetailsPanelCloseListener;
import cz.rion.buildserver.ui.utils.FontProvider;

public class TableView extends JTable {

	protected static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd. MM. yyyy - HH:mm");

	private final DefaultTableCellRenderer renderRight = new DefaultTableCellRenderer();
	private final DefaultTableCellRenderer renderDefault = new DefaultTableCellRenderer();
	private String[] _headers = new String[0];
	private String[][] _data = new String[0][0];
	private Field[] _fields = new Field[0];

	private ShowDetailsPanelCallback detailsCB;

	public static interface ShowDetailsPanelCallback {
		public void showDetails(JPanel panel);

		public void closeDetails();
	}

	public TableView(ShowDetailsPanelCallback detailsCB) {
		this.detailsCB = detailsCB;
		setFont(FontProvider.LabelFont);
		// table.getTableHeader().setFont(FontProvider.LabelFont);
		renderRight.setHorizontalAlignment(SwingConstants.RIGHT);

		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent mouseEvent) {
				if (mouseEvent.getClickCount() == 2 && getSelectedRow() != -1) {
					int row = rowAtPoint(mouseEvent.getPoint());
					if (row >= 0 && row < _data.length) {
						showDetails(row);
					}
				}
			}
		});
	}

	private void showDetails(int row) {
		JPanel pnl = new DetailsPanel(_fields, _data[row], new DetailsPanelCloseListener() {

			@Override
			public void close() {
				showData();
			}
		});
		detailsCB.showDetails(pnl);
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
							f = new Field(field, "", false, false);
						} else if (fn.equals("BIGTEXT")) {
							f = new Field(field, "", false, true);
						} else if (fn.equals("INT")) {
							f = new Field(field, "", false, false);
						} else if (fn.equals("DATE")) {
							f = new Field(field, "", true, false);
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

	public void setData(String contents, boolean isView) {
		showData();
		JsonValue val = JsonValue.parse(contents);

		if (val != null) {
			if (isView) {
				parseView(val);
			} else {
				parseTable(val);
			}
		}

		setModel(new TableModel() {

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
				if (_fields[columnIndex].IsDate) {
					long val = Long.parseLong(_data[rowIndex][columnIndex]);
					if ((val + "").toString().length() < "1000000000000".length()) { // int -> long
						val *= 1000;
					}
					return dateFormat.format(new Date(val));
				} else if (_fields[columnIndex].IsBigString) {
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
			if (f.IsBigString) {
				r = renderRight;
			}
			getColumnModel().getColumn(i).setCellRenderer(r);
		}

	}

}
