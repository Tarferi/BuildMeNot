package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.db.StaticDB.DatabaseFile;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.FileCreatedEvent;
import cz.rion.buildserver.ui.events.FileCreatedEvent.FileCreatedListener;
import cz.rion.buildserver.ui.events.FileCreatedEvent.FileCreationInfo;
import cz.rion.buildserver.ui.events.FileListLoadedEvent;
import cz.rion.buildserver.ui.events.FileListLoadedEvent.FileListLoadedListener;
import cz.rion.buildserver.ui.events.FileLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileLoadedListener;
import cz.rion.buildserver.ui.events.FileSavedEvent;
import cz.rion.buildserver.ui.events.FileSavedEvent.FileSavedListener;
import net.miginfocom.swing.MigLayout;
import java.awt.BorderLayout;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.border.MatteBorder;
import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.border.LineBorder;
import java.awt.event.ActionListener;
import java.util.List;
import java.awt.event.ActionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.JTextArea;
import java.awt.Font;

public class FilesPanel extends JPanel implements FileListLoadedListener, FileLoadedListener, FileSavedListener, FileCreatedListener {

	private final UIDriver driver;
	private Status status;
	private JTextField txtUserFilter;
	JList<DatabaseFile> list;
	private JPanel pnlOverview;
	private JTextArea txtContents;
	private JButton btnSave;
	private JButton btnSaveAndClose;

	private FileInfo loadedFile = null;
	private boolean closeAfterSave;
	private JLabel lblOverview;
	private JButton btnReload;
	private JTextField txtCreate;
	private JButton btnCreate;

	private void setComponentsEnabled(boolean enabled) {
		list.setEnabled(enabled);
		txtUserFilter.setEnabled(enabled);
		btnReload.setEnabled(enabled);
		btnCreate.setEnabled(enabled);
		txtCreate.setEnabled(enabled);
		if (enabled) {
			txtContents.setEnabled(enabled);
			btnSave.setEnabled(enabled);
			btnSaveAndClose.setEnabled(enabled);
		}
	}

	private void setListItems(DatabaseFile[] items) {
		String filter = "";
		ListModel<DatabaseFile> model = list.getModel();
		if (model != null) {
			if (model instanceof FilterModel) {
				filter = ((FilterModel<DatabaseFile>) model).getFilter();
			}
		}
		list.setModel(new FilterModel<DatabaseFile>(items, filter) {

			@Override
			public boolean show(DatabaseFile item, String filter) {
				return item.FileName.contains(filter);
			}
		});
	}

	public String getTabName() {
		return "Files";
	}

	public FilesPanel(UIDriver driver) {
		this.driver = driver;
		setLayout(new BorderLayout(0, 0));

		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.2);
		add(splitPane, BorderLayout.CENTER);

		JPanel panel = new JPanel();
		splitPane.setLeftComponent(panel);
		panel.setLayout(new MigLayout("", "[grow][]", "[][][][grow][]"));

		JLabel lblFiles = new JLabel("Files");
		lblFiles.setBorder(new MatteBorder(0, 0, 1, 0, (Color) new Color(0, 0, 0)));
		panel.add(lblFiles, "cell 0 0,alignx center");

