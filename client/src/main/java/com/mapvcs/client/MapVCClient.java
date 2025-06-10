package com.mapvcs.client;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.*;

import static com.mapvcs.core.MapVCSProtocol.*;

@Command(name = "mapvcs", mixinStandardHelpOptions = true, version = "MapVCS 1.0",
        description = "Minecraft Map Version Control System")
public class MapVCClient implements Callable<Integer> {

    @Option(names = {"-s", "--server"}, description = "Server address")
    private String serverAddress = "localhost:9090";

    @Option(names = {"-b", "--branch"}, description = "Branch name")
    private String branch = "main";

    @Parameters(index = "0", description = "Minecraft world directory")
    private File worldDir;

    private MapVCSService service;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MapVCClient()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!worldDir.isDirectory() || !new File(worldDir, "level.dat").exists()) {
            System.err.println("Invalid Minecraft world directory");
            return 1;
        }

        service = new MapVCSService(serverAddress, worldDir, branch);

        System.out.println("MapVCS Client connected to " + serverAddress);
        System.out.println("World: " + worldDir.getAbsolutePath());
        System.out.println("Branch: " + branch);
        System.out.println("Current commit: " + service.getCurrentCommitId());

        return 0;
    }

    @Command(name = "push", description = "Push local changes to server (local operation only)")
    public void push(
            @Option(names = {"-m", "--message"}, description = "Commit message", required = true) String message,
            @Option(names = {"-a", "--author"}, description = "Author name", required = true) String author
    ) {
        try {
            // 确保是本地连接
            if (!service.isLocalConnection()) {
                System.err.println("Push operations are only allowed from localhost");
                return;
            }

            String commitId = service.pushChanges(message, author);
            System.out.println("Pushed successfully! Commit ID: " + commitId);
        } catch (Exception e) {
            System.err.println("Push failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Command(name = "pull", description = "Pull updates from server")
    public void pull() {
        try {
            PullResult result = service.pullUpdates();
            if (result.hasUpdates()) {
                System.out.println("Pulled successfully!");
                System.out.println("New commit: " + result.getNewCommitId());
                System.out.println("Updated files: " + result.getUpdatedFiles());
            } else {
                System.out.println("Already up-to-date");
            }
        } catch (Exception e) {
            System.err.println("Pull failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Command(name = "history", description = "Show commit history")
    public void history(
            @Option(names = {"-l", "--limit"}, description = "Number of commits to show", defaultValue = "10") int limit
    ) {
        try {
            List<Commit> commits = service.getCommitHistory(limit);
            System.out.println("Commit History:");
            for (Commit commit : commits) {
                System.out.printf("[%s] %s - %s\n  %s\n",
                        commit.getId().substring(0, 8),
                        commit.getAuthor(),
                        new java.util.Date(commit.getTimestamp()),
                        commit.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Failed to get history: " + e.getMessage());
        }
    }

    @Command(name = "init", description = "Initialize new repository")
    public void init() {
        try {
            service.initializeRepository();
            System.out.println("Repository initialized successfully");
        } catch (Exception e) {
            System.err.println("Initialization failed: " + e.getMessage());
        }
    }
}