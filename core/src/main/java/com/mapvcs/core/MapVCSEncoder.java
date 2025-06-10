package com.mapvcs.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.google.gson.Gson;

@SuppressWarnings("unused")
public class MapVCSEncoder extends MessageToByteEncoder<MapVCSProtocol.BaseMessage> {
    private final Gson gson = new Gson();

    @Override
    protected void encode(ChannelHandlerContext ctx, MapVCSProtocol.BaseMessage msg, ByteBuf out) {
        String json = gson.toJson(msg);
        byte[] bytes = json.getBytes();
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }
}