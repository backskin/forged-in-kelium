package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.СуперЗадания;

/**
 * СЫРЫЕ ВЕЛИЧИНЫ, ПО КОТОРЫМ СЧИТАЮТСЯ КАТЕГОРИИ СУПЕР-ЗАДАНИЙ.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНО ОТ {@link КатегорииСупер}. Тот стенд меряет ОЧКИ и отвечает
 * на вопрос «сколько даёт категория сейчас». Этот меряет то, ИЗ ЧЕГО очки
 * считаются: сколько у игрока в финале зданий, войск, монет, жетонов на свалке.
 * Без этих чисел порог назначить нельзя — можно только угадывать.
 *
 * <p>КАК ЧИТАТЬ. Порог «до 4 ПО, но не задаром» ставится так: величина, которую
 * набирает половина стола (p50), должна давать 1–2 очка, а четыре очка —
 * величина верхней десятой (p90). Тогда карта платит всем понемногу, а полную
 * цену — тому, кто ради неё играл.
 *
 * <p>Запуск: {@code kelium.СырьёСупер [партий] [игроков] [характер]}
 */
public final class СырьёСупер {

    private СырьёСупер() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String характер = args.length > 2 ? args[2] : "balanced";

        Map<String, List<Integer>> сыр = new LinkedHashMap<>();
        for (String имя : List.of("зданий", "войск", "монет", "келемия", "боеприпасов",
                "жетонов на свалке", "модулей боя", "модулей сборки", "ступеней треков",
                "энергостанций вне круга", "добытчиков на контейнере",
                "войск у чужих зданий", "запитанных зданий", "гексов с войсками",
                "карт арсенала установлено", "золотых модулей", "кубиков в хранилище",
                "жетонов ЦУ", "карт заданий в руке", "ЗАДАНИЙ ВЫПОЛНЕНО", "убийств", "жетонов потеряно",
                "двухэнергийных ЗАПИТАНО", "двухэнергийных построено",
                "родов войск на поле", "добытчиков 3-4 уровня", "энергостанций НА круге")) {
            сыр.put(имя, new ArrayList<>());
        }
        for (int i = 0; i < партий; i++) {
            long seed = 91000L + i;
            GameState s = Setup.buildGame(
                GameConfig.buildCached(GameConfig.DEFAULT_RULESET, игроков, seed, null, null));
            List<Agent> боты = new ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                боты.add(kelium.agents.Bots.create(характер, seat,
                    new Random(seed * 31 + seat), игроков));
            }
            new GameEngine(s, боты, ev -> { }).run();
            for (int seat = 0; seat < игроков; seat++) {
                PlayerState p = s.player(seat);
                сыр.get("зданий").add(p.buildingsOnField().size());
                сыр.get("войск").add(p.unitsOnField().size());
                сыр.get("монет").add(p.resources.coin());
                сыр.get("келемия").add(p.resources.kelium());
                сыр.get("боеприпасов").add(p.resources.ammo());
                сыр.get("жетонов на свалке").add(p.destroyedTokens.size());
                // Категории, где очко даётся ровно за единицу, — очки и есть сырьё.
                сыр.get("модулей боя").add(СуперЗадания.очкиКатегории(s, p, "red_modules"));
                сыр.get("модулей сборки").add(СуперЗадания.очкиКатегории(s, p, "blue_modules"));
                сыр.get("ступеней треков").add(СуперЗадания.очкиКатегории(s, p, "tech_steps_4"));
                сыр.get("энергостанций вне круга")
                    .add(СуперЗадания.очкиКатегории(s, p, "plants_off_cell"));
                сыр.get("добытчиков на контейнере")
                    .add(СуперЗадания.очкиКатегории(s, p, "miners_on_container"));
                сыр.get("войск у чужих зданий")
                    .add(СуперЗадания.очкиКатегории(s, p, "units_at_enemy"));
                int запитанных = 0;
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    if (b.energySlots > 0 && b.energyPlaced >= b.energySlots) {
                        запитанных++;
                    }
                }
                сыр.get("запитанных зданий").add(запитанных);
                // ЗДАНИЯ С ДВУМЯ ЯЧЕЙКАМИ ЭНЕРГИИ — завод, авиабаза, добытчики
                // первого и третьего уровня. Их у игрока ровно четыре, поэтому
                // потолок в 4 ПО получается сам собой, без оговорки на карте.
                int двухЗапитано = 0;
                int двухПостроено = 0;
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    if (b.energySlots == 2) {
                        двухПостроено++;
                        if (b.energyPlaced >= 2) {
                            двухЗапитано++;
                        }
                    }
                }
                сыр.get("двухэнергийных ЗАПИТАНО").add(двухЗапитано);
                сыр.get("двухэнергийных построено").add(двухПостроено);
                // РОДЫ ВОЙСК НА ПОЛЕ — пехота, техника, авиация, вышка: ровно
                // четыре, и потолок снова получается сам собой. Требует трёх
                // военных зданий разом, а не одной раскачанной казармы.
                java.util.Set<Object> роды = new java.util.HashSet<>();
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    роды.add(u.type);
                }
                сыр.get("родов войск на поле").add(роды.size());
                int старшиеДобытчики = 0;
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    if (b.level != null && b.level >= 3 && b.type.code.startsWith("miner")) {
                        старшиеДобытчики++;
                    }
                }
                сыр.get("добытчиков 3-4 уровня").add(старшиеДобытчики);
                сыр.get("энергостанций НА круге").add(Math.max(0,
                    4 - СуперЗадания.очкиКатегории(s, p, "plants_off_cell")));
                java.util.Set<String> гексы = new java.util.HashSet<>();
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    гексы.add(u.hexId);
                }
                сыр.get("гексов с войсками").add(гексы.size());
                сыр.get("карт арсенала установлено").add(p.allInstalledArsenal().size());
                сыр.get("золотых модулей").add(p.goldModules);
                сыр.get("кубиков в хранилище").add(p.resources.kelium()
                    + p.resources.ammo() + p.resources.trophy());
                сыр.get("жетонов ЦУ").add(p.cuDestructionTokens);
                сыр.get("карт заданий в руке").add(p.objectiveHand.size());
                сыр.get("ЗАДАНИЙ ВЫПОЛНЕНО").add(p.objectivesCompleted);
                сыр.get("убийств").add(p.killsTotal);
                // Сколько ЕГО жетонов лежит на чужих свалках — то есть сколько
                // раз по нему ударили и добили за партию.
                int потеряно = 0;
                for (int o = 0; o < игроков; o++) {
                    if (o == seat) {
                        continue;
                    }
                    for (kelium.core.Token ж : s.player(o).destroyedTokens) {
                        if (ж instanceof kelium.core.UnitToken u && u.owner == seat) {
                            потеряно++;
                        } else if (ж instanceof kelium.core.BuildingToken b && b.owner == seat) {
                            потеряно++;
                        }
                    }
                }
                сыр.get("жетонов потеряно").add(потеряно);
            }
        }

        System.out.printf("ПАРТИЙ %d, игроков %d, характер %s%n%n", партий, игроков, характер);
        System.out.printf("%-26s %6s %6s %6s %6s %6s%n",
            "величина в финале", "сред", "p50", "p75", "p90", "макс");
        for (var e : сыр.entrySet()) {
            List<Integer> v = new ArrayList<>(e.getValue());
            java.util.Collections.sort(v);
            double сум = 0;
            for (int x : v) {
                сум += x;
            }
            System.out.printf("%-26s %6.2f %6d %6d %6d %6d%n", e.getKey(), сум / v.size(),
                кв(v, 0.50), кв(v, 0.75), кв(v, 0.90), v.get(v.size() - 1));
        }
    }

    private static int кв(List<Integer> отсортированные, double доля) {
        int i = (int) Math.floor(доля * (отсортированные.size() - 1));
        return отсортированные.get(Math.max(0, Math.min(отсортированные.size() - 1, i)));
    }
}
