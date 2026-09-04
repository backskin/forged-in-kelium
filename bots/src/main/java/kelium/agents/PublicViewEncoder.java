package kelium.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.observe.PublicView;

/**
 * ЗЕРКАЛО {@code training/kelium_ml/encode.py} НА JAVA.
 *
 * <p>Питон учит сеть на числах из этого же {@link PublicView}; здесь тот же
 * стол превращается в ТЕ ЖЕ числа для инференса уже обученной сети
 * ({@link ValueNetOnnx}). Порядок признаков, их число и формулы нормировки
 * должны совпадать с {@code encode.py} БУКВАЛЬНО — разойдись они хоть в одном
 * месте, сеть будет читать координаты как ресурсы, и ошибка не бросит
 * исключение, а просто тихо испортит оценку. Если меняешь один файл —
 * обязательно меняй и второй.
 */
final class PublicViewEncoder {

    static final int MAX_HEXES = 50;
    static final int MAX_OTHERS = 3;
    static final int HEX_FEATS = 17;
    static final int SEAT_FEATS = 24;
    static final int N_SCALARS = 4;
    static final int N_CHARACTERS = 8;

    private PublicViewEncoder() {
    }

    record Encoded(float[] hexX, float[] hexMask, float[] seatX, float[] seatMask,
                   float[] scalars) {
    }

    static Encoded encode(PublicView v) {
        Map<Integer, Integer> orderOf = new HashMap<>();
        orderOf.put(v.me.seat, 0);
        for (PublicView.Seat st : v.others) {
            orderOf.put(st.seat, st.order);
        }

        float[] hexX = new float[MAX_HEXES * HEX_FEATS];
        float[] hexMask = new float[MAX_HEXES];
        int hn = Math.min(v.hexes.size(), MAX_HEXES);
        for (int i = 0; i < hn; i++) {
            float[] f = hexFeatures(v.hexes.get(i), orderOf, v.players);
            System.arraycopy(f, 0, hexX, i * HEX_FEATS, HEX_FEATS);
            hexMask[i] = 1.0f;
        }

        float[] seatX = new float[(1 + MAX_OTHERS) * SEAT_FEATS];
        float[] seatMask = new float[1 + MAX_OTHERS];
        System.arraycopy(seatFeatures(v.me), 0, seatX, 0, SEAT_FEATS);
        seatMask[0] = 1.0f;
        for (PublicView.Seat st : v.others) {
            if (st.order >= 1 && st.order <= MAX_OTHERS) {
                System.arraycopy(seatFeatures(st), 0, seatX, st.order * SEAT_FEATS, SEAT_FEATS);
                seatMask[st.order] = 1.0f;
            }
        }

        float[] scalars = {
            v.round / 12.0f,
            v.circle / 6.0f,
            v.players / 4.0f,
            (v.active != null && v.active == v.me.seat) ? 1.0f : 0.0f,
        };

        return new Encoded(hexX, hexMask, seatX, seatMask, scalars);
    }

    private static float clip(double x) {
        return (float) Math.max(-2.0, Math.min(2.0, x));
    }

    private static float[] hexFeatures(kelium.report.ReplayRecord.HexState h,
                                        Map<Integer, Integer> orderOf, int players) {
        float[] f = new float[HEX_FEATS];
        f[0] = clip(h.containerCell / 6.0);
        f[1] = clip(h.energyCell / 6.0);
        f[2] = clip(orderOf.getOrDefault(h.ownerTint, -1) / (double) Math.max(1, players - 1));
        f[3] = h.ownerBuilt ? 1.0f : 0.0f;
        for (int i = 0; i < 6; i++) {
            int s = i < h.sideOwner.length ? h.sideOwner[i] : -1;
            f[4 + i] = clip(orderOf.getOrDefault(s, -1) / (double) Math.max(1, players - 1));
        }
        if (h.spawn != null) {
            f[10] = 1.0f;
            f[11] = clip(h.spawn.kelium / 4.0);
            f[12] = clip(h.spawn.stack / 2.0);
            f[13] = h.spawn.flipped ? 1.0f : 0.0f;
        }
        List<kelium.report.ReplayRecord.Neutral> neutrals = h.neutrals;
        f[14] = clip(neutrals.size() / 2.0);
        int hpSum = 0;
        int hpMaxSum = 0;
        for (kelium.report.ReplayRecord.Neutral n : neutrals) {
            hpSum += n.hp;
            hpMaxSum += n.hpMax;
        }
        f[15] = clip(hpSum / 4.0);
        f[16] = clip(hpMaxSum / 4.0);
        return f;
    }

    private static float[] seatFeatures(PublicView.Seat st) {
        int techSum = 0;
        int techPeaks = 0;
        for (int v : st.tech.values()) {
            techSum += v;
            if (v >= 3) {
                techPeaks++;
            }
        }
        int vpTotal = st.vp.getOrDefault("total", 0);
        float[] f = {
            clip(st.coin / 15.0),
            clip(st.kelium / 8.0),
            clip(st.ammo / 8.0),
            clip(st.trophy / 10.0),
            clip(st.keliumCap / 8.0),
            clip(st.ammoCap / 8.0),
            clip(st.trophyCap / 10.0),
            clip(st.storeCap / 6.0),
            clip(st.destroyedCount / 6.0),
            clip(st.destroyedValue / 10.0),
            clip(techSum / 12.0),
            clip(techPeaks / 3.0),
            clip(st.redModules / 3.0),
            clip(st.blueModules / 3.0),
            clip(st.goldModules / 2.0),
            clip(st.cuTokens / 2.0),
            st.ownCuToken ? 1.0f : 0.0f,
            clip(vpTotal / 30.0),
            clip(st.superProgress / 5.0),
            st.superComplete ? 1.0f : 0.0f,
            clip(st.containers / 4.0),
            clip(st.arsenalInstalled.size() / 5.0),
            clip(st.orderPlayed.size() / 3.0),
            clip(st.storageTokens.size() / 6.0),
        };
        return f;
    }
}
