package com.mapvcs.client;

import com.mapvcs.core.*;
import com.mapvcs.core.MapVCSProtocol.*;
import java.io.*;
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
            throw new IOException("Repository already initialized");
        }

        // 创建初始提交
        Commit initialCommit = new Commit(
                "INITIAL_COMMIT",
                branch,
                null,
                System.currentTimeMillis(),
                "system",
                "Initial repository"
        );

        // 保存状态
        saveState(initialCommit.getId());
    }

    private void loadState() {
        if (stateFile.exists()) {
            try {
                Properties props = new Properties();
                props.load(new FileInputStream(stateFile));
                currentCommitId = props.getProperty("commitId");
            } catch (IOException e) {
                currentCommitId = null;
            }
        }
    }

    private void saveState(String commitId) {
        Properties props = new Properties();
        props.setProperty("commitId", commitId);
        try (OutputStream out = new FileOutputStream(stateFile)) {
            props.store(out, "MapVCS State");
        } catch (IOException e) {
            System.err.println("Failed to save state: " + e.getMessage());
        }
    }

    public String pushChanges(String message, String author) throws Exception {
        // 1. 计算差异
        Map<String, byte[]> changes = calculateChanges();

        // 2. 创建提交对象
        Commit commit = new Commit(
                UUID.randomUUID().toString(),
                branch,
                currentCommitId,
                System.currentTimeMillis(),
                author,
                message
        );

        // 3. 序列化快照
        byte[] snapshot = createSnapshot(changes);

        // 4. 直接保存到本地存储（绕过网络）
        saveCommitLocally(commit, snapshot);

        // 5. 更新本地状态
        currentCommitId = commit.getId();
        saveState(currentCommitId);

        return commit.getId();
    }

    private void saveCommitLocally(Commit commit, byte[] snapshot) throws Exception {
        // 在实际项目中，这里会连接到本地数据库
        System.out.println("Saving commit locally: " + commit.getId());

        // 保存快照到文件系统（简化实现）
        File snapshotFile = new File(worldDir.getParentFile(), "snapshots/" + commit.getId() + ".zip");
        FileUtils.writeByteArrayToFile(snapshotFile, snapshot);
    }

    public PullResult pullUpdates() throws Exception {
        PullResult result = client.pull(branch, currentCommitId);

        if (result.hasUpdates()) {
            List<String> updatedFiles = applyChanges(result.getSnapshot());

            PullResult fullResult = new PullResult(
                    result.getNewCommitId(),
                    result.getSnapshot(),
                    updatedFiles
            );

            currentCommitId = fullResult.getNewCommitId();
            saveState(currentCommitId);

            return fullResult;
        }

        return new PullResult(
                result.getNewCommitId(),
                null,
                Collections.emptyList()
        );
    }

    private Map<String, byte[]> calculateChanges() throws IOException {
        Map<String, byte[]> changes = new HashMap<>();

        File regionDir = new File(worldDir, "region");
        if (regionDir.exists()) {
            for (File regionFile : regionDir.listFiles((dir, name) -> name.endsWith(".mca"))) {
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