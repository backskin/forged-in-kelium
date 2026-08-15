package kelium.core;

import java.util.List;
import java.util.Map;

/**
 * Доска игрока целиком: выбранная боевая сторона + складская сторона.
 *
 * <p>Обёртка незыблемого ядра над версионируемым контентом досок. Чтобы ядро не
 * зависело от слоя dataio, фабрики принимают уже разобранный список записей
 * контента досок ({@code List<Map<String,Object>>}).
 */
public final class PlayerBoard {

    public final TroopSide troop;
    public final StorageSide storage;

    public PlayerBoard(TroopSide troop, StorageSide storage) {
        this.troop = troop;
        this.storage = storage;
    }

    /** Собрать доску игрока из контента по кодам боевой и складской сторон. */
    public static PlayerBoard fromContent(List<Map<String, Object>> boardsEntries,
                                          String troopSide, String storageSide) {
        Map<String, Object> troopRaw = find(boardsEntries, "troop_side", troopSide);
        Map<String, Object> storageRaw = find(boardsEntries, "storage_side", storageSide);
        return new PlayerBoard(new TroopSide(troopSide, troopRaw),
                               new StorageSide(storageSide, storageRaw));
    }

    /** Найти в контенте досок запись заданного вида (kind) и стороны (side). */
    public static Map<String, Object> find(List<Map<String, Object>> boardsEntries,
                                           String kind, String side) {
        for (Map<String, Object> e : boardsEntries) {
            if (kind.equals(e.get("kind")) && side.equals(e.get("side"))) {
                return e;
            }
        }
        throw new IllegalArgumentException("нет " + kind + " со стороной " + side + " в контенте досок");
    }

    /** Достать запись с характеристиками жетонов (token_stats). */
    public static Map<String, Object> tokensEntry(List<Map<String, Object>> boardsEntries) {
        for (Map<String, Object> e : boardsEntries) {
            if ("token_stats".equals(e.get("kind"))) {
                return e;
            }
        }
        throw new IllegalArgumentException("нет token_stats в контенте досок");
    }

    /** Достать запись с раскладкой доски науки (tech_board). */
    public static Map<String, Object> techEntry(List<Map<String, Object>> boardsEntries) {
        for (Map<String, Object> e : boardsEntries) {
            if ("tech_board".equals(e.get("kind"))) {
                return e;
            }
        }
        throw new IllegalArgumentException("нет tech_board в контенте досок");
    }
}
