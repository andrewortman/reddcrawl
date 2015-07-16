package com.andrewortman.reddcrawl.services.archive;

import com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * Interface that implements some sort of persistent archive for json objects
 */
public interface JsonArchive {
    void writeJsonNodes(@Nonnull final String archive, @Nonnull final List<JsonNode> jsonNode) throws IOException;
}
