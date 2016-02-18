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
import com.google.api.services.storage.model.ComposeRequest;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    // GCS allows up to compose a file from many components by concatenating them
    // We do this by creating a 'composed.json.gz" file in each archive that is the
    // concatenation of all the files in that archive - this makes spark jobs far more efficient
    private void recomposeArchive(@Nonnull final String archiveName, @Nonnull final String lastArchivePath) {
        final List<ComposeRequest.SourceObjects> objectsList = new ArrayList<>(2);
        objectsList.add(new ComposeRequest.SourceObjects().setName(archiveName + "/composed.json.gz"));
        objectsList.add(new ComposeRequest.SourceObjects().setName(lastArchivePath));

        final ComposeRequest request = new ComposeRequest()
                .setSourceObjects(objectsList)
                .setDestination(new StorageObject().setName(archiveName+"/composed.json.gz").setBucket(this.rootBucketName));

        try {
            StorageObject execute = this.googleStorage.objects().compose(this.rootBucketName, archiveName + "/composed.json.gz", request).execute();
            LOGGER.info("Composition complete - component count for composition file " + execute.getName() + " is " + execute.getComponentCount());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void writeNodes(@Nonnull Multimap<String, JsonNode> nodesByArchiveName, @Nullable JsonArchiveEventHandler eventHandler) throws IOException {
        final DateTime currentTime = DateTime.now();
        final List<String> archivesUpdated = new ArrayList<String>();
        for (final String archiveName : nodesByArchiveName.keySet()) {
            //create a new buffer for this archive
            final ByteArrayOutputStream archiveOutputBuffer = new ByteArrayOutputStream();
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

            StorageObject uploadedStorageObject = this.googleStorage.objects()
                    .insert(this.rootBucketName, new StorageObject().setName(archiveObjectName), googleInputStreamContent)
                    .execute();

            if (uploadedStorageObject != null) {
                LOGGER.info("Wrote archive file " + rootBucketName + "/" + uploadedStorageObject.getName());
                if (eventHandler != null) {
                    eventHandler.handleArchiveComplete(nodesByArchiveName.get(archiveName));
                }

                LOGGER.info("Recomposing archive " + rootBucketName + "/" + uploadedStorageObject.getName());
                recomposeArchive(archiveName, uploadedStorageObject.getName());
            }
        }
    }
}
