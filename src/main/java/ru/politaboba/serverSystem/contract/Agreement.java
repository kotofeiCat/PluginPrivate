package ru.politaboba.serverSystem.contract;

import java.util.List;

public class Agreement {
    private final String id;
    private final String partyA;
    private final String partyB;
    private final String title;
    private final List<String> pages;
    private final long timestamp;

    public Agreement(String id, String partyA, String partyB, String title, List<String> pages, long timestamp) {
        this.id = id;
        this.partyA = partyA;
        this.partyB = partyB;
        this.title = title;
        this.pages = pages;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getPartyA() { return partyA; }
    public String getPartyB() { return partyB; }
    public String getTitle() { return title; }
    public List<String> getPages() { return pages; }
    public long getTimestamp() { return timestamp; }
}