package cz.rion.buildserver.ui.utils;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

public class BetterListCellRenderer extends DefaultListCellRenderer {

	private JLabel label;

	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		label.setFont(list.getFont());
		return label;
	}
}
