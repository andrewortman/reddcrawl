package com.andrewortman.reddcrawl.archive;

import com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface JsonArchiveEventHandler {
    void handleArchiveComplete(@Nonnull Collection<JsonNode> completedJsonNodes);
}
