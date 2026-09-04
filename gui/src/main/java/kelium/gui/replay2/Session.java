package kelium.gui.replay2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import kelium.report.ReplayRecord;

/**
 * Session — СОСТОЯНИЕ РАЗБОРА ПАРТИИ и весь счёт по записи. Ни одного импорта Swing.
 *
 * <p>Зачем отдельно от окна. В версии 1.0 весь разбор жил внутри {@code ReplayGui}
 * на 1700 строк: курсор, прыжки, фильтры и подсчёты были перемешаны с раскладкой,
 * поэтому ни то ни другое нельзя было ни проверить тестом, ни переиспользовать.
 * Здесь — только смысл: где мы стоим, что считается интересным, куда прыгать.
 * Окно подписывается на изменения и рисует.
 *
 * <p>Тяжёлые выборки (метки событий, накал, поворотные моменты, странности)
 * считаются ОДИН РАЗ при загрузке записи, а не на каждую перерисовку ленты.
 */
public final class Session {

    /** Чем шагают стрелки: одно событие, ход игрока, круг или целый раунд. */
    public enum Step {
        EVENT("событие"), TURN("ход"), CIRCLE("круг"), ROUND("раунд");

        public final String label;

        Step(String label) {
            this.label = label;
        }
    }

    /** На чём показ останавливается сам. */
    public enum Stop {
        COMBAT("бой"),
        DESTROY("уничтожение жетона"),
        BUILD("стройка"),
        OBJECTIVE("розыгрыш задания"),
        CONTAINER("вскрытие контейнера"),
        ROUND_END("конец раунда"),
        SUPER("супер-задание"),
        FAILED("неудавшееся действие");

        public final String label;

        Stop(String label) {
            this.label = label;
        }
    }

    /** Значок на ленте времени: что за событие и насколько оно весомое. */
    public record Mark(int frame, Kind kind, int seat, double weight) {
        public enum Kind { COMBAT, DESTROY, BUILD, CONTAINER, OBJECTIVE, SUPER }
    }

    /** Строка «поворотного момента» и «странности»: куда идти и что там. */
    public record Moment(int frame, String text) {
    }

    // ==================== состояние ====================
    private ReplayRecord record;
    /**
     * КАРТОЧНЫЙ НАБОР той версии, в которой сыграна партия. Нужен там, где карту
     * надо не назвать, а ПРОЧИТАТЬ — в личной зоне игрока. Грузится один раз на
     * запись; не загрузился — читалка честно скажет, что показать нечем.
     */
    private kelium.dataio.ContentLibrary content;
    private int cursor;
    private Step step = Step.EVENT;
    private final Set<Stop> stops = EnumSet.of(Stop.COMBAT);
    private final Set<Integer> bookmarks = new LinkedHashSet<>();
    private final Map<Integer, String> notes = new LinkedHashMap<>();
    private String selectedHex;

    private final List<Consumer<Session>> onFrame = new ArrayList<>();
    private final List<Consumer<Session>> onRecord = new ArrayList<>();

    // ==================== посчитанное по записи ====================
    private final List<Mark> marks = new ArrayList<>();
    private double[] heat = new double[0];
    private final List<Moment> turningPoints = new ArrayList<>();
    private final List<Moment> oddities = new ArrayList<>();
    /** Номер первого кадра каждого раунда: индекс = раунд. */
    private int[] roundStart = new int[0];
    /** Кадры начала ходов игроков — по ним шагает гранулярность «ход». */
    private final List<Integer> turnStarts = new ArrayList<>();
    private final List<Integer> circleStarts = new ArrayList<>();

    // ==================== подписка ====================
    /** Позвать, когда сменился кадр (курсор). */
    public void whenFrameChanged(Consumer<Session> listener) {
        onFrame.add(listener);
    }

    /** Позвать, когда загрузилась другая запись. */
    public void whenRecordChanged(Consumer<Session> listener) {
        onRecord.add(listener);
    }

    private void fireFrame() {
        for (Consumer<Session> l : onFrame) {
            l.accept(this);
        }
    }

