package kelium.gui.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingType;
import kelium.core.Resource;
import kelium.core.UnitType;

/**
 * КАТАЛОГ СЦЕН РЕЖИМА РАЗРАБОТЧИКА.
 *
 * <p>Каждая сцена — это состояние, которое надо УВИДЕТЬ, а не сыграть. Сцены
 * подобраны по одному правилу: сюда попадает то, что в случайной партии либо не
 * встречается вовсе, либо встречается так редко, что дожидаться его бессмысленно.
 *
 * <p>Добавить сцену — дописать одну запись в {@link #ALL}. Держать их лучше
 * короткими: сцена, которая занимает экран кода, проверяет уже не отрисовку, а
 * терпение.
 */
public final class DevScenes {

    private DevScenes() {
    }

    /** Что делает сцена, одной строкой — печатается в списке. */
    private record Item(String about, java.util.function.Supplier<Scene> make) {
    }

    private static final Map<String, Item> ALL = new LinkedHashMap<>();

    static {
        ALL.put("полный-гекс", new Item(
            "один гекс, забитый жетонами: здание на 2 сектора, пехота, техника, авиация",
            DevScenes::полныйГекс));
        ALL.put("подбитые", new Item(
            "все виды жетонов с разным уроном — проверка обводки цифр и полосок HP",
            DevScenes::подбитые));
        ALL.put("энергия", new Item(
            "станции всех уровней на жёлтом секторе и вне него, кубики и простой",
            DevScenes::энергия));
        ALL.put("супероружие", new Item(
            "вторая часть супер-задания: счётчик на 4 ячейки с повторами символов",
            DevScenes::супероружие));
        ALL.put("рынок", new Item(
            "карта рынка с занятыми ячейками обоих предложений",
            DevScenes::рынок));
        ALL.put("стенки", new Item(
            "нейтральные здания на 1, 2 и 3 сектора вокруг одного гекса",
            DevScenes::стенки));
        ALL.put("полный-стол", new Item(
            "живая партия на четверых, доигранная до пятого раунда",
            DevScenes::полныйСтол));
        ALL.put("колоды", new Item(
            "колода на исходе, толстый сброс, витрина арсенала — вкладка «Карты»",
            DevScenes::колоды));
        ALL.put("итоги", new Item(
            "партия окончена военной победой — экран итогов",
            DevScenes::итоги));
    }

    /** Имена всех сцен в порядке каталога. */
    public static List<String> names() {
        return List.copyOf(ALL.keySet());
    }

    /** Однострочное описание сцены. */
    public static String about(String name) {
        Item i = ALL.get(name);
        return i == null ? "" : i.about();
    }

    /** Собрать сцену по имени; {@code null} — такой сцены нет. */
    public static Scene build(String name) {
        Item i = ALL.get(name);
        return i == null ? null : i.make().get();
    }

    // ==================== сами сцены ====================

    /**
     * ГЕКС, ЗАБИТЫЙ ДО ОТКАЗА. Шесть секторов земли и небо заняты одновременно, и
     * жетоны разных игроков. В партии такое бывает раз в несколько десятков
     * партий, а рисовальщику это самый тяжёлый случай: подписей и полосок больше
     * всего именно здесь.
     */
    private static Scene полныйГекс() {
        return Scene.of(4)
            .title("гекс, забитый жетонами")
            .building(0, "h0_0", BuildingType.BARRACKS, null, 0)
            .unit(0, "h0_0", UnitType.INFANTRY)
            .unit(0, "h0_0", UnitType.VEHICLE)
            .unit(1, "h0_0", UnitType.AIRCRAFT)
            .unit(1, "h0_0", UnitType.TOWER)
            .neutral("h0_0", false, 4, 5)
            .round(6, 2);
    }

