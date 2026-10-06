package kelium.cards.язык;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.engine.Storage;
import kelium.engine.cards.CardContext;

/**
 * ПОКАЗАТЕЛЬ В ДИАПАЗОНЕ — требование Заданий 4.0 (дизайнер 06.10.2026).
 *
 * <p>Прежние узлы спрашивали только «не меньше»: «имей 3 добытчика». Такое
 * требование сильный игрок выполняет сам собой, и карта становится премией
 * лидеру. Задания 4.0 просят состояний, которые конвейеру НЕВЫГОДНЫ: «ни одного
 * боеприпаса», «все добытчики без энергии», «ни один жетон не у врага». Для этого
 * нужен диапазон с верхней границей — {@code от … до …}.
 *
 * <p>Запись: {@code {узел: показатель, что: <имя>, от: N, до: M}}; любой конец
 * можно опустить. Имена показателей — в {@link #ПОКАЗАТЕЛИ}.
 */
public record Показатель(String что, int от, int до) implements Требование {

    /** Имя показателя → как его прочесть словами (именительный падеж, «число …»). */
    public static final Map<String, String> ПОКАЗАТЕЛИ = new LinkedHashMap<>();

    static {
        ПОКАЗАТЕЛИ.put("войск", "ваших войск на поле");
        ПОКАЗАТЕЛИ.put("войск_макс_на_гексе", "ваших войск на одном гексе");
        ПОКАЗАТЕЛИ.put("гексов_с_войсками", "гексов с вашими войсками");
        ПОКАЗАТЕЛИ.put("войск_вдвоём_на_гексе", "гексов, где стоит больше одного вашего войска");
        ПОКАЗАТЕЛИ.put("жетонов_у_врага", "ваших жетонов на гексах с врагом или рядом с ним");
        ПОКАЗАТЕЛИ.put("зданий", "ваших зданий");
        ПОКАЗАТЕЛИ.put("зданий_запитано", "ваших запитанных зданий");
        ПОКАЗАТЕЛИ.put("добытчиков", "ваших добытчиков");
        ПОКАЗАТЕЛИ.put("добытчиков_запитано", "ваших запитанных добытчиков");
        ПОКАЗАТЕЛИ.put("энергостанций", "ваших энергостанций");
        ПОКАЗАТЕЛИ.put("военных", "ваших военных зданий");
        ПОКАЗАТЕЛИ.put("военных_незапитано", "ваших незапитанных военных зданий");
        ПОКАЗАТЕЛИ.put("зданий_на_гексах_врага", "ваших зданий на гексах с вражеским зданием");
        ПОКАЗАТЕЛИ.put("боеприпасы", "боеприпасов в хранилище");
        ПОКАЗАТЕЛИ.put("келемий", "келемия в хранилище");
        ПОКАЗАТЕЛИ.put("трофеи", "кубиков трофеев в хранилище");
        ПОКАЗАТЕЛИ.put("монеты", "монет");
        ПОКАЗАТЕЛИ.put("хранилище_занято", "занятых ячеек хранилища");
        ПОКАЗАТЕЛИ.put("хранилище_свободно", "свободных ячеек хранилища");
        ПОКАЗАТЕЛИ.put("цу_с_уроном", "урона на вашем ЦУ");
        ПОКАЗАТЕЛИ.put("отставание_по_трекам", "ступеней, на которые вы отстаёте от лидера по трекам");
        ПОКАЗАТЕЛИ.put("трофеев_на_свалке", "трофеев на жетонах вашей свалки");
        ПОКАЗАТЕЛИ.put("арсенал_установлен", "ваших установленных карт арсенала");
    }

    /** Без верхней границы. */
    public static final int БЕЗ_ПРЕДЕЛА = Integer.MAX_VALUE;

