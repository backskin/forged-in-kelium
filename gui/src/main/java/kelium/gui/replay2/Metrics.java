package kelium.gui.replay2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kelium.report.ReplayRecord;

/**
 * Metrics — ПОКАЗАТЕЛИ ИГРОКОВ ВО ВРЕМЕНИ, посчитанные по записи один раз.
 *
 * <p>Экран итогов рисует по ним графики развития: очки, деньги, келемий,
 * боеприпасы, обломки, энергия на поле, уничтоженные жетоны и свои жетоны
 * на поле. У расходуемых ресурсов, кроме мгновенного значения, есть вторая
 * линия — СКОЛЬКО ПОТРАЧЕНО за партию.
 *
 * <p>Честно про «потрачено»: движок отдельного счётчика расходов не пишет, поэтому
 * здесь это СУММА ВСЕХ УМЕНЬШЕНИЙ показателя по ходу партии. Для монет, келемия,
 * боеприпасов и обломков это ровно то, что игрок с себя списал: они
 * убывают только когда их тратят. Ни одна цифра не додумана — всё считается из
 * кадров записи.
 *
 * <p>Уничтоженные жетоны тоже считаются по кадрам: жетон был в предыдущем кадре и
 * пропал (или помечен неживым) в следующем — значит его снесли, и запись
 * приписывается тому, чьё это было событие. Нейтралы считаются по их прочности.
 */
public final class Metrics {

    /** Показатель, который можно вывести на график. */
    public enum Kind {
        VP("ПО", false),
        COIN("деньги", true),
        KELIUM("келемий", true),
        AMMO("боеприпасы", true),
        DEBRIS("обломки (склад + несданные трофеи)", true),
        ENERGY("энергия на поле", false),
        KILLS("уничтожено жетонов", false),
        TOKENS("своих жетонов на поле", false);

        /** Подпись на кнопке-переключателе. */
        public final String label;
        /** Есть ли вторая линия «потрачено за партию». */
        public final boolean hasSpent;

        Kind(String label, boolean hasSpent) {
            this.label = label;
            this.hasSpent = hasSpent;
        }
    }

    private final int seats;
    private final int frames;
    /** [показатель][место][кадр] — мгновенное значение. */
    private final Map<Kind, int[][]> instant = new HashMap<>();
    /** [показатель][место][кадр] — накопленный расход. */
    private final Map<Kind, int[][]> spent = new HashMap<>();

    private Metrics(int seats, int frames) {
        this.seats = seats;
        this.frames = frames;
        for (Kind k : Kind.values()) {
            instant.put(k, new int[seats][frames]);
            spent.put(k, new int[seats][frames]);
        }
    }

    public int seats() {
        return seats;
    }

    public int frames() {
        return frames;
    }

    /** Мгновенное значение показателя по кадрам. */
    public int[] instant(Kind k, int seat) {
        int[][] s = instant.get(k);
        return seat >= 0 && seat < s.length ? s[seat] : new int[frames];
    }

    /** Накопленный расход по кадрам (для показателей с {@code hasSpent}). */
    public int[] spent(Kind k, int seat) {
        int[][] s = spent.get(k);
        return seat >= 0 && seat < s.length ? s[seat] : new int[frames];
    }

    /** Наибольшее значение по всем местам и обеим линиям — для оси графика. */
    public int max(Kind k, boolean withSpent) {
        int max = 0;
        for (int seat = 0; seat < seats; seat++) {
            for (int v : instant(k, seat)) {
                max = Math.max(max, v);
            }
            if (withSpent && k.hasSpent) {
                for (int v : spent(k, seat)) {
                    max = Math.max(max, v);
                }
            }
        }
        return max;
    }

