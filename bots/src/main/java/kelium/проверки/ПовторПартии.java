package kelium.проверки;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ПОВТОР ПАРТИИ — партия ботов пишется лентой решений (номер варианта), потом
 * лента проигрывается на чистом столе тем же сидом. Вопросы движка обязаны
 * совпасть шаг в шаг — иначе сохранение партии не восстановить.
 */
public final class ПовторПартии {

    public static void main(String[] args) {
        long сид = args.length > 0 ? Long.parseLong(args[0]) : 777L;
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        String бот = args.length > 2 ? args[2] : "builder:2";
        List<Integer> лента = new ArrayList<>();
        List<String> вопросы = new ArrayList<>();
        GameState s = Setup.buildGame(GameConfig.buildCached("1.50.0", n, сид, null, null));
        List<Agent> ags = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Agent a = kelium.agents.BotCatalog.create(бот, i, new Random(сид + i), n);
            ags.add(new Agent(i, a.name) {
                @Override
                public Choice choose(GameState st, List<Choice> o, Map<String, Object> c) {
                    Choice p = a.choose(st, o, c);
                    лента.add(o.indexOf(p));
                    вопросы.add(seat + ":" + (c == null ? "" : c.get("kind")) + ":" + o.size());
                    return p;
                }

                @Override
                public void observeEvent(Map<String, Object> e) {
                    a.observeEvent(e);
                }

                @Override
                public void observePublicEvent(Map<String, Object> e) {
                    a.observePublicEvent(e);
                }

                @Override
                public boolean specInActionMenu() {
                    return a.specInActionMenu();
                }
            });
        }
        GameEngine.playGame(s, ags, ev -> { });
        System.out.println("записано решений: " + лента.size());
        // проигрывание
        GameState t = Setup.buildGame(GameConfig.buildCached("1.50.0", n, сид, null, null));
        int[] к = {0};
        List<Agent> проиг = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int место = i;
            проиг.add(new Agent(i, "лента#" + i) {
                @Override
                public Choice choose(GameState st, List<Choice> o, Map<String, Object> c) {
                    int j = к[0]++;
                    String q = место + ":" + (c == null ? "" : c.get("kind")) + ":" + o.size();
                    if (j >= лента.size() || !q.equals(вопросы.get(j))) {
                        throw new IllegalStateException("расхождение на шаге " + j + ": было "
                            + (j < вопросы.size() ? вопросы.get(j) : "—") + ", стало " + q
                            + "; до: " + вопросы.subList(Math.max(0, j - 5), Math.min(j, вопросы.size())));
                    }
                    return o.get(лента.get(j));
                }
            });
        }
        try {
            GameEngine.playGame(t, проиг, ev -> { });
            System.out.println("ПОВТОР СОШЁЛСЯ: " + к[0] + " решений");
        } catch (IllegalStateException e) {
            System.out.println("ПОВТОР РАЗОШЁЛСЯ: " + e.getMessage());
        }
    }
}
