package cz.rion.buildserver.ui;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuildThreadInfo;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuilderAvailableListener;
import cz.rion.buildserver.ui.events.BuilderUpdateEvent;
import cz.rion.buildserver.ui.events.BuilderUpdateEvent.BuilderUpdateListener;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent;
import cz.rion.buildserver.ui.events.EventManager;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.StatusMessageEvent;
import cz.rion.buildserver.ui.events.StatusMessageEvent.StatusMessage;
import cz.rion.buildserver.ui.events.StatusMessageEvent.StatusMessageListener;

public class StatusPanel extends JPanel implements BuilderAvailableListener, StatusMessageListener, BuilderUpdateListener {
	
	private static final long serialVersionUID = 1L;
	
	private JPanel pnlBuilders;
	private Status status;
	private UIDriver driver;

	private boolean acceptingBuildersUpdate = false;

	public String getTabName() {
		return "Status";
	}

	private BuilderPanel[] builders;
	private JTextArea txtStatus;

	private void setBuilders(List<BuildThreadInfo> builders) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < builders.size(); i++) {
			sb.append("[]");
		}
		pnlBuilders.setLayout(new MigLayout("", "[grow]", sb.toString()));
		int i = 0;
		this.builders = new BuilderPanel[builders.size()];
		for (BuildThreadInfo builder : builders) {
			this.builders[i] = new BuilderPanel(builder);
			pnlBuilders.add(this.builders[i], "cell 0 " + i + ",growx");
			i++;
		}
	}

	public StatusPanel(UIDriver driver) {
		setOpaque(false);
		this.driver = driver;
		JSplitPane splitter = new JSplitPane();
		splitter.setResizeWeight(0.8);
		splitter.setOrientation(JSplitPane.VERTICAL_SPLIT);
		this.setLayout(new BorderLayout());
		add(splitter, BorderLayout.CENTER);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBorder(new TitledBorder(null, "Builders", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		splitter.setLeftComponent(scrollPane);

		pnlBuilders = new JPanel();
		scrollPane.setViewportView(pnlBuilders);
		pnlBuilders.setLayout(new MigLayout("", "[grow]", "[][][][][]"));

		JScrollPane scrollPane_1 = new JScrollPane();
		scrollPane_1.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane_1.setBorder(new TitledBorder(null, "Status", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		splitter.setRightComponent(scrollPane_1);

		txtStatus = new JTextArea();
		txtStatus.setEditable(false);
		txtStatus.setOpaque(false);
		scrollPane_1.setViewportView(txtStatus);

		BuildersLoadedEvent.addStatusChangeListener(driver.EventManager, this);
		BuilderUpdateEvent.addBuilderUpdateListener(driver.EventManager, this);
		StatusMessageEvent.addStatusChangeListener(driver.EventManager, this);
	}

	public void update(Status status) {
		acceptingBuildersUpdate = false;
		this.status = status;
		if (status == EventManager.Status.CONNECTED) {
			pnlBuilders.removeAll();
			driver.getBuilders();
			redraw();
		} else if (status == EventManager.Status.DISCONNECTED) {
			pnlBuilders.removeAll();
			redraw();
		} else if (status == EventManager.Status.CONNECTING) {
			pnlBuilders.removeAll();
			redraw();
		}
	}

	private void redraw() {
		StatusPanel.this.invalidate();
		StatusPanel.this.repaint();
	}

	@Override
	public void buildersAvailable(List<BuildThreadInfo> builders) {
		acceptingBuildersUpdate = false;
		if (status == EventManager.Status.CONNECTED) {
			pnlBuilders.removeAll();
			setBuilders(builders);
			txtStatus.setText("");
			redraw();
			acceptingBuildersUpdate = true;
		}
	}

	@Override
	public void messageReceived(StatusMessage msg) {
		if (status == EventManager.Status.CONNECTED) {
			txtStatus.append(msg.toString() + "\r\n");
		}
	}

	@Override
	public void buildersUpdateAvailable(BuildThreadInfo builder) {
		if (acceptingBuildersUpdate) {
			for (BuilderPanel pnl : builders) {
				if (pnl.getID() == builder.ID) {
					pnl.updateBuilder(builder);
					redraw();
					return;
				}
			}
		}
	}

}
