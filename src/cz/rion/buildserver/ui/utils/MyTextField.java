package cz.rion.buildserver.ui.utils;

import javax.swing.JTextField;

public class MyTextField extends JTextField {

	public MyTextField() {
		this("");
	}

	public MyTextField(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
