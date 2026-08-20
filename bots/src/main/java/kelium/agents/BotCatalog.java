package kelium.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.core.Agent;

/**
 * СПРАВОЧНИК БОТОВ — единственный список того, кого можно посадить за стол.
 *
 * <p>Зачем. Список ботов был выписан ТРИ РАЗА: в лиге ({@code Arena.make}), в
 * прогонщике ({@code gui.RunnerGui}) и в записи партии ({@code gui.GameRecorder}).
 * Названия в них расходились («Стратег · ястреб» против «hawk» против
 * «strat:hawk»), состав тоже: в одном месте были боты, которых в другом не
 * существовало. Дизайнер это и увидел — «непонятно, кто за что отвечает, и почему
 * их так много».
 *
 * <p>Теперь список один: здесь и опознаваемое имя, и человеческое название, и
 * пояснение, и способ создать бота. Порядок — ПО СИЛЕ, сверху сильнейшие: выбирая
 * соперника, первым делом хочется знать, кто сильнее.
 */
public final class BotCatalog {

    private BotCatalog() {
    }

    /**
     * Бот в справочнике.
     *
     * @param id    опознаваемое имя (в файлах записи, в лиге, в командной строке)
     * @param label человеческое название для окна
     * @param tip   чем этот бот отличается от других
     */
    /**
     * Один вид бота.
     *
     * @param playsToWin ИГРАЕТ ЛИ ОН НА ПОБЕДУ. Часть линий обучалась НЕ победе:
     *     жнеца учили сносить жетоны, аксиому — трём заповедям, воителя — отрыву
     *     агрессией, исследователя — перебору механик, ищейку — поиску дыр в
     *     правилах. Это ПРИБОРЫ: ими меряют игру, и за столом они делают ходы,
     *     которые со стороны выглядят бессмысленно (замечание дизайнера
     *     20.08.2026: «страдают хуйней и делают непонятные тупые ходы»).
     *     Приборы остаются в каталоге — без них не работают замеры, — но в выбор
     *     соперника за столом не попадают.
     */
    public record Entry(String id, String label, String tip, boolean playsToWin) {
        /** Обычный игрок: играет на победу, отличается только стратегией. */
        public Entry(String id, String label, String tip) {
            this(id, label, tip, true);
        }

        @Override public String toString() {
            return label;
        }
    }

    /**
     * ВЕСЬ СОСТАВ. Ровно то, что имеет смысл посадить за стол, и ничего больше.
     *
     * <p>Что убрано 13.08.2026 и почему: две нейросетевые ветки («Нейросеть» на
     * своём формате и «Нейросеть ONNX» из PyTorch). Ни одна не проверялась в лиге,
     * обе слабее обученного генома, и различие между ними — вопрос формата файла, а
     * не игры. Держать в выборе две строки, разницу между которыми не может
     * объяснить даже автор, — прямой способ запутать.
     */
    public static final List<Entry> ALL = List.of(
        new Entry("search:balanced", "Просчёт вперёд · ровный",
            "САМЫЙ СИЛЬНЫЙ: проверяет ходы, доигрывая копию партии. "
                + "Обходит обычного бота по очкам в 69 партиях из 100. Медленнее вдвое"),
        new Entry("search:hawk", "Просчёт вперёд · агрессивный",
            "Тот же просчёт, но характер ястреба: чаще идёт в бой"),
        new Entry("trained:hawk", "Обученный · ястреб",
            "Обученный геном без просчёта. Рвётся в бой"),
        new Entry("trained:opportunist", "Обученный · оппортунист",
            "Бьёт лидера, ловит момент"),
        new Entry("trained:dove", "Обученный · голубь",
            "Мирный: экономика и наука"),
        new Entry("trained:balanced", "Обученный · ровный",
            "Без перекосов — репер, с которым сравнивают остальных"),
        new Entry("trained:explorer", "Обученный · исследователь",
            "Пробует за партию максимум разных механик — им проверяют, что механика вообще играется", false),
        new Entry("trained:warlord", "Обученный · воитель",
            "ЕДИНСТВЕННАЯ линия, которую учили НЕ на победу вообще, а на отрыв, "
                + "добытый агрессией: больше уничтожений, больше найма, все рода войск на поле", false),
        new Entry("trained:axiom", "Обученный · аксиома",
            "Очки и победа в её обучении не участвуют вовсе: линию учили трём "
                + "заповедям напрямую — высота сразу на всех треках науки, "
                + "уничтожения по трофейной цене, разнообразие жетонов на поле", false),
        new Entry("trained:reaper", "Обученный · жнец",
            "Учили одному: снести за партию как можно больше чужих жетонов и "
                + "уцелеть. Очки и победа в отборе не участвуют — это прибор, "
                + "которым меряют потолок войны в игре", false),
        new Entry("trained:chaos", "Обученный · вредитель",
            "Душит чужую экономику, на свои очки плевать", false),
        new Entry("trained:specialist", "Обученный · специалист",
            "Упирается в один род войск и держит его в количестве, а не вразнобой"),
        new Entry("trained:arsenal", "Обученный · арсенальщик",
            "Живёт рынком: не просто подбирает карты арсенала, а строит на них игру"),
        new Entry("trained:quester", "Обученный · задачник",
            "Машина по выполнению заданий — берёт каждое, до которого дотягивается"),
        new Entry("trained:berserker", "Обученный · громила",
            "Бьёт всех и как можно чаще, без затей воителя с разнообразием родов"),
        new Entry("trained:scientist", "Обученный · учёный",
            "Наука раньше и дороже всего остального, ради победы, а не заповеди"),
        new Entry("trained:superweapon", "Обученный · оружейник",
            "Рвётся к супер-заданию и старается его запустить"),
        new Entry("trained:cuhunter", "Обученный · охотник",
            "Цель одна — чужое ЦУ: техника и авиация бьют по нему мимо стен"),
        new Entry("human", "Как человек",
            "Смотрит не все варианты, ошибается тем сильнее, чем ближе оценки, "
                + "помнит обиды и держится замысла пару раундов"),
        new Entry("human:vengeful", "Как человек · злопамятный",
            "То же, но мстит за удары даже в убыток себе — играет слабее и злее"),
        new Entry("hunter", "Ищейка дыр",
            "Ищет не победу, а выгодные повторяемые связки — им проверяют правила на дыры", false),
        new Entry("simple", "Простой без обучения",
            "Жёсткие правила без генома — нижняя планка осмысленной игры", false),
        new Entry("random", "Случайный",
            "Ходит наугад — нижняя планка силы вообще", false));

