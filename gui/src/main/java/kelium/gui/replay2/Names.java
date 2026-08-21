package kelium.gui.replay2;

import java.util.Locale;
import java.util.Map;

import kelium.report.ReplayRecord;

/**
 * Names — ЕДИНЫЙ СЛОВАРЬ ЧЕЛОВЕЧЕСКИХ ПОДПИСЕЙ.
 *
 * <p>В версии 1.0 таких словарей было пять, в разных классах: {@code vpName} и
 * {@code trackName} в зоне игрока, {@code orderColour} рядом, {@code conditionRu} в
 * главном окне, {@code RU} в полоске приказов. Из-за этого часть внутренних ключей
 * не переводилась вовсе, и на планшете рынка красовалось
 * {@code kelium_to_ammo: per_kelium_ammo 2} — для дизайнера это загадка.
 *
 * <p>Правило: <b>ни один внутренний ключ не показывается человеку.</b> Нет
 * подписи — пишем «не описано», а не английское имя из данных.
 */
public final class Names {

    private Names() {
    }

    /**
     * Победные очки: откуда пришли.
     *
     * <p>НАЙДЕНО 18.08.2026: словарь и {@link kelium.engine.Scoring} разошлись —
     * ключ {@code "trophy"} здесь стоял годами, а сам подсчёт очков ни разу его
     * не выставлял (ресурс переименован в обломки, ключ разбивки — {@code
     * "debris"}); плюс {@code objective_card_vp} и {@code arsenal_vp} появились в
     * Scoring позже словаря. Из-за этого КАЖДАЯ партия с обломками или очками
     * задания/арсенала честно, но бесполезно писала в итогах «не описано».
     * Список сверен построчно с {@code Scoring.scorePlayer} — там, где строка
     * попадает в {@code breakdown}, здесь обязана быть подпись.
     */
    public static String vp(String key) {
        return switch (key) {
            case "kelium" -> "келемий";
            case "coins" -> "монеты";
            case "debris" -> "обломки";
            case "buildings_on_field" -> "здания";
            case "units_on_field" -> "войска";
            case "tech" -> "наука";
            case "gold_modules" -> "золотые модули";
            case "spawn_tiles" -> "тайлы";
            case "cu_tokens" -> "жетоны ЦУ";
            case "war_track" -> "военный трек";
            case "super_arsenal" -> "супер-арсенал";
            case "super_first_part" -> "первая часть супер-задания";
            case "kills" -> "уничтожения";
            case "level4_stars" -> "звёзды 4-го уровня";
            case "installed_arsenal" -> "установленные карты арсенала";
            case "installed_super_arsenal" -> "установленный супер-арсенал";
            // Прямые очки с карты задания (CardContext.grantVp — редкий пункт vp
            // в данных карты, отдельно от обычной награды монетами/картами).
            case "objective_card_vp" -> "очки с карты задания";
            // Установленная (не сожжённая) карта арсенала со своим scoring-условием.
            case "arsenal_vp" -> "очки арсенала";
            case "total" -> "всего";
            default -> unknown(key);
        };
    }

    /** Треки науки: в данных они left/middle/right. */
    public static String track(String id) {
        return switch (id) {
            case "left" -> "красный";
            case "middle" -> "зелёный";
            case "right" -> "синий";
            default -> unknown(id);
        };
    }

    /** Что даёт трек — короткой подписью под цветом. */
    public static String trackGives(String id) {
        return switch (id) {
            case "left" -> "красные модули";
            case "middle" -> "склад";
            case "right" -> "синие модули";
            default -> "";
        };
    }

    /**
     * КОЛОДА ПРИКАЗОВ — ПО ЖИВОТНОМУ, А НЕ ПО ЦВЕТУ (просьба дизайнера
     * 14.08.2026). Раньше колоду называли её печатным цветом («голубая»,
     * «алая»…), и это путалось с ЦВЕТОМ МЕСТА ЗА СТОЛОМ — тот задаётся номером
     * места и не выбирается, а колоду выбрать можно. Имя животного — узнаваемая
     * личность колоды в текстах и подсказках; печатный цвет карт остаётся,
     * но отдельно ({@link #orderDeckColourWord}) и как образец
     * ({@link #orderDeckColour}), а не как имя.
     */
    public static String orderDeck(String code) {
        return switch (code == null ? "" : code) {
            case "blue" -> "Волк";
            case "red", "scarlet" -> "Ястреб";
            case "green" -> "Барс";
            case "yellow" -> "Лиса";
            case "security" -> "БЕЗОПАСНОСТЬ";
            default -> unknown(code);
        };
    }

