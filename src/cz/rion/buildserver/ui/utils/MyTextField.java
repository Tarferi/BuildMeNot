package cz.rion.buildserver.ui.utils;

import javax.swing.JTextField;

public class MyTextField extends JTextField {
	
	private static final long serialVersionUID = 1L;
	
	public MyTextField() {
		this("");
	}

	public MyTextField(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