    // ==================== запись ====================
    /** Карточный набор партии (может быть null — тогда карт не прочитать). */
    public kelium.dataio.ContentLibrary content() {
        return content;
    }

    public void setContent(kelium.dataio.ContentLibrary content) {
        this.content = content;
    }

    public ReplayRecord record() {
        return record;
    }

    public boolean hasRecord() {
        return record != null && !record.frames.isEmpty();
    }

    /** Есть ли что листать: расстановка — это один кадр, её не «проигрывают». */
    public boolean playable() {
        return record != null && record.frames.size() > 1;
    }

    public void setRecord(ReplayRecord rec) {
        this.record = rec;
        this.cursor = 0;
        this.selectedHex = null;
        bookmarks.clear();
        notes.clear();
        analyse();
        for (Consumer<Session> l : onRecord) {
            l.accept(this);
        }
        fireFrame();
    }

    public int cursor() {
        return cursor;
    }

    public int frameCount() {
        return record == null ? 0 : record.frames.size();
    }

    public ReplayRecord.Frame frame() {
        return record == null ? null : record.frame(cursor);
    }

    public ReplayRecord.Frame frame(int i) {
        return record == null ? null : record.frame(i);
    }

    /** Перейти на кадр (с зажимом в границы). */
    public void seek(int index) {
        if (!hasRecord()) {
            return;
        }
        int i = Math.max(0, Math.min(record.frames.size() - 1, index));
        if (i == cursor) {
            return;
        }
        cursor = i;
        fireFrame();
    }

    public void seekEnd() {
        seek(frameCount() - 1);
    }

    // ==================== шаг и прыжки ====================
    public Step step() {
        return step;
    }

    public void setStep(Step s) {
        step = s;
    }

    /** Шаг вперёд или назад в ТЕКУЩЕЙ гранулярности. */
    public void stepBy(int direction) {
        if (!hasRecord()) {
            return;
        }
        switch (step) {
            case EVENT -> seek(cursor + direction);
            case TURN -> seekIn(turnStarts, direction);
            case CIRCLE -> seekIn(circleStarts, direction);
            case ROUND -> jumpRound(direction);
        }
    }

    /** Ближайшая граница из списка в нужную сторону. */
    private void seekIn(List<Integer> starts, int direction) {
        if (starts.isEmpty()) {
            seek(cursor + direction);
            return;
        }
        if (direction > 0) {
            for (int s : starts) {
                if (s > cursor) {
                    seek(s);
                    return;
                }
            }
            seekEnd();
        } else {
            int best = 0;
            for (int s : starts) {
                if (s < cursor) {
                    best = s;
                } else {
                    break;
                }
            }
            seek(best);
        }
    }

    /** К началу соседнего раунда. Никакой скрытой логики «если ты почти в начале». */
    public void jumpRound(int direction) {
        if (!hasRecord()) {
            return;
        }
        int round = frame().round;
        if (direction < 0) {
            int start = roundStartFrame(round);
            seek(cursor > start ? start : roundStartFrame(round - 1));
        } else {
            int next = roundStartFrame(round + 1);
            seek(next < 0 ? frameCount() - 1 : next);
        }
    }

    /** Первый кадр раунда; −1 — такого раунда в записи нет. */
    public int roundStartFrame(int round) {
        if (round <= 0) {
            return 0;
        }
        return round < roundStart.length ? roundStart[round] : -1;
    }

    /** Сколько раундов в записи. */
    public int roundCount() {
        return Math.max(0, roundStart.length - 1);
    }

    /** К ближайшему бою в нужную сторону; false — боёв там нет. */
    public boolean jumpBattle(int direction) {
        return jumpMark(direction, Mark.Kind.COMBAT);
    }

