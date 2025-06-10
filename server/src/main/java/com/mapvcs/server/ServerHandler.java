package com.mapvcs.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.mapvcs.core.MapVCSProtocol;
import com.mapvcs.core.MapVCSProtocol.*;

import java.io.File;
import java.util.List;

public class ServerHandler extends SimpleChannelInboundHandler<MapVCSProtocol.BaseMessage> {
    private final MapRepository repo;
    private final File storageDir;

    public ServerHandler(MapRepository repo, File storageDir) {
        this.repo = repo;
        this.storageDir = storageDir;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MapVCSProtocol.BaseMessage msg) {
        if (msg instanceof PullRequest) {
            handlePull(ctx, (PullRequest) msg);
        } else if (msg instanceof HistoryRequest) {
            handleHistory(ctx, (HistoryRequest) msg);
        } else {
            ctx.writeAndFlush(new ErrorResponse("Unsupported operation"));
        }
    }

    private void handlePull(ChannelHandlerContext ctx, PullRequest request) {
        try {
            String latestCommit = repo.getHeadCommit(request.getBranch());

            // 如果客户端已经是最新版本
            if (latestCommit != null && latestCommit.equals(request.getSinceCommit())) {
                ctx.writeAndFlush(new PullResponse(latestCommit, null));
                return;
            }

            // 返回最新快照
            byte[] snapshot = repo.getSnapshot(latestCommit);
            ctx.writeAndFlush(new PullResponse(latestCommit, snapshot));
        } catch (Exception e) {
            ctx.writeAndFlush(new ErrorResponse("Pull failed: " + e.getMessage()));
        }
    }

    private void handleHistory(ChannelHandlerContext ctx, HistoryRequest request) {
        try {
            List<Commit> commits = repo.getCommitHistory(request.getBranch(), request.getLimit());
            ctx.writeAndFlush(new HistoryResponse(commits));
        } catch (Exception e) {
            ctx.writeAndFlush(new ErrorResponse("History request failed: " + e.getMessage()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
