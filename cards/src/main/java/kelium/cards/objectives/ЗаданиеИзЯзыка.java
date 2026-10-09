package kelium.cards.objectives;

import java.util.LinkedHashMap;
import java.util.Map;

import kelium.cards.Награда;
import kelium.cards.язык.Требование;
import kelium.engine.cards.CardContext;

/**
 * ЗАДАНИЕ, ЗАПИСАННОЕ ЯЗЫКОМ КАРТ (Карты 2.0, 30.09.2026).
 *
 * <p>Требование, усиление, награда и верх — данные записи {@code язык}; текст
 * карты печатается из узлов требования ({@link Требование#текст()}), руками
 * не пишется. Так карты собирает программа: новая карта — новая запись, без
 * класса под неё.
 *
 * <pre>
 *   - id: g_0001
 *     язык:
 *       имя: Двойной удар
 *       требование: {узел: обе_ветки, развилка: command}
 *       усиление: {узел: уничтожь, цель: {кто: ЗДАНИЕ, чьё: врага}, сколько: 1}
 *       награда: {действие: "mining|build_miner", монеты: 1}
 *       сверх: {спецДействий: 1}
 *       верх: ДВИЖЕНИЕ
 *       значок: command
 * </pre>
 *
 * <p>Усиленное требование — обычное И усиление («Дополнительно — …»).
 */
public final class ЗаданиеИзЯзыка extends ЗаданиеВКоде {

    private final Map<String, Object> запись;
    private final String имя;
    private final Требование требование;
    private final Требование усиление;
    private final Награда награда;
    private final Награда сверх;
    private final Утиль верх;
    private final String значок;
    /** Жертва (Задания 4.0): {@code {ресурс: kelium|trophies|buildings_off_cu|…, сколько: N}}. */
    private final Map<String, Object> жертва;

    public ЗаданиеИзЯзыка(String id, Map<String, Object> язык) {
        super(id);
        this.запись = new LinkedHashMap<>(язык);
        this.имя = String.valueOf(язык.getOrDefault("имя", id));
        this.требование = Требование.из(язык.get("требование"));
        this.усиление = язык.get("усиление") == null ? null : Требование.из(язык.get("усиление"));
        this.награда = награда(язык.get("награда"));
        this.сверх = награда(язык.get("сверх"));
        this.жертва = язык.get("жертва") instanceof Map<?, ?> ж
            ? Map.of("resource", String.valueOf(ж.get("ресурс")),
                "amount", ж.get("сколько") instanceof Number n ? n.intValue() : 1)
            : null;
        this.верх = язык.get("верх") == null ? null : Утиль.valueOf(String.valueOf(язык.get("верх")));
        this.значок = язык.get("значок") == null ? null : String.valueOf(язык.get("значок"));
    }