    /** К ближайшему значку нужного вида. */
    public boolean jumpMark(int direction, Mark.Kind kind) {
        if (!hasRecord()) {
            return false;
        }
        Integer best = null;
        for (Mark m : marks) {
            if (m.kind() != kind) {
                continue;
            }
            if (direction > 0 && m.frame() > cursor && (best == null || m.frame() < best)) {
                best = m.frame();
            }
            if (direction < 0 && m.frame() < cursor && (best == null || m.frame() > best)) {
                best = m.frame();
            }
        }
        if (best == null) {
            return false;
        }
        seek(best);
        return true;
    }

    /** К следующему ходу ТОГО ЖЕ игрока, что ходит сейчас. */
    public boolean jumpSameSeatTurn(int direction) {
        if (!hasRecord()) {
            return false;
        }
        Integer seat = frame().snapshot == null ? null : frame().snapshot.active;
        if (seat == null) {
            return false;
        }
        for (int i = cursor + direction; i >= 0 && i < frameCount(); i += direction) {
            ReplayRecord.Frame f = record.frames.get(i);
            if ("turn_orders".equals(f.type) && f.seat != null && f.seat.equals(seat)) {
                seek(i);
                return true;
            }
        }
        return false;
    }

    // ==================== автостоп ====================
    public Set<Stop> stops() {
        return stops;
    }

    public void toggleStop(Stop s, boolean on) {
        if (on) {
            stops.add(s);
        } else {
            stops.remove(s);
        }
    }

    /**
     * Надо ли остановиться на этом кадре. Причина возвращается словами, чтобы
     * написать её в строке состояния: «остановился на бою», а не молча замереть.
     */
    public String stopReason(int index) {
        ReplayRecord.Frame f = frame(index);
        if (f == null) {
            return null;
        }
        if (stops.contains(Stop.COMBAT) && f.combat) {
            return "на бою";
        }
        if (stops.contains(Stop.DESTROY) && !f.highlight.destroyed.isEmpty()) {
            return "на уничтожении жетона";
        }
        if (stops.contains(Stop.BUILD) && !f.highlight.builds.isEmpty()) {
            return "на стройке";
        }
        if (stops.contains(Stop.OBJECTIVE) && f.type.startsWith("objective")) {
            return "на розыгрыше задания";
        }
        if (stops.contains(Stop.CONTAINER) && "container".equals(f.type)) {
            return "на вскрытии контейнера";
        }
        if (stops.contains(Stop.ROUND_END) && "return".equals(f.type)) {
            return "на конце раунда";
        }
        if (stops.contains(Stop.SUPER) && superDone(f)) {
            return "на супер-задании";
        }
        if (stops.contains(Stop.FAILED) && failed(f)) {
            return "на неудавшемся действии";
        }
        return null;
    }

    private static boolean superDone(ReplayRecord.Frame f) {
        if (f.snapshot == null) {
            return false;
        }
        for (ReplayRecord.Player p : f.snapshot.players) {
            if (p.superComplete) {
                return true;
            }
        }
        return false;
    }

    /**
     * Неудавшееся действие. Признак берём из строки лога: движок пишет отказ
     * словами («не удалось», «нельзя», «не хватает»), а отдельного поля в кадре нет.
     */
    private static boolean failed(ReplayRecord.Frame f) {
        String s = f.log == null ? "" : f.log.toLowerCase(java.util.Locale.ROOT);
        return s.contains("не удалось") || s.contains("нельзя") || s.contains("не хватает")
            || s.contains("отказ") || s.contains("пропуск");
    }

    // ==================== закладки ====================
    public Set<Integer> bookmarks() {
        return bookmarks;
    }

    /** Поставить или снять закладку на текущем шаге; true — поставили. */
    public boolean toggleBookmark() {
        if (!hasRecord()) {
            return false;
        }
        if (bookmarks.remove(cursor)) {
            notes.remove(cursor);
            return false;
        }
        bookmarks.add(cursor);
        return true;
    }

    public void setNote(int frame, String text) {
        if (text == null || text.isBlank()) {
            notes.remove(frame);
        } else {
            notes.put(frame, text);
            bookmarks.add(frame);
        }
    }

    public String note(int frame) {
        return notes.get(frame);
    }

