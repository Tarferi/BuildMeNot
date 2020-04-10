package cz.rion.buildserver.ui.events;

import java.util.Date;
import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class UsersLoadedEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.UsersLoaded.code;

	public static class UserInfo {
		public final int ID;
		public final String Login;
		public final String FullName;
		public final String Group;
		public final String PermissionGroup;

		public final Date RegistrationDate;
		public final Date LastActiveDate;
		public final Date LastLoginDate;
		public final int TotalTestsSubmitted;
		public final String LastTestID;
		public final Date LastTestDate;

		@Override
		public String toString() {
			return Login + " (" + FullName + ")";
		}

		public UserInfo(int id, String login, String fullName, String group, Date RegistrationDate, Date LastActiveDate, Date LastLoginDate, int TotalTestsSubmitted, String LastTestID, Date LastTestDate, String PermissionGroup) {
			this.ID = id;
			this.Login = login;
			this.FullName = fullName;
			this.Group = group;
			this.RegistrationDate = RegistrationDate;
			this.LastLoginDate = LastLoginDate;
			this.LastActiveDate = LastActiveDate;
			this.TotalTestsSubmitted = TotalTestsSubmitted;
			this.LastTestID = LastTestID;
			this.LastTestDate = LastTestDate;
			this.PermissionGroup = PermissionGroup;
		}
	}

	public static void addStatusChangeListener(EventManager m, UserListLoadedListener l) {
		synchronized (m.userListLoadedListeners) {
			if (!m.userListLoadedListeners.contains(l)) {
				m.userListLoadedListeners.add(l);
			}
		}
	}

	public UsersLoadedEvent(List<UserInfo> users) {
		super(users);
	}

	public static interface UserListLoadedListener {

		void userListLoaded(List<UserInfo> users);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			@SuppressWarnings("unchecked")
			final List<UserInfo> data = (List<UserInfo>) super.data;
			for (final UserListLoadedListener userListLoadedListener : m.userListLoadedListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						userListLoadedListener.userListLoaded(data);
					}

				});
			}
		}
	}
}
