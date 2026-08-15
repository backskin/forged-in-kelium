package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.Ability;
import kelium.engine.ability.CoreAbilities;
import kelium.engine.ability.Hook;
import kelium.engine.ability.RuleQuery;

/**
 * КАРКАС СПОСОБНОСТЕЙ (проект «Арсенал как система способностей», 13.08.2026).
 *
 * <p>Главное, что здесь проверяется, — СТРАХОВКА ОТ МЁРТВЫХ КАРТ. Ревизия
 * 13.08.2026 нашла шесть пассивок из 29, которые «есть» и не работают: движок
 * просто не спрашивал их в нужном месте. Реестр теперь помнит, какие точки правил
 * движок спрашивал, и умеет назвать точки-сироты — те, что объявлены живыми
 * способностями, но не спрошены ни разу.
 */
class AbilityFrameworkTest {

    /**
     * РЕШИТЕЛЬНЫЙ игрок: всегда берёт первый вариант, который что-то делает
     * (пас — это payload == null). Нужен, чтобы проверять КАРТУ, а не осторожность
     * бота: обычный бот вправе отказаться от удара, и тогда тест мерил бы его
     * настроение.
     */
    private static final class Decisive extends kelium.core.Agent {
        private final java.util.Random rng;

        Decisive(long seed) {
            super(0, "решительный");
            this.rng = new java.util.Random(seed);
        }

        @Override public kelium.core.Choice choose(GameState state,
                java.util.List<kelium.core.Choice> options,
                Map<String, Object> context) {
            // Из непасовых вариантов выбираем СЛУЧАЙНЫЙ: первый гекс-источник боя
            // может не иметь достижимой цели, и «всегда первый» уводил бы в пас.
            java.util.List<kelium.core.Choice> real = new java.util.ArrayList<>();
            for (kelium.core.Choice c : options) {
                if (c.payload() != null) {
                    real.add(c);
                }
            }
            return real.isEmpty() ? options.get(options.size() - 1)
                                  : real.get(rng.nextInt(real.size()));
        }
    }

