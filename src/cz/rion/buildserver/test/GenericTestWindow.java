package cz.rion.buildserver.test;

public class GenericTestWindow {

	public final String Title;
	public final String Contents;
	public final String Label;
	public final String ID;

	public GenericTestWindow(String id, String title, String contents, String label) {
		this.ID = id;
		this.Title = title;
		this.Contents = contents;
		this.Label = label;
	}
}
