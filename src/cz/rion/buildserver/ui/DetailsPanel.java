package cz.rion.buildserver.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import cz.rion.buildserver.db.SQLiteDB.Field;
import cz.rion.buildserver.ui.utils.MyLabel;
import cz.rion.buildserver.ui.utils.MyTextArea;
import cz.rion.buildserver.ui.utils.MyTextField;
import net.miginfocom.swing.MigLayout;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class DetailsPanel extends JPanel {

	protected static interface DetailsPanelCloseListener {
		public void close();
	}

	List<MyTextArea> areas = new ArrayList<>();

	public DetailsPanel(Field[] headers, String[] data, final DetailsPanelCloseListener detailsPanelCloseListener) {
		addComponentListener(new ComponentAdapter() {
			private final Dimension dim = new Dimension(100, 100);

			@Override
			public void componentResized(ComponentEvent e) {
				for (MyTextArea area : areas) {
					// Forced recalculation (could be more effective
					area.setSize(100, 100);
					area.setPreferredSize(dim);
				}
			}
		});
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].IsBigString) {
				sb.append("[][250px::250px]");
			} else {
				sb.append("[]");
			}
		}
		sb.append("[grow]");
		setLayout(new MigLayout("", "[:100px:][grow][:100px:]", sb.toString()));
		int totalDoubleTextsPlaced = 0;
		for (int i = 0; i < headers.length; i++) {
			add(new MyLabel(headers[i].name + ":"), "cell 0 " + (i + totalDoubleTextsPlaced) + ",alignx right");
			if (headers[i].IsBigString) {
				String dataEditPos = "cell 1 " + (i + totalDoubleTextsPlaced) + " 2 2,growx,growy";
				MyTextArea area = new MyTextArea(data[i]);
				areas.add(area);
				area.setLineWrap(true);
				area.setEditable(false);
				JScrollPane scroller = new JScrollPane(area);
				scroller.getVerticalScrollBar().setUnitIncrement(16);
				add(scroller, dataEditPos);
				totalDoubleTextsPlaced++;
			} else {
				String dataEditPos = "cell 1 " + (i + totalDoubleTextsPlaced) + " 2 1,growx,growy";
				String str = data[i];
				if (headers[i].IsDate) {
					long val = Long.parseLong(str);
					if ((val + "").toString().length() < "1000000000000".length()) { // int -> long
						val *= 1000;
					}
					str = TableView.dateFormat.format(new Date(val));
				}

				MyTextField textField = new MyTextField(str);
				textField.setEditable(false);
				add(textField, dataEditPos);
			}
		}
	}
}