    // ==================== выделенный гекс ====================
    public String selectedHex() {
        return selectedHex;
    }

    public void selectHex(String hexId) {
        selectedHex = hexId;
        fireFrame();
    }

    // ==================== посчитанное ====================
    public List<Mark> marks() {
        return marks;
    }

    /** Накал партии по кадрам: 0…1. Урон, уничтожения и смена очков. */
    public double[] heat() {
        return heat;
    }

    public List<Moment> turningPoints() {
        return turningPoints;
    }

    public List<Moment> oddities() {
        return oddities;
    }

    /** Победные очки игрока по раундам: [место][раунд]. */
    private int[][] vpByRound = new int[0][0];

    public int[][] vpByRound() {
        return vpByRound;
    }

    /** Значение показателя игрока по раундам — для графика и спарклайнов. */
    public int[] seriesByRound(int seat, String metric) {
        int[] out = new int[Math.max(1, roundCount() + 1)];
        for (int r = 0; r < out.length; r++) {
            int f = roundStartFrame(r + 1);
            if (f < 0) {
                f = frameCount() - 1;
            }
            ReplayRecord.Frame fr = frame(Math.max(0, f - 1));
            if (fr == null || fr.snapshot == null || seat >= fr.snapshot.players.size()) {
                continue;
            }
            out[r] = metric(fr.snapshot.players.get(seat), metric);
        }
        return out;
    }

    static int metric(ReplayRecord.Player p, String metric) {
        return switch (metric) {
            case "vp" -> p.vp.getOrDefault("total", 0);
            case "kelium" -> p.kelium;
            case "coin" -> p.coin;
            case "ammo" -> p.ammo;
            case "trophy" -> p.trophy;
            case "tech" -> p.tech.values().stream().mapToInt(Integer::intValue).sum();
            default -> 0;
        };
    }

