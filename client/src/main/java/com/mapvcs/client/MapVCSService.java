package com.mapvcs.client;

import com.mapvcs.core.MapVCSProtocol.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.io.*;

public class MapVCSService {
    private final String serverAddress;
    private final File worldDir;
    private final String branch;
    private final File stateFile;
    private String currentCommitId;
    private final MapVCSClient client;

    public MapVCSService(String serverAddress, File worldDir, String branch) {
        this.serverAddress = serverAddress;
        this.worldDir = worldDir;
        this.branch = branch;
        this.stateFile = new File(worldDir, ".mapvcs_state");
        this.client = new MapVCSClient(serverAddress);
        loadState();
    }

    public boolean isLocalConnection() {
        return serverAddress.startsWith("localhost") || serverAddress.startsWith("127.0.0.1");
    }

    public void initializeRepository() throws IOException {
        if (stateFile.exists()) {
            throw new IOException("Repository already initialized. State file exists: " + stateFile.getAbsolutePath());
        }

        // 确保必要的目录结构存在
        File snapshotsDir = new File(worldDir.getParentFile(), "snapshots");
        if (!snapshotsDir.exists() && !snapshotsDir.mkdirs()) {
            throw new IOException("Failed to create snapshots directory: " + snapshotsDir.getAbsolutePath());
        }

        // 创建初始提交
        Commit initialCommit = new Commit(
                "INITIAL_" + UUID.randomUUID().toString().substring(0, 8),
                branch,
                null,
                System.currentTimeMillis(),
                "system",
                "Initial repository"
        );

        // 保存初始空快照
        try {
            byte[] emptySnapshot = createSnapshot(Collections.emptyMap());
            saveCommitLocally(initialCommit, emptySnapshot);
        } catch (Exception e) {
            throw new IOException("Failed to create initial snapshot", e);
        }

        // 保存状态
        saveState(initialCommit.getId());
        currentCommitId = initialCommit.getId();

        System.out.println("Repository initialized with commit ID: " + currentCommitId);
    }

    private void saveCommitLocally(Commit commit, byte[] snapshot) throws IOException {
        File snapshotsDir = new File(worldDir.getParentFile(), "snapshots");
        File snapshotFile = new File(snapshotsDir, commit.getId() + ".zip");

        if (!snapshotsDir.exists() && !snapshotsDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + snapshotsDir.getAbsolutePath());
        }

        try {
            FileUtils.writeByteArrayToFile(snapshotFile, snapshot);
            System.out.println("Saved snapshot: " + snapshotFile.getAbsolutePath());
        } catch (IOException e) {
            throw new IOException("Failed to write snapshot file: " + snapshotFile.getAbsolutePath(), e);
        }
    }

    private void loadState() {
        if (stateFile.exists()) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(stateFile.toPath()));
                currentCommitId = props.getProperty("commitId");
            } catch (IOException e) {
                currentCommitId = null;
            }
        }
    }

    private void saveState(String commitId) {
        Properties props = new Properties();
        props.setProperty("commitId", commitId);
        try (OutputStream out = Files.newOutputStream(stateFile.toPath())) {
            props.store(out, "MapVCS State");
        } catch (IOException e) {
            System.err.println("Failed to save state: " + e.getMessage());
        }
    }

    public String pushChanges(String message, String author) throws Exception {
        Map<String, byte[]> changes = calculateChanges();

        Commit commit = new Commit(
                UUID.randomUUID().toString(),
                branch,
                currentCommitId,
                System.currentTimeMillis(),
                author,
                message
        );

        byte[] snapshot = createSnapshot(changes);

        saveCommitLocally(commit, snapshot);

        currentCommitId = commit.getId();
        saveState(currentCommitId);

        return commit.getId();
    }

    private byte[] mergeChunk(byte[] base, byte[] local, byte[] remote) {
        return (local != null) ? local : remote;
    }

    public PullResult pullUpdates() throws Exception {
        PullResult result = client.pull(branch, currentCommitId);

        if (result.hasUpdates()) {
            Map<String, byte[]> localChanges = calculateChanges();
            byte[] baseSnapshot = getLocalSnapshot(currentCommitId);
            Map<String, byte[]> remoteChanges = extractSnapshot(result.getSnapshot());

            Map<String, byte[]> merged = new HashMap<>();
            for (String file : remoteChanges.keySet()) {
                byte[] base = extractFile(baseSnapshot, file);
                byte[] local = localChanges.get(file);
                byte[] remote = remoteChanges.get(file);

                merged.put(file, mergeChunk(base, local, remote));
            }

            List<String> updatedFiles = applyChanges(createSnapshot(merged));
            currentCommitId = result.getNewCommitId();
            saveState(currentCommitId);

            return new PullResult(
                    result.getNewCommitId(),
                    result.getSnapshot(),
                    updatedFiles
            );
        }

        return result;
    }

    private byte[] getLocalSnapshot(String commitId) throws IOException {
        File snapshotFile = new File(worldDir.getParentFile(), "snapshots/" + commitId + ".zip");
        return FileUtils.readFileToByteArray(snapshotFile);
    }

    private byte[] extractFile(byte[] snapshot, String fileName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(snapshot))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (fileName.equals(entry.getName())) {
                    return IOUtils.toByteArray(zis);
                }
            }
        }
        return null;
    }

    private Map<String, byte[]> extractSnapshot(byte[] snapshot) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(snapshot))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.put(entry.getName(), IOUtils.toByteArray(zis));
            }
        }
        return files;
    }

    private Map<String, byte[]> calculateChanges() throws IOException {
        Map<String, byte[]> changes = new HashMap<>();

        File regionDir = new File(worldDir, "region");
        if (regionDir.exists()) {
            for (File regionFile : Objects.requireNonNull(regionDir.listFiles((dir, name) -> name.endsWith(".mca")))) {
                byte[] currentData = FileUtils.readFileToByteArray(regionFile);
                changes.put("region/" + regionFile.getName(), currentData);
            }
        }

        for (String file : new String[]{"level.dat", "level.dat_old", "session.lock"}) {
            File f = new File(worldDir, file);
            if (f.exists()) {
                changes.put(file, FileUtils.readFileToByteArray(f));
            }
        }

        return changes;
    }

    private byte[] createSnapshot(Map<String, byte[]> changes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : changes.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private List<String> applyChanges(byte[] snapshot) throws IOException {
        List<String> updatedFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(snapshot))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                File outputFile = new File(worldDir, fileName);
                FileUtils.forceMkdirParent(outputFile);
                FileUtils.copyToFile(zis, outputFile);
                zis.closeEntry();
                updatedFiles.add(fileName);
            }
        }
        return updatedFiles;
    }

    public List<Commit> getCommitHistory(int limit) throws Exception {
        return client.getCommitHistory(branch, limit);
    }

    public String getCurrentCommitId() {
        return currentCommitId != null ? currentCommitId : "N/A";
    }
}