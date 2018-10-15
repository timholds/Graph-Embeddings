/**
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.bench;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.neo4j.kernel.configuration.Settings.BOOLEAN;
import static org.neo4j.kernel.configuration.Settings.setting;

public final class LdbcDownloader {

    private static Path DEFAULT_TEMP_DIR;
    private static final Map<String, S3Location> FILES;

    private static final Setting<Boolean> udc = setting( "dbms.udc.enabled", BOOLEAN, "true" );

    static {
        FILES = new HashMap<>();
        FILES.put("L01", new S3Location("http://example-data.neo4j.org.s3.amazonaws.com/files/ldbc_sf001_p006_neo4j31.tgz"));
        FILES.put("L10", new S3Location("http://benchmarking-datasets.neo4j.org.s3.amazonaws.com/3.4-datasets/ldbc_sf001_p006.tgz"));
    }

    static synchronized GraphDatabaseAPI openDb() throws IOException {
        return openDb("L01");
    }

    static synchronized GraphDatabaseAPI openDb(String graphId) throws IOException {
        S3Location location = FILES.get(graphId);
        if (location == null) {
            throw new IllegalArgumentException("Unknown graph: " + graphId);
        }
        return openDb(graphId, location);
    }

    private static GraphDatabaseAPI openDb(String id, S3Location location) throws IOException {
        Path graphDir = tempDirFor("org.neo4j", "ldbc", id);
        Path graphDbDir = graphDir.resolve("graph.db");
        if (Files.isDirectory(graphDbDir)) {
            return openDb(graphDbDir);
        }
        Path zippedDb = graphDir.resolve(location.fileName);
        if (Files.isReadable(zippedDb)) {
            unzipFile(zippedDb, location);
            return openDb(id, location);
        }
        downloadFile(zippedDb, location.url);
        return openDb(id, location);
    }

    private static GraphDatabaseAPI openDb(Path dbLocation) {
        GraphDatabaseService db = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(dbLocation.toFile())
                .setConfig(GraphDatabaseSettings.pagecache_memory, "2G")
                .setConfig(GraphDatabaseSettings.allow_store_upgrade, "true")
                .setConfig(udc, "false")
                .newGraphDatabase();
        return (GraphDatabaseAPI) db;
    }

    private static void unzipFile(Path zippedDb, S3Location location)
    throws IOException {
        Path tarFile = unGzip(zippedDb, location);
        unTar(tarFile, zippedDb.getParent());
        Files.deleteIfExists(tarFile);
    }

    private static void downloadFile(Path target, URL url)
    throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(target)) {
                IOUtils.copy(in, out);
            }
        } else {
            throw new IOException("Invalid S3 response: " + responseCode + " [" + url + "]");
        }
    }

    private static Path tempDirFor(String... subDirs) throws IOException {
        Path tmpDir = getDefaultTempDir().toAbsolutePath();
        for (String subDir : subDirs) {
            tmpDir = tmpDir.resolve(subDir);
            createDir(tmpDir);
        }
        if (!Files.isWritable(tmpDir)) {
            throw new IOException("Temporary folder at [" + tmpDir + "] is not writable");
        }
        return tmpDir;
    }

    private static void createDir(Path tmpDir) throws IOException {
        try {
            Files.createDirectory(tmpDir);
        } catch (FileAlreadyExistsException faee) {
            // we want a tmp dir, if it's there, fine, if it's a file, throw
            if (!Files.isDirectory(tmpDir)) {
                throw faee;
            }
        }
    }

    private static synchronized Path getDefaultTempDir() throws IOException {
        if (DEFAULT_TEMP_DIR == null) {
            // Lazy init
            final String tempDirPath = System.getProperty("java.io.tmpdir");
            if (tempDirPath == null) {
                throw new IOException(
                        "Java has no temporary folder property (java.io.tmpdir)?");
            }
            final Path tempDirectory = Paths.get(tempDirPath);
            if (!Files.isWritable(tempDirectory)) {
                throw new IOException(
                        "Java's temporary folder not present or writeable?: "
                                + tempDirectory.toAbsolutePath());
            }
            DEFAULT_TEMP_DIR = tempDirectory;
        }

        return DEFAULT_TEMP_DIR;
    }

    private static void unTar(Path inputFile, Path targetDir)
    throws IOException {
        try (InputStream in = Files.newInputStream(inputFile);
        TarArchiveInputStream tar = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                Path outFile = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    if (!Files.isDirectory(outFile)) {
                        Files.createDirectory(outFile);
                    }
                } else {
                    try (OutputStream out = Files.newOutputStream(outFile)) {
                        IOUtils.copy(tar, out);
                    }
                }
            }
        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }

    private static Path unGzip(Path inputFile, S3Location location) throws IOException {
        String fileName = inputFile.getFileName().toString();
        assert fileName.endsWith(".tgz");
        Path targetFile = inputFile
                .getParent()
                .resolve(fileName.replace(".tgz", ".tar"));

        try (InputStream in = Files.newInputStream(inputFile);
             GZIPInputStream gzipIn = new GZIPInputStream(in);
             OutputStream out = Files.newOutputStream(targetFile)) {
            IOUtils.copy(gzipIn, out);
        } catch (EOFException e) {
            Files.deleteIfExists(inputFile);
            downloadFile(inputFile, location.url);
            return unGzip(inputFile, location);
        }

        return targetFile;
    }

    private static class S3Location {
        private URL url;
        private String fileName;

        S3Location(String location) {
            try {
                url = new URL(location);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            this.fileName = Paths.get(url.getFile()).getFileName().toString();
        }
    }
}
