package com.Nanbin.mtrphone.client.app;

import com.Nanbin.mtrphone.network.MetroCardCache;
import com.Nanbin.mtrphone.network.MetroRequestPacket;
import com.Nanbin.mtrphone.network.MetroStatusPacket;
import com.Nanbin.mtrphone.network.MtrphoneNetwork;
import com.november.mcphone.api.client.ui.IPhonePage;
import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.api.client.ui.PhoneStyle;
import com.november.mcphone.core.client.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * 「地铁充值」那一页。
 *
 * 输入的是【分数】——比如充 100 分，1 绿宝石 = 10 分，扣 10 个绿宝石，给 mtr_balance
 * 计分板 +100。快捷金额 20 / 50 / 100，也可以点页面里的数字键盘或实体键盘输入自定义
 * 金额（必须是 10 的整数倍）。汇率以服务端回传为准（MetroCardCache.rate()）。
 *
 * 页面自带数字键盘，点击即可输入，不依赖物理键盘的 char 事件（有些环境/输入法不会把
 * 数字以 charTyped 送进游戏）。capturesKeyboard() 仍返回 true：实体键盘可用，且不会被
 * 背包键 E 误关。
 */
public final class MetroRechargePage implements IPhonePage {

    private static final int PAD = 6;
    private static final int MAX_INPUT_LEN = 5;          // 最多 99999 分（9999 绿宝石），防溢出

    private static final int INPUT_H = 11;
    private static final int CHIP_H = 13;
    private static final int CHIP_GAP = 3;
    private static final int KEY_H = 13;
    private static final int KEY_GAP = 2;
    private static final int CONFIRM_H = 14;

