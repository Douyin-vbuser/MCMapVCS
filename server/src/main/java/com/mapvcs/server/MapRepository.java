package com.mapvcs.server;

import com.mapvcs.core.MapVCSProtocol.Commit;
import java.sql.SQLException;
import java.util.List;

public interface MapRepository {
    void saveCommit(Commit commit, byte[] snapshot) throws SQLException;
    Commit getCommit(String commitId) throws SQLException;
    byte[] getSnapshot(String commitId) throws SQLException;
    List<Commit> getCommitHistory(String branch, int limit) throws SQLException;
    String getHeadCommit(String branch) throws SQLException;
}
