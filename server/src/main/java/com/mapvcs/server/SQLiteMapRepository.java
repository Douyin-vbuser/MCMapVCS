package com.mapvcs.server;

import java.sql.*;
import java.util.*;
import java.io.*;
import static com.mapvcs.core.MapVCSProtocol.*;

public class SQLiteMapRepository implements MapRepository {
    private static final String DB_PATH = "mapvcs.db";
    private Connection conn;

    public SQLiteMapRepository() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            initDatabase();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void initDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 创建表结构
            stmt.execute("CREATE TABLE IF NOT EXISTS commits (" +
                    "id TEXT PRIMARY KEY, " +
                    "branch TEXT NOT NULL, " +
                    "parent TEXT, " +
                    "timestamp INTEGER, " +
                    "author TEXT, " +
                    "message TEXT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS snapshots (" +
                    "commit_id TEXT PRIMARY KEY, " +
                    "data BLOB, " +
                    "FOREIGN KEY(commit_id) REFERENCES commits(id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS branches (" +
                    "name TEXT PRIMARY KEY, " +
                    "head_commit TEXT)");
        }
    }

    @Override
    public void saveCommit(Commit commit, byte[] snapshot) throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO commits(id, branch, parent, timestamp, author, message) VALUES(?,?,?,?,?,?)")) {

            ps.setString(1, commit.getId());
            ps.setString(2, commit.getBranch());
            ps.setString(3, commit.getParent());
            ps.setLong(4, commit.getTimestamp());
            ps.setString(5, commit.getAuthor());
            ps.setString(6, commit.getMessage());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO snapshots(commit_id, data) VALUES(?,?)")) {

            ps.setString(1, commit.getId());
            ps.setBytes(2, snapshot);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE branches SET head_commit = ? WHERE name = ?")) {

            ps.setString(1, commit.getId());
            ps.setString(2, commit.getBranch());
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement insertPs = conn.prepareStatement(
                        "INSERT INTO branches(name, head_commit) VALUES(?,?)")) {
                    insertPs.setString(1, commit.getBranch());
                    insertPs.setString(2, commit.getId());
                    insertPs.executeUpdate();
                }
            }
        }
        conn.commit();
    }

    @Override
    public Commit getCommit(String commitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM commits WHERE id = ?")) {

            ps.setString(1, commitId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Commit(
                        rs.getString("id"),
                        rs.getString("branch"),
                        rs.getString("parent"),
                        rs.getLong("timestamp"),
                        rs.getString("author"),
                        rs.getString("message")
                );
            }
        }
        return null;
    }

    @Override
    public byte[] getSnapshot(String commitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT data FROM snapshots WHERE commit_id = ?")) {

            ps.setString(1, commitId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBytes("data");
            }
        }
        return null;
    }

    @Override
    public List<Commit> getCommitHistory(String branch, int limit) throws SQLException {
        List<Commit> commits = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM commits WHERE branch = ? ORDER BY timestamp DESC LIMIT ?")) {

            ps.setString(1, branch);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                commits.add(new Commit(
                        rs.getString("id"),
                        rs.getString("branch"),
                        rs.getString("parent"),
                        rs.getLong("timestamp"),
                        rs.getString("author"),
                        rs.getString("message")
                ));
            }
        }
        return commits;
    }

    @Override
    public String getHeadCommit(String branch) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT head_commit FROM branches WHERE name = ?")) {

            ps.setString(1, branch);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("head_commit");
            }
        }
        return null;
    }
}