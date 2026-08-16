package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import kelium.dataio.ContentLibrary;
import kelium.report.ReplayRecord;

/**
 * СУПЕР-ЗАДАНИЯ ВСЕХ ИГРОКОВ — третья вкладка проигрывателя (заказ дизайнера
 * 12.08.2026): большая открытая карта на каждого игрока, обе её половины и
 * видно, что уже собралось, а что нет.
 *
 * <p>ЛИЦО карты — сборка: список частей проекта, у каждой части своя полоска
 * «внесено из нужного». РУБАШКА — развёртывание: геометрический узор победы.
 * Прогресс берётся из записи партии по частям ({@code superParts}), а не
 * одним числом — иначе не видно, чего именно не хватает.
 */
public final class SuperObjectivesPanel extends JPanel implements javax.swing.Scrollable {

    private static final long serialVersionUID = 1L;

    /** Расчётный размер вёрстки: меньше — карты перестают читаться (см. BoardsPanel). */
    private static final int DESIGN_W = 1180;
    private static final int DESIGN_H = 860;

    /**
     * ПРОПОРЦИИ НАСТОЯЩЕЙ КАРТЫ: 63×89 мм — тот же формат, что у карт в коробке
     * (просьба дизайнера 13.08.2026). Супер-задание показывается ДВУМЯ картами
     * рядом: слева лицо (сборка), справа рубашка (развёртывание) — на столе они
     * так и лежат, и разбирать партию проще, когда экран это повторяет.
     */
    private static final double CARD_W = 63;
    private static final double CARD_H = 89;

    // Набор — как на планшетах: крупнее прежнего, пояснения самым лёгким
    // начертанием (см. BoardsPanel: то же правило, тот же коэффициент).
    private static final float TEXT_K = 1.55f;
    private static final float TEXT_MIN = 15f;

    private static float sz(double pt) {
        return (float) Math.max(TEXT_MIN, pt * TEXT_K);
    }

    private static Font bold(double pt) {
        return kelium.gui.replay2.Theme.font(Math.round(sz(pt)), Font.BOLD);
    }

    private static Font plain(double pt) {
        return kelium.gui.replay2.Theme.font(Math.round(sz(pt)), Font.PLAIN);
    }

    private static Font note(double pt) {
        return kelium.gui.replay2.Theme.note(Math.round(sz(pt)));
    }

    private ContentLibrary content;
    private ReplayRecord record;
    private ReplayRecord.Snapshot snap;

    public SuperObjectivesPanel() {
        setBackground(kelium.gui.replay2.Theme.bg());
    }

    /** Расчётный размер — в масштабе интерфейса (см. {@code BoardsPanel.design}). */
    private static Dimension design() {
        return new Dimension(kelium.gui.replay2.Theme.px(DESIGN_W),
            kelium.gui.replay2.Theme.px(DESIGN_H));
    }

    @Override public Dimension getPreferredSize() {
        return design();
    }

    @Override public Dimension getMinimumSize() {
        return design();
    }

