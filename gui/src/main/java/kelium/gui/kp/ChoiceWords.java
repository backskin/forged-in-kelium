package kelium.gui.kp;

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
        switch (kind) {
            case "action", "objective_reward_action", "energy_or_modules" -> {
                if (p instanceof String a) {
                    return ActionBar.ACTIONS.getOrDefault(a, "modules".equals(a)
                        ? "Смена модулей" : raw);
                }
            }
            case "move" -> {
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

    /** Пояснение мелко: цена, расход, последствие; null — нечего сказать. */
    public static String sub(String kind, Choice c) {
        Object p = c.payload();
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
            case "energy_place" -> "Оставить простаивать";
            case "mine" -> "Пропустить добытчик";
            case "assemble" -> "Пропустить здание";
            case "build_pick", "build_hex", "build_facing" -> "Не строить";
            case "combat_victim", "neutral_victim" -> "Не выбирать";
            case "market_offer", "market_rate" -> "Хватит торговать";
            case "storage_discard" -> "Ничего не выбрасывать";
            case "module_move_pick", "module_gild_pick" -> "Отмена";
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

    private static final Pattern OPEN_ONE =
        Pattern.compile("open one \\((\\d+) cont, (\\d+) ars\\)");

    /** Общая чистка подписи: без гексов и кодов, стрелки по-русски. */
    public static String tidy(String raw) {
        if (raw == null) {
            return "";
        }
        String s = AT_HEX.matcher(raw).replaceAll("");
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
