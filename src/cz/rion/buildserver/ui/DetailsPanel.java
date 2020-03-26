package cz.rion.buildserver.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import cz.rion.buildserver.db.SQLiteDB.Field;
import cz.rion.buildserver.ui.utils.MyButton;
import cz.rion.buildserver.ui.utils.MyLabel;
import cz.rion.buildserver.ui.utils.MyTextArea;
import cz.rion.buildserver.ui.utils.MyTextField;
import net.miginfocom.swing.MigLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class DetailsPanel extends JPanel {

	protected static interface DetailsPanelCloseListener {
		public void close();
	}

	public DetailsPanel(Field[] headers, String[] data, final DetailsPanelCloseListener detailsPanelCloseListener) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].IsBigString) {
				sb.append("[][250px::250px]");
			} else {
				sb.append("[]");
			}
		}
		sb.append("[grow][]");
		setLayout(new MigLayout("", "[][grow][]", sb.toString()));
		int totalDoubleTextsPlaced = 0;
		for (int i = 0; i < headers.length; i++) {
			add(new MyLabel(headers[i].name + ":"), "cell 0 " + (i + totalDoubleTextsPlaced) + ",alignx right");
			if (headers[i].IsBigString) {
				String dataEditPos = "cell 1 " + (i + totalDoubleTextsPlaced) + " 2 2,growx,growy";
				MyTextArea area = new MyTextArea(data[i]);
				area.setEditable(false);
				JScrollPane scroller = new JScrollPane(area);
				scroller.getVerticalScrollBar().setUnitIncrement(16);
				add(scroller, dataEditPos);
				totalDoubleTextsPlaced++;
			} else {
				String dataEditPos = "cell 1 " + (i + totalDoubleTextsPlaced) + " 2 1,growx,growy";
				MyTextField textField = new MyTextField(data[i]);
				textField.setEditable(false);
				add(textField, dataEditPos);
			}
		}

		MyButton btnClose = new MyButton("Return");
		btnClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				detailsPanelCloseListener.close();
			}
		});
		add(btnClose, "cell 2 " + (headers.length + totalDoubleTextsPlaced + 2));
	}
}
