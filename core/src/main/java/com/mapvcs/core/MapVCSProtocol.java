package com.mapvcs.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.List;

@SuppressWarnings("unused")
public class MapVCSProtocol {
    public enum MessageType {
        PULL, PULL_RESPONSE, COMMIT_HISTORY, HISTORY_RESPONSE, ERROR
    }

    @Data
    @NoArgsConstructor
    public static class BaseMessage {
        protected MessageType type;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class PullRequest extends BaseMessage {
        private String branch;
        private String sinceCommit;

        public PullRequest() {
            type = MessageType.PULL;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    public static class PullResponse extends BaseMessage {
        private String newCommitId;
        private byte[] snapshot;

        public PullResponse() {
            type = MessageType.PULL_RESPONSE;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class HistoryRequest extends BaseMessage {
        private String branch;
        private int limit;

        public HistoryRequest() {
            type = MessageType.COMMIT_HISTORY;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    public static class HistoryResponse extends BaseMessage {
        private List<Commit> commits;

        public HistoryResponse() {
            type = MessageType.HISTORY_RESPONSE;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    public static class ErrorResponse extends BaseMessage {
        private String message;

        public ErrorResponse() {
            type = MessageType.ERROR;
        }
    }

    @Data
    @AllArgsConstructor
    public static class Commit {
        private String id;
        private String branch;
        private String parent;
        private long timestamp;
        private String author;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class PullResult {
        private String newCommitId;
        private byte[] snapshot;
        private List<String> updatedFiles;

        public boolean hasUpdates() {
            return snapshot != null && snapshot.length > 0;
        }
    }
}