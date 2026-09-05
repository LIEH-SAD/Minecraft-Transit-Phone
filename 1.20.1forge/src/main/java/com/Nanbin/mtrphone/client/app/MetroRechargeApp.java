package com.Nanbin.mtrphone.client.app;

import com.Nanbin.mtrphone.Mtrphone;
import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.api.client.ui.IPhonePage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 「地铁充值」App —— 通过 mcphone 的 SPI 注册进手机。
 *
 * 默认【不预装】：第一次进世界不会自动上主屏，需要玩家到手机的应用商店里自行安装。
 *
 * 本类只能在客户端加载（IPhoneApp 的签名里有 GuiGraphics）：
 * 专用服务端不会实例化任何 IPhoneApp，这里只是声明给 mcphone 的客户端扫描器用。
 */
public final class MetroRechargeApp implements IPhoneApp {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Mtrphone.MODID, "metro_recharge");

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(Mtrphone.MODID, "textures/app/metro_recharge.png");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("mtrphone.app.metro_recharge");
    }

    @Override
    public ResourceLocation getIconTexture() {
        return ICON;
    }

    @Override
    public String getVersion() {
        return "1.0.1";
    }

    @Override
    public String getAuthor() {
        return "Nanbin-Studio";
    }

    @Override
    public IPhonePage openPage() {
        return new MetroRechargePage();
    }

    @Override
    public void onPress() {
        // 覆盖了 openPage() 之后这里不会被调用，接口要求实现它，留空即可。
    }

    /** 不预装：进应用商店由玩家自己安装 */
    @Override
    public boolean isPreinstalled() {
        return false;
    }

    @Override
    public String getDescription() {
        String key = "mtrphone.app.metro_recharge.desc";
        return I18n.exists(key) ? I18n.get(key) : "";
    }
}