    /**
     * ВСЕ ВИДЫ ЖЕТОНОВ С РАЗНЫМ УРОНОМ. Ради этой сцены режим и заводился в первую
     * очередь: дизайнер попросил снизить обводку цифр возле здоровья, а увидеть её
     * на всех жетонах сразу было негде.
     */
    private static Scene подбитые() {
        Scene s = Scene.of(4).title("урон на всех видах жетонов");
        String[] hexes = {"h0_0", "h1_0", "h0_1", "h-1_0", "h0_-1", "h1_-1"};
        BuildingType[] types = {BuildingType.COMMAND_CENTER, BuildingType.BARRACKS,
            BuildingType.FACTORY, BuildingType.AIRBASE, BuildingType.MINER,
            BuildingType.POWER_PLANT};
        for (int i = 0; i < types.length; i++) {
            Integer lvl = types[i] == BuildingType.MINER || types[i] == BuildingType.POWER_PLANT
                ? (i % 4) + 1 : null;
            // РАНЕН, НО ЖИВ: урон на единицу меньше прочности — по правилам
            // «двух атак» жетон с damage >= hp мёртв и с поля исчезает,
            // и прежняя сцена (урон i % 3) после смены прочностей показывала
            // пустое поле вместо раненых.
            int hp = s.state().tokenStats.buildingHp(types[i], lvl);
            s.building(0, hexes[i % hexes.length], types[i], lvl, 0)
                .damage(Math.max(0, hp - 1));
        }
        UnitType[] units = {UnitType.INFANTRY, UnitType.VEHICLE, UnitType.AIRCRAFT,
            UnitType.TOWER};
        for (int i = 0; i < units.length; i++) {
            int hp = s.state().tokenStats.unitHp(units[i]);
            s.unit(1, hexes[(i + 2) % hexes.length], units[i])
                .damage(Math.max(0, hp - 1));
        }
        return s.round(7, 1);
    }

    /**
     * ЭНЕРГИЯ ВО ВСЕХ ВИДАХ: станция на жёлтом секторе (полный номинал) и такая же
     * рядом (один кубик), кубики на потребителях и простаивающие на источнике.
     * Правило жёлтого сектора видно только рядом стоящими станциями.
     */
    private static Scene энергия() {
        Scene s = Scene.of(4).title("энергия: жёлтый сектор, кубики, простой");
        // ГЕКСЫ БЕРУТСЯ ИЗ ПОЛЯ, А НЕ ПО ИМЕНИ. Раскладка зависит от свода и
        // зерна, и «h0_0» в ней может не оказаться вовсе — сцена тогда падала
        // на разыменовании пустого гекса (поймано StartMenuTest после смены
        // свода по умолчанию). Берём размеченные гексы: у них по определению
        // есть жёлтый сектор, ради которого сцена и существует.
        List<String> hs = размеченныеГексы(s, 3);
        String hex = hs.get(0);
        int yellow = s.state().field.get(hex).energyCell;
        // Жёлтый сектор у каждого гекса свой — ставим станцию ИМЕННО на него и
        // такую же на соседний сектор, чтобы разницу было видно рядом.
        s.building(0, hex, BuildingType.POWER_PLANT, 4, yellow).idleEnergy(3);
        s.building(0, hex, BuildingType.POWER_PLANT, 4, (yellow + 2) % 6).idleEnergy(1);
        s.building(0, hs.get(1), BuildingType.AIRBASE, null, 0).energy(3);
        s.building(0, hs.get(2), BuildingType.FACTORY, null, 0).energy(1);
        return s.round(4, 1);
    }

    /**
     * ПЕРВЫЕ {@code n} РАЗМЕЧЕННЫХ ГЕКСОВ поля в порядке имён. Размеченный —
     * значит с напечатанным жёлтым сектором, то есть заведомо играбельный и не
     * накрытый грядой зарождения. Порядок по имени, а не по обходу карты, чтобы
     * сцена не менялась от прогона к прогону.
     */
    private static List<String> размеченныеГексы(Scene s, int n) {
        List<String> out = new ArrayList<>();
        for (var e : new java.util.TreeMap<>(s.state().field.hexes).entrySet()) {
            if (e.getValue().energyCell >= 0) {
                out.add(e.getKey());
            }
            if (out.size() == n) {
                break;
            }
        }
        if (out.size() < n) {
            throw new IllegalStateException(
                "в поле меньше " + n + " размеченных гексов: " + out);
        }
        return out;
    }

