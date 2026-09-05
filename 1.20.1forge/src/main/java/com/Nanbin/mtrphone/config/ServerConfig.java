package com.Nanbin.mtrphone.config;

import net.minecraftforge.common.ForgeConfigSpec;

// 服务器完成充值操作
public final class ServerConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // 计分板目标名
    private static final ForgeConfigSpec.ConfigValue<String> OBJECTIVE_NAME = BUILDER
            .comment("充值后累加到的计分板目标(objective)名称。目标不存在时应用会自动创建。",
                     "命名规则与原版一致：只能由字母、数字、点、下划线、短横线组成。")
            .define("objectiveName", "mtr_balance",
                    obj -> obj instanceof String s
                            && !s.isBlank()
                            && s.matches("[A-Za-z0-9._-]+"));

    private static final ForgeConfigSpec.ConfigValue<String> OBJECTIVE_DISPLAY = BUILDER
            .comment("由本应用自动创建计分板目标时使用的显示名称。"
                     + "目标已存在时这一项不生效。")
            .define("objectiveDisplayName", "地铁余额");

    private static final ForgeConfigSpec.IntValue POINTS_PER_EMERALD = BUILDER
            .comment("每 1 个绿宝石兑换的分数。")
            .defineInRange("pointsPerEmerald", 10, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {}

    public static String objectiveName() {
        return OBJECTIVE_NAME.get();
    }

    public static String objectiveDisplayName() {
        return OBJECTIVE_DISPLAY.get();
    }

    public static int pointsPerEmerald() {
        return POINTS_PER_EMERALD.get();
    }
}
