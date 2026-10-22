package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;

/**
 * ИСТОЧНИК УНИЧТОЖЕН ИЛИ СНЕСЁН (решение дизайнера 23.09.2026): энергия, которую
 * он давал, уходит в общий запас, и КАКИЕ кубики убрать, решает владелец — с
 * самого источника или с любых своих ячеек энергии.
 *
 * <p>Как это сделано. Кубики ушедшего источника снимаются с тех зданий, где
 * они лежали, — как и раньше. Затем владельцу предлагается перенести до
 * стольких же кубиков с других своих зданий на обесточенные. Итог тот же, что
 * у «выбери, откуда убрать»: потерял энергию тот, кого выбрал владелец, а число
 * убранных кубиков равно тому, что давал источник. Зато учёт «чей кубик где
 * лежит» остаётся верным без переписывания меток.
 */
public final class УходИсточника {

    private УходИсточника() {
    }

    /** Действует ли правило выбора владельца ({@code energy.lost_source_owner_chooses}). */
    static boolean выбираетВладелец(GameState s) {
        return Boolean.TRUE.equals(kelium.dataio.Ctx.rules(s)
            .get("energy.lost_source_owner_chooses", Boolean.FALSE));
    }

    /**
     * Снять с потребителей владельца кубики ушедшего источника и, если правило
     * включено, дать владельцу перераспределить потерю.
     *
     * @return сколько кубиков ушло
     */
    public static int уйти(GameState s, PlayerState владелец, int источник) {
        // ПОРЯДОК ВАЖЕН: партия обязана повторяться (пошаговый API), поэтому
        // список упорядоченный, а не по хешу жетона.
        Map<BuildingToken, Integer> лишились = new java.util.LinkedHashMap<>();
        int ушло = 0;
        for (BuildingToken b : владелец.buildingsOnField()) {
            if (b.uid == источник) {
                continue;
            }
            int n = b.stripEnergyOf(источник);
            if (n > 0) {
                лишились.put(b, n);
                ушло += n;
            }
        }
        if (ушло == 0 || !выбираетВладелец(s)) {
            return ушло;
        }
        Agent агент = s.agents == null || владелец.seat >= s.agents.size()
            ? null : s.agents.get(владелец.seat);
        if (агент == null) {
            return ушло;
        }
        // До «ушло» переносов: с любого своего здания с кубиком — на обесточенное.
        for (int шаг = 0; шаг < ушло; шаг++) {
            List<Choice> варианты = new ArrayList<>();
            for (BuildingToken куда : лишились.keySet()) {
                if (куда.hexId == null || куда.energyPlaced >= куда.energySlots) {
                    continue;
                }
                for (BuildingToken откуда : владелец.buildingsOnField()) {
                    if (откуда == куда || откуда.energyPlaced <= 0
                            || лишились.containsKey(откуда) && откуда.energyPlaced <= 0) {
                        continue;
                    }
                    Integer чей = любойЧужой(откуда);
                    if (чей == null) {
                        continue;
                    }
                    Map<String, Object> p = new HashMap<>();
                    p.put("from", откуда.uid);
                    p.put("to", куда.uid);
                    варианты.add(new Choice("energy_loss_shift", p,
                        "кубик с " + откуда.type.code + "@" + откуда.hexId + " на "
                            + куда.type.code + "@" + куда.hexId));
                }
            }
            if (варианты.isEmpty()) {
                break;
            }
            варианты.add(new Choice("pass", null, "оставить как есть"));
            Choice ч = агент.choose(s, варианты, Map.of("kind", "energy_loss_shift"));
            if (ч == null || ч.payload() == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) ч.payload();
            BuildingToken откуда = найти(владелец, ((Number) p.get("from")).intValue());
            BuildingToken куда = найти(владелец, ((Number) p.get("to")).intValue());
            if (откуда == null || куда == null) {
                break;
            }
            Integer чей = любойЧужой(откуда);
            if (чей == null) {
                break;
            }
            снятьОдин(откуда, чей);
            куда.addEnergyFrom(чей, 1);
        }
        return ушло;
    }

    /** Источник любого кубика на здании (ключ учёта), или null. */
    private static Integer любойЧужой(BuildingToken b) {
        for (Map.Entry<Integer, Integer> e : b.energyBySource.entrySet()) {
            // ЦУ свой собственный кубик не отдаёт — как и в Питании
            if (e.getValue() > 0 && e.getKey() != b.uid) {
                return e.getKey();
            }
        }
        return null;
    }

    private static void снятьОдин(BuildingToken b, int источник) {
        int n = b.energyBySource.getOrDefault(источник, 0);
        if (n <= 1) {
            b.energyBySource.remove(источник);
        } else {
            b.energyBySource.put(источник, n - 1);
        }
        b.energyPlaced = Math.max(0, b.energyPlaced - 1);
    }

    private static BuildingToken найти(PlayerState p, int uid) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.uid == uid) {
                return b;
            }
        }
        return null;
    }
}
