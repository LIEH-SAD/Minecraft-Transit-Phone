package com.Nanbin.mtrphone.network;

/**
 * 服务端回包在客户端的着陆点 —— 只存裸数据，不碰任何客户端类型，
 * 所以这个类在专用服务端上加载也没有问题（S2C 本来就不会在那边触发）。
 *
 * 页面每次渲染读这里的值。手机界面每帧新建 PhoneCanvas，不能把
 * "上一帧拿到的余额"存在页面对象里当长存状态——对象换页就没了。
 */
public final class MetroCardCache {

    private MetroCardCache() {}

    private static int balance;
    private static int rate = 10; // 默认 10，query 回来后以服务端为准
    private static boolean loaded;
    private static MetroStatusPacket.Status lastStatus = MetroStatusPacket.Status.QUERY;
    private static int lastPoints;
    private static int lastEmeralds;
    private static int lastPointsAdded;
    private static long lastUpdateMs = Long.MIN_VALUE;

    /** 收到任意一个服务端回包时调用 */
    public static void update(MetroStatusPacket packet) {
        balance = packet.balance();
        rate = packet.rate();
        lastStatus = packet.status();
        lastPoints = packet.points();
        lastEmeralds = packet.emeralds();
        lastPointsAdded = packet.pointsAdded();
        lastUpdateMs = System.currentTimeMillis();
        loaded = true;
    }

    /** 页面刚打开、查询还没回来之前，余额显示成"…"而不是上一次服务器的旧值 */
    public static void markUnloaded() {
        loaded = false;
    }

    public static int balance() {
        return balance;
    }

    /** 1 绿宝石 = rate 分 */
    public static int rate() {
        return rate;
    }

    public static boolean loaded() {
        return loaded;
    }

    public static MetroStatusPacket.Status lastStatus() {
        return lastStatus;
    }

    /** 本次请求的充值金额（分），非充值结果时为 0 */
    public static int lastPoints() {
        return lastPoints;
    }

    /** OK=已扣除的绿宝石；NOT_ENOUGH=需要的绿宝石 */
    public static int lastEmeralds() {
        return lastEmeralds;
    }

    /** OK=实际加上的分数 */
    public static int lastPointsAdded() {
        return lastPointsAdded;
    }

    public static long lastUpdateMs() {
        return lastUpdateMs;
    }
}
