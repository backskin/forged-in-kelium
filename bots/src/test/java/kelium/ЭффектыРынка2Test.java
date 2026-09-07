package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Effects;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ПЯТЬ НОВЫХ ЭФФЕКТОВ КАРТ РЫНКА 2.0 (заказ дизайнера 02.09.2026).
 *
 * <p>Эффект, который «вроде работает», потому что партия не упала, — не
 * проверен: движок ловит отказ предложения и молча пишет «failed», как за
 * столом. Поэтому каждый эффект вызывается здесь ПРЯМО, на построенной сцене, и
 * проверяется по состоянию: кубик встал, келемий куплен, жетон обменян, чужое
 * здание уехало в запас.
 */
class ЭффектыРынка2Test {

    private static GameState партия() {
        GameState s = Setup.buildGame(GameConfig.buildCached("1.33.0", 4, 7L, null, null));
        List<kelium.core.Agent> agents = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(kelium.agents.Bots.create("builder", i, new java.util.Random(i), 4));
        }
        GameEngine.bind(s, agents);
        s.round = 1;
        s.circle = 1;
        return s;
    }

    @Test
    void келемийВстаётВЯчейкуЭнергии() {
        GameState s = партия();
        PlayerState p = s.player(0);
        BuildingToken голодное = null;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.energySlots > b.energyPlaced) {
                голодное = b;
                break;
            }
        }
        if (голодное == null) {
            // На старте у всех зданий ячейки заполнены — освободим одну руками:
            // проверяется эффект, а не расстановка.
            голодное = p.buildingsOnField().get(0);
            голодное.energySlots += 1;
        }
        int было = голодное.energyPlaced;
        p.resources.add(Resource.KELIUM, 1);
        int келемийБыл = p.resources.kelium();

        Map<String, Object> got = Effects.apply("place_on_energy_cell", s, 0,
            Map.of("pay_kelium", true));
        assertEquals(1, got.get("placed"), "кубик обязан встать: " + got);
        assertEquals(было + 1, голодное.energyPlaced, "ячейка занята");
        assertEquals(келемийБыл - 1, p.resources.kelium(),
            "с pay_kelium келемий берётся из хранилища");
    }

    @Test
    void келемийПокупаетсяЗаМонетуИНеПереливаетСклад() {
        GameState s = партия();
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 5);
        int монетБыло = p.resources.coin();
        int келемийБыл = p.resources.kelium();
        int потолок = kelium.engine.Storage.keliumMax(s, p);

        Map<String, Object> got = Effects.apply("buy_kelium", s, 0, Map.of("coin", 1));
        if (келемийБыл < потолок) {
            assertEquals(1, got.get("bought"), "место есть — покупка идёт: " + got);
            assertEquals(келемийБыл + 1, p.resources.kelium());
            assertEquals(монетБыло - 1, p.resources.coin());
        }

        // Забиваем хранилище до потолка и пробуем снова: перелива быть не должно,
        // и монеты обязаны остаться у игрока.
        while (p.resources.kelium() < потолок) {
            p.resources.add(Resource.KELIUM, 1);
        }
        int монетПеред = p.resources.coin();
        Map<String, Object> отказ = Effects.apply("buy_kelium", s, 0, Map.of("coin", 1));
        assertEquals(0, отказ.get("bought"), "полное хранилище — покупки нет: " + отказ);
        assertEquals(потолок, p.resources.kelium(), "перелива через потолок нет");
        assertEquals(монетПеред, p.resources.coin(), "за отказ монеты не берут");
    }

    @Test
    void жетонМодуляМеняетсяЧерезМешок() {
        GameState s = партия();
        PlayerState p = s.player(0);
        String свой = kelium.engine.Modules.awardModule(s, p, "red");
        if (свой == null) {
            return;         // мешки выключены сводом — проверять нечего
        }
        int жетоновБыло = p.redTokens.size();
        int мешокБыл = s.redBag.size();

        Map<String, Object> got = Effects.apply("swap_module_from_bag", s, 0, Map.of());
        assertEquals(1, got.get("swapped"), "обмен обязан пройти: " + got);
        assertEquals(жетоновБыло, p.redTokens.size(), "жетонов столько же, но другой");
        assertEquals(мешокБыл, s.redBag.size(), "мешок не изменился в размере");
        assertNotNull(got.get("got"));
        assertFalse(p.redTokens.contains(String.valueOf(got.get("gave")))
                && !got.get("gave").equals(got.get("got")),
            "сданный жетон ушёл в мешок");
    }

    @Test
    void чужоеЗданиеЗаменяетсяНейтральным() {
        GameState s = партия();
        PlayerState враг = s.player(1);
        BuildingToken цель = враг.buildingsOnField().isEmpty()
            ? null : враг.buildingsOnField().get(0);
        assertNotNull(цель, "на старте у соперника есть здание на поле");
        String hex = цель.hexId;
        int uid = цель.uid;
        int зданийБыло = враг.buildingsOnField().size();
        int нейтраловБыло = s.field.get(hex).neutrals.size();

        Map<String, Object> got = Effects.apply("replace_building_with_neutral", s, 0,
            Map.of());
        assertEquals(1, got.get("replaced"), "замена обязана пройти: " + got);
        boolean наПолеЛи = false;
        for (BuildingToken b : враг.buildingsOnField()) {
            if (b.uid == uid) {
                наПолеЛи = true;
            }
        }
        assertFalse(наПолеЛи, "заменённое здание ушло с поля");
        assertTrue(враг.buildingsOnField().size() < зданийБыло
                || !hex.equals(String.valueOf(got.get("hex"))),
            "у соперника стало меньше зданий на поле");
        if (hex.equals(String.valueOf(got.get("hex")))) {
            assertEquals(нейтраловБыло + 1, s.field.get(hex).neutrals.size(),
                "на его месте встал нейтрал");
        }
    }

    /**
     * НАУКА «КАК БУДТО НА ТРОФЕЙ БОЛЬШЕ» — трофей ВИРТУАЛЬНЫЙ: шаг он
     * оплачивает, но в хранилище не появляется и после действия не остаётся.
     * Первый шаг трека стоит один трофей, поэтому игрок с пустым складом
     * обязан на него встать — и остаться с пустым складом.
     */
    @Test
    void наукаСВиртуальнымТрофеем() {
        GameState s = партия();
        PlayerState p = s.player(0);
        while (p.resources.trophy() > 0) {
            p.resources.pay(Resource.TROPHY, 1);
        }
        int шаговБыло = p.techSteps.values().stream().mapToInt(Integer::intValue).sum();

        Effects.apply("free_action", s, 0,
            Map.of("action", "science", "virtual_trophy", 1));

        int шаговСтало = p.techSteps.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(шаговСтало > шаговБыло,
            "виртуальный трофей обязан оплатить первый шаг трека");
        assertEquals(0, p.resources.trophy(),
            "виртуальный трофей в хранилище не появляется");
    }

    @Test
    void сменаМодулейПоКартеНеПадает() {
        GameState s = партия();
        Map<String, Object> got = Effects.apply("module_swap", s, 0, Map.of());
        assertEquals(1, got.get("module_swap"));
    }
}