    private static final int KEY_ROWS = 4;
    private static final int KEY_COLS = 3;
    /** 数字键盘键位：0-11 → C/⌫ 放在最后一行 */
    private static final String[] KEY_LABELS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "C", "0", "⌫"
    };

    /** 快捷充值金额（分） */
    private static final int[] PRESETS = {20, 50, 100};

    /** 汇率还没回来时兜底用的默认值，与 ServerConfig 的默认一致 */
    private static final int FALLBACK_RATE = 10;

    /** 请求发出后多久没回音就当作服务端没装本模组/网络断，放玩家再按一次 */
    private static final long TIMEOUT_MS = 2500L;

    private static final int COLOR_DIVIDER = 0x44FFFFFF;
    private static final int COLOR_BOX = 0x33000000;
    private static final int COLOR_OK = 0xFF55FF55;
    private static final int COLOR_ERR = 0xFFFF5555;

    //  数字键盘配色（中性拨号盘风格）
    /** 数字键：中性灰底，白字 */
    private static final int KEY_BG = 0xFF6E6E78;
    private static final int KEY_BG_HOVER = 0xFF8A8A95;
    private static final int KEY_FG = 0xFFFFFFFF;
    /** C（清空）：橙红底 */
    private static final int KEY_C_BG = 0xFF8A2B22;
    private static final int KEY_C_BG_HOVER = 0xFFB04038;
    private static final int KEY_C_FG = 0xFFFFE3DD;
    /** ⌫（退格）：比数字键暗一档的灰 */
    private static final int KEY_BACK_BG = 0xFF56565E;
    private static final int KEY_BACK_BG_HOVER = 0xFF71717B;
    private static final int KEY_BACK_FG = 0xFFC9C9D2;

    /** 用户输入的充值金额（分），只存数字 */
    private String amountInput = "";

    /** 是否有请求在途（查询或充值）。在途时确认键置灰，防连点 */
    private boolean busy;
    private long busySinceMs;

    /** 在途请求的类型。收到回包后用来决定要不要弹"充值成功/失败" */
    private MetroRequestPacket.Kind pendingKind;

    private String feedback = "";
    private int feedbackColor = COLOR_OK;
    private long feedbackShownMs;

    /** 金额输入框是否处于"聚焦"态：只在点击输入框后才弹出数字键盘（仿智能手机） */
    private boolean keypadOpen = false;

    //  最近一帧渲染算出来的几何，供 mouseClicked 用（点总在渲染之后发生）
    private int lastX;
    private int lastW;
    private int lastInputY;
    private int lastChipsY;
    private int lastKeypadY;
    private int lastConfirmY;
    private final int[] lastChipXs = new int[PRESETS.length];

    //  生命周期

    @Override
    public void onOpen() {
        amountInput = "";
        feedback = "";
        keypadOpen = false;
        MetroCardCache.markUnloaded();
        send(MetroRequestPacket.Kind.QUERY, 0);
    }

    @Override
    public void onClose() {
        // 无资源要释放；缓存留给下次打开继续显示
    }

    //  输入（实体键盘）

    @Override
    public boolean capturesKeyboard() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            requestRecharge();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= '0' && codePoint <= '9') {
            typeDigit((char) codePoint);
            return true;
        }
        return false;
    }

    //  输入（鼠标点按）

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // 在途请求还没回音时不吃新指令，等回包或超时
        if (busyNow()) return true;

        // 确认充值：发起请求，并收起键盘
        if (inRect(mouseX, mouseY, lastX, lastConfirmY, lastW, CONFIRM_H)) {
            keypadOpen = false;
            requestRecharge();
            return true;
        }
        // 点金额输入框：弹出/保持数字键盘（聚焦）
        if (inRect(mouseX, mouseY, lastX, lastInputY, lastW, INPUT_H)) {
            keypadOpen = true;
            return true;
        }
        // 快捷金额 20/50/100
        for (int i = 0; i < PRESETS.length; i++) {
            if (inRect(mouseX, mouseY, lastChipXs[i], lastChipsY, chipWidth(), CHIP_H)) {
                amountInput = Integer.toString(PRESETS[i]);
                return true;
            }
        }
        // 数字键盘按键（只在键盘弹出时生效）
        if (keypadOpen) {
            for (int i = 0; i < KEY_LABELS.length; i++) {
                int row = i / KEY_COLS;
                int col = i % KEY_COLS;
                int kx = lastX + col * (keyWidth() + KEY_GAP);
                int ky = lastKeypadY + row * (KEY_H + KEY_GAP);
                if (inRect(mouseX, mouseY, kx, ky, keyWidth(), KEY_H)) {
                    pressKey(i);
                    return true;
                }
            }
            // 键盘开着时点到别处＝收起键盘
            keypadOpen = false;
        }
        // 其余空白点击都吞掉，不让手机误判为关机
        return true;
    }

    private void pressKey(int index) {
        String label = KEY_LABELS[index];
        switch (label) {
            case "C" -> amountInput = "";
            case "⌫" -> backspace();
            default -> typeDigit(label.charAt(0));
        }
    }

    private void typeDigit(char digit) {
        if (amountInput.length() < MAX_INPUT_LEN) {
            amountInput += digit;
        }
    }

    private void backspace() {
        if (!amountInput.isEmpty()) {
            amountInput = amountInput.substring(0, amountInput.length() - 1);
        }
    }

    @Override
    public boolean onBack() {
        // 默认退回主屏即可
        return false;
    }

    //  请求

    private void send(MetroRequestPacket.Kind kind, int points) {
        busy = true;
        busySinceMs = System.currentTimeMillis();
        pendingKind = kind;
        feedback = "";
        MtrphoneNetwork.sendToServer(new MetroRequestPacket(kind, points));
    }

    private void requestRecharge() {
        if (busyNow()) return;

        int points = parseAmount();
        int rate = currentRate();
        if (amountInput.isBlank()) {
            flash(I18n.get("mtrphone.metro.empty"), COLOR_ERR);
            return;
        }
        if (points <= 0) {
            flash(I18n.get("mtrphone.metro.bad_amount"), COLOR_ERR);
            return;
        }
        if (points % rate != 0) {
            flash(I18n.get("mtrphone.metro.bad_multiple", rate), COLOR_ERR);
            return;
        }
        keypadOpen = false;
        send(MetroRequestPacket.Kind.RECHARGE, points);
    }

    /** 输入框里的数值；解析不了返回 0 */
    private int parseAmount() {
        try {
            return Integer.parseInt(amountInput.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int currentRate() {
        return MetroCardCache.loaded() ? MetroCardCache.rate() : FALLBACK_RATE;
    }

    //  渲染

    @Override
    public void render(PhoneCanvas canvas) {
        updateAsync();

        GuiGraphics g = canvas.graphics();
        Font font = canvas.font();
        PhoneStyle s = canvas.style();

        final int x = canvas.x() + PAD;
        final int w = canvas.width() - PAD * 2;
        lastX = x;
        lastW = w;

        int y = canvas.y() + 2;

        // ---- 标题 ----
        String title = I18n.get("mtrphone.metro.title");
        g.drawString(font, title, x + (w - font.width(title)) / 2, y, s.titleColor(), false);
        y += font.lineHeight + 3;
        g.fill(x, y, x + w, y + 1, COLOR_DIVIDER);
        y += 4;

        // ---- 余额 ----
        String balanceText = MetroCardCache.loaded()
                ? Integer.toString(MetroCardCache.balance())
                : I18n.get("mtrphone.metro.unknown");
        y = drawValueRow(g, font, x, w, y,
                I18n.get("mtrphone.metro.balance"), balanceText,
                s.subtleColor(), MetroCardCache.loaded() ? s.accentColor() : s.subtleColor());

        // ---- 持有绿宝石（带图标） ----
        y = drawEmeraldRow(g, font, s, x, w, y);

        // ---- 汇率 ----
        g.drawString(font, I18n.get("mtrphone.metro.rate", currentRate()),
                x, y, s.subtleColor(), false);
        y += font.lineHeight + 2;
        g.fill(x, y, x + w, y + 1, COLOR_DIVIDER);
        y += 3;

        // ---- 金额输入框 ----
        lastInputY = y + font.lineHeight + 1;
        g.drawString(font, I18n.get("mtrphone.metro.amount"), x, y, s.subtleColor(), false);
        y = lastInputY;

        g.fill(x, y, x + w, y + INPUT_H, COLOR_BOX);
        g.fill(x, y, x + w, y + 1, COLOR_DIVIDER);
        g.fill(x, y + INPUT_H - 1, x + w, y + INPUT_H, COLOR_DIVIDER);
        drawInputText(g, font, s, x, y);
        y += INPUT_H + 2;

        // ---- 快捷金额 ----
        lastChipsY = y;
        int chipW = chipWidth();
        for (int i = 0; i < PRESETS.length; i++) {
            int cx = x + i * (chipW + CHIP_GAP);
            lastChipXs[i] = cx;
            drawButton(g, font, s, canvas, cx, y, chipW, CHIP_H,
                    Integer.toString(PRESETS[i]), false);
        }
        y += CHIP_H + 3;

        // ---- 数字键盘：只在点过金额输入框后弹出（仿智能手机） ----
        if (keypadOpen) {
            lastKeypadY = y;
            int keyW = keyWidth();
            for (int i = 0; i < KEY_LABELS.length; i++) {
                int row = i / KEY_COLS;
                int col = i % KEY_COLS;
                int kx = x + col * (keyW + KEY_GAP);
                int ky = lastKeypadY + row * (KEY_H + KEY_GAP);
                drawKey(g, font, s, canvas, kx, ky, keyW, KEY_H, KEY_LABELS[i]);
            }
            y = lastKeypadY + KEY_ROWS * KEY_H + (KEY_ROWS - 1) * KEY_GAP + 3;
        }

        // ---- 确认按钮 ----
        lastConfirmY = y;
        boolean enabled = !busyNow();
        boolean hovered = enabled && canvas.hovered(x, y, w, CONFIRM_H);
        drawButton(g, font, s, canvas, x, y, w, CONFIRM_H,
                I18n.get("mtrphone.metro.confirm"), false, enabled, hovered);
        y += CONFIRM_H + 5;

        // ---- 反馈 ----
        if (!feedback.isEmpty() && System.currentTimeMillis() - feedbackShownMs < 5000L) {
            g.drawString(font, truncate(font, feedback, w), x, y, feedbackColor, false);
        }
    }

    /** 输入框内容 + 闪烁光标 */
    private void drawInputText(GuiGraphics g, Font font, PhoneStyle s, int x, int y) {
        if (amountInput.isEmpty()) {
            g.drawString(font, I18n.get("mtrphone.metro.amount_hint"), x + 2,
                    y + (INPUT_H - font.lineHeight) / 2 + 1, s.subtleColor(), false);
        } else {
            g.drawString(font, amountInput, x + 2, y + (INPUT_H - font.lineHeight) / 2 + 1,
                    s.titleColor(), false);
        }
        boolean cursorOn = keypadOpen && (System.currentTimeMillis() / 500L) % 2 == 0;
        if (cursorOn) {
            int textW = font.width(amountInput);
            int cy = y + (INPUT_H - font.lineHeight) / 2 + 1;
            g.fill(x + 2 + textW + 1, cy, x + 2 + textW + 2, cy + font.lineHeight, s.accentColor());
        }
    }

    private void drawKey(GuiGraphics g, Font font, PhoneStyle s, PhoneCanvas canvas,
                         int kx, int ky, int kw, int kh, String label) {
        boolean hovered = canvas.hovered(kx, ky, kw, kh);
        int bg;
        int fg;
        switch (label) {
            case "C" -> {
                bg = hovered ? KEY_C_BG_HOVER : KEY_C_BG;
                fg = KEY_C_FG;
            }
            case "⌫" -> {
                bg = hovered ? KEY_BACK_BG_HOVER : KEY_BACK_BG;
                fg = KEY_BACK_FG;
            }
            default -> {
                bg = hovered ? KEY_BG_HOVER : KEY_BG;
                fg = KEY_FG;
            }
        }
        g.fill(kx, ky, kx + kw, ky + kh, bg);
        g.drawString(font, label, kx + (kw - font.width(label)) / 2,
                ky + (kh - font.lineHeight) / 2 + 1, fg, false);
    }

    private void drawButton(GuiGraphics g, Font font, PhoneStyle s, PhoneCanvas canvas,
                            int bx, int by, int bw, int bh, String label, boolean hoverable) {
        boolean hovered = hoverable && canvas.hovered(bx, by, bw, bh);
        int bg = hovered ? s.buttonHoverColor() : s.buttonColor();
        g.fill(bx, by, bx + bw, by + bh, bg);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2,
                by + (bh - font.lineHeight) / 2 + 1, s.titleColor(), false);
    }

    private void drawButton(GuiGraphics g, Font font, PhoneStyle s, PhoneCanvas canvas,
                            int bx, int by, int bw, int bh, String label,
                            boolean hoverable, boolean enabled, boolean hovered) {
        int bg = !enabled ? s.buttonDisabledColor()
                : hovered ? s.buttonHoverColor() : s.buttonColor();
        int fg = !enabled ? s.buttonDisabledTextColor() : s.titleColor();
        g.fill(bx, by, bx + bw, by + bh, bg);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2,
                by + (bh - font.lineHeight) / 2 + 1, fg, false);
    }

    /** "持有绿宝石"一行：数字右侧对齐，数字左边画一颗原版绿宝石图标 */
    private int drawEmeraldRow(GuiGraphics g, Font font, PhoneStyle s, int x, int w, int y) {
        String label = I18n.get("mtrphone.metro.emeralds");
        String value = Integer.toString(emeraldCount());
        int valueW = font.width(value);

        int iconSize = 9;
        int iconX = x + w - valueW - 2 - iconSize;
        boolean iconDrawn = GuiUtil.drawItemIcon(g, new ItemStack(Items.EMERALD),
                iconX, y + (font.lineHeight - iconSize) / 2, iconSize);
        if (!iconDrawn) iconX = x + w - valueW; // 画不出图标就退回纯右对齐

        g.drawString(font, truncate(font, label, Math.max(0, iconX - x - 4)),
                x, y, s.subtleColor(), false);
        g.drawString(font, value, x + w - valueW, y, s.bodyColor(), false);
        return y + font.lineHeight + 1;
    }

    //  状态推进：看回包、判超时

    private void updateAsync() {
        long now = System.currentTimeMillis();

        if (!busy) return;

        // 超时：当作请求没送出去，放玩家重试
        if (now - busySinceMs > TIMEOUT_MS) {
            busy = false;
            pendingKind = null;
            flash(I18n.get("mtrphone.metro.timeout"), COLOR_ERR);
            return;
        }
        // 服务端回包到了吗
        if (MetroCardCache.lastUpdateMs() <= busySinceMs) return;

        MetroStatusPacket.Status status = MetroCardCache.lastStatus();
        MetroRequestPacket.Kind kind = pendingKind;
        busy = false;
        pendingKind = null;

        if (kind != MetroRequestPacket.Kind.RECHARGE) return; // 查询结果：余额已在上面刷新

        switch (status) {
            case OK -> {
                amountInput = "";
                keypadOpen = false;
                flash(I18n.get("mtrphone.metro.ok",
                        MetroCardCache.lastPointsAdded(), MetroCardCache.lastEmeralds()), COLOR_OK);
            }
            case NOT_ENOUGH -> flash(I18n.get("mtrphone.metro.not_enough",
                    MetroCardCache.lastEmeralds(), emeraldCount()), COLOR_ERR);
            case BAD_AMOUNT -> {
                int rate = currentRate();
                int points = MetroCardCache.lastPoints();
                int emeralds = MetroCardCache.lastEmeralds();
                if (points > 0 && emeralds == 0 && points % rate != 0) {
                    flash(I18n.get("mtrphone.metro.bad_multiple", rate), COLOR_ERR);
                } else {
                    flash(I18n.get("mtrphone.metro.bad_amount"), COLOR_ERR);
                }
            }
            case NO_PHONE -> flash(I18n.get("mtrphone.metro.no_phone"), COLOR_ERR);
            default -> { /* QUERY 不会出现在充值回包里，忽略 */ }
        }
    }

    private boolean busyNow() {
        if (!busy) return false;
        // 超时后不算 busy（updateAsync 会在渲染帧把它清掉；点击发生前至少渲染过一帧）
        return System.currentTimeMillis() - busySinceMs <= TIMEOUT_MS;
    }

    private void flash(String text, int color) {
        feedback = text;
        feedbackColor = color;
        feedbackShownMs = System.currentTimeMillis();
    }

    //  小工具

    private int chipWidth() {
        return (lastW - CHIP_GAP * (PRESETS.length - 1)) / PRESETS.length;
    }

    private int keyWidth() {
        return (lastW - KEY_GAP * (KEY_COLS - 1)) / KEY_COLS;
    }

    private boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    /** 一行"标签 …… 值"，值靠右；标签太长就让路 */
    private int drawValueRow(GuiGraphics g, Font font, int x, int w, int y,
                             String label, String value, int labelColor, int valueColor) {
        int valueW = font.width(value);
        g.drawString(font, truncate(font, label, Math.max(0, w - valueW - 4)),
                x, y, labelColor, false);
        g.drawString(font, value, x + w - valueW, y, valueColor, false);
        return y + font.lineHeight + 1;
    }

    /** 太长就截断，末尾补省略号。maxWidth<=0 时返回空串 */
    private static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    /** 客户端自己的视角数一遍绿宝石，只用于展示 */
    private static int emeraldCount() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int found = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.EMERALD)) found += stack.getCount();
        }
        return found;
    }
}
