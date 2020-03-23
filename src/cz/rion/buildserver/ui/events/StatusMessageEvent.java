package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.json.JsonValue.JsonObject;

public class StatusMessageEvent extends Event {

	public static final int ID = 40;

	private static StatusMessage parse(JsonObject data) {
		if (data.containsNumber("code") && data.containsString("address") && data.containsString("login") && data.containsString("result") && data.containsNumber("type")) {
			int code = data.getNumber("code").Value;
			String address = data.getString("address").Value;
			String login = data.getString("login").Value;
			String result = data.getString("result").Value;
			int type = data.getNumber("type").Value;
			if (type == StatusMessageType.GET_RESOURCE.code && data.containsString("path")) {
				String resource = data.getString("path").Value;
				return new GetResourceMessage(login, address, resource, code, result);
			} else if (type == StatusMessageType.LOAD_HTML.code && data.containsString("path")) {
				String resource = data.getString("path").Value;
				return new GetHTMLMessage(login, address, resource, code, result);
			} else if (type == StatusMessageType.PERFORM_TEST.code && data.containsString("asm") && data.containsString("test_id")) {
				String asm = data.getString("asm").Value;
				String test_id = data.getString("test_id").Value;
				return new GetTestResultMessage(login, address, asm, test_id, code, result);
			} else if (type == StatusMessageType.GET_TESTS.code) {
				return new GetTestsMessage(login, address);
			}
		}
		return null;
	}

	public StatusMessageEvent(JsonObject data) {
		super(parse(data));

	}

	public static abstract class StatusMessage {

		public final String Login;
		public final String Address;
		public final StatusMessageType Type;

		private StatusMessage(String login, String address, StatusMessageType type) {
			this.Login = login;
			this.Address = address;
			this.Type = type;
		}

		private StatusMessage(String login, String address, int type) {
			this(login, address, StatusMessageType.values()[type]);
		}
	}

	public static class GetResourceMessage extends StatusMessage {
		public final String Resource;
		public final int Code;
		public final String ResultDescription;

		private GetResourceMessage(String login, String address, String resource, int code, String description) {
			super(login, address, StatusMessageType.GET_RESOURCE);
			this.Resource = resource;
			this.Code = code;
			this.ResultDescription = description;
		}

		@Override
		public String toString() {
			return "[" + Login + "@" + Address + "] Downloaded (" + Code + ") \"" + Resource + "\"";
		}
	}

	public static class GetHTMLMessage extends StatusMessage {
		public final String Resource;
		public final int Code;
		public final String ResultDescription;

		private GetHTMLMessage(String login, String address, String resource, int code, String description) {
			super(login, address, StatusMessageType.LOAD_HTML);
			this.Resource = resource;
			this.Code = code;
			this.ResultDescription = description;
		}

		@Override
		public String toString() {
			return "[" + Login + "@" + Address + "] Downloaded (" + Code + ") \"" + Resource + "\"";
		}
	}

	public static class GetTestResultMessage extends StatusMessage {
		public final String ASM;
		public final String Test_id;
		public final int Code;
		public final String ResultDescription;

		private GetTestResultMessage(String login, String address, String asm, String test_id, int code, String description) {
			super(login, address, StatusMessageType.LOAD_HTML);
			this.ASM = asm;
			this.Test_id = test_id;
			this.Code = code;
			this.ResultDescription = description;
		}

		@Override
		public String toString() {
			return "[" + Login + "@" + Address + "] Performed test (" + Code + ") on \"" + Test_id + "\"";
		}
	}

	public static class GetTestsMessage extends StatusMessage {

		private GetTestsMessage(String login, String address) {
			super(login, address, StatusMessageType.GET_TESTS);
		}

		@Override
		public String toString() {
			return "[" + Login + "@" + Address + "] Downloaded all tests";
		}
	}

	public enum StatusMessageType {
		GET_RESOURCE(0), LOAD_HTML(1), PERFORM_TEST(2), GET_TESTS(3);

		public final int code;

		private StatusMessageType(int code) {
			this.code = code;
		}
	}

	public static interface StatusMessageListener {

		public void messageReceived(StatusMessage msg);
	}

	public static void addStatusChangeListener(EventManager m, StatusMessageListener l) {
		synchronized (m.statusMessageListeners) {
			if (!m.statusMessageListeners.contains(l)) {
				m.statusMessageListeners.add(l);
			}
		}
	}

	@Override
	public void dispatch(EventManager ev) {
		synchronized (ev.statusMessageListeners) {
			final StatusMessage data = (StatusMessage) super.data;
			if (data != null) {
				for (final StatusMessageListener statusMessageListener : ev.statusMessageListeners) {
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							statusMessageListener.messageReceived(data);
						}

					});
				}
			}
		}
	}
}