    // ---- Scrollable: растягиваться, когда место есть, и прокручиваться, когда нет ----
    @Override public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible,
                                                    int orientation, int direction) {
        return 24;
    }

    @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible,
                                                     int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
            ? visible.height - 24 : visible.width - 24;
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof javax.swing.JViewport v
            && v.getWidth() >= design().width;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof javax.swing.JViewport v
            && v.getHeight() >= design().height;
    }

    /** Подключить карточный набор супер-заданий той версии, в которой сыграна партия. */
    public void setContent(ContentLibrary content) {
        this.content = content;
        repaint();
    }

    public void show(ReplayRecord rec, ReplayRecord.Snapshot s) {
        this.record = rec;
        this.snap = s;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (snap == null || snap.players.isEmpty()) {
            g.setColor(BoardsPanel.ink3());
            g.drawString("партия не загружена", 16, 28);
            return;
        }
        int n = snap.players.size();
        int cols = n <= 2 ? n : 2;
        int rows = (n + cols - 1) / cols;
        int gap = 16;
        int cw = (getWidth() - gap * (cols + 1)) / cols;
        int ch = (getHeight() - gap * (rows + 1)) / rows;
        for (int i = 0; i < n; i++) {
            int cx = gap + (i % cols) * (cw + gap);
            int cy = gap + (i / cols) * (ch + gap);
            paintPlayer(g, cx, cy, cw, ch, snap.players.get(i));
        }
    }

    /**
     * ОДИН ИГРОК: строка-шапка и под ней ДВЕ КАРТЫ формата 63×89 — лицо и рубашка.
     * Раньше всё это было одним широким прямоугольником, и на карту оно похоже не
     * было (просьба дизайнера 13.08.2026).
     */
    private void paintPlayer(Graphics2D g, int x, int y, int w, int h,
                             ReplayRecord.Player p) {
        Color accent = FieldView.seatStroke(p.seat);
        Map<String, Object> card = find(p.superObjective);

        int headH = 30;
        g.setColor(accent);
        g.fillRoundRect(x, y, w, headH, 10, 10);
        g.fillRect(x, y + headH - 10, w, 10);
        g.setColor(Color.WHITE);
        g.setFont(bold(11));
        String seatName = record != null && p.seat < record.seatLabels.size()
            ? record.seatLabels.get(p.seat) : "игрок " + (p.seat + 1);
        String name = card != null ? String.valueOf(card.get("name"))
            : p.superObjective == null ? "супер-задание не выдано" : p.superObjective;
        g.drawString(BoardsPanel.clip(g, "игрок " + (p.seat + 1) + " · " + seatName
            + "  —  " + name, w - 16), x + 10, y + 21);

        // РАЗМЕР КАРТЫ — ОТ МЕНЬШЕЙ СТОРОНЫ: обе карты обязаны сохранить свои
        // пропорции, поэтому берём то, что помещается и по ширине, и по высоте.
        int gap = 14;
        int top = y + headH + 10;
        int availW = (w - gap) / 2;
        int availH = h - (top - y) - 6;
        int cardW = (int) Math.min(availW, availH * CARD_W / CARD_H);
        int cardH = (int) Math.round(cardW * CARD_H / CARD_W);
        int fx = x + (w - (2 * cardW + gap)) / 2;

        paintFace(g, fx, top, cardW, cardH, p, card, accent);
        paintBack(g, fx + cardW + gap, top, cardW, cardH, p, card, accent);
    }

    /** Общая рамка карты: скруглённый прямоугольник с цветной кромкой места. */
    private void cardFrame(Graphics2D g, int x, int y, int w, int h, Color accent,
                           Color fill) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.6f));
        g.drawRoundRect(x, y, w, h, 16, 16);
    }

    /**
     * ЗОЛОТАЯ ЗВЕЗДА С ЧИСЛОМ ПОБЕДНЫХ ОЧКОВ. Тот же знак, что на планшете науки
     * ({@code BoardsPanel.star}): одна и та же награда должна выглядеть на всех
     * экранах одинаково, иначе её приходится узнавать заново.
     *
     * <p>Число пишется ВНУТРИ звезды, а не сбоку: на карте формата 63×89 места
     * справа нет — там уже край.
     */
    private void star(Graphics2D g, int cx, int cy, double r, int vp) {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double rr = i % 2 == 0 ? r : r * 0.45;
            double a = Math.toRadians(-90 + i * 36);
            double px = cx + rr * Math.cos(a);
            double py = cy + rr * Math.sin(a);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        g.setColor(new Color(0xF2, 0xC0, 0x2E));
        g.fill(p);
        g.setColor(new Color(0x8A, 0x63, 0x00));
        g.setStroke(new java.awt.BasicStroke(1.0f));
        g.draw(p);
        g.setFont(bold(11));
        // Число стоит РЯДОМ со звездой, то есть на бумаге карты, — значит и цвет
        // берёт у бумаги: тёмно-коричневым по тёмной теме его не было видно.
        g.setColor(BoardsPanel.ink());
        String tag = "+" + vp;
        g.drawString(tag, cx + (int) r + 3, cy + (int) (r * 0.5));
    }

    /** ЛИЦО КАРТЫ — сборка: название, цена сборки звездой, состояние и части. */
    @SuppressWarnings("unchecked")
    private void paintFace(Graphics2D g, int x, int y, int w, int h,
                           ReplayRecord.Player p, Map<String, Object> card, Color accent) {
        cardFrame(g, x, y, w, h, accent,
            p.superComplete ? BoardsPanel.wash(new Color(0x1E, 0x7A, 0x33)) : BoardsPanel.paper());

        int ty = y + 30;
        g.setColor(accent.darker());
        g.setFont(bold(9.5));
        g.drawString("ЛИЦО · СБОРКА", x + 12, ty);

        // ЧЕГО СТОИТ СОБРАТЬ ЛИЦО — ЗВЕЗДОЙ В УГЛУ КАРТЫ (просьба дизайнера
        // 14.08.2026). Очки за сборку лица (first_part_vp) на карте не были
        // написаны нигде: игрок видел, что вносить, но не видел, ради чего. Знак
        // тот же, что на планшете науки, — золотая звезда с числом, чтобы «сколько
        // это стоит» читалось одинаково по всей игре.
        int faceVp = card != null && card.get("first_part_vp") instanceof Number n
            ? n.intValue() : 0;
        if (faceVp > 0) {
            // ОТ ПРАВОГО КРАЯ КАРТЫ, А НЕ ОТ СЕРЕДИНЫ ЗВЕЗДЫ. Раньше середина
            // звезды ставилась на глазок, и число «+5» вылезало на самую кромку
            // карты (замечание дизайнера 14.08.2026). Теперь место считается
            // назад от края: сначала отступ, потом ширина числа, потом звезда.
            double r = 9.0;
            g.setFont(bold(11));
            int tagW = g.getFontMetrics().stringWidth("+" + faceVp);
            int cx = (int) Math.round(x + w - 14 - tagW - 3 - r);
            star(g, cx, ty - 3, r, faceVp);
        }

        ty += 30;
        g.setColor(BoardsPanel.ink());
        g.setFont(kelium.gui.replay2.Theme.display(20));
        String name = card != null ? String.valueOf(card.get("name"))
            : p.superObjective == null ? "не выдано" : p.superObjective;
        g.drawString(BoardsPanel.clip(g, name, w - 24), x + 12, ty);
        if (card != null && card.get("subtitle") != null) {
            ty += 22;
            g.setFont(note(10));
            g.setColor(BoardsPanel.ink3());
            g.drawString(BoardsPanel.clip(g, String.valueOf(card.get("subtitle")), w - 24),
                x + 12, ty);
        }

        ty += 30;
        g.setFont(bold(10));
        g.setColor(p.superComplete ? new Color(0x1E, 0x7A, 0x33) : new Color(0x99, 0x66, 0x11));
        g.drawString(p.superComplete ? "СОБРАНО" : "внесено частей " + p.superProgress,
            x + 12, ty);
        if (p.superComplete) {
            ty += 20;
            g.setFont(note(9.5));
            g.setColor(BoardsPanel.ink2());
            g.drawString("ждёт развёртывания", x + 12, ty);
        }
        if (card == null) {
            return;
        }

        ty += 26;
        g.setFont(bold(9.5));
        g.setColor(BoardsPanel.ink2());
        g.drawString("одна часть за СПЕЦ-действие", x + 12, ty);

        List<Object> parts = new ArrayList<>();
        if (card.get("assembly") instanceof Map<?, ?> asm
                && asm.get("parts") instanceof List<?> list) {
            parts.addAll((List<Object>) list);
        }
        for (Object po : parts) {
            if (!(po instanceof Map<?, ?> part)) {
                continue;
            }
            String kind = String.valueOf(part.get("kind"));
            int need = part.get("amount") instanceof Number nn ? nn.intValue() : 0;
            int have = Math.min(need, p.superParts.getOrDefault(kind, 0));
            ty += 24;
            if (ty > y + h - 22) {
                break;
            }
            // НАЗВАНИЕ ЧАСТИ — СВЕРХУ, полоска — под ним: карта узкая, в одну
            // строку название и полоска не помещаются (формат 63×89).
            g.setFont(plain(9.5));
            g.setColor(BoardsPanel.ink());
            g.drawString(BoardsPanel.clip(g, partRu(kind), w - 60), x + 14, ty);
            g.setFont(bold(9));
            g.setColor(have >= need ? new Color(0x1E, 0x7A, 0x33) : BoardsPanel.ink3());
            String tag = have + "/" + need;
            g.drawString(tag, x + w - 14 - g.getFontMetrics().stringWidth(tag), ty);
            ty += 12;
            int bw = w - 28;
            int bh = 9;
            for (int i = 0; i < need; i++) {
                int sw = Math.max(5, bw / Math.max(1, need) - 3);
                int sx = x + 14 + i * (sw + 3);
                g.setColor(i < have ? new Color(0x1E, 0x7A, 0x33) : BoardsPanel.emptyCell());
                g.fillRoundRect(sx, ty - bh, sw, bh, 3, 3);
                g.setColor(BoardsPanel.line());
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(sx, ty - bh, sw, bh, 3, 3);
            }
        }
    }

    /**
     * РУБАШКА КАРТЫ — развёртывание. Рубашка на то и рубашка, что на ней рисунок:
     * поле из шестиугольников цвета места, а на нём — тот узор, который надо
     * выстроить, и подпись словами.
     */
    private void paintBack(Graphics2D g, int x, int y, int w, int h,
                           ReplayRecord.Player p, Map<String, Object> card, Color accent) {
        cardFrame(g, x, y, w, h, accent, BoardsPanel.paper());

        java.awt.Shape old = g.getClip();
        g.clipRect(x + 3, y + 3, w - 6, h - 6);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 26));
        double r = w / 9.0;
        for (int row = -1; row * r * 1.5 < h + r; row++) {
            for (int col = -1; col * r * 1.74 < w + r; col++) {
                double cx = x + col * r * 1.74 + (row % 2 == 0 ? 0 : r * 0.87);
                double cy = y + row * r * 1.5;
                java.awt.geom.Path2D hex = new java.awt.geom.Path2D.Double();
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(60 * k - 90);
                    double px = cx + r * 0.92 * Math.cos(a);
                    double py = cy + r * 0.92 * Math.sin(a);
                    if (k == 0) {
                        hex.moveTo(px, py);
                    } else {
                        hex.lineTo(px, py);
                    }
                }
                hex.closePath();
                g.fill(hex);
            }
        }
        g.setClip(old);

        int ty = y + 30;
        g.setColor(accent.darker());
        g.setFont(bold(9.5));
        g.drawString("РУБАШКА · РАЗВЁРТЫВАНИЕ", x + 12, ty);
        ty += 26;
        g.setFont(bold(10));
        g.setColor(BoardsPanel.ink());
        g.drawString("мгновенная победа", x + 12, ty);

        String pattern = card != null && card.get("win_pattern") instanceof Map<?, ?> wp
            ? String.valueOf(wp.get("id")) : "не задан";
        ty += 28;
        g.setFont(note(10));
        g.setColor(BoardsPanel.ink());
        BoardsPanel.wrap(g, patternRu(pattern), x + 12, ty, w - 24, 20, 8);
    }

    /** Человеческое название вида части сборки. */
    private static String partRu(String kind) {
        return switch (kind) {
            case "kelium" -> "келемий в проект";
            case "coin" -> "монеты в проект";
            case "ammo" -> "боеприпасы в проект";
            case "trophy" -> "обломки в проект";
            case "enemy_unit_token" -> "захваченные жетоны чужих ВОЙСК";
            case "enemy_building_token" -> "захваченные жетоны чужих ЗДАНИЙ";
            case "own_miner_bordering_grid" -> "свои добытчики у грядки";
            case "own_building_adjacent_enemy" -> "свои здания вплотную к чужим";
            case "own_unit_on_enemy_hex" -> "свои войска на чужом гексе";
            default -> kind;
        };
    }

    /**
     * Расшифровка геометрических узоров победы. Тексты — из карточного набора
     * super_objectives (id узора); проверка узора в движке пока заглушка, об
     * этом и подписано, чтобы вкладка не обещала лишнего.
     */
    private static String patternRu(String id) {
        String base = switch (id) {
            case "sp_line_of_three_buildings_mid_adjacent_enemy" ->
                "три своих здания в линию, среднее — вплотную к чужому";
            case "sp_three_buildings_one_hex_touching" ->
                "три своих здания, касающиеся одного гекса";
            case "sp_triangle_three_buildings_one_adjacent_enemy" ->
                "три своих здания треугольником, одно — вплотную к чужому";
            case "sp_four_buildings_around_common_hex" ->
                "четыре своих здания вокруг общего гекса";
            case "sp_chain_three_miners_bordering_grids" ->
                "цепочка из трёх добытчиков, каждый у своей грядки";
            case "sp_three_unit_hexes_adjacent_one_enemy_hex" ->
                "три гекса со своими войсками вокруг одного чужого гекса";
            case "sp_four_hexes_diamond_each_with_unit" ->
                "четыре гекса ромбом, на каждом своё войско";
            case "sp_three_tower_building_pairs" ->
                "три пары «вышка + здание»";
            default -> "узор " + id;
        };
        return base + ". Проверка узора в движке пока заглушка: развёртывание "
            + "записывается, но геометрия не сверяется.";
    }

    private Map<String, Object> find(String id) {
        if (content == null || id == null) {
            return null;
        }
        try {
            return content.get("super_objectives").find(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
