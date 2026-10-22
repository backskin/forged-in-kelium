package kelium.проверки;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.HeuristicAgent;
import kelium.agents.Решатель;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * УТЕЧКА КОПИИ — меняют ли размышления бота настоящую партию. Две одинаковые
 * партии простых ботов; во второй рядом с каждым решением «думает» решатель
 * (его ответ выбрасывается). Итоги должны совпасть до последнего очка.
 */
public final class УтечкаКопии {

    @SuppressWarnings("unchecked")
    static List<String>[] ПОСЛЕДНЯЯ = new List[2];

    public static void main(String[] args) {
        long сид = args.length > 0 ? Long.parseLong(args[0]) : 4242L;
        String a = партия(сид, false);
        String b = партия(сид, true);
        System.out.println("без решателя: " + a);
        System.out.println("с решателем:  " + b);
        System.out.println(a.equals(b) ? "СОВПАДАЕТ — утечки нет" : "РАСХОДИТСЯ — копия трогает настоящую партию");
        List<String> x = ПОСЛЕДНЯЯ[0];
        List<String> y = ПОСЛЕДНЯЯ[1];
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            if (!x.get(i).equals(y.get(i))) {
                System.out.println("первое расхождение на событии " + i + ":\n  " + x.get(i) + "\n  " + y.get(i));
                for (int j = Math.max(0, i - 6); j < i; j++) {
                    System.out.println("    до: " + x.get(j));
                }
                break;
            }
        }
    }

    static String партия(long сид, boolean думать) {
        GameConfig cfg = GameConfig.buildCached("1.50.0", 3, сид, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> ags = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Agent прост = new HeuristicAgent(i, new Random(1), "balanced");
            Решатель реш = new Решатель(i, "builder", 3, 6, 2);
            ags.add(new Agent(i, "проба#" + i) {
                @Override
                public Choice choose(GameState st, List<Choice> o, Map<String, Object> c) {
                    if (думать) {
                        реш.choose(st, o, c);   // ответ выбрасывается
                    }
                    return прост.choose(st, o, c);
                }

                @Override
                public void observePublicEvent(Map<String, Object> e) {
                    реш.observePublicEvent(e);
                }
            });
        }
        StringBuilder лента = new StringBuilder();
        int[] n = {0};
        String[] первое = {null};
        List<String> строки = new ArrayList<>();
        GameEngine.playGame(s, ags, ev -> строки.add(ev.get("type") + "|" + ev.get("seat") + "|" + ev.get("detail")
            + "|" + ev.get("card") + "|" + ev.get("action")));
        лента.append(строки.size()).append(" событий, хэш ").append(строки.hashCode());
        ПОСЛЕДНЯЯ[думать ? 1 : 0] = строки;
        return лента + " " + Scoring.scorePlayer(s, 0).get("total") + ":" + Scoring.scorePlayer(s, 1).get("total")
            + ":" + Scoring.scorePlayer(s, 2).get("total")
            + " раунд " + s.round + " " + s.winCondition;
    }
}
