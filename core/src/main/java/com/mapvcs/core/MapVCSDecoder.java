package com.mapvcs.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import com.google.gson.Gson;
import java.util.List;

@SuppressWarnings("unused")
public class MapVCSDecoder extends ByteToMessageDecoder {
    private final Gson gson = new Gson();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) return;

        in.markReaderIndex();
        int length = in.readInt();
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        String json = new String(bytes);

        // 根据消息类型反序列化
        MapVCSProtocol.BaseMessage base = gson.fromJson(json, MapVCSProtocol.BaseMessage.class);
        switch (base.getType()) {
            case PULL:
                out.add(gson.fromJson(json, MapVCSProtocol.PullRequest.class));
                break;
            case PULL_RESPONSE:
                out.add(gson.fromJson(json, MapVCSProtocol.PullResponse.class));
                break;
            case COMMIT_HISTORY:
                out.add(gson.fromJson(json, MapVCSProtocol.HistoryRequest.class));
                break;
            case HISTORY_RESPONSE:
                out.add(gson.fromJson(json, MapVCSProtocol.HistoryResponse.class));
                break;
            case ERROR:
                out.add(gson.fromJson(json, MapVCSProtocol.ErrorResponse.class));
                break;
            default:
                ctx.fireChannelRead(json);
        }
    }
}