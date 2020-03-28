package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.db.layers.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.ui.TableView.ShowDetailsPanelCallback;
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
import cz.rion.buildserver.ui.utils.BetterListCellRenderer;
import cz.rion.buildserver.ui.utils.MyButton;
import cz.rion.buildserver.ui.utils.MyLabel;
import cz.rion.buildserver.ui.utils.MyTextField;
import cz.rion.buildserver.ui.utils.NavigationList;
import cz.rion.buildserver.ui.utils.NavigationList.FileSelectedListener;
import net.miginfocom.swing.MigLayout;
import java.awt.BorderLayout;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import java.awt.event.ActionListener;
import java.util.List;
import java.awt.event.ActionEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;
import java.awt.Font;
import javax.swing.ScrollPaneConstants;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class FilesPanel extends JPanel implements FileListLoadedListener, FileLoadedListener, FileSavedListener, FileCreatedListener {

	private final UIDriver driver;
	private Status status;
	private MyTextField txtFileFilter;
	private NavigationList list;
	private JPanel pnlOverview;
	private JTextArea txtContents;
	private MyButton btnSave;
	private MyButton btnSaveAndClose;

	private FileInfo loadedFile = null;
	private boolean closeAfterSave;
	private MyLabel lblOverview;
	private MyButton btnReload;
	private MyTextField txtCreate;
	private MyButton btnCreate;

	private enum ActionState {
		DISCONNECTED, FILES_NOT_LOADED, LOADING_FILES, FILES_LOADED, CREATING_FILE, EDITING_FILE, SAVING_FILE, LOADING_FILE
	}

	private MyButton btnClear;
	private MyButton btnClose;
	private JPanel pnlLeft;
	private JSplitPane splitPane;
	private JScrollPane scrollContents;
	private TableView myTable;
	private MyButton btnEdit;

	private class PropertyWrapper {
		private JComponent component;
		private boolean enabled;
		private boolean visible;
		private String text = null;

		private boolean initialEnabled;
		private boolean initialVisible;
		private String initialText;

		private PropertyWrapper(JComponent c) {
			this.component = c;
			this.enabled = false;
			this.visible = false;
			if (c instanceof MyLabel) {
				this.initialText = ((MyLabel) c).getText();
			} else if (c instanceof MyButton) {
				this.initialText = ((MyButton) c).getText();
			} else if (c instanceof MyTextField) {
				this.initialText = ((MyTextField) c).getText();
			} else if (c instanceof JTextArea) {
				this.initialText = ((JTextArea) c).getText();
			}
			this.initialEnabled = c.isEnabled();
			this.initialVisible = c.isVisible();
			this.text = this.initialText == null ? null : this.initialText; // TODO: Intent clarification?
		}

		protected void commit() {
			if (initialEnabled != enabled) {
				component.setEnabled(enabled);
			}
			if (initialVisible != visible) {
				component.setVisible(visible);
			}
			if (text != initialText) {
				if (text != null) {
					if (initialText != null) {
						if (text.equals(initialText)) {
							return;
						}
					}
					if (component instanceof MyLabel) {
						((MyLabel) component).setText(text);
					} else if (component instanceof MyButton) {
						((MyButton) component).setText(text);
					} else if (component instanceof MyTextField) {
						((MyTextField) component).setText(text);
					} else if (component instanceof JTextArea) {
						((JTextArea) component).setText(text);
					}
				}
			}
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public void setVisible(boolean visible) {
			this.visible = visible;
		}

		public void setText(String string) {
			if (text != null) {
				text = string;
			}
		}
	}

	private void setState(ActionState state) {
		PropertyWrapper wList = new PropertyWrapper(list);
		PropertyWrapper wTxtFileFilter = new PropertyWrapper(txtFileFilter);
		PropertyWrapper wBtnReload = new PropertyWrapper(btnReload);
		PropertyWrapper wBtnCreate = new PropertyWrapper(btnCreate);
		PropertyWrapper wBtnClear = new PropertyWrapper(btnClear);
		PropertyWrapper wTxtCreate = new PropertyWrapper(txtCreate);
		PropertyWrapper wWxtContents = new PropertyWrapper(txtContents);
		PropertyWrapper wBtnSave = new PropertyWrapper(btnSave);
		PropertyWrapper wBtnSaveAndClose = new PropertyWrapper(btnSaveAndClose);
		PropertyWrapper wBtnClose = new PropertyWrapper(btnClose);
		PropertyWrapper wPnlOverview = new PropertyWrapper(pnlOverview);
		PropertyWrapper wLblOverview = new PropertyWrapper(lblOverview);
		PropertyWrapper wTxtContents = new PropertyWrapper(txtContents);
		PropertyWrapper wBtnEdit = new PropertyWrapper(btnEdit);
		PropertyWrapper wBtnReturn = new PropertyWrapper(btnReturn);

		switch (state) {
		case DISCONNECTED:
			break;

		case FILES_NOT_LOADED:
			wTxtCreate.setText("");
			wLblOverview.setText("Editor");
			wBtnReload.setVisible(true);
			wBtnReload.setEnabled(true);

			wBtnClear.setVisible(true);

			wBtnCreate.setVisible(true);

			wTxtCreate.setEnabled(true);

			wTxtFileFilter.setVisible(true);

			break;

		case LOADING_FILES:
			wBtnReload.setVisible(true);

			wBtnClear.setVisible(true);

			wBtnCreate.setVisible(true);

			wTxtCreate.setEnabled(true);

			wTxtFileFilter.setVisible(true);

			wTxtCreate.setText("");
			break;

		case FILES_LOADED:
			wList.setVisible(true);
			wList.setEnabled(true);

			wBtnReload.setVisible(true);
			wBtnReload.setEnabled(true);

			wTxtCreate.setText("");
			wTxtCreate.setEnabled(true);
			wTxtCreate.setVisible(true);

			wTxtContents.setText("");

			wTxtFileFilter.setVisible(true);
			wTxtFileFilter.setEnabled(true);

			wBtnClear.setVisible(true);
			wBtnClear.setEnabled(true);

			wBtnCreate.setVisible(true);
			wBtnCreate.setEnabled(true);
			break;

		case CREATING_FILE: // Async progress
			wList.setVisible(true);

			wBtnClear.setVisible(true);

			wBtnCreate.setVisible(true);

			wTxtCreate.setEnabled(true);

			wTxtFileFilter.setVisible(true);
			break;

		case EDITING_FILE: // File loaded in txtContents
			wPnlOverview.setVisible(true);

			wList.setVisible(true);
			wList.setEnabled(true);

			wBtnClear.setVisible(true);

			wBtnCreate.setVisible(true);

			wTxtCreate.setEnabled(true);

			wTxtFileFilter.setVisible(true);

			wTxtContents.setVisible(true);
			wTxtContents.setEnabled(true);

			wTxtCreate.setVisible(true);
			wTxtCreate.setEnabled(true);
			wTxtCreate.setText(this.loadedFile.FileName);

			wBtnReload.setVisible(true);
			wBtnReload.setEnabled(true);

			wLblOverview.setVisible(true);
			wLblOverview.setEnabled(true);
			wLblOverview.setText(this.loadedFile.FileName);

			if (this.loadedFile.FileName.endsWith(".table")) {
				setTableEditing();
				myTable.setData(this.loadedFile.Contents, false);
			} else if (this.loadedFile.FileName.endsWith(".view")) {
				setTableEditing();
				myTable.setData(this.loadedFile.Contents, true);

				// Load SQL
				JsonValue val = JsonValue.parse(this.loadedFile.Contents);
				String sql = "";
				if (val != null) {
					if (val.isObject()) {
						if (val.asObject().containsString("SQL")) {
							sql = val.asObject().getString("SQL").Value;
						}
					}
				}

				wTxtContents.setText(sql);
				wBtnEdit.setEnabled(true);
				wBtnEdit.setVisible(true);
				wBtnEdit.setText("Edit");

				wBtnSave.setVisible(true);
				wBtnSave.setEnabled(true);

				wBtnSaveAndClose.setVisible(true);
				wBtnSaveAndClose.setEnabled(true);
			} else {
				setTextAreaEditing();
				wTxtContents.setText(this.loadedFile.Contents);
				wBtnSave.setVisible(true);
				wBtnSave.setEnabled(true);

				wBtnSaveAndClose.setVisible(true);
				wBtnSaveAndClose.setEnabled(true);
			}
			scrollContents.getVerticalScrollBar().setValue(0);

			wBtnClose.setVisible(true);
			wBtnClose.setEnabled(true);
			break;

		case SAVING_FILE: // Async progress
			wPnlOverview.setVisible(true);
			wList.setVisible(true);
			break;

		case LOADING_FILE:
			wPnlOverview.setVisible(true);

			wList.setVisible(true);

			wBtnClear.setVisible(true);

			wBtnCreate.setVisible(true);

			wTxtCreate.setEnabled(true);

			wTxtFileFilter.setVisible(true);

			break;
		}

		wList.commit();
		wTxtFileFilter.commit();
		wBtnReload.commit();
		wBtnCreate.commit();
		wBtnClear.commit();
		wTxtCreate.commit();
		wWxtContents.commit();
		wBtnSave.commit();
		wBtnSaveAndClose.commit();
		wBtnClose.commit();
		wPnlOverview.commit();
		wLblOverview.commit();
		wTxtContents.commit();
		wBtnEdit.commit();
		wBtnReturn.commit();
	}

	private void setListItems(DatabaseFile[] items) {
		list.setItems(items, true);
	}

	public String getTabName() {
		return "Files";
	}

	private JComponent currentRightSideContent = null;
	private MyButton btnReturn;
	private JPanel panel_1;

	private void setRightSideContents(JComponent c) {
		scrollContents.setViewportView(c);
		currentRightSideContent = c;
		redraw();
	}

	private void setTextAreaEditing() {
		setRightSideContents(txtContents);
	}

	private void setTableEditing() {
		setRightSideContents(myTable);
	}

	public FilesPanel(UIDriver driver) {
		this.driver = driver;
		setLayout(new BorderLayout(0, 0));

		splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.2);
		add(splitPane, BorderLayout.CENTER);

		pnlLeft = new JPanel();
		splitPane.setLeftComponent(pnlLeft);
		pnlLeft.setLayout(new MigLayout("", "[grow][]", "[][][][grow][]"));

		MyLabel lblFiles = new MyLabel("Files");
		lblFiles.setBorder(new MatteBorder(0, 0, 1, 0, (Color) new Color(0, 0, 0)));
		pnlLeft.add(lblFiles, "cell 0 0 2 1,alignx center");

		btnReload = new MyButton("Reload");
		btnReload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				reloadFiles();
			}
		});
		pnlLeft.add(btnReload, "cell 0 1 2 1,grow");
		myTable = new TableView(new ShowDetailsPanelCallback() {

			@Override
			public void showDetails(JPanel panel) {
				setRightSideContents(panel);
				btnReturn.setVisible(true);
				btnReturn.setEnabled(true);
			}

			@Override
			public void closeDetails() {
				setRightSideContents(myTable);
			}
		});

		txtFileFilter = new MyTextField();
		txtFileFilter.getDocument().addDocumentListener(new DocumentListener() {
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
				if (txtFileFilter != null) {
					String ntext = txtFileFilter.getText();
					if (!prevTxt.equals(ntext)) {
						prevTxt = ntext;
						refilter(ntext);
					}
				}
			}
		});
		txtFileFilter.setBorder(new MatteBorder(1, 1, 1, 1, (Color) new Color(0, 0, 0)));
		pnlLeft.add(txtFileFilter, "cell 0 2,grow");
		txtFileFilter.setColumns(10);

		btnClear = new MyButton("Clear");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				txtFileFilter.setText("");
				refilter("");
			}
		});
		pnlLeft.add(btnClear, "cell 1 2,grow");

		JScrollPane scrollFiles = new JScrollPane();
		scrollFiles.getVerticalScrollBar().setUnitIncrement(16);
		pnlLeft.add(scrollFiles, "cell 0 3 2 1,grow");

		JPanel pnlFiles = new JPanel();
		scrollFiles.setViewportView(pnlFiles);
		pnlFiles.setLayout(new BorderLayout(0, 0));

		list = new NavigationList();
		BetterListCellRenderer renderer = new BetterListCellRenderer();
		renderer.setIconsEnabled(true);
		list.setCellRenderer(renderer);
		list.setFont(new Font("Tahoma", Font.PLAIN, 17));
		list.addFileSelectedListener(new FileSelectedListener() {
			public void FileSelected(DatabaseFile file) {
				selectedFile(file);
			}
		});
		setListItems(new DatabaseFile[0]);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pnlFiles.add(list, BorderLayout.CENTER);

		txtCreate = new MyTextField();
		pnlLeft.add(txtCreate, "cell 0 4,grow");
		txtCreate.setColumns(10);

		btnCreate = new MyButton("Create");
		btnCreate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String txt = txtCreate.getText().trim();
				if (!txt.isEmpty()) {
					createFile(txt);
				}
			}
		});
		pnlLeft.add(btnCreate, "cell 1 4,grow");

		JPanel pnlRight = new JPanel();
		splitPane.setRightComponent(pnlRight);
		pnlRight.setLayout(new MigLayout("", "[grow]", "[grow]"));

		pnlOverview = new JPanel();
		pnlOverview.setBorder(new LineBorder(new Color(0, 0, 0)));
		pnlRight.add(pnlOverview, "cell 0 0,grow");
		pnlOverview.setLayout(new MigLayout("", "[grow]", "[][grow][]"));

		lblOverview = new MyLabel("Editor");
		pnlOverview.add(lblOverview, "cell 0 0,alignx center");
		lblOverview.setBorder(new MatteBorder(0, 0, 1, 0, (Color) new Color(0, 0, 0)));

		panel_1 = new JPanel();
		pnlOverview.add(panel_1, "cell 0 1,grow");
		panel_1.setLayout(new BorderLayout(0, 0));

		txtContents =new JTextArea();
		txtContents.setFont(new Font("Monospaced", Font.PLAIN, 17));
		scrollContents = new JScrollPane(txtContents);
		panel_1.add(scrollContents, BorderLayout.CENTER);
		scrollContents.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (currentRightSideContent != null) {
					currentRightSideContent.setSize(100, 100);
				}
				int height = scrollContents.getViewport().getHeight();
				int width = scrollContents.getViewport().getWidth();
				int newWidth = scrollContents.getWidth();
				// scrollContents.getViewport().setSize(scrollContents.getWidth()-50, height);
			}
		});
		scrollContents.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollContents.getVerticalScrollBar().setUnitIncrement(16);
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
						lblOverview.setText(loadedFile.FileName);
					} else {
						lblOverview.setText(loadedFile.FileName + " (unsaved changes)");
					}
				}
			}
		});
		JPanel panel_3 = new JPanel();
		pnlOverview.add(panel_3, "cell 0 2,grow");
		panel_3.setLayout(new MigLayout("", "[grow][][][][][]", "[]"));

		btnSave = new MyButton("Save");
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save();
			}
		});

		btnClose = new MyButton("Close");
		btnClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setState(ActionState.FILES_LOADED);
			}
		});

		btnEdit = new MyButton("Edit");
		btnEdit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editBtnPressed();
			}
		});

		btnReturn = new MyButton("Return");
		btnReturn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				myTable.showData();
				btnReturn.setVisible(false);
			}
		});
		panel_3.add(btnReturn, "cell 1 0");
		panel_3.add(btnEdit, "cell 2 0");
		panel_3.add(btnClose, "cell 3 0");
		panel_3.add(btnSave, "cell 4 0");

		btnSaveAndClose = new MyButton("Save and close");
		panel_3.add(btnSaveAndClose, "cell 5 0");
		btnSaveAndClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveAndClose();
			}
		});

		FileListLoadedEvent.addFileListChangeListener(driver.EventManager, this);
		FileLoadedEvent.addFileLoadedListener(driver.EventManager, this);
		FileSavedEvent.addFileSavedListener(driver.EventManager, this);
		FileCreatedEvent.addFileLoadedListener(driver.EventManager, this);
	}

	protected void createFile(String name) {
		setState(ActionState.CREATING_FILE);
		driver.createFile(name);
	}

	protected void save() {
		setState(ActionState.SAVING_FILE);
		driver.saveFile(loadedFile.ID, txtCreate.getText(), txtContents.getText());
		this.closeAfterSave = false;
	}

	protected void saveAndClose() {
		setState(ActionState.SAVING_FILE);
		driver.saveFile(loadedFile.ID, txtCreate.getText(), txtContents.getText());
		this.closeAfterSave = true;
	}

	private void refilter(String string) {
		list.refilter(string);
		this.redraw();
	}

	private void selectedFile(DatabaseFile file) {
		setState(ActionState.LOADING_FILE);
		lblOverview.setText(file.FileName);
		if (status == Status.CONNECTED) {
			setState(ActionState.LOADING_FILE);
			driver.loadFile(file.ID);
		} else {
			setState(ActionState.DISCONNECTED);
		}
		redraw();
	}

	public void update(Status status) {
		this.status = status;
		if (status != Status.CONNECTED) {
			setState(ActionState.DISCONNECTED);
		} else {
			setState(ActionState.FILES_NOT_LOADED);
		}
		redraw();
	}

	private void reloadFiles() {
		setState(ActionState.LOADING_FILES);
		this.driver.reloadFiles();
	}

	private void editBtnPressed() {
		if (this.loadedFile != null) {
			if (this.loadedFile.FileName.endsWith(".view")) { // Editing view
				if (currentRightSideContent == txtContents) { // Edit pressed in table -> return
					this.btnEdit.setText("Edit");
					setTableEditing();
				} else {
					this.btnEdit.setText("View");
					setTextAreaEditing();
				}
			}
		}
	}

	private void redraw() {
		this.invalidate();
		this.revalidate();
		this.repaint();
	}

	@Override
	public void fileListLoaded(List<DatabaseFile> files) {
		if (status == Status.CONNECTED) {
			DatabaseFile[] fileList = new DatabaseFile[files.size()];
			int index = 0;
			for (DatabaseFile file : files) {
				fileList[index] = file;
				index++;
			}
			setListItems(fileList);
			setState(ActionState.FILES_LOADED);
		} else {
			driver.disconnect();
			setState(ActionState.DISCONNECTED);
		}
		this.redraw();
	}

	@Override
	public void fileLoaded(FileInfo file) {
		loadedFile = null;
		txtContents.setText("");
		if (status == Status.CONNECTED) {
			if (file == null) { // Failed to load
				driver.disconnect();
			} else {
				loadedFile = file;
				lblOverview.setText(file.FileName);
				setState(ActionState.EDITING_FILE);
				// txtContents.setText(file.Contents);
			}
		} else {
			driver.disconnect();
			setState(ActionState.DISCONNECTED);
		}
		this.redraw();
	}

	@Override
	public void fileSaved(FileInfo file, boolean saved) {
		if (!saved) {
			driver.disconnect();
			setState(ActionState.DISCONNECTED);
		} else {
			setState(ActionState.EDITING_FILE);
			lblOverview.setText(file.FileName);
			if (closeAfterSave) {
				loadedFile = null;
				setState(ActionState.FILES_LOADED);
			}
		}
	}

	@Override
	public void fileCreated(FileCreationInfo file) {
		txtCreate.setText("");
		reloadFiles();
	}

}
