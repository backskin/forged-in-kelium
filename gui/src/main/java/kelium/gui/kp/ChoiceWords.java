package kelium.gui.kp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kelium.core.Choice;

/**
 * ВАРИАНТ РЕШЕНИЯ СЛОВАМИ — для пузырей на поле и карточки вопроса.
 *
 * <p>Движок подписывает варианты для журнала и ботов: там встречаются коды
 * («infantry->h2_1», «burn top o17», «stop attacking»). Игроку это читать
 * нельзя (блокер приёмки №2 — никаких внутренних кодов на экране). Здесь
 * подпись переводится на язык правил; гекс из подписи убирается вовсе —
 * вариант и так нарисован у своего гекса.
 */
public final class ChoiceWords {

    private ChoiceWords() {
    }

    /**
     * ЖЕТОН МОДУЛЯ СЛОВАМИ по его номеру («R30-1» → «модуль боя: пехота или
     * техника»). Номер — ярлык из данных, игроку он ничего не говорит.
     * Окно партии подставляет описатель по библиотеке модулей своей партии.
     */
    public static Function<String, String> moduleWords = id -> null;

    private static String module(String id) {
        String w = id == null ? null : moduleWords.apply(id);
        return w != null ? w : "модуль";
    }

    private static final Pattern AT_HEX = Pattern.compile("\\s*@h-?\\d+_-?\\d+(/\\d+)?");
    private static final Pattern HEX = Pattern.compile("h(-?\\d+)_(-?\\d+)");
    private static final Pattern LEVELED = Pattern.compile(
        "\\b(miner|power_plant|plant|MINER|POWER_PLANT)\\s?L(\\d)\\b");
    private static final Pattern CODE = Pattern.compile(
        "\\b(command_center|power_plant|barracks|factory|airbase|miner|plant|"
            + "infantry|vehicle|aircraft|tower|COMMAND_CENTER|POWER_PLANT|BARRACKS|"
            + "FACTORY|AIRBASE|MINER|INFANTRY|VEHICLE|AIRCRAFT|TOWER)\\b");

