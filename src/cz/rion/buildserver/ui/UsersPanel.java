package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.ui.events.UsersLoadedEvent;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserInfo;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserListLoadedListener;
import cz.rion.buildserver.ui.utils.BetterListCellRenderer;
import cz.rion.buildserver.ui.utils.FilterModel;
import cz.rion.buildserver.ui.utils.MyButton;
import cz.rion.buildserver.ui.utils.MyLabel;
import cz.rion.buildserver.ui.utils.MyTextField;
import net.miginfocom.swing.MigLayout;
import java.awt.BorderLayout;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.ListModel;
import javax.swing.border.LineBorder;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.awt.event.ActionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.Font;

public class UsersPanel extends JPanel implements UserListLoadedListener {

	private final UIDriver driver;
	private Status status;
	private MyTextField txtUserFilter;
	private JPanel pnlDiv;
	JList<UserInfo> list;
	private JPanel pnlOverview;
	private MyLabel lblRegisted;
	private MyLabel lblLastActive;
	private MyLabel lblFullname;
	private MyLabel lblTotalTests;
	private MyLabel lblGroup;
	private MyLabel lblLastTest;

	private void setComponentsEnabled(boolean enabled) {
		txtUserFilter.setEnabled(enabled);
		list.setEnabled(enabled);
		btnReload.setEnabled(enabled);
	}

	private void setListItems(UserInfo[] items) {
		String filter = "";
		ListModel<UserInfo> model = list.getModel();
		if (model != null) {
			if (model instanceof FilterModel) {
				filter = ((FilterModel<UserInfo>) model).getFilter();
			}
		}
		list.setModel(new FilterModel<UserInfo>(items, filter) {

			@Override
			public boolean show(UserInfo item, String filter) {
				return item.Login.toLowerCase().contains(filter) || item.FullName.toLowerCase().contains(filter);
			}
		});
	}

	public String getTabName() {
		return "Users";
	}

	public UsersPanel(UIDriver driver) {
		this.driver = driver;
		setLayout(new BorderLayout(0, 0));

		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.2);
		add(splitPane, BorderLayout.CENTER);

		JPanel panel = new JPanel();
		splitPane.setLeftComponent(panel);
		panel.setLayout(new MigLayout("", "[grow][]", "[][][][grow]"));

		MyLabel lblUsers = new MyLabel("Users");
		lblUsers.setBorder(new MatteBorder(0, 0, 1, 0, (Color) new Color(0, 0, 0)));
		panel.add(lblUsers, "cell 0 0,alignx center");

