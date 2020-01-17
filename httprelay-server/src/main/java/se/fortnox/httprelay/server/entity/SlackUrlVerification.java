package se.fortnox.httprelay.server.entity;

public class SlackUrlVerification {
	String token;
	String challenge;
	String type;

	public SlackUrlVerification() {
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getChallenge() {
		return challenge;
	}

	public void setChallenge(String challenge) {
		this.challenge = challenge;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
