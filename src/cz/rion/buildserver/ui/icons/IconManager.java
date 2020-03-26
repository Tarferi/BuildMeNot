package cz.rion.buildserver.ui.icons;

import javax.swing.ImageIcon;

public class IconManager {

	public final static ImageIcon FolderIcon = IconLoader.LoadIcon("folder.png");
	public final static ImageIcon CFGIcon = IconLoader.LoadIcon("cfg.png");
	public final static ImageIcon JSONIcon = IconLoader.LoadIcon("json.png");
	public final static ImageIcon DBIcon = IconLoader.LoadIcon("db.png");
	public final static ImageIcon FileIcon = IconLoader.LoadIcon("file.png");
	public final static ImageIcon UpIcon = IconLoader.LoadIcon("folder.png");
	public final static ImageIcon TableIcon = IconLoader.LoadIcon("table.png");
	public final static ImageIcon ViewIcon = IconLoader.LoadIcon("view.png");

	public static final ImageIcon IconForFile(String path) {
		path = path.toLowerCase();
		if (path.endsWith(".jsn") || path.endsWith(".json")) {
			return JSONIcon;
		} else if (path.endsWith("..")) {
			return UpIcon;
		} else if (path.endsWith(".cfg")) {
			return CFGIcon;
		} else if (path.endsWith(".db")) {
			return DBIcon;
		} else if (path.endsWith(".table")) {
			return TableIcon;
		} else if (path.endsWith(".view")) {
			return ViewIcon;
		} else { // Coult be a folder
			int pos1 = path.lastIndexOf("/");
			int pos2 = path.lastIndexOf(".");
			if (pos2 == -1 || pos1 > pos2) {
				return FolderIcon;
			}
		}
		return FileIcon;
	}
}