    /** Награда из записи: ключи — имена полей {@link Награда}. */
    static Награда награда(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return Награда.нет();
        }
        return new Награда(число(m, "монеты"), число(m, "боеприпасы"), число(m, "трофеи"),
            число(m, "келемий"), число(m, "картыЗаданий"), число(m, "картыАрсенала"),
            число(m, "картыСВитрины"), m.get("модуль") == null ? null : String.valueOf(m.get("модуль")),
            m.get("действие") == null ? null : String.valueOf(m.get("действие")),
            Boolean.TRUE.equals(m.get("позолота")), число(m, "спецДействий"), эффекты(m.get("эффекты")));
    }

    /**
     * ГОТОВЫЕ РЕЗУЛЬТАТЫ (Задания 4.0): список {@code {id: эффект, …параметры}} —
     * эффекты реестра движка. Пример: {@code {id: free_action, action: science,
     * virtual_trophy: 5}} — шаг по треку даром.
     */
    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> эффекты(Object o) {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (o instanceof java.util.List<?> l) {
            for (Object x : l) {
                if (x instanceof Map<?, ?> m) {
                    out.add(new java.util.LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        return out;
    }

    private static int число(Map<?, ?> m, String ключ) {
        return m.get(ключ) instanceof Number n ? n.intValue() : 0;
    }

    @Override
    public Лицо лицо() {
        // Природу карты задаёт основное требование: усиление проверяется в тот же
        // миг, что и оно, и печатается продолжением («Дополнительно — …»).
        Лицо.Природа природа = требование.происшествие()
            ? Лицо.Природа.ПРОИСШЕСТВИЕ : Лицо.Природа.СОСТОЯНИЕ;
        String доп = усиление == null ? null
            : (усиление.происшествие() && !требование.происшествие() ? "в этот ход " : "")
                + усиление.суть();
        return печатное(имя, природа, требование.текст(), доп, награда, сверх, верх);
    }

    @Override
    protected Map<String, Object> жертваВЗаписи() {
        return жертва;
    }

    @Override
    public boolean satisfied(CardContext ctx) {
        return требование.выполнено(ctx) && запасЖертвы(ctx) >= нужноЖертвы();
    }

    /** Сколько единиц жертвы просит карта (0 — карта без жертвы). */
    private int нужноЖертвы() {
        return жертва == null ? 0 : ((Number) жертва.get("amount")).intValue();
    }

    /**
     * ЧЕМ ИГРОК МОЖЕТ ЗАПЛАТИТЬ ЖЕРТВУ — тем же счётом, что и движок при
     * розыгрыше: келемий из хранилища, жетоны свалки, здания кроме ЦУ, карты
     * арсенала в руке. Без этого карта-жертва с требованием «всегда» говорила
     * боту «готово», и он не копил под неё ни келемия, ни трофеев.
     */
    private int запасЖертвы(CardContext ctx) {
        if (жертва == null) {
            return 0;
        }
        kelium.core.PlayerState p = ctx.state().player(ctx.seat());
        String рес = String.valueOf(жертва.get("resource"));
        return switch (рес) {
            case "trophies" -> p.destroyedTokens.size();
            case "arsenal_cards" -> p.arsenalHand.size();
            case "units_on_field" -> p.unitsOnField().size();
            case "buildings_off_cu" -> (int) p.buildingsOnField().stream()
                .filter(b -> b.type != kelium.core.BuildingType.COMMAND_CENTER).count();
            default -> {
                try {
                    yield p.resources.get(kelium.core.Resource.fromCode(рес));
                } catch (RuntimeException e) {
                    yield 0;
                }
            }
        };
    }

    @Override
    public boolean satisfiedEnhanced(CardContext ctx) {
        return усиление != null && требование.выполнено(ctx) && усиление.выполнено(ctx);
    }

    /**
     * ЧЕГО НЕ ХВАТАЕТ — словами карты. Прежде у карт языка этого не было вовсе
     * (пустая строка по умолчанию): подсказка в окне и договор карт молчали.
     */
    @Override
    public String needed(CardContext ctx) {
        if (требование.выполнено(ctx)) {
            return усиление != null && !усиление.выполнено(ctx)
                ? "выполнено; дополнительно — " + усиление.суть() : "готово";
        }
        return требование.текст() + String.format(" (пройдено %d%%)",
            Math.round(100 * Math.max(0, Math.min(1, требование.близость(ctx)))));
    }

    @Override
    public double progress(CardContext ctx) {
        double б = Math.max(0, Math.min(1, требование.близость(ctx)));
        int надо = нужноЖертвы();
        if (надо > 0) {
            б = Math.min(б, Math.min(1.0, (double) запасЖертвы(ctx) / надо));
        }
        return б;
    }

    @Override
    protected String действие(CardContext ctx) {
        // НЕЧЕМ ЗАПЛАТИТЬ — сперва добыть то, чем платят
        if (нужноЖертвы() > 0 && запасЖертвы(ctx) < нужноЖертвы()) {
            return switch (String.valueOf(жертва.get("resource"))) {
                case "kelium" -> "mining";
                case "ammo" -> "assembly";
                case "coin" -> "market";
                case "trophies", "trophy" -> "combat";
                case "arsenal_cards" -> "science";
                case "units_on_field" -> "assembly";
                case "buildings_off_cu" -> "build_miner";
                default -> требование.действие(ctx);
            };
        }
        return требование.действие(ctx);
    }

    @Override
    protected String действие() {
        return требование.действие();
    }

    /** Значок развилки карты ({@code extract}, {@code command}…) или {@code null}. */
    public String значок() {
        return значок;
    }

    /** Требование карты — для генератора и измерителя. */
    public Требование требование() {
        return требование;
    }

    /** Выгрузка несёт и запись языка — чтобы карту можно было собрать заново. */
    public Map<String, Object> данныеЯзыка() {
        Map<String, Object> out = new LinkedHashMap<>(super.data());
        out.put("язык", запись);
        if (значок != null) {
            out.put("значок", значок);
        }
        return out;
    }
}