    /**
     * ВТОРАЯ ЧАСТЬ СУПЕР-ЗАДАНИЯ. Счётчик запуска на четырёх ячейках, символы
     * повторяются (правила это разрешают), супероружие стоит НЕ на гексе своего
     * завода. В партии до этого места доходят единицы игр из ста.
     */
    private static Scene супероружие() {
        Scene s = Scene.of(4).title("счётчик запуска супероружия");
        s.building(0, "h0_0", BuildingType.FACTORY, null, 0);
        // КАРТУ ВЫДАТЬ ОБЯЗАТЕЛЬНО: планшет смотрит на карту, а не на ячейки, и
        // без неё пишет «супер-задание: не выдано» при выставленном счётчике.
        String card = firstSuperObjective(s);
        s.superObjective(0, card, 3);
        s.superWeapon(0, "h1_0", UnitType.VEHICLE, "h0_0");
        s.superCells(0, 4);
        s.superObjective(1, card, 1);
        s.superCells(1, 2);
        return s.round(8, 1);
    }

    /** Первый идентификатор из каталога супер-заданий (какой именно — не важно). */
    private static String firstSuperObjective(Scene s) {
        var content = s.cfg().content.get("super_objectives");
        if (content == null || content.entries.isEmpty()) {
            return null;
        }
        Object first = content.entries.get(0);
        return first instanceof java.util.Map<?, ?> m ? String.valueOf(m.get("id")) : null;
    }

    /** КАРТА РЫНКА с занятыми ячейками — проверка планшета рынка целиком. */
    private static Scene рынок() {
        return Scene.of(4)
            .title("планшет рынка: обе ячейки заняты")
            .market("civil_contract")
            .marketCell(0, 0, 1)
            .marketCell(0, 1, 2)
            .marketCell(1, 0, 3)
            .res(0, Resource.KELIUM, 4)
            .res(0, Resource.COIN, 11)
            .round(5, 1);
    }

    /**
     * НЕЙТРАЛЬНЫЕ СТЕНКИ всех размеров вокруг одного гекса. Нужно после правки
     * «Восстановления»: нейтрал теперь ставится на 1 или 2 сектора куда угодно, и
     * посмотреть на все размеры рядом иначе негде.
     */
    private static Scene стенки() {
        return Scene.of(4)
            .title("нейтральные стенки на 1, 2 и 3 сектора")
            .neutral("h0_0", false, 0)
            .neutral("h0_0", false, 2, 3)
            .neutral("h1_0", true, 0, 1, 2)
            .unit(0, "h0_0", UnitType.INFANTRY)
            .round(3, 1);
    }

    /**
     * ЖИВАЯ ПАРТИЯ до пятого раунда — эталон, с которым сравниваются ручные
     * сцены. Если ручная сцена рисуется иначе, чем настоящее состояние, дело в
     * сцене, а не в рисовальщике.
     */
    private static Scene полныйСтол() {
        return Scene.played(4, 4242L, 5, List.of("hawk", "dove", "explorer", "warlord"))
            .title("живая партия, пятый раунд");
    }

    /**
     * КРАЙНИЕ СОСТОЯНИЯ КОЛОД: колода на исходе (две карты), толстый сброс, витрина
     * арсенала. В случайной партии такое сходится к концу и не всегда, а посмотреть
     * на вкладку «Карты» надо именно в крайних положениях.
     */
    private static Scene колоды() {
        Scene s = Scene.played(4, 4242L, 6, List.of("hawk", "dove", "explorer", "warlord"))
            .title("колоды: одна на исходе, сброс толстый");
        s.deckSize("objectives", 2);
        s.deckSize("containers", 1);
        // Витрину задаём явно: в партии там лежит что легло, а проверять надо, что
        // обе карты рисуются и обе читаются.
        var набор = s.cfg().content.get("arsenal");
        if (набор != null && набор.entries.size() >= 2) {
            s.arsenalDisplay(ид(набор.entries.get(0)), ид(набор.entries.get(1)));
        }
        s.containers(0, 4);
        s.cuTokens(0, 1, true);
        return s;
    }

    /** ЭКРАН ИТОГОВ: без флага окончания партии его нечем посмотреть. */
    private static Scene итоги() {
        return Scene.played(4, 4242L, 6, List.of("hawk", "dove", "explorer", "warlord"))
            .title("итоги партии, военная победа")
            .cuTokens(0, 1, true)
            .finished(0, "cu");
    }

    private static String ид(Object запись) {
        return запись instanceof java.util.Map<?, ?> m ? String.valueOf(m.get("id")) : null;
    }
}