    /**
     * КОГО МОЖНО ПОСАДИТЬ ЗА СТОЛ — только те, кто играет на победу.
     *
     * <p>Приборы ({@code playsToWin == false}) сюда не входят: их ходы осмысленны
     * лишь для замера, а человеку за столом кажутся случайными. В самом каталоге
     * они остаются — {@link #ALL} читают инструменты замера.
     */
    public static List<Entry> players() {
        return ALL.stream().filter(Entry::playsToWin).toList();
    }

    /**
     * ПЕРЕВОД СТАРЫХ ИМЁН на нынешние.
     *
     * <p>Записи уже сыгранных партий хранят имена ботов внутри файла, и старые
     * записи должны открываться после переименования. Плюс старые имена остались в
     * командных строках и заметках. Поэтому перевод, а не поломка:
     * {@code strat:X → trained:X}, {@code heur:X → simple:X},
     * {@code deep:X → search:X}, отдельные {@code explorer}/{@code chaos} — в
     * обученные линии, а мёртвые {@code neural}/{@code onnx} — в обученного ровного,
     * потому что этих ботов больше нет вовсе.
     */
    public static String canonical(String id) {
        if (id == null) {
            return "trained:balanced";
        }
        if (id.startsWith("strat:")) {
            return "trained:" + id.substring("strat:".length());
        }
        if (id.startsWith("heur:")) {
            return "simple:" + id.substring("heur:".length());
        }
        if (id.startsWith("deep:")) {
            return "search:" + id.substring("deep:".length());
        }
        return switch (id) {
            case "explorer", "chaos", "hawk", "dove", "opportunist", "balanced",
                 "warlord", "axiom", "reaper",
                 "specialist", "arsenal", "quester", "berserker", "scientist",
                 "superweapon", "cuhunter" ->
                "trained:" + id;
            case "neural", "onnx", "default", "strategic" -> "trained:balanced";
            case "heuristic" -> "simple:balanced";
            case "exploit" -> "hunter";
            case "vengeful" -> "human:vengeful";
            case "cool" -> "human";
            default -> id;
        };
    }

    /** Название бота по имени; неизвестное имя возвращается как есть. */
    public static String label(String id) {
        String canon = canonical(id);
        for (Entry e : ALL) {
            if (e.id().equals(canon)) {
                return e.label();
            }
        }
        return id;
    }

    /**
     * Название ОБУЧАЕМОЙ ЛИНИИ по её характеру ({@code hawk}, {@code warlord}…).
     *
     * <p>Отличается от {@link #label(String)} тем, что на вход идёт характер, а не
     * имя бота: в окне обучения выбирают именно линию. Нужно, чтобы в списке
     * стояло «Обученный · воитель», а не {@code warlord}.
     */
    public static String labelOfCharacter(String character) {
        return label("trained:" + character);
    }

    /** Есть ли такой бот в справочнике. */
    public static boolean known(String id) {
        String canon = canonical(id);
        for (Entry e : ALL) {
            if (e.id().equals(canon)) {
                return true;
            }
        }
        return false;
    }

    /** Имена всех ботов (для командной строки и подсказок). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Entry e : ALL) {
            out.add(e.id());
        }
        return out;
    }

    /**
     * Посадить бота за стол. Имя вида {@code вид} или {@code вид:характер}.
     * Неизвестное имя даёт обученного ровного — чтобы прогон не падал из-за
     * опечатки в строке запуска.
     */
    public static Agent create(String id, int seat, Random rng, int players) {
        String kind = canonical(id);
        String arg = null;
        int colon = kind.indexOf(':');
        if (colon >= 0) {
            arg = kind.substring(colon + 1);
            kind = kind.substring(0, colon);
        }
        String character = arg == null ? "balanced" : arg;
        return switch (kind) {
            case "search" -> SearchAgent.deep(seat, rng,
                Bots.genome(character, players), character);
            case "trained" -> new StrategicAgent(seat, rng,
                Bots.genome(character, players), character);
            case "human" -> "vengeful".equals(arg)
                ? HumanLikeAgent.vengeful(seat, rng, Bots.genome("hawk", players), players)
                : HumanLikeAgent.normal(seat, rng, Bots.genome("balanced", players), players);
            case "hunter" -> ExploitAgent.hunter(seat, rng, Bots.genome("balanced", players));
            case "simple" -> new HeuristicAgent(seat, rng,
                arg == null ? "balanced" : arg);
            case "random" -> new RandomAgent(seat, rng);
            default -> new StrategicAgent(seat, rng,
                Bots.genome("balanced", players), "balanced");
        };
    }
}
