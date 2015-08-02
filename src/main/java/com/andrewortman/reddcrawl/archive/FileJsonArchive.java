package com.andrewortman.reddcrawl.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

public class FileJsonArchive implements JsonArchive {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileJsonArchive.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nonnull
    private final File archiveRootDirectory;

    public FileJsonArchive(@Nonnull final File archiveRootDirectory) {
        Preconditions.checkState(archiveRootDirectory.isDirectory(), "Archive directory path is not a directory!");
        this.archiveRootDirectory = archiveRootDirectory;
    }

    @Override
    public void writeNodes(@Nonnull Multimap<String, JsonNode> nodesByArchiveName, @Nullable JsonArchiveEventHandler eventHandler) throws IOException {
        final DateTime currentTime = DateTime.now();

        for (final String archiveName : nodesByArchiveName.keySet()) {
            //first, make sure the archive folder doesn't already exist
            final File archiveFolder = new File(archiveRootDirectory, archiveName);

            if (archiveFolder.exists() && !archiveFolder.isDirectory()) {
                throw new IOException("Archive Folder named " + archiveName + " already exists but it is not a directory!");
            }

            if (!archiveFolder.exists()) {
                final boolean createdDirectory = archiveFolder.mkdir();
                if (!createdDirectory) {
                    throw new IOException("Could not create folder named " + archiveName);
                }
            }

            final String archiveFilename = "stories-" + currentTime.getMillis() + ".json.gz";
            final File archiveFile = new File(archiveFolder, archiveFilename);
            final FileOutputStream fileOutputStream = new FileOutputStream(archiveFile, true);
            final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new GZIPOutputStream(fileOutputStream), "UTF-8");

            final Iterator<JsonNode> nodeIterator = nodesByArchiveName.get(archiveName).iterator();
            while (nodeIterator.hasNext()) {
                final JsonNode nodeToWrite = nodeIterator.next();
                outputStreamWriter.write(OBJECT_MAPPER.writeValueAsString(nodeToWrite));
                if (nodeIterator.hasNext()) {
                    outputStreamWriter.write('\n');
                }
            }

            outputStreamWriter.close();
            fileOutputStream.close();

            LOGGER.info("Wrote archive file " + archiveFile.getAbsolutePath());
            if (eventHandler != null) {
                eventHandler.handleArchiveComplete(nodesByArchiveName.get(archiveName));
            }
        }
    }
}
