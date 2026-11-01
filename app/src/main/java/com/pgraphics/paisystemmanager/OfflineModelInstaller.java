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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class OfflineModelInstaller {

    private static final String TAG = "PAI_MODEL_INSTALLER";

    private static final String MODEL_VERSION = "1.1";

    private static final String MODEL_URL =
            "https://github.com/PGraphics001/p-ai-system-manager/releases/download/v1.1-model/vosk-model-small-en-us-0.15.zip";

    private static final String MODEL_DIRECTORY =
            "model-en-us";

    private static final String ZIP_FILENAME =
            "vosk-model.zip";

    private static final String MARKER_FILENAME =
            ".installation_complete";

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    private static final long MAX_DOWNLOAD_SIZE =
            150L * 1024L * 1024L;

    private static final long MAX_EXTRACTED_SIZE =
            300L * 1024L * 1024L;

    private OfflineModelInstaller() {
        // Prevent object creation.
    }

    /**
     * Returns the installed model directory.
     */
    public static File getModelDirectory(Context context) {

        return new File(
                context.getApplicationContext().getFilesDir(),
                MODEL_DIRECTORY
        );
    }

    /**
     * Checks whether the model is already installed.
     */
    public static boolean isModelInstalled(Context context) {

        File modelDirectory = getModelDirectory(context);

        File marker = new File(
                modelDirectory,
                MARKER_FILENAME
        );

        if (!modelDirectory.isDirectory() || !marker.isFile()) {
            return false;
        }

        try (InputStream input = new FileInputStream(marker)) {

            byte[] buffer = new byte[32];

            int length = input.read(buffer);

            String installedVersion = length > 0
                    ? new String(buffer, 0, length).trim()
                    : "";

            return MODEL_VERSION.equals(installedVersion)
                    && validateModel(modelDirectory);

        } catch (IOException exception) {

            Log.e(TAG, "Model verification failed.", exception);

            return false;
        }
    }

    /**
     * Downloads, extracts and installs the model.
     *
     * IMPORTANT:
     * Call this method from a background thread.
     */
    public static File install(Context context) throws IOException {

        Context appContext = context.getApplicationContext();

        if (isModelInstalled(appContext)) {

            Log.i(TAG, "Offline model already installed.");

            return getModelDirectory(appContext);
        }

        File rootDirectory = appContext.getFilesDir();

        File temporaryZip = new File(
                rootDirectory,
                ZIP_FILENAME
        );

        File temporaryDirectory = new File(
                rootDirectory,
                MODEL_DIRECTORY + ".installing"
        );

        File finalDirectory = getModelDirectory(appContext);

        try {

            deleteRecursively(temporaryDirectory);

            deleteRecursively(temporaryZip);

            downloadModel(temporaryZip);

            extractModel(
                    temporaryZip,
                    temporaryDirectory
            );

            File modelRoot = locateModelDirectory(
                    temporaryDirectory
            );

            if (modelRoot == null || !validateModel(modelRoot)) {

                throw new IOException(
                        "Downloaded Vosk model is incomplete or invalid."
                );
            }

            File marker = new File(
                    modelRoot,
                    MARKER_FILENAME
            );

            try (FileOutputStream output =
                         new FileOutputStream(marker)) {

                output.write(MODEL_VERSION.getBytes("UTF-8"));
                output.flush();
            }

            deleteRecursively(finalDirectory);

            if (!modelRoot.renameTo(finalDirectory)) {

                throw new IOException(
                        "Unable to move model into permanent storage."
                );
            }

            if (!isModelInstalled(appContext)) {

                deleteRecursively(finalDirectory);

                throw new IOException(
                        "Final model verification failed."
                );
            }

            Log.i(TAG, "Offline Vosk model installed successfully.");

            return finalDirectory;

        } finally {

            deleteRecursively(temporaryZip);

            deleteRecursively(temporaryDirectory);
        }
    }

    /**
     * Downloads the model ZIP from GitHub Releases.
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

            connection.connect();

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {

                throw new IOException(
                        "Model download failed. HTTP status: "
                                + responseCode
                );
            }

            long contentLength = connection.getContentLengthLong();

            if (contentLength > MAX_DOWNLOAD_SIZE) {

                throw new IOException(
                        "Model download exceeds the permitted size."
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

                byte[] buffer = new byte[8192];

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
            }

            if (downloadedBytes == 0) {

                throw new IOException(
                        "The downloaded model file is empty."
                );
            }

            Log.i(
                    TAG,
                    "Model downloaded: " + downloadedBytes + " bytes"
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Safely extracts the downloaded ZIP file.
     * Prevents ZIP path traversal attacks.
     */
    private static void extractModel(
            File zipFile,
            File destinationDirectory
    ) throws IOException {

        if (!destinationDirectory.mkdirs()
                && !destinationDirectory.isDirectory()) {

            throw new IOException(
                    "Unable to create temporary model directory."
            );
        }

        String canonicalDestination =
                destinationDirectory.getCanonicalPath()
                        + File.separator;

        long extractedBytes = 0;

        try (
                ZipInputStream zipInput = new ZipInputStream(
                        new BufferedInputStream(
                                new FileInputStream(zipFile)
                        )
                )
        ) {

            ZipEntry entry;

            byte[] buffer = new byte[8192];

            while ((entry = zipInput.getNextEntry()) != null) {

                File outputFile = new File(
                        destinationDirectory,
                        entry.getName()
                );

                String canonicalOutput =
                        outputFile.getCanonicalPath();

                if (!canonicalOutput.startsWith(
                        canonicalDestination
                )) {

                    throw new IOException(
                            "Unsafe ZIP entry detected."
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
                                        "Extracted model exceeds size limit."
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

        Log.i(
                TAG,
                "Model extracted: " + extractedBytes + " bytes"
        );
    }

    /**
     * Locates the actual Vosk model root.
     *
     * Supports ZIP files containing an extra parent folder.
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

            if (child.isDirectory() && validateModel(child)) {
                return child;
            }
        }

        return null;
    }

    /**
     * Validates the essential Vosk model directories.
     */
    private static boolean validateModel(File directory) {

        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        return containsFile(directory, "am")
                && containsFile(directory, "conf")
                && containsFile(directory, "graph");
    }

    private static boolean containsFile(
            File directory,
            String childName
    ) {

        File child = new File(directory, childName);

        return child.exists();
    }

    /**
     * Deletes temporary files and directories.
     */
    private static void deleteRecursively(File file)
            throws IOException {

        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {

            File[] children = file.listFiles();

            if (children != null) {

                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (!file.delete()) {

            throw new IOException(
                    "Unable to delete temporary file: "
                            + file.getAbsolutePath()
            );
        }
    }
}