		btnReload = new MyButton("Reload");
		btnReload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				reloadUsers();
			}
		});
		panel.add(btnReload, "cell 0 1 2 1,grow");

		txtUserFilter = new MyTextField();
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

		MyButton btnClear = new MyButton("Clear");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				txtUserFilter.setText("");
				refilter("");
			}
		});
		panel.add(btnClear, "cell 1 2,grow");

		JScrollPane scrollUsers = new JScrollPane();
		scrollUsers.getVerticalScrollBar().setUnitIncrement(16);
		panel.add(scrollUsers, "cell 0 3 2 1,grow");

		JPanel pnlUsers = new JPanel();
		scrollUsers.setViewportView(pnlUsers);
		pnlUsers.setLayout(new BorderLayout(0, 0));

		list = new JList<>();
		list.setFont(new Font("Tahoma", Font.PLAIN, 17));
		list.setCellRenderer(new BetterListCellRenderer());
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				int selIndex = list.getSelectedIndex();
				if (selIndex < 0) {
					nothingSelected();
				} else {
					selectedUser(list.getModel().getElementAt(selIndex));
				}
			}
		});
		setListItems(new UserInfo[0]);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pnlUsers.add(list, BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		splitPane.setRightComponent(panel_1);
		panel_1.setLayout(new MigLayout("", "[grow]", "[][grow]"));

		MyLabel lblOverview = new MyLabel("Overview");
		lblOverview.setBorder(new MatteBorder(0, 0, 1, 0, (Color) new Color(0, 0, 0)));
		panel_1.add(lblOverview, "cell 0 0,alignx center");

		pnlOverview = new JPanel();
		pnlOverview.setBorder(new LineBorder(new Color(0, 0, 0)));
		panel_1.add(pnlOverview, "cell 0 1,grow");
		pnlOverview.setLayout(new MigLayout("", "[][][grow][][]", "[][][][grow][]"));

		pnlOverview.add(new MyLabel("Registered:"), "cell 0 0,alignx right");

		lblRegisted = new MyLabel("<Registered>");
		pnlOverview.add(lblRegisted, "cell 1 0");

		pnlDiv = new JPanel() {
			@Override
			public void paint(Graphics g) {
				super.paint(g);
				g.setColor(Color.BLACK);
				g.drawLine(pnlDiv.getWidth() / 2, 0, pnlDiv.getWidth() / 2, pnlDiv.getHeight());
			}
		};
		pnlDiv.setOpaque(false);
		pnlOverview.add(pnlDiv, "cell 2 0 1 3,grow");

		pnlOverview.add(new MyLabel("Last active:"), "cell 3 0,alignx right");

		lblLastActive = new MyLabel("<Last active>");
		pnlOverview.add(lblLastActive, "cell 4 0");

		pnlOverview.add(new MyLabel("Full name:"), "cell 0 1,alignx right");

		lblFullname = new MyLabel("<Full name>");
		pnlOverview.add(lblFullname, "cell 1 1");

		pnlOverview.add(new MyLabel("Tests submitted:"), "cell 3 1,alignx right");

		lblTotalTests = new MyLabel("<Total tests>");
		pnlOverview.add(lblTotalTests, "cell 4 1");

		pnlOverview.add(new MyLabel("Group:"), "cell 0 2,alignx right");

		lblGroup = new MyLabel("<Group>");
		pnlOverview.add(lblGroup, "cell 1 2");

		pnlOverview.add(new MyLabel("Last submit:"), "cell 3 2,alignx right");

		lblLastTest = new MyLabel("<Last test>");
		pnlOverview.add(lblLastTest, "cell 4 2");

		JPanel panel_3 = new JPanel();
		pnlOverview.add(panel_3, "cell 0 4 5 1,grow");
		panel_3.setLayout(new MigLayout("", "[]", "[]"));

		MyButton btnViewTests = new MyButton("View tests");
		panel_3.add(btnViewTests, "cell 0 0");

		UsersLoadedEvent.addStatusChangeListener(driver.EventManager, this);

		setComponentsEnabled(false);
	}

	private void refilter(String string) {
		((FilterModel<UserInfo>) list.getModel()).filter(string);
		this.redraw();
	}

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd. MM. yyyy - HH:mm");
	private MyButton btnReload;

	private String DateToText(Date date) {
		return dateFormat.format(date);
	}

	private void selectedUser(UserInfo user) {
		if (status == Status.CONNECTED) {
			pnlOverview.setVisible(true);
			lblRegisted.setText(DateToText(user.RegistrationDate));
			lblLastActive.setText(DateToText(user.LastActiveDate));
			lblFullname.setText(user.FullName);
			lblTotalTests.setText(user.TotalTestsSubmitted + "");
			lblGroup.setText(user.Group);
			lblLastTest.setText(user.TotalTestsSubmitted > 0 ? DateToText(user.LastActiveDate) + " (" + user.LastTestID + ")" : "");
		} else {
			nothingSelected();
			setListItems(new UserInfo[0]);
		}
		redraw();
	}

	private void nothingSelected() {
		pnlOverview.setVisible(false);
		redraw();
	}

	public void update(Status status) {
		this.status = status;
		if (status != Status.CONNECTED) {
			nothingSelected();
			setListItems(new UserInfo[0]);
			setComponentsEnabled(false);
		} else {
			setComponentsEnabled(true);
		}
		redraw();
	}

	private void reloadUsers() {
		setComponentsEnabled(false);
		this.driver.reloadUsers();
	}

	private void redraw() {
		this.invalidate();
		this.revalidate();
		this.repaint();
	}

	@Override
	public void userListLoaded(List<UserInfo> users) {
		setComponentsEnabled(true);
		UserInfo[] userList = new UserInfo[users.size()];
		int index = 0;
		for (UserInfo user : users) {
			userList[index] = user;
			index++;
		}
		setListItems(userList);
		this.redraw();
	}
}
