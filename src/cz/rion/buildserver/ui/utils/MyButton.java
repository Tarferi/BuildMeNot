package cz.rion.buildserver.ui.utils;

import javax.swing.JButton;

public class MyButton extends JButton {

	public MyButton() {
		this("");
	}

	public MyButton(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
