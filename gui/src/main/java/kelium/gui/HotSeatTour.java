package kelium.gui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import kelium.core.Choice;
import kelium.core.InteractiveAgent;

/**
 * ОБХОД ПАРТИИ ГЛАЗАМИ ИГРОКА — проверка интерфейса (заказ дизайнера 28.09.2026:
 * «сам оттестируй: каждый ход понятно, что происходит, что надо сделать, где
 * нажимать»).
 *
 * <p>Играет целую партию за живое место 0 случайными ответами против ботов и на
 * каждом НОВОМ виде решения (первые {@code per} раз) снимает окно в PNG и
 * записывает, что окно в этот миг говорит игроку. Снимки и опись — в папку
 * {@code out}; по ним проверяют, у всякого ли вопроса есть понятный заголовок,
 * подсказка и подсвеченное место, куда жать.
 *
 * <p>Запуск: {@code HotSeatTour <папка> [сид] [per]}; состав мест —
 * {@code -Dshot.specs=human,punisher:1,builder:1}.
 */
public final class HotSeatTour {

    private HotSeatTour() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("kelium.gui.offscreen", "true");
        File out = new File(args[0]);
        out.mkdirs();
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260928L;
        int per = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        int w = 1600;
        int h = 1000;
        List<String> specs = List.of(System.getProperty("shot.specs",
            "human,punisher:1,builder:1").split(","));
        HotSeatWindow win = new HotSeatWindow(
            HotSeatWindow.Options.simple(specs.size(), seed, specs));
        SwingUtilities.invokeAndWait(win::start);
        SwingUtilities.invokeAndWait(() -> win.frame.setSize(w, h));

        Random rnd = new Random(seed);
        Map<String, Integer> видели = new HashMap<>();
        int answered = 0;
        int снимков = 0;
        long deadline = System.currentTimeMillis() + Long.getLong("tour.ms", 900_000L);
        try (PrintWriter log = new PrintWriter(new File(out, "опись.txt"),
                StandardCharsets.UTF_8)) {
            int предел = Integer.getInteger("tour.max", Integer.MAX_VALUE);
            // ПОДКЛАДКА (01.10.2026): -Dtour.arsenal=<карта> -Dtour.kelium=N — перед
            // первым решением карта встаёт на планшет живого игрока, келемий — в
            // хранилище. Без неё вопрос карты с платой за случайную партию не всплывал.
            String подложить = System.getProperty("tour.arsenal");
            boolean подложено = подложить == null;
            while (!win.finishedForTest() && System.currentTimeMillis() < deadline
                    && answered < предел) {
                var agent = win.humansBySeat.get(0);
                InteractiveAgent.PendingDecision d = agent == null ? null : agent.pending();
                if (d == null) {
                    Thread.sleep(10);
                    continue;
                }
                Thread.sleep(30);
                SwingUtilities.invokeAndWait(() -> { });
                SwingUtilities.invokeAndWait(() -> { });
                if (agent.pending() != d) {
                    continue;
                }
                if (!подложено) {
                    подложено = true;
                    kelium.core.GameState живая = win.liveStateForTest();
                    if (живая != null) {
                        kelium.core.PlayerState я = живая.player(0);
                        я.arsenalInstalled.add(подложить);
                        я.resources.add(kelium.core.Resource.KELIUM, Integer.getInteger("tour.kelium", 2));
                        log.println("подложено: " + подложить + ", келемий " + я.resources.kelium());
                    }
                }
                String k = String.valueOf(d.context().get("kind"));
                if (d.context().get("reaction") != null) {
                    k += "-" + d.context().get("reaction");
                }
                if (Boolean.TRUE.equals(d.context().get("turn_end"))) {
                    k += "-конец_хода";
                }
                int n = видели.merge(k, 1, Integer::sum);
                if (n <= per) {
                    Thread.sleep(350);      // дать плашкам и подсветке встать
                    снимков++;
                    String имя = String.format("%03d-%s-%d.png", снимков, k, n);
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    SwingUtilities.invokeAndWait(() -> {
                        win.frame.getRootPane().validate();
                        Graphics2D g = img.createGraphics();
                        win.frame.getRootPane().paint(g);
                        g.dispose();
                    });
                    ImageIO.write(img, "png", new File(out, имя));
                    List<String> варианты = new ArrayList<>();
                    for (Choice c : d.options()) {
                        варианты.add(c.kind() + ":" + c.label());
                    }
                    log.println(имя + " | окно: " + win.statusForTest()
                        + " | контекст: " + d.context() + " | варианты: " + варианты);
                    log.flush();
                }
                int idx = pick(rnd, d.options());
                SwingUtilities.invokeAndWait(() -> win.answerForTest(0, idx));
                answered++;
            }
            log.println("ответов " + answered + ", видов решений: " + видели);
        }
        System.out.println("обход: снимков " + снимков + ", ответов " + answered
            + ", видов " + видели.size());
        System.exit(0);
    }

    /** Чаще играет, чем пасует: иначе до войны и реакций дело не доходит. */
    private static int pick(Random rnd, List<Choice> options) {
        // на вопрос о плате — платить: иначе за картой нечего смотреть дальше
        for (int i = 0; i < options.size(); i++) {
            if ("trigger_pay".equals(options.get(i).kind())) {
                return i;
            }
        }
        List<Integer> plays = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).payload() != null) {
                plays.add(i);
            }
        }
        if (plays.isEmpty() || rnd.nextInt(5) == 0) {
            return rnd.nextInt(options.size());
        }
        return plays.get(rnd.nextInt(plays.size()));
    }
}