    /**
     * РАЗБОР ЗАПИСИ ОДИН РАЗ. Здесь считается всё, что потом рисуется каждую
     * перерисовку: границы раундов, ходов и кругов, значки событий для ленты,
     * кривая накала, поворотные моменты и журнал странностей.
     */
    private void analyse() {
        marks.clear();
        turningPoints.clear();
        oddities.clear();
        turnStarts.clear();
        circleStarts.clear();
        heat = new double[0];
        roundStart = new int[0];
        vpByRound = new int[0][0];
        if (record == null || record.frames.isEmpty()) {
            return;
        }
        int n = record.frames.size();
        int maxRound = 0;
        for (ReplayRecord.Frame f : record.frames) {
            maxRound = Math.max(maxRound, f.round);
        }
        roundStart = new int[maxRound + 2];
        java.util.Arrays.fill(roundStart, -1);
        roundStart[0] = 0;

        heat = new double[n];
        double[] raw = new double[n];
        Set<Integer> superDoneAt = new LinkedHashSet<>();
        int prevTotal = -1;
        int lastCircle = -1;
        for (int i = 0; i < n; i++) {
            ReplayRecord.Frame f = record.frames.get(i);
            if (f.round >= 0 && f.round < roundStart.length && roundStart[f.round] < 0) {
                roundStart[f.round] = i;
            }
            if ("turn_orders".equals(f.type)) {
                turnStarts.add(i);
            }
            if (f.circle != lastCircle) {
                lastCircle = f.circle;
                circleStarts.add(i);
            }

            double weight = 0;
            if (f.combat) {
                marks.add(new Mark(i, Mark.Kind.COMBAT, seatOf(f), 1));
                weight += 1;
            }
            if (!f.highlight.destroyed.isEmpty()) {
                marks.add(new Mark(i, Mark.Kind.DESTROY, seatOf(f),
                    1.5 * f.highlight.destroyed.size()));
                weight += 2.0 * f.highlight.destroyed.size();
            }
            if (!f.highlight.builds.isEmpty()) {
                marks.add(new Mark(i, Mark.Kind.BUILD, seatOf(f), 0.7));
                weight += 0.4;
            }
            weight += 0.5 * f.highlight.damaged.size();
            if ("container".equals(f.type)) {
                marks.add(new Mark(i, Mark.Kind.CONTAINER, seatOf(f), 0.6));
            }
            // Значок ставим только за ВЫПОЛНЕННОЕ задание. Взятие карты
            // (objective_drawn) случается почти каждый ход, и от таких значков
            // дорожка событий превращалась в сплошную полосу.
            if ("objective".equals(f.type)) {
                marks.add(new Mark(i, Mark.Kind.OBJECTIVE, seatOf(f), 0.8));
                weight += 0.5;
            }
            if (f.snapshot != null) {
                int total = 0;
                for (ReplayRecord.Player p : f.snapshot.players) {
                    total += p.vp.getOrDefault("total", 0);
                    // Значок ставим на ПЕРВЫЙ кадр, где задание собралось: дальше
                    // флаг остаётся поднятым до конца партии, и значок печатался бы
                    // на каждом кадре, заливая дорожку сплошной полосой.
                    if (p.superComplete && superDoneAt.add(p.seat)) {
                        marks.add(new Mark(i, Mark.Kind.SUPER, p.seat, 2));
                    }
                }
                if (prevTotal >= 0) {
                    weight += Math.abs(total - prevTotal);
                }
                prevTotal = total;
            }
            raw[i] = weight;
            if (failed(f)) {
                oddities.add(new Moment(i, "шаг " + (i + 1) + " · "
                    + "не получилось: " + shorten(f.log)));
            }
        }
        // сглаживание накала окном в 5 кадров — иначе кривая рябит
        double max = 0.0001;
        for (int i = 0; i < n; i++) {
            double s = 0;
            int c = 0;
            for (int k = Math.max(0, i - 2); k <= Math.min(n - 1, i + 2); k++) {
                s += raw[k];
                c++;
            }
            heat[i] = s / c;
            max = Math.max(max, heat[i]);
        }
        for (int i = 0; i < n; i++) {
            heat[i] = heat[i] / max;
        }
        // незаполненные раунды (их в записи нет) — тянем от предыдущего
        for (int r = 1; r < roundStart.length; r++) {
            if (roundStart[r] < 0) {
                roundStart[r] = r == 0 ? 0 : roundStart[r - 1];
            }
        }
        computeVpByRound();
        computeTurningPoints();
    }

    private static int seatOf(ReplayRecord.Frame f) {
        return f.seat == null ? -1 : f.seat;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() <= 80 ? t : t.substring(0, 79) + "…";
    }

    private void computeVpByRound() {
        int rounds = Math.max(1, roundCount());
        vpByRound = new int[record.players][rounds + 1];
        for (int seat = 0; seat < record.players; seat++) {
            vpByRound[seat] = seriesByRound(seat, "vp");
        }
    }

