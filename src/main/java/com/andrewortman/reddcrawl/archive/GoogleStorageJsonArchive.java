package com.andrewortman.reddcrawl.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.collect.Multimap;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

public class GoogleStorageJsonArchive implements JsonArchive {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleStorageJsonArchive.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nonnull
    private final Storage googleStorage;

    @Nonnull
    private final String rootBucketName;

    public GoogleStorageJsonArchive(@Nonnull final String rootBucketName) throws GeneralSecurityException, IOException {
        final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredential credential = GoogleCredential
                .getApplicationDefault(httpTransport, JSON_FACTORY)
                .createScoped(StorageScopes.all());

        this.googleStorage = new Storage.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("reddcrawl-worker")
                .build();

        this.rootBucketName = rootBucketName;
    }

    @Override
    public void writeNodes(@Nonnull Multimap<String, JsonNode> nodesByArchiveName, @Nullable JsonArchiveEventHandler eventHandler) throws IOException {
        final DateTime currentTime = DateTime.now();
        for (final String archiveName : nodesByArchiveName.keySet()) {
            //create a new buffer for this archive
            final ByteArrayOutputStream archiveOutputBuffer = new ByteArrayOutputStream();
            //first, make sure the archive folder doesn't already exist
            final String archiveObjectName = archiveName + "/stories-" + currentTime.getMillis() + ".json.gz";
            final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new GZIPOutputStream(archiveOutputBuffer), "UTF-8");

            final Iterator<JsonNode> nodeIterator = nodesByArchiveName.get(archiveName).iterator();
            while (nodeIterator.hasNext()) {
                final JsonNode nodeToWrite = nodeIterator.next();
                outputStreamWriter.write(OBJECT_MAPPER.writeValueAsString(nodeToWrite));
                if (nodeIterator.hasNext()) {
                    outputStreamWriter.write('\n');
                }
            }
            outputStreamWriter.close();

            final ByteArrayInputStream bufferInputStream = new ByteArrayInputStream(archiveOutputBuffer.toByteArray());
            final InputStreamContent googleInputStreamContent = new InputStreamContent("application/x-gzip", bufferInputStream);

            StorageObject uploadedStorageObject = googleStorage.objects()
                    .insert(this.rootBucketName, new StorageObject().setName(archiveObjectName), googleInputStreamContent)
                    .execute();

            if (uploadedStorageObject != null) {
                LOGGER.info("Wrote archive file " + rootBucketName + "/" + uploadedStorageObject.getName());
                if (eventHandler != null) {
                    eventHandler.handleArchiveComplete(nodesByArchiveName.get(archiveName));
                }
            }
        }
    }
}