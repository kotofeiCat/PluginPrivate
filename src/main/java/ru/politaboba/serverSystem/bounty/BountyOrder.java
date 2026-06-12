package ru.politaboba.serverSystem.bounty;

import java.util.UUID;

public class BountyOrder {
    private final String targetName;
    private final UUID targetUUID;
    private final String creatorName;
    private final int rewardAmount;

    public BountyOrder(String targetName, UUID targetUUID, String creatorName, int rewardAmount) {
        this.targetName = targetName;
        this.targetUUID = targetUUID;
        this.creatorName = creatorName;
        this.rewardAmount = rewardAmount;
    }

    public String getTargetName() { return targetName; }
    public UUID getTargetUUID() { return targetUUID; }
    public String getCreatorName() { return creatorName; }
    public int getRewardAmount() { return rewardAmount; }
}