    /** Печатный цвет колоды словом — как он назван в данных карты. */
    public static String orderDeckColourWord(String code) {
        return switch (code == null ? "" : code) {
            case "blue" -> "голубая";
            case "red", "scarlet" -> "алая";
            case "green" -> "зелёная";
            case "yellow" -> "жёлтая";
            case "security" -> "БЕЗОПАСНОСТЬ";
            default -> unknown(code);
        };
    }

    /** Печатный цвет колоды образцом — для цветной точки рядом с именем животного. */
    public static java.awt.Color orderDeckColour(String code) {
        return switch (code == null ? "" : code) {
            case "blue" -> new java.awt.Color(0x2F6FC4);
            case "red", "scarlet" -> new java.awt.Color(0xC0392B);
            case "green" -> new java.awt.Color(0x2E8B4E);
            case "yellow" -> new java.awt.Color(0xD1A62A);
            default -> Theme.ink3();
        };
    }

    /**
     * Пара приказов на карте словами: «ПРИОБРЕТЕНИЯ / ИНФРАСТРУКТУРА». Названия у
     * карт приказов в наборе нет вовсе — карта опознаётся колодой и этой парой,
     * поэтому имя ей собирается здесь, а не берётся из данных.
     */
    public static String orderPair(String top, String bottom) {
        String t = top == null || top.isBlank() ? "не описано" : order(top);
        return bottom == null || bottom.isBlank() ? t : t + " / " + order(bottom);
    }

    /**
     * ИМЯ КАРТЫ ДЛЯ ЭКРАНА — единственный способ показать карту человеку.
     *
     * <p>{@link kelium.report.ReplayRecord#cardName(String)} при отсутствии названия
     * отдаёт сам идентификатор, и на планшете игрока красовалось {@code blue_acq}
     * (найдено 13.08.2026) — ровно то, что правилу этого класса запрещено.
     *
     * <p>Порядок: название из записи → имя приказа, собранное словами из уже
     * вскрытых приказов той же карты → честное «не описано». Внутренний код не
     * показывается ни в одном из случаев.
     */
    public static String card(ReplayRecord rec, String id) {
        if (id == null || id.isBlank()) {
            return "—";
        }
        if (rec == null) {
            return "не описано";
        }
        String n = rec.cardNames.get(id);
        if (n != null && !n.isBlank() && !n.equals(id)) {
            return printable(n);
        }
        String pair = orderPairOf(rec, id);
        return pair == null ? "не описано" : pair;
    }

    /**
     * Какие приказы напечатаны на карте — по уже вскрытым приказам той же партии.
     * Колода за раунды проходит по кругу, и карта почти всегда где-то вскрывалась;
     * не нашлось — {@code null}, и вызвавший скажет «не описано».
     */
    private static String orderPairOf(ReplayRecord rec, String id) {
        for (ReplayRecord.OrderPlay op : rec.orderPlays) {
            if (id.equals(op.card)) {
                return orderPair(op.top, op.bottom);
            }
        }
        return null;
    }

    /** Название приказа (верх или низ карты). */
    public static String order(String code) {
        return switch (code) {
            case "development" -> "РАЗРАБОТКА";
            case "infrastructure" -> "ИНФРАСТРУКТУРА";
            case "operation" -> "ОПЕРАЦИЯ";
            case "acquisitions" -> "ПРИОБРЕТЕНИЯ";
            // Джокер печатается как БЕЗОПАСНОСТЬ — у него нет верха и низа.
            case "security", "joker" -> "БЕЗОПАСНОСТЬ";
            // НЕИЗВЕСТНЫЙ КОД НЕ ВЫДАЁТ СЕБЯ ЗА КАРТУ. Прежде здесь стояла та же
            // БЕЗОПАСНОСТЬ: любая опечатка в данных и любой приказ из нового
            // набора читались как настоящая карта, и отличить это на экране было
            // нечем.
            default -> unknown(code);
        };
    }