    /**
     * Что сделать — крупной строкой.
     *
     * @param cardName имя карты по id (для СПЕЦ-вариантов и реакций)
     */
    public static String label(String kind, Choice c, Function<String, String> cardName) {
        Object p = c.payload();
        String raw = c.label() == null ? String.valueOf(p) : c.label();
        if ("pass".equals(c.kind()) && p == null) {
            return passWords(kind, raw);
        }
        // ПО ВИДУ ВАРИАНТА — то, что встречается в разных решениях.
        switch (c.kind()) {
            case "free_objective_burn" -> {
                return "Сжечь «" + cardName.apply(String.valueOf(p)) + "» — верх даром";
            }
            case "spec_arsenal_use" -> {
                return "СПЕЦ: «" + cardName.apply(String.valueOf(p)) + "»";
            }
            case "red_slot", "blue_slot", "red_replace", "blue_replace" -> {
                if (p instanceof Map<?, ?> m) {
                    Object where = m.get(c.kind().startsWith("red") ? "unit" : "building");
                    String code = where instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT)
                        : String.valueOf(where);
                    String place = c.kind().startsWith("red") ? unitRu(code) : buildingRu(code);
                    String what = c.kind().startsWith("red") ? "Модуль боя" : "Модуль сборки";
                    return what + " → " + place + (raw.contains("обмен") ? " (поменять местами)"
                        : c.kind().endsWith("replace") ? " (заменить)" : "");
                }
            }
            case "energy_give" -> {
                Matcher m = ENERGY_GIVE.matcher(raw);
                if (m.find()) {
                    int n = Integer.parseInt(m.group(1));
                    return "Раздать " + n + (n == 1 ? " кубик" : n < 5 ? " кубика" : " кубиков")
                        + " с " + sourceGen(m.group(2));
                }
            }
            case "energy_take" -> {
                Matcher m = ENERGY_TAKE.matcher(raw);
                return "Забрать все кубики обратно на "
                    + (m.find() ? sourceAcc(m.group(1)) : "источник");
            }
            case "energy_done" -> {
                return "Закончить смену энергии";
            }
            case "pay_power" -> {
                if (Boolean.TRUE.equals(p)) {
                    Matcher m = PAY_POWER.matcher(raw);
                    if (m.find()) {
                        int n = Integer.parseInt(m.group(2));
                        return "Заплатить " + n + (n == 1 ? " монету" : n < 5 ? " монеты" : " монет");
                    }
                    return "Заплатить монетами";
                }
                return "Не платить";
            }
            case "module_keep" -> {
                if (p instanceof String id) {
                    return "Оставить: " + module(id);
                }
            }
            case "gild_red", "gild_blue" -> {
                String code = p instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT)
                    : String.valueOf(p);
                return "Позолотить модуль на ячейке «" + ("gild_red".equals(c.kind())
                    ? unitRu(code) : buildingRu(code)) + "»";
            }
            case "neutral" -> {
                if (p instanceof Map<?, ?> m && m.get("sectors") instanceof List<?> s) {
                    return "Нейтральная постройка на " + s.size()
                        + (s.size() == 1 ? " сектор" : " сектора") + " · сторона "
                        + sidesRu(s);
                }
            }
            case "combat_victim" -> {
                if (p instanceof kelium.core.Token t) {
                    return cap(tokenRu(t)) + " игрока " + (t.owner() + 1)
                        + (raw.contains("(урон ") ? " — урон " + raw.substring(
                            raw.indexOf("(урон ") + 6).replace(")", "").replace("/", " из ")
                            : "");
                }
            }
            case "move_red", "move_blue" -> {
                String code = p instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT)
                    : String.valueOf(p);
                return "Снять модуль с ячейки «" + ("move_red".equals(c.kind())
                    ? unitRu(code) : buildingRu(code)) + "»";
            }
            case "spec_cu_return" -> {
                return "Вернуть ЦУ на поле";
            }
            case "order_spec", "order_plate" -> {
                return "Плашка приказа: " + switch (String.valueOf(p)) {
                    case "ammo" -> "1 боеприпас";
                    case "objective" -> "1 карта задания";
                    case "coin" -> "1 монета";
                    case "kelium" -> "1 келемий";
                    case "trophy" -> "1 трофей";
                    case "movement" -> "движение";
                    default -> tidy(String.valueOf(p));
                };
            }
            case "attack" -> {
                if (p instanceof Map<?, ?> m) {
                    return attack(raw, m);
                }
            }
            default -> {
            }
        }
        switch (kind) {
            case "action", "objective_reward_action", "energy_or_modules" -> {
                if (p instanceof String a) {
                    return ActionBar.ACTIONS.getOrDefault(a, "modules".equals(a)
                        ? "Смена модулей" : raw);
                }
            }
            case "move" -> {
                // вариант «гарнизон» в том же выборе: «infantry в BARRACKS»
                if (!raw.contains("->") && raw.contains(" в ")) {
                    return cap(unitRu(before(raw, " в "))) + " — в здание: "
                        + buildingRu(after(raw, " в "));
                }
                String unit = before(raw, "->");
                return cap(unitRu(unit)) + " — сюда";
            }
            case "maneuver_unit", "return_unit" -> {
                String unit = before(raw, "@");
                return cap(unitRu(unit)) + ("return_unit".equals(kind) ? " — в запас" : "");
            }
            case "energy_place" -> {
                return "Энергию на " + tidy(before(raw, "@").trim());
            }
            case "mine" -> {
                return "container".equals(p) ? "Взять контейнер" : "Добыть келемий";
            }
            case "assemble" -> {
                if (p instanceof Map<?, ?> m) {
                    return "ammo".equals(m.get("kind")) ? "Боеприпасы"
                        : "Нанять: " + unitRu(after(raw, "->"));
                }
            }
            case "landing" -> {
                if (p instanceof Map<?, ?> m) {
                    return "Высадить: " + unitRu(String.valueOf(m.get("type")));
                }
            }
            case "sci_track" -> {
                String track = before(raw, "->").trim();
                String rest = after(raw, "->").trim();
                return trackRu(track) + ": " + before(rest, "(").trim();
            }
            case "sci_exchange" -> {
                if (p instanceof Map<?, ?> m) {
                    return switch (String.valueOf(m.get("id"))) {
                        case "move_module" -> "Переставить модуль";
                        case "draw_arsenal" -> "Взять 2 карты арсенала, оставить 1";
                        case "gild" -> "Позолотить модуль";
                        default -> tidy(after(raw, "->"));
                    };
                }
            }
            case "spec", "mass_open", "reaction" -> {
                return specWords(c, cardName);
            }
            case "storage_side" -> {
                return String.valueOf(p).contains("energy") ? "Постоянный кубик энергии"
                    : "Универсальная ячейка склада";
            }
            case "build_pick" -> {
                if (p instanceof Map<?, ?> m && m.get("btype") != null) {
                    Integer lvl = m.get("level") instanceof Number n ? n.intValue() : null;
                    String b = buildingRu(String.valueOf(m.get("btype")));
                    return cap(b) + (lvl == null ? "" : " " + lvl);
                }
            }
            case "discard_enemy_arsenal" -> {
                if (p instanceof Map<?, ?> m && m.get("card") != null) {
                    return "Удалить «" + cardName.apply(String.valueOf(m.get("card")))
                        + "» у игрока " + (((Number) m.get("seat")).intValue() + 1);
                }
            }
            case "destroyed_pay" -> {
                if (p instanceof kelium.core.Token t) {
                    return "Жетон со свалки: " + tokenRu(t) + " — стоит " + t.trophyValue();
                }
            }
            case "ricochet_target" -> {
                if (p instanceof kelium.core.Token t) {
                    return cap(tokenRu(t)) + " игрока " + (t.owner() + 1);
                }
            }
            case "keep_objective", "objective_keep", "arsenal_draw2", "module_keep" -> {
                if (p instanceof String id) {
                    return "Оставить «" + cardName.apply(id) + "»";
                }
            }
            default -> {
            }
        }
        return tidy(raw);
    }

    /** «отдать 3 с POWER_PLANT», «отдать 1 с карты арсенала». */
    private static final Pattern ENERGY_GIVE = Pattern.compile("отдать (\\d+) с (.+)$");
    /** «забрать всё обратно на COMMAND_CENTER». */
    private static final Pattern ENERGY_TAKE = Pattern.compile("обратно на (\\S+)");

    /** Источник энергии в родительном: «с энергостанции», «с ЦУ». */
    private static String sourceGen(String code) {
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "power_plant" -> "энергостанции";
            case "command_center" -> "ЦУ";
            default -> code.trim();
        };
    }

    /** Источник энергии в винительном: «на энергостанцию», «на ЦУ». */
    private static String sourceAcc(String code) {
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "power_plant" -> "энергостанцию";
            case "command_center" -> "ЦУ";
            default -> code.trim();
        };
    }

    /** «запитать barracks монетами (2 МОН …» — здание и цена. */
    private static final Pattern PAY_POWER = Pattern.compile("запитать (\\S+) монетами \\((\\d+)");

    /** Пояснение мелко: цена, расход, последствие; null — нечего сказать. */
    public static String sub(String kind, Choice c) {
        Object p = c.payload();
        if ("pay_power".equals(c.kind()) && Boolean.TRUE.equals(p) && c.label() != null) {
            Matcher m = PAY_POWER.matcher(c.label());
            if (m.find()) {
                return "и запитать «" + buildingRu(m.group(1)) + "» на это действие";
            }
        }
        if ("attack".equals(kind) && p instanceof Map<?, ?> m) {
            StringBuilder s = new StringBuilder();
            if (m.get("ammo") instanceof Number n) {
                s.append("боеприпасов: ").append(n);
            }
            if (m.get("surcharge") instanceof Number n && n.intValue() > 0) {
                s.append(" · надбавка ").append(n);
            }
            return s.length() == 0 ? null : s.toString();
        }
        if ("sci_track".equals(kind) && c.label() != null && c.label().contains("(")) {
            return tidy(c.label().substring(c.label().indexOf('(') + 1).replace(")", ""));
        }
        if ("sci_exchange".equals(kind) && p instanceof Map<?, ?> m && m.get("give") != null) {
            return "трофеев: " + m.get("give");
        }
        if ("build_pick".equals(kind) && p instanceof Map<?, ?> m && m.get("cost") != null) {
            return "монет: " + m.get("cost");
        }
        if ("move".equals(kind) && p instanceof Map<?, ?> m
                && m.get("path") instanceof java.util.List<?> path && path.size() > 1) {
            return "шагов: " + path.size();
        }
        return null;
    }

    private static String passWords(String kind, String raw) {
        return switch (kind) {
            case "action" -> "Завершить ход";
            case "attack", "combat_source" -> "Прекратить бой";
            case "move" -> "Больше не двигаться";
            case "sci_track", "sci_exchange" -> "Закончить с наукой";
            case "market" -> "Хватит торговать";
            case "spec" -> "Без СПЕЦ-действия";
            case "mass_open" -> "Больше не вскрывать";
            case "reaction" -> "Не отвечать";
            case "maneuver_unit" -> "Без манёвра";
            case "return_unit" -> "Никого не возвращать";
            case "energy_place" -> "Хватит — остаток оставить на источнике";
            case "mine" -> "Пропустить добытчик";
            case "assemble" -> "Пропустить здание";
            case "build_pick" -> "Закончить стройку";
            case "build_hex", "build_facing" -> "Не строить";
            case "combat_victim", "neutral_victim" -> "Не выбирать";
            case "market_offer", "market_rate" -> "Хватит торговать";
            case "storage_discard" -> "Ничего не выбрасывать";
            case "module_move_pick", "module_gild_pick" -> "Отмена";
            case "module_place_red", "module_place_blue" -> "Оставить в запасе";
            case "move_source", "maneuver_hex" -> "Больше не двигаться";
            case "order_spec" -> "Не брать плашку";
            default -> {
                String t = tidy(raw);
                yield t.isEmpty() || t.equals("null") ? "Пропустить" : cap(t);
            }
        };
    }

    private static String specWords(Choice c, Function<String, String> cardName) {
        String id = c.payload() instanceof String s ? s : null;
        String name = id == null ? "" : "«" + cardName.apply(id) + "»";
        return switch (c.kind()) {
            case "spec_objective" -> "Выполнить " + name;
            case "spec_objective_enh" -> "Выполнить усиленно " + name;
            case "spec_objective_burn" -> "Сжечь " + name + " ради утиля";
            case "spec_arsenal_install" -> "Установить " + name;
            case "spec_arsenal_burn" -> "Сжечь " + name;
            case "mass_container" -> "Вскрыть контейнер";
            case "reaction_burn" -> {
                String raw = c.label() == null ? "" : c.label();
                int colon = raw.indexOf(':');
                yield "Сжечь " + name + (colon > 0 ? ":" + raw.substring(colon + 1) : "");
            }
            default -> {
                String raw = c.label() == null ? String.valueOf(c.payload()) : c.label();
                Matcher open = OPEN_ONE.matcher(raw);
                if (open.find()) {
                    yield "Вскрыть одну находку (контейнеров " + open.group(1)
                        + ", арсенала " + open.group(2) + ")";
                }
                yield tidy(raw);
            }
        };
    }

    /** «infantry.universal->units@h1_2» → «Пехота · обычная атака → по войскам». */
    public static String attack(String raw, Map<?, ?> payload) {
        int dot = raw.indexOf('.');
        int arrow = raw.indexOf("->");
        String unit = dot > 0 ? unitRu(raw.substring(0, dot)) : "";
        String rowCode = dot > 0 && arrow > dot ? raw.substring(dot + 1, arrow)
            : String.valueOf(payload.get("row"));
        String row = switch (rowCode) {
            case "universal" -> "обычная атака";
            case "special", "specialized" -> "спец-атака";
            default -> "атака";
        };
        String tcat = Boolean.TRUE.equals(payload.get("neutral")) ? "снести нейтральную постройку"
            : switch (String.valueOf(payload.get("tcat"))) {
                case "infantry" -> "по пехоте";
                case "vehicle" -> "по технике";
                case "aircraft" -> "по авиации";
                case "units" -> "по войскам";
                case "buildings_towers" -> "по зданиям и вышкам";
                case "any" -> "по любой цели";
                default -> "";
            };
        String head = unit.isEmpty() ? "Атака" : cap(unit);
        return head + " · " + row + (tcat.isEmpty() ? "" : " → " + tcat);
    }

    /** Английские фразы движка, которые доходят до игрока, — по-русски. */
    private static final String[][] ENGLISH = {
        {"stop moving", "Больше не двигаться"},
        {"stop building", "Не строить"},
        {"stop science", "Закончить с наукой"},
        {"stop attacking", "Прекратить бой"},
        {"stop opening", "Больше не вскрывать"},
        {"leave in reserve", "Оставить в запасе"},
        {"cancel", "Отмена"},
        {"extract kelium", "Добыть келемий"},
        {"take container", "Взять контейнер"},
        {"1 trophy -> 1 coin", "1 трофей → 1 монета"},
        {"1 trophy -> move a module", "1 трофей → переставить модуль"},
        {"hit", "Нанести урон"},
    };

    private static final Pattern OPEN_ONE =
        Pattern.compile("open one \\((\\d+) cont, (\\d+) ars\\)");

    /** Общая чистка подписи: без гексов и кодов, стрелки по-русски. */
    public static String tidy(String raw) {
        if (raw == null) {
            return "";
        }
        for (String[] e : ENGLISH) {
            if (raw.trim().equalsIgnoreCase(e[0])
                    || raw.trim().toLowerCase(Locale.ROOT).startsWith(e[0] + " @")) {
                return e[1];
            }
        }
        if (raw.startsWith("skip ")) {
            return "Пропустить";
        }
        if (raw.startsWith("tower @")) {
            return "Вышка сюда";
        }
        String s = AT_HEX.matcher(raw).replaceAll("");
        // жетоны модулей «R30-1», «C30-12» — служебные номера
        s = s.replaceAll("\\b[RC]\\d+-\\d+\\b", "модуль");
        s = s.replace("->", "→").replace("КЕЛ", "келемий").replace("МОН", "монет");
        // «miner L1», «power_plantL3» — здание с уровнем
        Matcher lv = LEVELED.matcher(s);
        StringBuilder lb = new StringBuilder();
        while (lv.find()) {
            lv.appendReplacement(lb, Matcher.quoteReplacement(
                buildingRu(lv.group(1)) + " " + lv.group(2)));
        }
        lv.appendTail(lb);
        s = lb.toString();
        Matcher m = CODE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String code = m.group(1).toLowerCase(Locale.ROOT);
            String ru = isUnit(code) ? unitRu(code) : buildingRu(code);
            m.appendReplacement(out, Matcher.quoteReplacement(ru));
        }
        m.appendTail(out);
        s = HEX.matcher(out.toString()).replaceAll("($1,$2)");
        return s.replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean isUnit(String code) {
        return switch (code) {
            case "infantry", "vehicle", "aircraft", "tower" -> true;
            default -> false;
        };
    }

    public static String unitRu(String code) {
        if (code == null) {
            return "";
        }
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "infantry" -> "пехота";
            case "vehicle" -> "техника";
            case "aircraft" -> "авиация";
            case "tower" -> "вышка";
            default -> code.trim();
        };
    }

    /** Стороны гекса по часовой с севера, номерами с единицы: «2», «2–3». */
    private static String sidesRu(List<?> sides) {
        StringBuilder sb = new StringBuilder();
        for (Object o : sides) {
            if (sb.length() > 0) {
                sb.append('–');
            }
            sb.append(o instanceof Number n ? n.intValue() + 1 : o);
        }
        return sb.toString();
    }

    /** Жетон словами: «добытчик 3», «пехота». */
    public static String tokenRu(kelium.core.Token t) {
        if (t instanceof kelium.core.BuildingToken b) {
            return buildingRu(b.type.code) + (b.level == null ? "" : " " + b.level);
        }
        return unitRu(((kelium.core.UnitToken) t).type.code);
    }

    public static String buildingRu(String code) {
        if (code == null) {
            return "";
        }
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "command_center" -> "центр управления";
            case "barracks" -> "казарма";
            case "factory" -> "завод";
            case "airbase" -> "авиабаза";
            case "miner" -> "добытчик";
            case "power_plant", "plant" -> "энергостанция";
            default -> code.trim();
        };
    }

    private static String trackRu(String t) {
        return switch (t) {
            case "left" -> "Левый трек";
            case "middle" -> "Средний трек";
            case "right" -> "Правый трек";
            default -> t;
        };
    }

    private static String before(String s, String sep) {
        int i = s.indexOf(sep);
        return i < 0 ? s : s.substring(0, i);
    }

    private static String after(String s, String sep) {
        int i = s.indexOf(sep);
        return i < 0 ? s : s.substring(i + sep.length());
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
