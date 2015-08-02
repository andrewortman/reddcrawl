package com.andrewortman.reddcrawl.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Multimap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Interface that implements some sort of persistent archive for json objects
 */
public interface JsonArchive {
    void writeNodes(@Nonnull final Multimap<String, JsonNode> nodesByArchiveName, @Nullable JsonArchiveEventHandler eventHandler) throws IOException;
}
