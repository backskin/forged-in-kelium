package kelium.report.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.ForcedAgent;
import kelium.agents.Genome;
import kelium.agents.Lookahead;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.report.NarrativeLog;

/**
 * ANALYST — РАЗБОР ПАРТИИ с доказательствами. Отвечает на вопрос, который живой
 * человек задаёт первым: «а надо было как?»
 *
 * <p>Прежний комментатор ругал ходы НА ГЛАЗОК: «боеприпасы на ветер», «совсем не
 * тронул треки науки». Упрёк мог быть и верным, и вздорным — проверить было
 * нечем. Здесь всё иначе: на каждом решении аналитик берёт КАЖДЫЙ вариант,
 * доигрывает с ним копию партии и смотрит, чем кончилось. Разница итогов и есть
 * цена решения — в победных очках, а не в словах.
 *
 * <p>Именно это и нужно, чтобы из игры ботов человек понял, как играть: не
 * «бот сделал ход», а «этот ход стоил ему 2.4 очка, а надо было вскрыть
 * Приобретения».
 *
 * <p>Аналитик — ОБЁРТКА: он не играет сам, а следит за чужими решениями. Значит
 * разобрать можно любого бота, включая просчитывающего.
 */
public final class Analyst extends Agent {

    /**
     * Оценка одного решения: что выбрано, что было лучшим, чего это стоило и
     * НАСКОЛЬКО ЭТОМУ МОЖНО ВЕРИТЬ.
     *
     * <p>Про {@code noise}. Итог доигранной партии — случайная величина: раздача
     * карт, выбор соперников, порядок добора. Если сравнить варианты по паре
     * прогонов и взять лучший, «лучший» окажется просто САМЫМ ВЕЗУЧИМ — и разница
     * с выбранным раздуется до величин вроде 30 очков, которых в игре и не бывает.
     * Поэтому здесь считается разброс оценок, и промах называется промахом только
     * если он больше собственной погрешности.
     */
    public record Verdict(int round, int circle, int seat, String kind,
                          String chosen, double chosenValue,
                          String best, double bestValue, double noise) {

        /** Во сколько обошёлся выбор (0 — выбрано лучшее из проверенного). */
        public double cost() {
            return Math.max(0.0, bestValue - chosenValue);
        }

        /** Промах больше собственной погрешности — значит он настоящий. */
        public boolean significant() {
            return cost() > 2.0 * noise;
        }
    }

    private final Agent inner;
    private final Genome model;
    private final int rollouts;
    private final int horizon;
    private final List<Verdict> verdicts = new ArrayList<>();
    private final Random rng;
    private final NarrativeLog narrative;

    public Analyst(Agent inner, Genome model, int rollouts, int horizon,
                   NarrativeLog narrative, long seed) {
        super(inner.seat, "разбор/" + inner.name);
        this.inner = inner;
        this.model = model != null ? model : Genome.defaults();
        this.rollouts = Math.max(1, rollouts);
        this.horizon = Math.max(1, horizon);
        this.narrative = narrative;
        this.rng = new Random(seed);
    }

    /** Все вынесенные оценки решений. */
    public List<Verdict> verdicts() {
        return verdicts;
    }

    @Override
    public void observeEvent(Map<String, Object> event) {
        inner.observeEvent(event);
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        String kind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
        boolean interesting = ("action".equals(kind) || "reveal_order".equals(kind))
            && countReal(options) > 1;
        // ВАЖЕН ПОРЯДОК: сперва оцениваем варианты на КОПИЯХ, потом отдаём решение
        // настоящему боту. Иначе разбор считался бы уже после хода и мерил не то.
        List<double[]> values = interesting ? valueAll(state, options, kind) : null;
        Choice pick = inner.choose(state, options, context);
        if (interesting) {
            record(state, options, values, pick, kind);
        }
        return pick;
    }

