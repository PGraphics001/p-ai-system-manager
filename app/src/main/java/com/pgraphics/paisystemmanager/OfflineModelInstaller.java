package com.pgraphics.paisystemmanager;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class OfflineModelInstaller {

    private static final String TAG = "PAI_MODEL_INSTALLER";

    // Model release information
    private static final String MODEL_VERSION = "1.2";

    private static final String MODEL_URL =
            "https://github.com/PGraphics001/p-ai-system-manager/releases/download/v1.1-model/vosk-model-en-us-0.22-lgraph.1.zip";

    // Internal storage directories
    private static final String MODEL_DIRECTORY = "model-en-us";

    private static final String TEMP_DIRECTORY =
            "model-en-us.installing";

    private static final String BACKUP_DIRECTORY =
            "model-en-us.backup";

    private static final String ZIP_FILENAME =
            "vosk-model-en-us-0.22-lgraph.zip";

    private static final String MARKER_FILENAME =
            ".installation_complete";

    // Network configuration
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 120000;

    // Security limits
    private static final long MAX_DOWNLOAD_SIZE =
            200L * 1024L * 1024L;

    private static final long MAX_EXTRACTED_SIZE =
            512L * 1024L * 1024L;

    private static final int BUFFER_SIZE = 8192;

    private OfflineModelInstaller() {
        // Utility class.
    }

    /**
     * Returns the permanent model directory.
     *
     * This method remains compatible with VoiceRecognitionService.
     */
    public static File getModelDirectory(Context context) {

        return new File(
                context.getApplicationContext().getFilesDir(),
                MODEL_DIRECTORY
        );
    }

    /**
     * Checks whether the current model is installed
     * and belongs to the expected model version.
     */
    public static boolean isModelInstalled(Context context) {

        File directory = getModelDirectory(context);

        File marker = new File(
                directory,
                MARKER_FILENAME
        );

        if (!directory.isDirectory() || !marker.isFile()) {
            return false;
        }

        try (InputStream input = new FileInputStream(marker)) {

            byte[] buffer = new byte[64];

            int length = input.read(buffer);

            if (length <= 0) {
                return false;
            }

            String installedVersion = new String(
                    buffer,
                    0,
                    length,
                    StandardCharsets.UTF_8
            ).trim();

            return MODEL_VERSION.equals(installedVersion)
                    && validateModel(directory);

        } catch (IOException exception) {

            Log.e(TAG, "Model verification failed.", exception);

            return false;
        }
    }

    /**
     * Installs the new Vosk model.
     *
     * MUST be called from a background thread.
     */
    public static File install(Context context) throws IOException {

        Context appContext = context.getApplicationContext();

        if (isModelInstalled(appContext)) {

            Log.i(TAG, "Vosk model 1.2 already installed.");

            return getModelDirectory(appContext);
        }

        File root = appContext.getFilesDir();

        File finalDirectory = getModelDirectory(appContext);

        File temporaryDirectory = new File(
                root,
                TEMP_DIRECTORY
        );

        File backupDirectory = new File(
                root,
                BACKUP_DIRECTORY
        );

        File temporaryZip = new File(
                root,
                ZIP_FILENAME
        );

        boolean oldModelMoved = false;
        boolean newModelMoved = false;

        /*
         * Recover from an interrupted previous installation.
         */
        recoverPreviousInstallation(
                finalDirectory,
                backupDirectory
        );

        try {

            cleanup(temporaryZip);
            cleanup(temporaryDirectory);

            /*
             * Download the model.
             */
            downloadModel(temporaryZip);

            /*
             * Extract the downloaded archive.
             */
            extractModel(
                    temporaryZip,
                    temporaryDirectory
            );

            /*
             * Locate the actual model root.
             */
            File modelRoot = locateModelDirectory(
                    temporaryDirectory
            );

            if (modelRoot == null || !validateModel(modelRoot)) {

                throw new IOException(
                        "The downloaded Vosk model is invalid or incomplete."
                );
            }

            /*
             * Write installation version marker.
             */
            File marker = new File(
                    modelRoot,
                    MARKER_FILENAME
            );

            try (FileOutputStream output =
                         new FileOutputStream(marker)) {

                output.write(
                        MODEL_VERSION.getBytes(StandardCharsets.UTF_8)
                );

                output.flush();
                output.getFD().sync();
            }

            /*
             * Preserve the existing installation.
             */
            if (finalDirectory.exists()) {

                if (!finalDirectory.renameTo(backupDirectory)) {

                    throw new IOException(
                            "Unable to preserve the previous model."
                    );
                }

                oldModelMoved = true;
            }

            /*
             * Move the new model into permanent storage.
             */
            if (!modelRoot.renameTo(finalDirectory)) {

                throw new IOException(
                        "Unable to install the new Vosk model."
                );
            }

            newModelMoved = true;

            /*
             * Verify the final installation.
             */
            if (!isModelInstalled(appContext)) {

                throw new IOException(
                        "Final model verification failed."
                );
            }

            Log.i(
                    TAG,
                    "Vosk model 1.2 installed and verified successfully."
            );

            /*
             * Installation succeeded.
             * The old backup is no longer required.
             */
            cleanup(backupDirectory);

            return finalDirectory;

        } catch (IOException exception) {

            Log.e(TAG, "Model installation failed.", exception);

            /*
             * Remove the failed new installation.
             */
            if (newModelMoved) {
                cleanupQuietly(finalDirectory);
            }

            /*
             * Restore the previous installation.
             */
            if (oldModelMoved && backupDirectory.exists()) {

                if (!backupDirectory.renameTo(finalDirectory)) {

                    IOException restoreError = new IOException(
                            "Could not restore the previous Vosk model."
                    );

                    exception.addSuppressed(restoreError);

                    Log.e(TAG, restoreError.getMessage());
                }
            }

            throw exception;

        } finally {

            cleanupQuietly(temporaryZip);

            cleanupQuietly(temporaryDirectory);
        }
    }

    /**
     * Recovers the previous model if the application
     * was interrupted during an earlier installation.
     */
    private static void recoverPreviousInstallation(
            File finalDirectory,
            File backupDirectory
    ) throws IOException {

        if (!backupDirectory.exists()) {
            return;
        }

        if (!finalDirectory.exists()) {

            if (!backupDirectory.renameTo(finalDirectory)) {

                throw new IOException(
                        "Unable to recover the previous model."
                );
            }

            Log.i(TAG, "Previous model successfully recovered.");

        } else if (validateModel(finalDirectory)) {

            cleanup(backupDirectory);

        } else {

            cleanup(finalDirectory);

            if (!backupDirectory.renameTo(finalDirectory)) {

                throw new IOException(
                        "Unable to restore the backup model."
                );
            }
        }
    }

    /**
     * Downloads the model from GitHub Releases.
     */
    private static void downloadModel(File destination)
            throws IOException {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(MODEL_URL);

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            connection.setConnectTimeout(CONNECT_TIMEOUT);

            connection.setReadTimeout(READ_TIMEOUT);

            connection.setInstanceFollowRedirects(true);

            connection.setRequestProperty(
                    "Accept",
                    "application/octet-stream"
            );

            connection.setRequestProperty(
                    "User-Agent",
                    "PAI-System-Manager-Android"
            );

            connection.connect();

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {

                throw new IOException(
                        "GitHub download failed. HTTP status: "
                                + responseCode
                );
            }

            long contentLength = connection.getContentLengthLong();

            if (contentLength > MAX_DOWNLOAD_SIZE) {

                throw new IOException(
                        "Download exceeds the permitted size."
                );
            }

            long downloadedBytes = 0;

            try (
                    InputStream input = new BufferedInputStream(
                            connection.getInputStream()
                    );

                    FileOutputStream fileOutput =
                            new FileOutputStream(destination);

                    BufferedOutputStream output =
                            new BufferedOutputStream(fileOutput)
            ) {

                byte[] buffer = new byte[BUFFER_SIZE];

                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {

                    downloadedBytes += bytesRead;

                    if (downloadedBytes > MAX_DOWNLOAD_SIZE) {

                        throw new IOException(
                                "Download size limit exceeded."
                        );
                    }

                    output.write(buffer, 0, bytesRead);
                }

                output.flush();
                fileOutput.getFD().sync();
            }

            if (downloadedBytes == 0) {

                throw new IOException(
                        "Downloaded ZIP file is empty."
                );
            }

            if (contentLength >= 0
                    && downloadedBytes != contentLength) {

                throw new IOException(
                        "Downloaded file size does not match the server response."
                );
            }

            Log.i(
                    TAG,
                    "Model downloaded successfully: "
                            + downloadedBytes + " bytes."
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Safely extracts the model archive.
     *
     * Prevents ZIP path traversal and oversized extraction.
     */
    private static void extractModel(
            File zipFile,
            File destinationDirectory
    ) throws IOException {

        if (!destinationDirectory.mkdirs()
                && !destinationDirectory.isDirectory()) {

            throw new IOException(
                    "Unable to create extraction directory."
            );
        }

        String canonicalRoot =
                destinationDirectory.getCanonicalPath();

        String canonicalPrefix =
                canonicalRoot + File.separator;

        long extractedBytes = 0;

        try (
                ZipInputStream zipInput = new ZipInputStream(
                        new BufferedInputStream(
                                new FileInputStream(zipFile)
                        )
                )
        ) {

            ZipEntry entry;

            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zipInput.getNextEntry()) != null) {

                String entryName = entry.getName();

                if (entryName == null
                        || entryName.isEmpty()) {

                    throw new IOException(
                            "Invalid ZIP entry."
                    );
                }

                File outputFile = new File(
                        destinationDirectory,
                        entryName
                );

                String canonicalOutput =
                        outputFile.getCanonicalPath();

                if (!canonicalOutput.startsWith(canonicalPrefix)) {

                    throw new IOException(
                            "Unsafe ZIP path detected: " + entryName
                    );
                }

                if (entry.isDirectory()) {

                    if (!outputFile.mkdirs()
                            && !outputFile.isDirectory()) {

                        throw new IOException(
                                "Unable to create model directory."
                        );
                    }

                } else {

                    File parent = outputFile.getParentFile();

                    if (parent != null
                            && !parent.mkdirs()
                            && !parent.isDirectory()) {

                        throw new IOException(
                                "Unable to create model folder."
                        );
                    }

                    try (
                            FileOutputStream fileOutput =
                                    new FileOutputStream(outputFile);

                            BufferedOutputStream output =
                                    new BufferedOutputStream(fileOutput)
                    ) {

                        int bytesRead;

                        while ((bytesRead =
                                        zipInput.read(buffer)) != -1) {

                            extractedBytes += bytesRead;

                            if (extractedBytes >
                                    MAX_EXTRACTED_SIZE) {

                                throw new IOException(
                                        "Extracted model exceeds the size limit."
                                );
                            }

                            output.write(buffer, 0, bytesRead);
                        }

                        output.flush();
                    }
                }

                zipInput.closeEntry();
            }
        }

        if (extractedBytes == 0) {

            throw new IOException(
                    "ZIP archive contains no extractable model data."
            );
        }

        Log.i(
                TAG,
                "Model extracted successfully: "
                        + extractedBytes + " bytes."
        );
    }

    /**
     * Finds the actual Vosk model root.
     *
     * Supports archives with one parent directory.
     */
    private static File locateModelDirectory(File directory) {

        if (validateModel(directory)) {
            return directory;
        }

        File[] children = directory.listFiles();

        if (children == null) {
            return null;
        }

        for (File child : children) {

            if (child.isDirectory()
                    && validateModel(child)) {

                return child;
            }
        }

        return null;
    }

    /**
     * Validates essential files in the Vosk Lgraph model.
     */
    private static boolean validateModel(File directory) {

        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        return isValidFile(directory, "am/final.mdl")
                && isValidFile(directory, "conf/mfcc.conf")
                && isValidFile(directory, "conf/model.conf")
                && isValidFile(directory, "graph/Gr.fst")
&& isValidFile(directory, "graph/HCLr.fst")
                && isValidFile(
                        directory,
                        "graph/phones/word_boundary.int"
                );
    }

    /**
     * Checks that a required model file exists and is non-empty.
     */
    private static boolean isValidFile(
            File directory,
            String relativePath
    ) {

        File file = new File(directory, relativePath);

        return file.isFile() && file.length() > 0;
    }

    /**
     * Deletes a file or directory recursively.
     */
    private static void cleanup(File file) throws IOException {

        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {

            File[] children = file.listFiles();

            if (children == null) {

                throw new IOException(
                        "Unable to list directory: "
                                + file.getAbsolutePath()
                );
            }

            for (File child : children) {
                cleanup(child);
            }
        }

        if (!file.delete()) {

            throw new IOException(
                    "Unable to delete: "
                            + file.getAbsolutePath()
            );
        }
    }

    /**
     * Best-effort cleanup that does not hide
     * the original installation error.
     */
    private static void cleanupQuietly(File file) {

        try {

            cleanup(file);

        } catch (IOException exception) {

            Log.w(
                    TAG,
                    "Temporary cleanup failed: "
                            + file.getAbsolutePath(),
                    exception
            );
        }
    }
}