    /** Значение показателя для игрока. */
    public static int значение(String что, GameState s, int seat) {
        PlayerState p = s.player(seat);
        return switch (что) {
            case "войск" -> p.unitsOnField().size();
            case "войск_макс_на_гексе" -> {
                Map<String, Integer> поГексам = new HashMap<>();
                for (Token u : p.unitsOnField()) {
                    поГексам.merge(u.hexId(), 1, Integer::sum);
                }
                yield поГексам.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            }
            case "гексов_с_войсками" -> {
                Set<String> г = new HashSet<>();
                for (Token u : p.unitsOnField()) {
                    г.add(u.hexId());
                }
                yield г.size();
            }
            case "войск_вдвоём_на_гексе" -> {
                Map<String, Integer> поГексам = new HashMap<>();
                for (Token u : p.unitsOnField()) {
                    поГексам.merge(u.hexId(), 1, Integer::sum);
                }
                yield (int) поГексам.values().stream().filter(n -> n > 1).count();
            }
            case "жетонов_у_врага" -> {
                Set<String> вражьи = new HashSet<>();
                for (Token t : Требование.жетоны(s, seat, false)) {
                    if (t.hexId() != null) {
                        вражьи.add(t.hexId());
                    }
                }
                int n = 0;
                for (Token t : Требование.жетоны(s, seat, true)) {
                    String h = t.hexId();
                    if (h == null) {
                        continue;
                    }
                    boolean рядом = вражьи.contains(h);
                    for (String с : s.field.neighbors(h)) {
                        рядом |= вражьи.contains(с);
                    }
                    if (рядом) {
                        n++;
                    }
                }
                yield n;
            }
            case "зданий" -> счёт(s, seat, Кто.ЗДАНИЕ, Группа.Состояние.ЛЮБОЕ);
            case "зданий_запитано" -> счёт(s, seat, Кто.ЗДАНИЕ, Группа.Состояние.ЗАПИТАН);
            case "добытчиков" -> счёт(s, seat, Кто.ДОБЫТЧИК, Группа.Состояние.ЛЮБОЕ);
            case "добытчиков_запитано" -> счёт(s, seat, Кто.ДОБЫТЧИК, Группа.Состояние.ЗАПИТАН);
            case "энергостанций" -> счёт(s, seat, Кто.ЭНЕРГОСТАНЦИЯ, Группа.Состояние.ЛЮБОЕ);
            case "военных" -> счёт(s, seat, Кто.ВОЕННОЕ, Группа.Состояние.ЛЮБОЕ);
            case "военных_незапитано" -> счёт(s, seat, Кто.ВОЕННОЕ, Группа.Состояние.НЕ_ЗАПИТАН);
            case "зданий_на_гексах_врага" -> {
                Set<String> вражьи = new HashSet<>();
                Группа чужоеЗдание = new Группа(Кто.ЗДАНИЕ, false, Группа.Состояние.ЛЮБОЕ);
                for (Token t : Требование.жетоны(s, seat, false)) {
                    if (чужоеЗдание.подходит(t) && t.hexId() != null) {
                        вражьи.add(t.hexId());
                    }
                }
                int n = 0;
                Группа своё = new Группа(Кто.ЗДАНИЕ, true, Группа.Состояние.ЛЮБОЕ);
                for (Token t : Требование.жетоны(s, seat, true)) {
                    if (своё.подходит(t) && вражьи.contains(t.hexId())) {
                        n++;
                    }
                }
                yield n;
            }
            case "боеприпасы" -> p.resources.ammo();
            case "келемий" -> p.resources.kelium();
            case "трофеи" -> p.resources.trophy();
            case "монеты" -> p.resources.coin();
            case "хранилище_занято" -> p.resources.kelium() + p.resources.ammo() + p.resources.trophy();
            case "хранилище_свободно" -> Math.max(0, Storage.totalMax(s, p)
                - p.resources.kelium() - p.resources.ammo() - p.resources.trophy());
            case "цу_с_уроном" -> счёт(s, seat, Кто.ЦУ, Группа.Состояние.С_УРОНОМ);
            case "отставание_по_трекам" -> {
                int моё = ступеней(p);
                int лучшее = 0;
                for (PlayerState о : s.players) {
                    if (о.seat != seat) {
                        лучшее = Math.max(лучшее, ступеней(о));
                    }
                }
                yield Math.max(0, лучшее - моё);
            }
            case "трофеев_на_свалке" -> p.destroyedTokens.stream().mapToInt(Token::trophyValue).sum();
            case "арсенал_установлен" -> p.arsenalInstalled.size();
            default -> throw new IllegalArgumentException("неизвестный показатель: " + что);
        };
    }

    private static int ступеней(PlayerState p) {
        return p.techSteps.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int счёт(GameState s, int seat, Кто кто, Группа.Состояние сост) {
        Группа г = new Группа(кто, true, сост);
        int n = 0;
        for (Token t : Требование.жетоны(s, seat, true)) {
            if (г.подходит(t)) {
                n++;
            }
        }
        return n;
    }

    @Override public boolean выполнено(CardContext ctx) {
        int v = значение(что, ctx.state(), ctx.seat());
        return v >= от && v <= до;
    }

    /**
     * Близость: снизу — доля пути до нижней границы; сверху — чем дальше за
     * верхнюю, тем меньше (лишнее приходится ещё и убирать).
     */
    @Override public double близость(CardContext ctx) {
        int v = значение(что, ctx.state(), ctx.seat());
        if (v < от) {
            return от <= 0 ? 0 : (double) v / от;
        }
        if (v > до) {
            return 1.0 / (1 + v - до);
        }
        return 1.0;
    }

    @Override public String суть() {
        String имя = ПОКАЗАТЕЛИ.getOrDefault(что, что);
        if (до == 0) {
            return "ни одного: " + имя;
        }
        if (от == до) {
            return имя + " — ровно " + от;
        }
        if (до == БЕЗ_ПРЕДЕЛА) {
            return имя + " — не меньше " + от;
        }
        if (от <= 0) {
            return имя + " — не больше " + до;
        }
        return имя + " — от " + от + " до " + до;
    }

    @Override public boolean происшествие() {
        return false;
    }

    @Override public Map<String, Object> запись() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("узел", "показатель");
        m.put("что", что);
        if (от > 0) {
            m.put("от", от);
        }
        if (до != БЕЗ_ПРЕДЕЛА) {
            m.put("до", до);
        }
        return m;
    }

    static Показатель из(Map<?, ?> m) {
        String что = String.valueOf(m.get("что"));
        if (!ПОКАЗАТЕЛИ.containsKey(что)) {
            throw new IllegalArgumentException("неизвестный показатель: " + что);
        }
        int от = m.get("от") instanceof Number n ? n.intValue() : 0;
        int до = m.get("до") instanceof Number n ? n.intValue() : БЕЗ_ПРЕДЕЛА;
        return new Показатель(что, от, до);
    }

    /** Список имён — для справки и проверки каталога. */
    public static List<String> имена() {
        return List.copyOf(ПОКАЗАТЕЛИ.keySet());
    }
}
