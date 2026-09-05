package com.Nanbin.mtrphone.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 客户端 → 服务端：查询或充值请求。
 *
 * {@code points} 是充值金额（计分板分数）。1 绿宝石 = ServerConfig.pointsPerEmerald 分，
 * 所以金额必须是倍数的整数倍，服务端按此折算要扣的绿宝石。包体不带玩家身份：
 * 服务端从连接上下文取玩家，带玩家 ID 等于给伪造客户端开后门。
 */
public record MetroRequestPacket(Kind kind, int points) {

    /** 这一包想让服务端干什么 */
    public enum Kind {
        /** 只查询当前余额与汇率，不扣任何东西 */
        QUERY,
        /** 用指定分数充值（对应扣除 分数/汇率 个绿宝石） */
        RECHARGE
    }

    public static void encode(MetroRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.kind);
        buf.writeVarInt(msg.points);
    }

    public static MetroRequestPacket decode(FriendlyByteBuf buf) {
        return new MetroRequestPacket(buf.readEnum(Kind.class), buf.readVarInt());
    }
}
