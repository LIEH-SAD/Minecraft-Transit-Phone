package com.Nanbin.mtrphone.network;

import com.Nanbin.mtrphone.config.ServerConfig;
import com.november.mcphone.core.PhoneItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * "地铁充值"的服务端逻辑。一切数值都以这里为准：
 * 客户端发来的东西一律不信，绿宝石的数量在服务端现数、现扣。
 *
 * 金额单位是"分"（计分板分数）：玩家充 N 分，扣 N/汇率 个绿宝石，加 N 分。
 */
public final class MetroCardService {

    private MetroCardService() {}

    /**
     * 处理一次查询或充值请求。
     *
     * @param player 发来这一包的玩家（由通道保证非空、已在主线程）
     */
    public static MetroStatusPacket serve(MetroRequestPacket msg, ServerPlayer player) {
        Scoreboard board = player.getScoreboard();
        Objective objective = ensureObjective(board);
        Score score = board.getOrCreatePlayerScore(player.getScoreboardName(), objective);
        int balance = score.getScore();
        int rate = ServerConfig.pointsPerEmerald();

        if (msg.kind() == MetroRequestPacket.Kind.QUERY) {
            return new MetroStatusPacket(MetroStatusPacket.Status.QUERY, balance, rate, 0, 0, 0);
        }

        int points = msg.points();

        // 包是客户端发的，不能信"我在手机里点的"——服务端自己验一遍身上有没有手机
        if (!PhoneItem.isCarriedBy(player)) {
            return new MetroStatusPacket(MetroStatusPacket.Status.NO_PHONE, balance, rate, points, 0, 0);
        }
        // 金额必须 > 0 且是汇率的整数倍（否则绿宝石扣不干净）
        if (points <= 0 || points % rate != 0) {
            return new MetroStatusPacket(MetroStatusPacket.Status.BAD_AMOUNT, balance, rate, points, 0, 0);
        }

        int cost = points / rate;
        int held = countEmeralds(player);
        if (held < cost) {
            return new MetroStatusPacket(MetroStatusPacket.Status.NOT_ENOUGH, balance, rate, points, cost, 0);
        }

        // 先数够不够再动手：扣到一半发现不够会让玩家白白损失前半截绿宝石
        if (!consumeEmeralds(player, cost)) {
            return new MetroStatusPacket(MetroStatusPacket.Status.NOT_ENOUGH, balance, rate, points, cost, 0);
        }

        score.add(points);
        return new MetroStatusPacket(MetroStatusPacket.Status.OK,
                score.getScore(), rate, points, cost, points);
    }

    /** 取计分板目标，不存在就建一个 dummy 类型的目标（显示名来自 ServerConfig） */
    private static Objective ensureObjective(Scoreboard board) {
        String name = ServerConfig.objectiveName();
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY,
                    Component.literal(ServerConfig.objectiveDisplayName()),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        return objective;
    }

    /** 身上一共有几个绿宝石。遍历全部隔间，盔甲栏与副手也算 */
    private static int countEmeralds(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.EMERALD)) found += stack.getCount();
        }
        return found;
    }

    /** 从背包里扣指定数量的绿宝石。扣不完返回 false（调用前应先数过） */
    private static boolean consumeEmeralds(ServerPlayer player, int count) {
        int remaining = count;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(Items.EMERALD)) continue;

            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return remaining == 0;
    }
}
