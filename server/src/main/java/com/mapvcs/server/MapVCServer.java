package com.mapvcs.server;

import com.mapvcs.core.MapVCSDecoder;
import com.mapvcs.core.MapVCSEncoder;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.File;
import java.util.List;

import static com.mapvcs.core.MapVCSProtocol.Commit;

@SuppressWarnings("all")
public class MapVCServer {
    private static final int TCP_PORT = 9090;
    private static final int HTTP_PORT = 9091;
    private static final MapRepository repo = new SQLiteMapRepository();
    private static final File storageDir = new File("mapvcs_storage");

    public static void main(String[] args) throws Exception {
        if (!storageDir.exists()) storageDir.mkdirs();

        startTCPServer();
        startHTTPServer();
    }

    private static void startTCPServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            if (!ch.remoteAddress().getAddress().isLoopbackAddress()) {
                                ch.close();
                                return;
                            }

                            ch.pipeline().addLast(
                                    new MapVCSDecoder(),
                                    new MapVCSEncoder(),
                                    new ServerHandler(repo, storageDir)
                            );
                        }
                    });

            b.bind("127.0.0.1", TCP_PORT).sync();
            System.out.println("MapVCS TCP Server started on port " + TCP_PORT);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void startHTTPServer() {
        Javalin app = Javalin.create(config -> {
            config.addStaticFiles(staticFiles -> {
                staticFiles.directory = "public";
                staticFiles.hostedPath = "/";
                staticFiles.location = Location.EXTERNAL;
            });
        }).start(HTTP_PORT);

        app.get("/api/history/{branch}", ctx -> {
            String branch = ctx.pathParam("branch");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
            List<Commit> commits = repo.getCommitHistory(branch, limit);
            ctx.json(commits);
        });

        app.get("/api/snapshot/{commitId}", ctx -> {
            String commitId = ctx.pathParam("commitId");
            byte[] snapshot = repo.getSnapshot(commitId);
            if (snapshot == null) ctx.status(404);
            else {
                ctx.header("Content-Disposition", "attachment; filename=snapshot_" + commitId + ".zip");
                ctx.contentType("application/zip").result(snapshot);
            }
        });

        System.out.println("MapVCS HTTP Server started on port " + HTTP_PORT);
    }
}