    /**
     * ПОВОРОТНЫЕ МОМЕНТЫ — арифметика, а не мнение: шаги с наибольшим изменением
     * суммы очков, плюс все уничтожения ЦУ и закрытые супер-задания. Оценок вроде
     * «играл агрессивно» здесь не будет никогда: дизайнер примет их за факт.
     */
    private void computeTurningPoints() {
        List<Moment> found = new ArrayList<>();
        int n = record.frames.size();
        int prev = -1;
        List<int[]> jumps = new ArrayList<>();     // {кадр, скачок}
        for (int i = 0; i < n; i++) {
            ReplayRecord.Frame f = record.frames.get(i);
            if (f.snapshot == null) {
                continue;
            }
            int total = 0;
            for (ReplayRecord.Player p : f.snapshot.players) {
                total += p.vp.getOrDefault("total", 0);
            }
            if (prev >= 0 && total - prev > 0) {
                jumps.add(new int[]{i, total - prev});
            }
            prev = total;
            // уничтоженное ЦУ — всегда поворотный момент
            for (String hex : f.highlight.destroyed) {
                for (ReplayRecord.Tok t : f.snapshot.tokens) {
                    if (hex.equals(t.hexId) && t.building && "cu".equalsIgnoreCase(t.type)) {
                        found.add(new Moment(i, "шаг " + (i + 1) + " · Р" + f.round
                            + " · снесено ЦУ игрока " + (t.owner + 1)));
                    }
                }
            }
            for (ReplayRecord.Player p : f.snapshot.players) {
                if (p.superComplete && found.stream().noneMatch(
                        m -> m.text().contains("супер-задание игрока " + (p.seat + 1)))) {
                    found.add(new Moment(i, "шаг " + (i + 1) + " · Р" + f.round
                        + " · закрыто супер-задание игрока " + (p.seat + 1)));
                }
            }
        }
        jumps.sort((a, b) -> Integer.compare(b[1], a[1]));
        for (int k = 0; k < Math.min(5, jumps.size()); k++) {
            int i = jumps.get(k)[0];
            ReplayRecord.Frame f = record.frames.get(i);
            found.add(new Moment(i, "шаг " + (i + 1) + " · Р" + f.round + " · сразу +"
                + jumps.get(k)[1] + " ПО: " + shorten(f.log)));
        }
        found.sort((a, b) -> Integer.compare(a.frame(), b.frame()));
        turningPoints.addAll(found.size() > 8 ? found.subList(0, 8) : found);
    }

    // ==================== заголовок и подписи ====================
    /** Строка контекста: раунд, круг, чей ход. */
    public String contextLine() {
        ReplayRecord.Frame f = frame();
        if (f == null || f.snapshot == null) {
            return "запись не загружена";
        }
        StringBuilder sb = new StringBuilder("Раунд ").append(f.snapshot.round);
        // Заказ дизайнера 17.08.2026: после просчёта партии видно, сколько
        // раундов она заняла ВСЕГО, а не только на каком раунде сейчас курсор.
        if (record.rounds > 0) {
            sb.append(" из ").append(record.rounds);
        }
        if (f.snapshot.circle > 0) {
            sb.append(" · круг ").append(f.snapshot.circle);
        }
        sb.append(f.snapshot.active != null
            ? " · ходит " + record.playerName(f.snapshot.active)
            : " · общая фаза раунда");
        return sb.toString();
    }

    /** Последняя прозвучавшая мысль бота на этом шаге (или null). */
    public ReplayRecord.Thought thought() {
        ReplayRecord.Frame f = frame();
        if (f == null || f.thoughts.isEmpty()) {
            return null;
        }
        return f.thoughts.get(f.thoughts.size() - 1);
    }

    /**
     * БИОГРАФИЯ ГЕКСА: всё, что на нём происходило за партию. Нужна для ответа на
     * вопрос «отчего у этого гекса всю партию мясорубка».
     */
    public List<Moment> hexBiography(String hexId) {
        List<Moment> out = new ArrayList<>();
        if (record == null || hexId == null) {
            return out;
        }
        for (int i = 0; i < record.frames.size(); i++) {
            ReplayRecord.Frame f = record.frames.get(i);
            ReplayRecord.Highlight h = f.highlight;
            String what = null;
            for (String[] mv : h.moves) {
                if (hexId.equals(mv[1])) {
                    what = "пришёл жетон";
                } else if (hexId.equals(mv[0])) {
                    what = "ушёл жетон";
                }
            }
            for (String[] at : h.attacks) {
                if (hexId.equals(at[1])) {
                    what = "удар сюда";
                } else if (hexId.equals(at[0])) {
                    what = "удар отсюда";
                }
            }
            if (h.builds.contains(hexId)) {
                what = "стройка";
            }
            if (h.damaged.contains(hexId)) {
                what = "получил урон";
            }
            if (h.destroyed.contains(hexId)) {
                what = "уничтожение";
            }
            if (what == null && f.log != null && f.log.contains(hexId)) {
                what = "упомянут в событии";
            }
            if (what != null) {
                out.add(new Moment(i, "Р" + f.round + " · шаг " + (i + 1) + " · " + what
                    + " — " + shorten(f.log)));
            }
        }
        return out;
    }
}