    /** Разобрать запись. Пустая запись даёт пустые ряды, а не null. */
    public static Metrics of(ReplayRecord rec) {
        if (rec == null || rec.frames.isEmpty()) {
            return new Metrics(Math.max(1, rec == null ? 1 : rec.players), 1);
        }
        int n = rec.frames.size();
        int seats = Math.max(1, rec.players);
        Metrics m = new Metrics(seats, n);
        Map<Kind, int[]> prevOf = new HashMap<>();
        for (Kind k : Kind.values()) {
            prevOf.put(k, new int[seats]);
        }
        boolean first = true;
        Set<Integer> aliveBefore = new HashSet<>();
        int[] kills = new int[seats];
        Map<String, Integer> neutralHpBefore = new HashMap<>();

        for (int i = 0; i < n; i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            ReplayRecord.Snapshot snap = f.snapshot;
            if (snap == null) {
                carryForward(m, i);
                continue;
            }
            // ---- кто что уничтожил: сравниваем состав жетонов с прошлым кадром
            Set<Integer> aliveNow = new HashSet<>();
            for (ReplayRecord.Tok t : snap.tokens) {
                if (t.alive) {
                    aliveNow.add(t.uid);
                }
            }
            int actor = f.seat == null ? -1 : f.seat;
            if (!first && actor >= 0 && actor < seats) {
                for (int uid : aliveBefore) {
                    if (!aliveNow.contains(uid)) {
                        kills[actor]++;
                    }
                }
            }
            // нейтралы: у них нет uid, поэтому считаем по прочности на каждом гексе
            Map<String, Integer> neutralHpNow = new HashMap<>();
            for (ReplayRecord.HexState hs : snap.hexes) {
                for (ReplayRecord.Neutral nb : hs.neutrals) {
                    // ключ по гексу и углам: снесённый нейтрал ИСЧЕЗАЕТ из списка,
                    // и по номеру в списке остальные бы «сдвинулись»
                    neutralHpNow.put(hs.id + "#" + nb.corners, nb.hp);
                }
            }
            if (!first && actor >= 0 && actor < seats) {
                for (Map.Entry<String, Integer> e : neutralHpBefore.entrySet()) {
                    int now = neutralHpNow.getOrDefault(e.getKey(), 0);
                    if (now == 0 && e.getValue() > 0) {
                        kills[actor]++;
                    }
                }
            }
            aliveBefore = aliveNow;
            neutralHpBefore = neutralHpNow;

            for (int seat = 0; seat < seats; seat++) {
                ReplayRecord.Player p = seat < snap.players.size()
                    ? snap.players.get(seat) : null;
                for (Kind k : Kind.values()) {
                    int v = value(k, p, snap, seat, kills);
                    m.instant.get(k)[seat][i] = v;
                    int[] pv = prevOf.get(k);
                    int drop = first ? 0 : Math.max(0, pv[seat] - v);
                    int before = first || i == 0 ? 0 : m.spent.get(k)[seat][i - 1];
                    m.spent.get(k)[seat][i] = before + (k.hasSpent ? drop : 0);
                    pv[seat] = v;
                }
            }
            first = false;
        }
        return m;
    }

    /** Кадр без состояния — тянем предыдущие значения, чтобы график не рвался. */
    private static void carryForward(Metrics m, int i) {
        if (i == 0) {
            return;
        }
        for (Kind k : Kind.values()) {
            for (int seat = 0; seat < m.seats; seat++) {
                m.instant.get(k)[seat][i] = m.instant.get(k)[seat][i - 1];
                m.spent.get(k)[seat][i] = m.spent.get(k)[seat][i - 1];
            }
        }
    }

    private static int value(Kind k, ReplayRecord.Player p, ReplayRecord.Snapshot snap,
                             int seat, int[] kills) {
        return switch (k) {
            case VP -> p == null ? 0 : p.vp.getOrDefault("total", 0);
            case COIN -> p == null ? 0 : p.coin;
            case KELIUM -> p == null ? 0 : p.kelium;
            case AMMO -> p == null ? 0 : p.ammo;
            // ОБЛОМКИ — ВСЁ, чем игрок может заплатить в Науку: и обломки на
            // складе, и очки с ЖЕТОНОВ-трофеев, ещё не сданных. Показывать
            // только одно бессмысленно: Наука платит и тем, и другим, и на
            // графике получалась половина правды (замечание дизайнера 13.08.2026).
            case DEBRIS -> p == null ? 0 : p.debris + p.trophyPoints;
            case ENERGY -> energyOnField(snap, seat);
            case KILLS -> seat < kills.length ? kills[seat] : 0;
            case TOKENS -> tokensOnField(snap, seat);
        };
    }

    private static int energyOnField(ReplayRecord.Snapshot snap, int seat) {
        int sum = 0;
        for (ReplayRecord.Tok t : snap.tokens) {
            if (t.alive && t.owner == seat) {
                sum += Math.max(0, t.energyPlaced) + Math.max(0, t.energyIdle);
            }
        }
        return sum;
    }

    private static int tokensOnField(ReplayRecord.Snapshot snap, int seat) {
        int count = 0;
        for (ReplayRecord.Tok t : snap.tokens) {
            if (t.alive && t.owner == seat) {
                count++;
            }
        }
        return count;
    }
}