    private static int countReal(List<Choice> options) {
        int n = 0;
        for (Choice o : options) {
            if (o.payload() != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * Оценить каждый вариант доигрыванием: [индекс, средний итог, погрешность].
     *
     * <p>ОБЩИЕ СЛУЧАЙНЫЕ ЗЁРНА. Все варианты проигрываются на ОДНОМ И ТОМ ЖЕ
     * наборе зёрен. Это ключевой приём: сравниваются варианты в ОДИНАКОВЫХ
     * условиях, и общая для них случайность (какие карты пришли, как сыграли
     * соперники) вычитается сама собой. Без этого разница между вариантами
     * тонет в разнице между раздачами, и разбор показывает не силу хода, а
     * везение прогона.
     */
    private List<double[]> valueAll(GameState state, List<Choice> options, String kind) {
        long[] seeds = new long[rollouts];
        for (int r = 0; r < rollouts; r++) {
            seeds[r] = rng.nextLong();
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Choice o = options.get(i);
            if (o.payload() == null) {
                continue;   // пас доигрыванием не оценить: это отказ от решения
            }
            double sum = 0;
            double sumSq = 0;
            int taken = 0;
            for (int r = 0; r < rollouts; r++) {
                double v;
                if ("action".equals(kind)) {
                    Lookahead.ActionOutcome ao = Lookahead.actionOutcome(state, seat,
                        String.valueOf(o.payload()), model, model, horizon, seeds[r]);
                    if (!ao.ok() || Double.isNaN(ao.playOut())) {
                        continue;
                    }
                    v = ao.playOut();
                } else {
                    v = Lookahead.playOut(state, seat, model, model,
                        new ForcedAgent.Forced(kind, o.payload()), horizon, seeds[r]);
                }
                sum += v;
                sumSq += v * v;
                taken++;
            }
            if (taken > 0) {
                double mean = sum / taken;
                // Погрешность среднего: разброс, поделённый на корень из числа
                // прогонов. При одном прогоне честной оценки нет — берём разброс
                // целиком, и такой вариант просто не пройдёт порог значимости.
                double var = Math.max(0.0, sumSq / taken - mean * mean);
                double se = taken > 1 ? Math.sqrt(var / (taken - 1.0)) : Math.sqrt(var) + 5.0;
                out.add(new double[]{i, mean, se});
            }
        }
        return out;
    }

    private void record(GameState state, List<Choice> options, List<double[]> values,
                        Choice pick, String kind) {
        if (values == null || values.isEmpty()) {
            return;
        }
        double bestVal = Double.NEGATIVE_INFINITY;
        int bestIdx = -1;
        double bestSe = 0;
        double chosenVal = Double.NaN;
        double chosenSe = 0;
        for (double[] v : values) {
            int i = (int) v[0];
            if (v[1] > bestVal) {
                bestVal = v[1];
                bestIdx = i;
                bestSe = v[2];
            }
            if (options.get(i).equals(pick)) {
                chosenVal = v[1];
                chosenSe = v[2];
            }
        }
        if (bestIdx < 0 || Double.isNaN(chosenVal)) {
            return;
        }
        Verdict v = new Verdict(state.round, state.circle, seat, kind,
            label(options, pick), chosenVal, label(options, options.get(bestIdx)), bestVal,
            bestSe + chosenSe);
        verdicts.add(v);
        // Говорим только о промахах, которые БОЛЬШЕ СВОЕЙ ПОГРЕШНОСТИ: иначе
        // комментатор ругал бы за случайность прогона, и доверять ему было бы нельзя.
        if (narrative != null && narrative.isOpen() && v.significant() && v.cost() >= 1.0) {
            narrative.criticize(String.format(Locale.ROOT,
                "%s: выбрал «%s», а просчёт даёт «%s» — разница %.1f очка",
                who(kind), v.chosen(), v.best(), v.cost()));
        }
    }

    private static String who(String kind) {
        return "reveal_order".equals(kind) ? "На выборе приказа" : "На выборе действия";
    }

    private static String label(List<Choice> options, Choice c) {
        String l = c.label();
        if (l != null && !l.isEmpty()) {
            return l;
        }
        return String.valueOf(c.payload());
    }

    // ==================================================================
    //  Отчёт
    // ==================================================================

    /** Разбор партии в Markdown: где потеряно больше всего и где сыграно точно. */
    public static String report(List<Analyst> analysts, int topN) {
        List<Verdict> all = new ArrayList<>();
        for (Analyst a : analysts) {
            all.addAll(a.verdicts());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Разбор партии: чего стоило каждое решение\n\n");
        sb.append("Цена решения посчитана ЧЕСТНО: для каждого варианта копия партии ")
          .append("доигрывалась заново, и сравнивались итоги. Цена — разница в ")
          .append("«отрыве от сильнейшего соперника плюс премия за победу». ")
          .append("Это не мнение комментатора, а измерение.\n\n");
        if (all.isEmpty()) {
            sb.append("Решений, достойных разбора, в партии не нашлось ")
              .append("(везде выбора не было).\n");
            return sb.toString();
        }

        // ЧЕСТНОСТЬ ПРЕЖДЕ ВСЕГО: сперва скажем, какова погрешность самого метода.
        double noiseSum = 0;
        int significant = 0;
        for (Verdict v : all) {
            noiseSum += v.noise();
            if (v.significant()) {
                significant++;
            }
        }
        sb.append(String.format(Locale.ROOT,
            "**Погрешность метода:** в среднем ±%.1f очка на решение. Ниже показаны "
            + "ТОЛЬКО промахи, которые больше своей погрешности (%d из %d "
            + "разобранных решений). Остальное — разброс раздач, а не ошибки: если "
            + "сравнивать варианты по паре прогонов и брать лучший, «лучшим» "
            + "окажется самый везучий.%n%n",
            noiseSum / all.size(), significant, all.size()));

        Map<Integer, double[]> perSeat = new java.util.TreeMap<>();
        for (Verdict v : all) {
            double[] agg = perSeat.computeIfAbsent(v.seat(), k -> new double[3]);
            agg[1] += 1;
            if (v.significant()) {
                agg[0] += v.cost();
                agg[2] += 1;
            }
        }
        sb.append("## Сколько каждый игрок потерял на решениях\n\n");
        sb.append("| игрок | решений разобрано | доказанных промахов | потеряно очков |\n");
        sb.append("|-------|-------------------|---------------------|----------------|\n");
        for (var e : perSeat.entrySet()) {
            sb.append(String.format(Locale.ROOT, "| игрок %d | %.0f | %.0f | %.1f |%n",
                e.getKey(), e.getValue()[1], e.getValue()[2], e.getValue()[0]));
        }

        all.sort(Comparator.comparingDouble((Verdict v) -> -v.cost()));
        sb.append("\n## Самые дорогие ДОКАЗАННЫЕ промахи\n\n");
        sb.append("| раунд | круг | игрок | решение | выбрал | надо было | цена | ±погр. |\n");
        sb.append("|-------|------|-------|---------|--------|-----------|------|--------|\n");
        int shown = 0;
        for (Verdict v : all) {
            if (shown >= topN) {
                break;
            }
            if (!v.significant() || v.cost() < 0.5) {
                continue;
            }
            sb.append(String.format(Locale.ROOT,
                "| %d | %d | игрок %d | %s | %s | %s | %.1f | %.1f |%n",
                v.round(), v.circle(), v.seat(), kindRu(v.kind()),
                v.chosen(), v.best(), v.cost(), v.noise()));
            shown++;
        }
        if (shown == 0) {
            sb.append("| — | — | — | — | — | — | — | промахов сверх погрешности "
                + "не нашлось |\n");
        }

        int exact = 0;
        for (Verdict v : all) {
            if (v.cost() < 1e-9) {
                exact++;
            }
        }
        sb.append(String.format(Locale.ROOT,
            "%n**Точных решений:** %d из %d (%.0f%%) — выбран лучший из проверенных "
            + "вариантов.%n", exact, all.size(), 100.0 * exact / all.size()));
        sb.append("\n**Как этим пользоваться.** Строки сверху — места, где игра ")
          .append("действительно решалась. Если один и тот же промах повторяется у ")
          .append("разных игроков и в разных партиях, это не ошибка бота, а ")
          .append("ЛОВУШКА В ПРАВИЛАХ: правила подталкивают к ходу, который ")
          .append("оказывается невыгодным.\n");
        return sb.toString();
    }

    private static String kindRu(String kind) {
        return "reveal_order".equals(kind) ? "выбор приказа" : "выбор действия";
    }
}