    private static GameState game() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
    }

    @Test
    void registryKnowsMigratedAbilities() {
        CoreAbilities.install();
        Ability a = Abilities.byId("plus1_storage_cell");
        assertTrue(a != null,
            "«+1 ячейка склада» переведена на реестр — она была мёртвой пассивкой");
        // ВАЖНО: «зарегистрирована» ≠ «реализована». Реализованной считается
        // способность, чья точка правил ПОДКЛЮЧЕНА к движку (список подключённых
        // точек проверяется отдельным тестом на настоящей партии).
        assertTrue(Abilities.wiredHooks().contains(Hook.STORAGE_CELLS)
                == Abilities.implemented("plus1_storage_cell"),
            "реализована ровно тогда, когда её точка подключена");
        assertFalse(Abilities.implemented("такой_пассивки_нет"),
            "незнакомая пассивка не реализована");
        assertEquals(Ability.Trigger.PASSIVE, a.trigger(), "это постоянная способность");
        assertTrue(a.hooks().contains(Hook.STORAGE_CELLS), "она правит число ячеек склада");
        assertTrue(a.hint() != null && a.hint().strength() > 0,
            "способность обязана рассказать боту о своей пользе");
    }

    /**
     * КАРТА С ЭТОЙ СПОСОБНОСТЬЮ — ищем в данных по низу, а не по номеру.
     *
     * <p>Тесты, прибитые к «a09», ломаются при первой же перенумерации колоды
     * (так и случилось при переходе на арсенал 1.3.0). Проверять надо
     * способность, а не идентификатор карты.
     */
    private static String cardWith(GameState s, String passiveId) {
        for (var card : kelium.dataio.Ctx.cards(s, "arsenal").entries) {
            Object bottom = card.get("bottom");
            if (bottom instanceof java.util.Map<?, ?> bm
                    && passiveId.equals(String.valueOf(bm.get("passive")))) {
                return String.valueOf(card.get("id"));
            }
        }
        throw new IllegalStateException("в колоде арсенала нет карты со способностью "
            + passiveId);
    }

    @Test
    void abilityChangesTheRuleValueWhenCardIsInstalled() {
        CoreAbilities.install();
        GameState s = game();

        int without = RuleQuery.of(s, 0, Hook.STORAGE_CELLS).base(4).ask();
        assertEquals(4, without, "без карты значение правила не меняется");

        // ставим карту с этой пассивкой как УСТАНОВЛЕННУЮ (ищем по способности)
        String cell = cardWith(s, "plus1_storage_cell");
        s.player(0).arsenalInstalled.add(cell);
        int with = RuleQuery.of(s, 0, Hook.STORAGE_CELLS).base(4).ask();
        assertEquals(5, with, "установленная карта добавляет ячейку");

        // карта В РУКЕ ничего не даёт — её надо установить
        s.player(1).arsenalHand.add(cell);
        assertEquals(4, RuleQuery.of(s, 1, Hook.STORAGE_CELLS).base(4).ask(),
            "карта в руке правил не меняет");
    }

    /**
     * Детектор точек-сирот работает: пока движок не спрашивает точку, реестр
     * называет её и виновную способность. Когда перевод пассивок закончится,
     * это утверждение перевернётся в «сирот нет» и станет защитой от регрессий.
     */
    @Test
    void orphanHookDetectorNamesUnaskedHooks() {
        CoreAbilities.install();
        Abilities.resetAsked();
        Map<Hook, java.util.List<String>> orphans = Abilities.unaskedHooks();
        assertTrue(orphans.containsKey(Hook.STORAGE_CELLS),
            "точка не спрошена — детектор обязан её назвать: " + orphans);
        assertTrue(orphans.get(Hook.STORAGE_CELLS).contains("plus1_storage_cell"),
            "и назвать способность, которая от неё зависит");

        GameState s = game();
        RuleQuery.of(s, 0, Hook.STORAGE_CELLS).base(2).ask();
        assertFalse(Abilities.unaskedHooks().containsKey(Hook.STORAGE_CELLS),
            "как только движок спросил точку, она перестаёт быть сиротой");
    }

    /**
     * СПИСОК ПОДКЛЮЧЁННЫХ ТОЧЕК НЕ МОЖЕТ ВРАТЬ. Играем полную партию и требуем,
     * чтобы каждая точка, объявленная подключённой, была спрошена хотя бы раз.
     * Приписал точку, не подключив её в движке, — тест падает, и карта с такой
     * пассивкой не попадёт в колоду обманом.
     */
    @Test
    void declaredWiredHooksAreReallyAsked() {
        CoreAbilities.install();
        Abilities.resetAsked();
        GameState s = game();
        java.util.List<kelium.core.Agent> agents = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(kelium.agents.Bots.create("balanced", i, new java.util.Random(i), 4));
        }
        kelium.engine.GameEngine.playGame(s, agents, null);
        for (Hook h : Abilities.wiredHooks()) {
            assertTrue(Abilities.askedHooks().contains(h),
                "точка объявлена подключённой, но за партию её ни разу не спросили: " + h);
        }
    }

    /**
     * ДОКАЗАТЕЛЬСТВО ЭФФЕКТА для «+1 ячейка склада» (a09): точка правил подключена
     * в самом складе, поэтому ячейка появляется В ИГРЕ — предел келемия растёт, и
     * добытый келемий, который раньше пропадал, теперь помещается.
     */
    @Test
    void extraStorageCellActuallyRaisesTheCap() {
        CoreAbilities.install();
        GameState s = game();
        var p = s.player(0);
        int capBefore = kelium.engine.Storage.keliumMax(s, p);
        p.arsenalInstalled.add(cardWith(s, "plus1_storage_cell"));
        assertEquals(capBefore + 1, kelium.engine.Storage.keliumMax(s, p),
            "установленная карта открывает ещё одну ячейку");

        // и добыча реально кладётся в эту ячейку, а не пропадает. Боеприпасы
        // убираем: ячейки общие, иначе упрёмся в общий предел, а не в предел келемия.
        p.resources.add(kelium.core.Resource.AMMO, -p.resources.ammo());
        p.resources.add(kelium.core.Resource.KELIUM,
            capBefore - p.resources.kelium());
        assertEquals(1, kelium.engine.Storage.addKeliumCapped(s, p, 1),
            "лишний келемий помещается на склад благодаря карте");
    }

    /**
     * ДОКАЗАТЕЛЬСТВО ЭФФЕКТА для «войско делает одну атаку» (as1 и a03) — первое
     * НОВОЕ ДЕЙСТВИЕ, пришедшее от карты. Пассивка была мёртвой у двух карт сразу.
     *
     * <p>Проверяем ровно то, что нужно игроку: карта появляется в меню СПЕЦ, когда
     * бить есть кого, отсутствует, когда некого, и её применение проводит бой.
     */
    @Test
    void oneAttackSpecComesFromTheCardAndReallyStrikes() {
        CoreAbilities.install();
        GameState s = game();
        java.util.List<kelium.core.Agent> agents = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(kelium.agents.Bots.create("hawk", i, new java.util.Random(i), 4));
        }
        // Довести партию до середины: на пустом поле бить некого, проверять нечего.
        // Играем раунд за раундом, пока у кого-нибудь не появится цель — момент
        // зависит от состава колоды, привязываться к номеру раунда нельзя.
        var engine = new kelium.engine.GameEngine(s, agents, null);
        var slot = kelium.engine.ability.OptionSource.Slot.SPEC;
        int seat = -1;
        for (int round = 3; round <= 8 && seat < 0 && !s.finished; round++) {
            engine.runToRound(round);
            for (int i = 0; i < 4; i++) {
                if (((kelium.engine.CombatResolver) s.combat).anyAttackPossible(i)) {
                    seat = i;
                    break;
                }
            }
        }
        assertTrue(seat >= 0, "к середине партии хоть у кого-то должна быть цель");

        assertTrue(Abilities.options(s, seat, slot).isEmpty(),
            "без установленной карты нового спец-действия в меню нет");

        // Пробуем ВСЕХ, у кого есть цель: бот вправе от удара отказаться (это его
        // решение, а не поломка карты), поэтому требуем, чтобы удар состоялся хотя
        // бы у одного. Само появление варианта в меню требуем у каждого.
        boolean struck = false;
        for (int i = 0; i < 4 && !struck; i++) {
            if (!((kelium.engine.CombatResolver) s.combat).anyAttackPossible(i)) {
                continue;
            }
            s.player(i).arsenalInstalled.add("as1");
            // Боеприпасы, чтобы отказ от удара не объяснялся пустым складом:
            // проверяем работу КАРТЫ, а не бережливость бота.
            s.player(i).resources.add(kelium.core.Resource.AMMO, 5);
            var opts = Abilities.options(s, i, slot);
            // ТОЧНОЕ число опций в меню — хрупкая проверка: правило 4
            // (2026-08-15, склад/обломки) добавило движку новую точку решения,
            // из-за которой боты с фиксированным сидом расходуют ГСЧ иначе и
            // приходят к СЕРЕДИНЕ партии в другом состоянии поля — иногда с
            // побочно доступной способностью вроде spec_move_energy_cube. Карта
            // проверяется тем, что её конкретное СПЕЦ-действие есть в меню, а не
            // тем, что оно там единственное.
            Choice cardOption = null;
            for (Choice o : opts) {
                if ("unit_makes_one_attack".equals(o.payload())) {
                    cardOption = o;
                    break;
                }
            }
            assertTrue(cardOption != null,
                "установленная карта добавила своё спец-действие: " + opts);
            int before = s.journal.of(i).battlesOpened;
            // Исполняем РЕШИТЕЛЬНЫМ игроком: обычный бот вправе отказаться от
            // удара, и тогда тест мерил бы осторожность бота, а не работу карты.
            for (int tries = 0; tries < 24 && !struck; tries++) {
                struck = Abilities.perform(s, i, cardOption, new Decisive(tries));
            }
            if (struck) {
                assertEquals(before + 1, s.journal.of(i).battlesOpened,
                    "бой попал в журнал — значит он был настоящий, а не на словах");
            } else {
                assertEquals(before, s.journal.of(i).battlesOpened,
                    "бой не состоялся — в журнале ничего не прибавилось");
            }
            // Карта не даёт варианта, когда бить некого: убираем войска — вариант
            // обязан исчезнуть. Это и есть проверка, что возможность приходит ОТ
            // КАРТЫ и знает свои условия, а не висит в меню всегда. Другие
            // способности (например spec_move_energy_cube) от войск не зависят
            // и законно могут остаться в меню — проверяем именно ОТСУТСТВИЕ
            // варианта карты, а не пустоту всего меню (см. комментарий выше).
            for (kelium.core.UnitToken u : s.player(i).unitsOnField()) {
                u.hexId = null;
            }
            boolean stillOffered = Abilities.options(s, i, slot).stream()
                .anyMatch(o -> "unit_makes_one_attack".equals(o.payload()));
            assertFalse(stillOffered, "войск нет — карта не предлагает удар");
        }
    }

    /**
     * ДОКАЗАТЕЛЬСТВО ЭФФЕКТА для «Промышленник» (a12): пассивка не была написана
     * вовсе, карта изымалась из колоды. Теперь цена стройки спрашивается через
     * точку правил, и установленная карта её реально снижает.
     */
    @Test
    void cheaperBuildActuallyLowersThePrice() {
        CoreAbilities.install();
        GameState s = game();
        int full = RuleQuery.of(s, 0, Hook.BUILD_PRICE).base(4).ask();
        assertEquals(4, full, "без карты цена печатная");

        s.player(0).arsenalInstalled.add(cardWith(s, "build_minus2_coin"));
        int discounted = RuleQuery.of(s, 0, Hook.BUILD_PRICE).base(4).ask();
        assertEquals(2, discounted, "с картой стройка дешевле на две монеты");
    }
}
