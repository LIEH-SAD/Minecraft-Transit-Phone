package com.Nanbin.mtrphone.network;

import com.Nanbin.mtrphone.Mtrphone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 本模组自己的 SimpleChannel。
 *
 * 序号的规矩：只在末尾追加。SimpleChannel 认的是整数序号而不是名字，
 * 中间插一个包会让它后面所有包的序号平移，两端对不上就互相解错包。
 * 所以「动了包的顺序就必须把 PROTOCOL_VERSION +1，往末尾追加不用动」。
 */
public final class MtrphoneNetwork {

    private MtrphoneNetwork() {}

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Mtrphone.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    /** 下一个包的序号。见类注释里"只在末尾追加"那一段 */
    private static int nextId = 0;

    /** 由 {@link Mtrphone} 构造函数调用：客户端与服务端各跑一遍，顺序必须一致 */
    public static void register() {
        // C2S: 玩家在手机里查询/充值
        CHANNEL.messageBuilder(MetroRequestPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MetroRequestPacket::encode)
                .decoder(MetroRequestPacket::decode)
                .consumerMainThread(MtrphoneNetwork::handleRequest)
                .add();

        // S2C: 服务端回传查询/充值结果
        CHANNEL.messageBuilder(MetroStatusPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MetroStatusPacket::encode)
                .decoder(MetroStatusPacket::decode)
                .consumerMainThread(MtrphoneNetwork::handleStatus)
                .add();
    }

    /** 客户端调用：把包发给服务端 */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** 服务端调用：把包发给某一个玩家 */
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static void handleRequest(MetroRequestPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        ctx.setPacketHandled(true);
        // 连接在包排队期间断掉就会是 null。方向已由 NetworkDirection 限死，
        // 这里只可能是"人走了"，静默丢弃即可
        if (player == null) return;

        MetroStatusPacket reply = MetroCardService.serve(packet, player);
        sendToPlayer(player, reply);
    }

    private static void handleStatus(MetroStatusPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
        // 只落一份裸数据缓存，见 MetroCardCache 的类注释
        MetroCardCache.update(packet);
    }
}
