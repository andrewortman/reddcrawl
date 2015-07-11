package com.andrewortman.reddcrawl.services.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class FileJsonArchive implements JsonArchive {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileJsonArchive.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nonnull
    private final File archiveDirectory;

    public FileJsonArchive(@Nonnull final File archiveDirectory) {
        Preconditions.checkState(archiveDirectory.isDirectory(), "Archive directory path is not a directory!");
        this.archiveDirectory = archiveDirectory;
    }

    @Override
    public void writeJsonNodes(@Nonnull final String archive, @Nonnull final List<JsonNode> jsonNodes) throws IOException {
        final File archiveFile = new File(this.archiveDirectory, archive + ".json.gz");

        if (!archiveFile.exists()) {
            LOGGER.debug("Creating archive file: " + archiveFile.getAbsolutePath());
            archiveFile.createNewFile();
        }


        final FileOutputStream fileOutputStream = new FileOutputStream(archiveFile, true);
        final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new GZIPOutputStream(fileOutputStream), "UTF-8");

        for (final JsonNode jsonNode : jsonNodes) {
            final String mapped = OBJECT_MAPPER.writeValueAsString(jsonNode);
            outputStreamWriter.write(mapped);
            outputStreamWriter.write('\n');
        }

        outputStreamWriter.close();
        fileOutputStream.close();
        LOGGER.debug("Closing stream to archive file: " + archiveFile.getAbsolutePath());
    }
}
