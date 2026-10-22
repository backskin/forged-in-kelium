package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.HeuristicAgent;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.step.Летопись;
import kelium.engine.step.Позиция;
import kelium.engine.step.Шаг;

/**
 * СХОДИТСЯ ЛИ ПОВТОР С ПАРТИЕЙ (30.09.2026). Поиск ботов считает ходы
 * повтором позиции ({@link Шаг}); если повтор хоть в одном решении видит не
 * те варианты, что настоящая партия, бот думает о чужой партии. Прибор
 * играет партии быстрыми ботами, на каждом решении запоминает варианты и
 * проверяет, что повтор той же позиции видит ровно их.
 *
 * <p>Запуск: {@code kelium.ПроверкаПовтора [партий]}.
 */
public final class ПроверкаПовтора {

    private ПроверкаПовтора() {
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int решений = 0;
        int расхождений = 0;
        for (int g = 0; g < партий; g++) {
            long seed = 5_600_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
            Летопись летопись = new Летопись();
            List<Agent> простые = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                простые.add(new HeuristicAgent(i, new Random(seed + i), "balanced"));
            }
            List<Позиция> позиции = new ArrayList<>();
            List<List<String>> варианты = new ArrayList<>();
            List<String> где = new ArrayList<>();
            List<List<String>> отпечатки = new ArrayList<>();
            List<Agent> обёрнутые = new ArrayList<>();
            for (Agent a : летопись.подключить(s, простые)) {
                обёрнутые.add(new Agent(a.seat, a.name) {
                    @Override
                    public Choice choose(GameState st, List<Choice> o, Map<String, Object> c) {
                        Позиция п = летопись.сейчас();
                        if (п != null) {
                            позиции.add(п);
                            List<String> подписи = new ArrayList<>();
                            for (Choice ch : o) {
                                подписи.add(ch.kind() + "|" + ch.label());
                            }
                            варианты.add(подписи);
                            где.add("раунд " + st.round + " круг " + st.circle + " место " + a.seat
                                + " вид " + (c == null ? "" : c.get("kind")));
                            отпечатки.add(отпечаток(st));
                        }
                        return a.choose(st, o, c);
                    }
                });
            }
            GameEngine.playGame(s, обёрнутые, null);
            for (int i = 0; i < позиции.size(); i++) {
                решений++;
                String беда = null;
                try {
                    Шаг.Развилка р = Шаг.достать(позиции.get(i));
                    List<String> подписи = new ArrayList<>();
                    for (Choice ch : р.варианты()) {
                        подписи.add(ch.kind() + "|" + ch.label());
                    }
                    if (!подписи.equals(варианты.get(i))) {
                        беда = "варианты разные:\n    партия: " + варианты.get(i)
                            + "\n    повтор: " + подписи + "\n    повтор спросил: " + р.вид()
                            + " место " + р.место();
                    } else {
                        List<String> мой = отпечаток(р.стол());
                        List<String> был = отпечатки.get(i);
                        for (int k = 0; k < Math.min(мой.size(), был.size()); k++) {
                            if (!мой.get(k).equals(был.get(k))) {
                                беда = "стол разный: партия «" + был.get(k) + "», повтор «"
                                    + мой.get(k) + "»";
                                break;
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    беда = "повтор упал: " + e.getMessage();
                }
                if (беда != null) {
                    расхождений++;
                    if (расхождений <= 5) {
                        System.out.println("партия " + seed + ", решение " + i + " (" + где.get(i)
                            + ", глубина " + позиции.get(i).глубина() + "): " + беда);
                    }
                }
            }
        }
        System.out.printf("решений %d, расхождений %d%n", решений, расхождений);
    }

    /** Отпечаток стола по полям — чтобы назвать первое разошедшееся. */
    static List<String> отпечаток(GameState s) {
        List<String> out = new ArrayList<>();
        for (var p : s.players) {
            String м = "место " + p.seat + ": ";
            out.add(м + "ресурсы " + p.resources.coin() + "/" + p.resources.kelium() + "/"
                + p.resources.ammo() + "/" + p.resources.trophy());
            StringBuilder в = new StringBuilder(м + "войска");
            for (var u : p.units) {
                в.append(' ').append(u.uid).append(u.type.code.charAt(0)).append('@').append(u.hexId)
                    .append(" у").append(u.damage).append(" с").append(u.sideMask)
                    .append(" в").append(u.insideBuildingUid);
            }
            out.add(в.toString());
            StringBuilder з = new StringBuilder(м + "здания");
            for (var b : p.buildings) {
                з.append(' ').append(b.uid).append(b.type.code.charAt(0)).append('@').append(b.hexId)
                    .append(" у").append(b.damage).append(" э").append(b.energyPlaced);
            }
            out.add(з.toString());
            out.add(м + "свалка " + p.destroyedTokens.size() + " задания " + p.objectiveHand
                + " арсенал " + p.arsenalHand + "/" + p.arsenalInstalled + " контейнеры "
                + p.containers);
            if (s.journal != null) {
                var ф = s.journal.of(p.seat);
                out.add(м + "журнал убито " + ф.enemyTokensDestroyed + " по гексам "
                    + new java.util.TreeMap<>(ф.destroyedOnHex) + " раненых " + ф.enemyTokensDamaged.size()
                    + " ветки " + ф.веткиХода);
            }
        }
        for (var h : s.field.hexes.values()) {
            out.add("гекс " + h.id + " стороны " + java.util.Arrays.toString(h.sideOwner)
                + (h.spawnTile == null ? "" : " келемий " + h.spawnTile.kelium)
                + (h.hasNeutral() ? " нейтрал" : ""));
        }
        return out;
    }
}