    /** Действие: то, что игрок делает по приказу. */
    public static String action(String code) {
        return switch (code) {
            case "assembly" -> "снаряжение";
            case "mining" -> "добыча";
            case "build" -> "стройка";
            case "energy_swap" -> "энергия";
            case "movement" -> "движение";
            case "combat" -> "бой";
            case "market" -> "рынок";
            case "science" -> "наука";
            case "special" -> "спец-действие";
            default -> unknown(code);
        };
    }

    /** Чем кончилась партия. */
    public static String condition(String code) {
        return switch (code == null ? "" : code) {
            case "victory_points" -> "по победным очкам";
            case "super_objective" -> "супер-заданием";
            case "all_peaks_occupied" -> "заняты все вершины треков";
            case "last_spawn_tile" -> "кончился келемий на поле";
            case "military" -> "военная победа";
            case "" -> "партия завершена";
            default -> unknown(code);
        };
    }

    /** Чем кончилась партия — фразой для экрана итогов. */
    public static String conditionLong(String code) {
        return switch (code == null ? "" : code) {
            case "victory_points" -> "победа по победным очкам";
            case "military" -> "ВОЕННАЯ победа: уничтожено второе ЦУ";
            case "super_objective" -> "победа по СУПЕР-ЗАДАНИЮ: проект развёрнут";
            case "all_peaks_occupied" -> "конец по науке: заняты все три вершины треков "
                + "· победа по очкам";
            case "last_spawn_tile" -> "конец по келемию: на поле остался последний тайл "
                + "зарождения · победа по очкам";
            case "" -> "партия завершена";
            default -> unknown(code);
        };
    }

    /** Тип события кадра — короткой подписью для ленты времени и лога. */
    public static String eventType(String type) {
        return switch (type == null ? "" : type) {
            case "", "setup", "preview" -> "расстановка";
            case "game_start" -> "начало партии";
            case "refresh" -> "обновление";
            case "circle" -> "круг";
            case "reveal" -> "вскрытие приказов";
            case "turn_orders" -> "ход игрока";
            case "action" -> "действие";
            case "combat_hit" -> "удар";
            case "objective" -> "задание выполнено";
            case "objective_burn" -> "задание сожжено";
            case "objective_drawn" -> "взято задание";
            case "container" -> "контейнер вскрыт";
            case "raze_neutral" -> "нейтрал снесён";
            case "tokens_returned" -> "жетоны вернулись";
            case "return" -> "конец раунда";
            case "game_end" -> "конец партии";
            // Неизвестный тип — нейтральное слово, а не «не описано»: это заголовок
            // титра на поле, и «не описано» там читается как ошибка.
            default -> "шаг партии";
        };
    }

    /**
     * ОБМЕНЫ И ЭФФЕКТЫ КАРТ — самое больное место 1.0: сюда попадали ключи из
     * данных как есть. Переводим то, что знаем, и честно говорим про остальное.
     */
    public static String effect(String key, Object value) {
        String v = value == null ? "" : String.valueOf(value);
        return switch (key == null ? "" : key) {
            case "per_kelium_coin" -> "за 1 келемий — " + v + " монет";
            case "per_kelium_ammo" -> "за 1 келемий — " + v + " боеприпасов";
            case "per_kelium_cards" -> "за 1 келемий — " + v + " карт заданий";
            case "kelium_to_coin" -> "келемий в монеты";
            case "kelium_to_ammo" -> "келемий в боеприпасы";
            case "kelium_to_objective" -> "келемий в карты заданий";
            case "kelium_to_energy" -> "келемий в энергию";
            case "place_on_energy_cell" -> "поставить кубик в ячейку энергии";
            case "noop" -> "ничего не даёт";
            // ОБМЕНЫ НАУЧНОГО ОТДЕЛА: «отдал столько — получил столько».
            case "give_trophy" -> "отдать обломков: " + v;
            case "get_coin" -> "получить монет: " + v;
            case "trophy_to_coin" -> "обломки в монеты";
            case "move_module" -> "переставить модуль";
            case "draw_arsenal", "draw2_keep1" -> "взять две карты арсенала, оставить одну";
            case "gild_module" -> "озолотить модуль";
            default -> unknown(key) + (v.isBlank() ? "" : " " + v);
        };
    }