		btnReload = new JButton("Reload");
		btnReload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				reloadFiles();
			}
		});
		panel.add(btnReload, "cell 0 1 2 1,grow");

		txtUserFilter = new JTextField();
		txtUserFilter.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				filter();
			}

			public void removeUpdate(DocumentEvent e) {
				filter();
			}

			public void insertUpdate(DocumentEvent e) {
				filter();
			}

			private String prevTxt = "";

			private void filter() {
				if (txtUserFilter != null) {
					String ntext = txtUserFilter.getText();
					if (!prevTxt.equals(ntext)) {
						prevTxt = ntext;
						refilter(ntext);
					}
				}
			}
		});
		txtUserFilter.setBorder(new MatteBorder(1, 1, 1, 1, (Color) new Color(0, 0, 0)));
		panel.add(txtUserFilter, "cell 0 2,grow");
		txtUserFilter.setColumns(10);

		JButton btnClear = new JButton("Clear");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				txtUserFilter.setText("");
				refilter("");
			}
		});
		panel.add(btnClear, "cell 1 2,grow");

		JScrollPane scrollUsers = new JScrollPane();
		panel.add(scrollUsers, "cell 0 3 2 1,grow");

		JPanel pnlUsers = new JPanel();
		scrollUsers.setViewportView(pnlUsers);
		pnlUsers.setLayout(new BorderLayout(0, 0));

		list = new JList<>();
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				int selIndex = list.getSelectedIndex();
				if (selIndex < 0) {
					nothingSelected();
				} else {
					selectedFile(list.getModel().getElementAt(selIndex));
				}
			}
		});
		setListItems(new DatabaseFile[0]);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pnlUsers.add(list, BorderLayout.CENTER);

		txtCreate = new JTextField();
		panel.add(txtCreate, "cell 0 4,grow");
		txtCreate.setColumns(10);

		btnCreate = new JButton("Create");
		btnCreate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String txt = txtCreate.getText().trim();
				if (!txt.isEmpty()) {
					createFile(txt);
				}
			}
		});
		panel.add(btnCreate, "cell 1 4,grow");

		JPanel panel_1 = new JPanel();
		splitPane.setRightComponent(panel_1);
		panel_1.setLayout(new MigLayout("", "[grow]", "[][grow]"));

		lblOverview = new JLabel("Editor");
		lblOverview.setBorder(new MatteBorder(0, 0, 1, 0, (Color) new Color(0, 0, 0)));
		panel_1.add(lblOverview, "cell 0 0,alignx center");

		pnlOverview = new JPanel();
		pnlOverview.setBorder(new LineBorder(new Color(0, 0, 0)));
		panel_1.add(pnlOverview, "cell 0 1,grow");
		pnlOverview.setLayout(new MigLayout("", "[grow]", "[grow][]"));

		txtContents = new JTextArea();
		txtContents.setFont(new Font("Monospaced", Font.PLAIN, 11));
		pnlOverview.add(txtContents, "cell 0 0,grow");
		txtContents.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				filter();
			}

			public void removeUpdate(DocumentEvent e) {
				filter();
			}

			public void insertUpdate(DocumentEvent e) {
				filter();
			}

			private void filter() {
				if (loadedFile != null) {
					if (loadedFile.Contents.equals(txtContents.getText())) {
						lblOverview.setText("Editor");
					} else {
						lblOverview.setText("Editor (unsaved changes)");
					}
				}
			}
		});
		JPanel panel_3 = new JPanel();
		pnlOverview.add(panel_3, "cell 0 1,grow");
		panel_3.setLayout(new MigLayout("", "[grow][][]", "[]"));

		btnSave = new JButton("Save");
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save();
			}
		});
		panel_3.add(btnSave, "cell 1 0");

		btnSaveAndClose = new JButton("Save and close");
		panel_3.add(btnSaveAndClose, "cell 2 0");
		btnSaveAndClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveAndClose();
			}
		});

		FileListLoadedEvent.addFileListChangeListener(driver.EventManager, this);
		FileLoadedEvent.addFileLoadedListener(driver.EventManager, this);
		FileSavedEvent.addFileSavedListener(driver.EventManager, this);
		FileCreatedEvent.addFileLoadedListener(driver.EventManager, this);
		setComponentsEnabled(false);
	}

	protected void createFile(String name) {
		setComponentsEnabled(false);
		driver.createFile(name);
	}

	protected void save() {
		setComponentsEnabled(false);
		driver.saveFile(loadedFile.ID, txtContents.getText());
		this.closeAfterSave = false;
	}

	protected void saveAndClose() {
		setComponentsEnabled(false);
		driver.saveFile(loadedFile.ID, txtContents.getText());
		this.closeAfterSave = true;
	}

	private void refilter(String string) {
		((FilterModel<DatabaseFile>) list.getModel()).filter(string);
		this.redraw();
	}

	private void selectedFile(DatabaseFile file) {
		lblOverview.setText("Editor");
		if (status == Status.CONNECTED) {
			pnlOverview.setVisible(true);
			setComponentsEnabled(false);
			driver.loadFile(file.ID);
		} else {
			nothingSelected();
			setListItems(new DatabaseFile[0]);
		}
		redraw();
	}

	private void nothingSelected() {
		lblOverview.setText("Editor");
		pnlOverview.setVisible(false);
		redraw();
	}

	public void update(Status status) {
		this.status = status;
		if (status != Status.CONNECTED) {
			setComponentsEnabled(false);
			nothingSelected();
			setListItems(new DatabaseFile[0]);
		} else {
			setComponentsEnabled(true);
		}
		redraw();
	}

	private void reloadFiles() {
		this.driver.reloadFiles();
	}

	private void redraw() {
		this.invalidate();
		this.revalidate();
		this.repaint();
	}

	@Override
	public void fileListLoaded(List<DatabaseFile> files) {
		if (status == Status.CONNECTED) {
			setComponentsEnabled(true);
			DatabaseFile[] fileList = new DatabaseFile[files.size()];
			int index = 0;
			for (DatabaseFile file : files) {
				fileList[index] = file;
				index++;
			}
			setListItems(fileList);
		} else {
			setComponentsEnabled(false);
			setListItems(new DatabaseFile[0]);
		}
		this.redraw();
	}

	@Override
	public void fileLoaded(FileInfo file) {
		loadedFile = null;
		txtContents.setText("");
		lblOverview.setText("Editor");
		if (status == Status.CONNECTED) {
			if (file == null) { // Failed to load
				driver.disconnect();
			} else {
				loadedFile = file;
				setComponentsEnabled(true);
				txtContents.setEnabled(true);
				btnSave.setEnabled(true);
				btnSaveAndClose.setEnabled(true);
				txtContents.setText(file.Contents);
			}
		} else {
			setComponentsEnabled(false);
			setListItems(new DatabaseFile[0]);
		}
		this.redraw();
	}

	@Override
	public void fileSaved(FileInfo file, boolean saved) {
		if (!saved) {
			driver.disconnect();
		} else {
			lblOverview.setText("Editor");
			setComponentsEnabled(true);
			if (closeAfterSave) {
				loadedFile = null;
				txtContents.setText("");
				txtContents.setEnabled(false);
				btnSaveAndClose.setEnabled(false);
				btnSave.setEnabled(false);
			}
		}
	}

	@Override
	public void fileCreated(FileCreationInfo file) {
		txtCreate.setText("");
		reloadFiles();
	}

}
