package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ПРОБА ВЫПУСКА — последняя проверка перед тем, как выпуск уходит игрокам
 * (02.10.2026). Запускается из собранного архива — тем же jar и той же папкой
 * data, что получит игрок: на каждом своде из data/rulesets играется по
 * партии вдвоём, втроём и вчетвером до конца, боты-любители. Хоть одна партия
 * упала или кончилась без победителя — выход с кодом 1, выпуска нет.
 *
 * <p>Запуск: {@code java -Dkelium.data=data -cp kelium-runner.jar kelium.ПробаВыпуска [свод…]}.
 */
public final class ПробаВыпуска {

    private ПробаВыпуска() {
    }

    public static void main(String[] args) throws Exception {
        List<String> своды = new ArrayList<>(List.of(args));
        if (своды.isEmpty()) {
            Path папка = Path.of(System.getProperty("kelium.data", "data")).resolve("rulesets");
            try (Stream<Path> ф = Files.list(папка)) {
                ф.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".yaml"))
                    .map(n -> n.substring(0, n.length() - ".yaml".length())).sorted().forEach(своды::add);
            }
        }
        int плохих = 0;
        for (String свод : своды) {
            for (int игроков = 2; игроков <= 4; игроков++) {
                long сид = 7_000L + игроков;
                try {
                    GameConfig база = GameConfig.buildCached(свод, игроков, сид, null, null);
                    GameState s = Setup.buildGame(LayoutLibrary.configFor(база, игроков, сид));
                    List<Agent> боты = new ArrayList<>();
                    for (int i = 0; i < игроков; i++) {
                        боты.add(Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ЛЮБИТЕЛЬ, i,
                            new Random(сид * 31 + i), игроков));
                    }
                    GameEngine.playGame(s, боты, ev -> { });
                    boolean хорошо = s.winner != null && s.round > 0;
                    System.out.printf("%s, игроков %d: раундов %d, победил %s — %s%n", свод, игроков,
                        s.round, s.winner == null ? "никто" : "место " + (s.winner + 1),
                        хорошо ? "ок" : "ПЛОХО");
                    if (!хорошо) {
                        плохих++;
                    }
                } catch (Throwable e) {
                    плохих++;
                    System.out.printf("%s, игроков %d: СОРВАЛАСЬ — %s%n", свод, игроков, e);
                    e.printStackTrace(System.out);
                }
            }
        }
        System.out.println(плохих == 0 ? "проба выпуска: все партии доиграны"
            : "проба выпуска: сорвалось партий — " + плохих);
        System.exit(плохих == 0 ? 0 : 1);
    }
}
