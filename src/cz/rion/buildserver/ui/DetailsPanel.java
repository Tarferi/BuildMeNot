package cz.rion.buildserver.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import cz.rion.buildserver.db.SQLiteDB.Field;
import cz.rion.buildserver.db.SQLiteDB.FieldType;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.ui.utils.MyLabel;
import cz.rion.buildserver.ui.utils.MyTextArea;
import cz.rion.buildserver.ui.utils.MyTextField;
import net.miginfocom.swing.MigLayout;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DetailsPanel extends JPanel {

	protected static interface DetailsPanelCloseListener {
		public void close();
	}

	List<MyTextArea> areas = new ArrayList<>();
	private Field[] headers;
	private SimpleDateFormat format;
	private ComponentManipulator[] retrievers;

	private interface ComponentManipulator {
		public String getText();

		public void setEnabled(boolean enabled);
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (retrievers != null) {
			for (ComponentManipulator retriever : retrievers) {
				retriever.setEnabled(enabled);
			}
		}
	}

	public void reset(Field[] headers, String[] data, final DetailsPanelCloseListener detailsPanelCloseListener, SimpleDateFormat format, boolean editingTable) {
		this.headers = headers;
		this.format = format;
		this.retrievers = new ComponentManipulator[data.length];
		this.removeAll();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].IsBigString()) {
				sb.append("[][250px::250px]");
			} else {
				sb.append("[]");
			}
		}
		setLayout(new MigLayout("", "[:100px:][grow][:100px:]", sb.toString()));
		int totalDoubleTextsPlaced = 0;
		for (int i = 0; i < headers.length; i++) {
			add(new MyLabel(headers[i].name + ":"), "cell 0 " + (i + totalDoubleTextsPlaced) + ",alignx right");
			boolean editable = !headers[i].name.equals("ID") && editingTable;
			if (headers[i].IsBigString()) {
				String dataEditPos = "cell 1 " + (i + totalDoubleTextsPlaced) + " 2 2,growx,growy";
				final MyTextArea area = new MyTextArea(data[i]);
				retrievers[i] = new ComponentManipulator() {

					@Override
					public String getText() {
						return area.getText();
					}

					@Override
					public void setEnabled(boolean enabled) {
						area.setEnabled(enabled);
					}
				};

				areas.add(area);
				area.setLineWrap(true);
				area.setEditable(editable);
				JScrollPane scroller = new JScrollPane(area);
				scroller.getVerticalScrollBar().setUnitIncrement(16);
				scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
				add(scroller, dataEditPos);
				totalDoubleTextsPlaced++;
			} else {
				String dataEditPos = "cell 1 " + (i + totalDoubleTextsPlaced) + " 2 1,growx,growy";
				String str = data[i];
				if (headers[i].IsDate()) {
					long val = Long.parseLong(str);
					if ((val + "").toString().length() < "1000000000000".length()) { // int -> long
						val *= 1000;
					}
					str = TableView.dateFormat.format(new Date(val));
				}

				final MyTextField textField = new MyTextField(str);

				retrievers[i] = new ComponentManipulator() {

					@Override
					public String getText() {
						return textField.getText();
					}

					@Override
					public void setEnabled(boolean enabled) {
						textField.setEnabled(enabled);
					}
				};

				textField.setEditable(editable);
				add(textField, dataEditPos);
			}
		}
	}

	public JsonObject collectValues() {
		JsonObject obj = new JsonObject();
		for (int i = 0; i < headers.length; i++) {
			Field f = headers[i];
			String d = retrievers[i].getText();
			if (f.IsBigString() || f.type == FieldType.STRING) {
				obj.add(f.name, new JsonString(d));
			} else if (f.IsDate()) {
				try {
					Date val = format.parse(d);
					obj.add(f.name, new JsonNumber((int) val.getTime(), val.getTime() + ""));
				} catch (ParseException e) {
					return null;
				}
			} else if (f.type == FieldType.INT) {
				try {
					obj.add(f.name, new JsonNumber(Integer.parseInt(d), d));
				} catch (Exception e) { // Not an integer
					return null;
				}
			}
		}
		return obj;
	}
}
