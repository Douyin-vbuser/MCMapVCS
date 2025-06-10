package com.mapvcs.client;

import com.mapvcs.core.*;
import com.mapvcs.core.MapVCSProtocol.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;

import java.util.Collections;
import java.util.List;

public class MapVCSClient implements AutoCloseable {
    private final String serverAddress;
    private final int port;
    private Channel channel;
    private final EventLoopGroup group;
    private static final AttributeKey<MapVCSClient> CLIENT_KEY = AttributeKey.newInstance("client");


    public MapVCSClient(String serverAddress) {
        String[] parts = serverAddress.split(":");
        this.serverAddress = parts[0];
        this.port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;
        this.group = new NioEventLoopGroup();
    }

    public void connect() throws InterruptedException {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.attr(CLIENT_KEY).set(MapVCSClient.this);

                        ch.pipeline().addLast(
                                new MapVCSDecoder(),
                                new MapVCSEncoder(),
                                new ClientHandler()
                        );
                    }
                });

        channel = b.connect(serverAddress, port).sync().channel();
    }

    public PullResult pull(String branch, String sinceCommit) throws Exception {
        if (channel == null || !channel.isActive()) {
            connect();
        }

        PullRequest request = new PullRequest();
        request.setBranch(branch);
        request.setSinceCommit(sinceCommit);

        channel.writeAndFlush(request);

        // 等待响应
        synchronized (this) {
            wait(5000); // 等待5秒
        }

        PullResult result = ClientHandler.lastPullResult;
        if (result == null) {
            throw new Exception("No response from server");
        }
        return result;
    }

    public List<Commit> getCommitHistory(String branch, int limit) throws Exception {
        if (channel == null || !channel.isActive()) {
            connect();
        }

        HistoryRequest request = new HistoryRequest();
        request.setBranch(branch);
        request.setLimit(limit);

        channel.writeAndFlush(request);

        // 等待响应
        synchronized (this) {
            wait(5000);
        }

        List<Commit> history = ClientHandler.lastHistory;
        if (history == null) {
            throw new Exception("No response from server");
        }
        return history;
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    private static class ClientHandler extends SimpleChannelInboundHandler<BaseMessage> {

        public static PullResult lastPullResult;
        public static List<Commit> lastHistory;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, BaseMessage msg) {
            if (msg instanceof PullResponse) {
                lastPullResult = new PullResult(
                        ((PullResponse) msg).getNewCommitId(),
                        ((PullResponse) msg).getSnapshot(),
                        Collections.emptyList()
                );
            } else if (msg instanceof HistoryResponse) {
                lastHistory = ((HistoryResponse) msg).getCommits();
            }

            MapVCSClient client = ctx.channel().attr(CLIENT_KEY).get();
            if (client != null) {
                synchronized (client) {
                    client.notifyAll();
                }
            }
        }
    }
}