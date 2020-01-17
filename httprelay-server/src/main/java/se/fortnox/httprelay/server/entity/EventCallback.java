package se.fortnox.httprelay.server.entity;

import java.util.HashMap;

public class EventCallback {
	private HashMap<String, String> metadata;
	private String                  token;
	private WebhookEvent event;
	private String type;
	private String event_id;
	private long event_time;

	public HashMap<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(HashMap<String, String> metadata) {
		this.metadata = metadata;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public WebhookEvent getEvent() {
		return event;
	}

	public void setEvent(WebhookEvent event) {
		this.event = event;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getEvent_id() {
		return event_id;
	}

	public void setEvent_id(String event_id) {
		this.event_id = event_id;
	}

	public long getEvent_time() {
		return event_time;
	}

	public void setEvent_time(long event_time) {
		this.event_time = event_time;
	}
}
