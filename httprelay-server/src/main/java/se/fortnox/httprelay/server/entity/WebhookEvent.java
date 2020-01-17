package se.fortnox.httprelay.server.entity;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class WebhookEvent {
	private String              type;

	@JsonAnySetter()
	private Map<String, Object> properties = new HashMap<>();

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}
}