    /**
     * Предложение карты рынка: строка вида {@code free Build (no surcharge)} или
     * {@code free Energy-swap + 2 coin} из данных — по-русски.
     */
    public static String offer(String raw) {
        if (raw == null || raw.isBlank()) {
            return "не описано";
        }
        String s = raw.trim();
        String low = s.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        // «free <действие>» — самое частое, и именно оно оставалось английским
        for (Map.Entry<String, String> e : FREE.entrySet()) {
            if (low.startsWith("free " + e.getKey())) {
                out.append(e.getValue()).append(" бесплатно");
                String tail = s.substring(("free " + e.getKey()).length()).trim();
                if (!tail.isEmpty()) {
                    out.append(' ').append(tail(tail));
                }
                return out.toString();
            }
        }
        return tail(s);
    }

    private static final Map<String, String> FREE = Map.of(
        "assembly", "снаряжение",
        "build", "стройка",
        "mining", "добыча",
        "movement", "движение",
        "combat", "бой",
        "market", "рынок",
        "science", "наука",
        "energy-swap", "обмен энергии",
        "energy_swap", "обмен энергии");

    /** Хвост строки предложения: «+ 2 coin», «(no surcharge)», «5 coin». */
    private static String tail(String s) {
        String r = s
            .replace("(no surcharge)", "(без надбавки)")
            .replace("no surcharge", "без надбавки")
            .replace("(no ammo)", "(без затрат боеприпасов)")
            .replace("(no kelium)", "(без затрат келемия)")
            .replace("(no coin)", "(без затрат монет)")
            .replaceAll("(?i)\\bcoins?\\b", "мон.")
            .replaceAll("(?i)\\bammo\\b", "БПР")
            .replaceAll("(?i)\\bkelium\\b", "КЕЛ")
            .replaceAll("(?i)\\btrophy\\b", "трофей")
            .replaceAll("(?i)\\bcards?\\b", "карт")
            .replaceAll("(?i)\\bvp\\b", "ПО");
        return r;
    }

    /** Код здания на поле → человеческое имя (движок отдаёт коды). */
    public static String building(String type) {
        return kelium.gui.GameRecorder.buildingName(type);
    }

    public static String unit(String type) {
        return kelium.gui.GameRecorder.unitName(type);
    }

    /** Короткий код здания для плиток: ЦУ, Кз, Зв, Ав, Д, Э. */
    public static String buildingCode(String type) {
        return kelium.gui.GameRecorder.buildingCode(type);
    }

    /**
     * Ключ, для которого подписи нет. Показываем ЧЕСТНО, что не описано, но не сам
     * ключ: внутреннее имя на экране — это всегда ошибка.
     */
    private static String unknown(String key) {
        return "не описано";
    }

    /** Число со знаком для дельт: {@code +2}, {@code −1} (настоящий минус). */
    public static String delta(int value) {
        return value > 0 ? "+" + value : "−" + Math.abs(value);
    }

    /**
     * ТОЛЬКО ТО, ЧТО ШРИФТ УМЕЕТ НАРИСОВАТЬ.
     *
     * <p>Строки лога приходят из движка, и в них попадаются символы-украшения
     * (маркеры списка, значки карт), которых в системном шрифте нет — на экране
     * вместо них пустые квадраты. Один раз это уже испортило пульт, поэтому весь
     * чужой текст проходит через этот фильтр: непечатаемое молча выбрасывается.
     */
    public static String printable(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        java.awt.Font f = kelium.gui.replay2.Theme.body();
        if (f.canDisplayUpTo(s) < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\t' || f.canDisplay(c)) {
                sb.append(c);
            }
        }
        // ВНУТРЕННИЕ КОДЫ В СКОБКАХ убираем: движок иногда дописывает в лог
        // идентификатор карты вроде «(yellow_dev)» — человеку он ничего не говорит,
        // а правило у нас одно: внутренних ключей на экране быть не должно.
        return sb.toString()
            .replaceAll("\\s*\\([a-z][a-z0-9_]*\\)", "")
            .replaceAll("^[\\s·•]+", "")
            .trim();
    }
}
