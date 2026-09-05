package com.Nanbin.mtrphone.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 服务端 → 客户端：查询 / 充值的结果。
 *
 * <ul>
 *   <li>{@code balance}：该请求之后的最新余额（分）</li>
 *   <li>{@code rate}：当前汇率，1 绿宝石 = rate 分，客户端据此折算与提示</li>
 *   <li>{@code points}：本次请求的充值金额（分），失败分支也回传，供提示</li>
 *   <li>{@code emeralds}：OK 时真正扣除的绿宝石数；NOT_ENOUGH 时为需要多少个</li>
 *   <li>{@code pointsAdded}：OK 时实际加上的分数</li>
 * </ul>
 */
public record MetroStatusPacket(Status status, int balance, int rate,
                                int points, int emeralds, int pointsAdded) {

    public enum Status {
        /** 纯查询结果，未发生任何扣款 */
        QUERY,
        /** 充值成功 */
        OK,
        /** 身上绿宝石不够 */
        NOT_ENOUGH,
        /** 请求的金额非法（<=0 或不是汇率的整数倍） */
        BAD_AMOUNT,
        /** 玩家没有随身携带手机 */
        NO_PHONE
    }

    public static void encode(MetroStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.status);
        buf.writeVarInt(msg.balance);
        buf.writeVarInt(msg.rate);
        buf.writeVarInt(msg.points);
        buf.writeVarInt(msg.emeralds);
        buf.writeVarInt(msg.pointsAdded);
    }

    public static MetroStatusPacket decode(FriendlyByteBuf buf) {
        return new MetroStatusPacket(
                buf.readEnum(Status.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }
}
