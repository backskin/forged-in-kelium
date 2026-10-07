package kelium.gui.cardshop;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** Цвета и шрифты мастерской — тёмная тема в тон окну партии. */
final class Style {

    private Style() {
    }

    static final Color BG = new Color(0x1d2129);
    static final Color PANEL = new Color(0x272c36);
    static final Color PANEL_HOVER = new Color(0x323846);
    static final Color LINE = new Color(0x3a4150);
    static final Color INK = new Color(0xe8ebf0);
    static final Color INK2 = new Color(0xa3abb8);
    static final Color INK3 = new Color(0x6f7886);
    static final Color ACCENT = new Color(0xd8503c);
    static final Color GOOD = new Color(0x5fbf7a);
    static final Color BAD = new Color(0xe56b5b);

    private static final CardAssets FONTS = new CardAssets();

    static Font title(float px) {
        return FONTS.font("TekturNarrow-Bold.ttf", px);
    }

    /** Подпись раздела: крупно, цветом акцента, с отступом сверху. */
    static JLabel caption(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(title(15));
        l.setForeground(ACCENT);
        l.setBorder(BorderFactory.createEmptyBorder(14, 2, 4, 2));
        l.setAlignmentX(0f);
        return l;
    }
}
