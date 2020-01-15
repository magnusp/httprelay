package se.fortnox.httprelay.server;

public class DataFrame {
    private String value;

    public DataFrame() {
    }

    public DataFrame(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
