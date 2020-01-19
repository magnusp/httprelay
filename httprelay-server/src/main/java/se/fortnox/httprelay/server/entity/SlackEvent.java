package se.fortnox.httprelay.server.entity;

import javax.validation.constraints.NotEmpty;

public class SlackEvent {
    @NotEmpty
    private String type;

    public SlackEvent() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
