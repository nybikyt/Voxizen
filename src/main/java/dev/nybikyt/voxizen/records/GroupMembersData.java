package dev.nybikyt.voxizen.records;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupMembersData {

    public static final Map<UUID, Set<UUID>> groupMembers = new ConcurrentHashMap<>();

}
