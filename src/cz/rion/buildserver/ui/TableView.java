package cz.rion.buildserver.ui;

import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.utils.FontProvider;

public class TableView extends JTable {

	public TableView() {
		this.setFont(FontProvider.LabelFont);
	}

	public void setData(String contents) {
		JsonValue val = JsonValue.parse(contents);
		String[] headers = new String[0];
		String[][] data = new String[0][0];

		if (val != null) {
			if (val.isObject()) {
				JsonObject vobj = val.asObject();
				if (vobj.containsArray("columns") && vobj.containsArray("data")) {
					JsonArray cols = vobj.getArray("columns");
					JsonArray datajsn = vobj.getArray("data");
					headers = new String[cols.Value.size()];
					data = new String[datajsn.Value.size()][cols.Value.size()];
					int totalCols = cols.Value.size();
					for (int colIndex = 0; colIndex < totalCols; colIndex++) {
						headers[colIndex] = cols.Value.get(colIndex).asString().Value;
					}
					int index = 0;
					for (JsonValue row : datajsn.Value) {
						JsonArray rw = row.asArray();
						for (int colIndex = 0; colIndex < totalCols; colIndex++) {
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
					}
				}
			}
		}
		final String[] _headers = headers;
		final String[][] _data = data;

		this.setModel(new TableModel() {

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
		for (int i = 0; i < _headers.length; i++) {
			//this.columnModel.getColumn(i).setMaxWidth(120);
		}
	}

}
