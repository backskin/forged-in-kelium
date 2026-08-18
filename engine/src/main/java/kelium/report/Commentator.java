package kelium.report;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import kelium.core.GameState;
import kelium.core.PlayerState;

/**
 * Commentator — «живой» комментатор партии. Слушает поток событий движка и пишет
 * в {@link NarrativeLog}: ведёт структуру рассказа (раунды/круги), поясняет, что
 * происходит, ХВАЛИТ удачные ходы (уничтожения, задания, развороты супер-заданий)
 * и КРИТИКУЕТ нелогичные (бой без результата, размен впустую).
 *
 * <p>Комментатор не лезет в движок — он лишь наблюдатель ({@code onEvent}), так
 * что его можно включать и выключать, не влияя на игру.
 */
public final class Commentator implements Consumer<Map<String, Object>> {

    private final GameState state;
    private final NarrativeLog narrative;

    /** Сколько раз игрок бил в этом ходу и сколько раз уничтожил (для оценки боя). */
    private int curActor = -1;
    private int curHits = 0;
    private int curKills = 0;

    public Commentator(GameState state, NarrativeLog narrative) {
        this.state = state;
        this.narrative = narrative;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void accept(Map<String, Object> ev) {
        String t = String.valueOf(ev.get("type"));
        switch (t) {
            case "game_start" -> {
                if (!narrative.isOpen()) {
                    narrative.open();
                }
                narrative.commentator("Партия начинается. Посмотрим, кто грамотнее разыграет свои ресурсы.");
            }
            case "refresh" -> {
                if (!Boolean.TRUE.equals(ev.get("skipped"))) {
                    narrative.round(asInt(ev.get("round")), asInt(ev.get("first_player")));
                    // обзор начала раунда: поле, статусы всех, наука, рынок
                    narrative.fieldMap();
                    for (int seat = 0; seat < state.numPlayers(); seat++) {
                        narrative.playerStatus(seat);
                    }
                    narrative.scienceBoard();
                    narrative.marketCard();
                }
            }
            case "blind_discard" -> {
                Object sa = ev.get("set_aside");
                if (sa instanceof Map<?, ?> m) {
                    narrative.blindDiscard(toIntKeyMap(m));
                }
            }
            case "reveal" -> {
                Object rv = ev.get("revealed");
                if (rv instanceof Map<?, ?> m) {
                    narrative.reveal(asInt(ev.get("circle")), toIntKeyMap(m));
                } else {
                    narrative.circle(asInt(ev.get("circle")));
                }
            }
            case "action" -> onAction(ev);
            case "combat_hit" -> onCombatHit(ev);
            case "objective" -> {
                boolean enh = Boolean.TRUE.equals(ev.get("enhanced"));
                narrative.praise(narrative.who(asInt(ev.get("seat"))) + " выполнил задание "
                    + ev.get("card") + (enh ? " с усилением — отличная работа!" : " — чистые очки в копилку."));
            }
            case "super_deploy" -> narrative.praise(narrative.who(asInt(ev.get("seat")))
                + " РАЗВЕРНУЛ супер-задание! Это мгновенная победа — блестящий розыгрыш.");
            case "turn_end" -> {
                flushCombatVerdict();
                // показать статус игрока, только если он изменился за этот ход
                narrative.playerStatusIfChanged(asInt(ev.get("seat")));
            }
            case "game_end" -> onGameEnd(ev);
            default -> { }
        }
    }

    private void onAction(Map<String, Object> ev) {
        int seat = asInt(ev.get("seat"));
        // сменился ходящий — подвести итог боя предыдущего
        if (seat != curActor) {
            flushCombatVerdict();
            curActor = seat;
            curHits = 0;
            curKills = 0;
        }
        boolean ok = Boolean.TRUE.equals(ev.get("ok"));
        String action = String.valueOf(ev.get("action"));
        if (!ok) {
            narrative.criticize(narrative.who(seat) + " попытался «" + actRu(action)
                + "», но действие не прошло — зря потратил ход.");
            return;
        }
        // мягкая оценка выбора действия: хвалим науку только если реально был
        // потрачен трофей и сделан шаг трека (видно по телеметрии).
        if ("science".equals(action)) {
            Object tel = ev.get("telemetry");
            if (tel instanceof Map<?, ?> m && m.get("track") != null) {
                narrative.praise(narrative.who(seat) + " поднялся по треку «" + m.get("track")
                    + "» до шага " + m.get("step") + " — вот это движение к очкам.");
            }
        }
    }

    private void onCombatHit(Map<String, Object> ev) {
        int seat = asInt(ev.get("seat"));
        if (seat != curActor) {
            flushCombatVerdict();
            curActor = seat;
            curHits = 0;
            curKills = 0;
        }
        curHits++;
        boolean destroyed = Boolean.TRUE.equals(ev.get("destroyed"));
        if (destroyed) {
            curKills++;
            narrative.event(narrative.who(seat) + " уничтожает " + ev.get("victim")
                + " (" + narrative.who(asInt(ev.get("victim_owner"))) + ") на " + ev.get("target") + "!");
        } else {
            narrative.event(narrative.who(seat) + " наносит урон по " + ev.get("target")
                + ", но цель устояла.");
        }
    }

    /** Подвести итог боевой активности игрока за ход: похвала или критика. */
    private void flushCombatVerdict() {
        if (curActor < 0 || curHits == 0) {
            return;
        }
        if (curKills == 0) {
            narrative.criticize(narrative.who(curActor) + " ввязался в бой (" + curHits
                + " атак(и)), но никого не уничтожил — боеприпасы на ветер.");
        } else if (curKills >= 2) {
            narrative.praise(narrative.who(curActor) + " за ход уничтожил " + curKills
                + " цел(и) — мощный размен в свою пользу!");
        }
        curHits = 0;
        curKills = 0;
    }

    @SuppressWarnings("unchecked")
    private void onGameEnd(Map<String, Object> ev) {
        flushCombatVerdict();
        Object winner = ev.get("winner");
        Map<Integer, Map<String, Integer>> scores = ev.get("scores") instanceof Map<?, ?> m
            ? new TreeMap<>((Map<Integer, Map<String, Integer>>) m) : new TreeMap<>();
        int winSeat = winner instanceof Number n ? n.intValue() : -1;

        // разбор аутсайдера — ДО финального блока, чтобы «└──» была последней строкой
        int worstSeat = -1;
        int worstVp = Integer.MAX_VALUE;
        for (var e : scores.entrySet()) {
            int vp = e.getValue().getOrDefault("total", 0);
            if (vp < worstVp) {
                worstVp = vp;
                worstSeat = e.getKey();
            }
        }
        if (worstSeat >= 0 && worstSeat != winSeat) {
            narrative.criticize(narrative.who(worstSeat) + " набрал всего " + worstVp
                + " ПО — " + adviseLoser(scores.getOrDefault(worstSeat, Map.of())));
        }

        if (winSeat >= 0) {
            Map<String, Integer> bd = scores.getOrDefault(winSeat, Map.of());
            narrative.ending("ПОБЕДА: " + narrative.who(winSeat) + " — " + bd.getOrDefault("total", 0)
                + " ПО. " + praiseWinner(bd));
        } else {
            narrative.ending("Партия завершена.");
        }
        narrative.close();
    }

    /** Похвала победителю по структуре его очков (за счёт чего выиграл). */
    private String praiseWinner(Map<String, Integer> bd) {
        String topSrc = null;
        int topVal = 0;
        for (var e : bd.entrySet()) {
            if ("total".equals(e.getKey())) {
                continue;
            }
            if (e.getValue() > topVal) {
                topVal = e.getValue();
                topSrc = e.getKey();
            }
        }
        if (topSrc == null) {
            return "Ровная игра.";
        }
        return "Основа успеха — " + sourceRu(topSrc) + ". Так и надо: сфокусировался и дожал.";
    }

    /** Совет проигравшему по «дыре» в его очках. */
    private String adviseLoser(Map<String, Integer> bd) {
        if (bd.getOrDefault("tech", 0) == 0) {
            return "совсем не тронул треки науки, а это до 7 ПО за лестницу. Обидное упущение.";
        }
        if (bd.getOrDefault("kelium", 0) == 0 && bd.getOrDefault("coins", 0) == 0) {
            return "провалил экономику: ни келемия, ни монет. Без ресурсов ходов нет.";
        }
        return "надо было активнее конвертировать ресурсы в очки.";
    }

    private static String actRu(String a) {
        return switch (a) {
            case "assembly" -> "Сборка";
            case "mining" -> "Добыча";
            case "build" -> "Стройка";
            case "energy_swap" -> "Смена энергии";
            case "movement" -> "Движение";
            case "combat" -> "Бой";
            case "market" -> "Маркет";
            case "science" -> "Наука";
            default -> a;
        };
    }

    private static String sourceRu(String s) {
        return switch (s) {
            case "kelium" -> "запас келемия";
            case "coins" -> "монеты";
            case "debris" -> "обломки";
            case "buildings_on_field" -> "сеть зданий";
            case "units_on_field" -> "армия на поле";
            case "tech" -> "треки науки";
            case "gold_modules" -> "золотые модули";
            case "spawn_tiles" -> "захваченные тайлы зарождения";
            case "cu_tokens" -> "уничтожение вражеских ЦУ";
            case "level4_stars" -> "здания 4-го уровня";
            case "super_first_part" -> "первая часть супер-задания";
            case "super_arsenal" -> "карты супер-арсенала";
            case "war_track" -> "военный трек";
            case "kills" -> "уничтожения";
            case "objective_card_vp" -> "прямые очки от заданий";
            case "arsenal_vp" -> "очки от карт арсенала";
            default -> s;
        };
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : -1;
    }

    /** Привести Map<?,?> (ключи могут быть Integer или String) к Map<Integer,String>. */
    private static java.util.Map<Integer, String> toIntKeyMap(java.util.Map<?, ?> m) {
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        for (var e : m.entrySet()) {
            int k = e.getKey() instanceof Number num ? num.intValue()
                : Integer.parseInt(String.valueOf(e.getKey()));
            out.put(k, String.valueOf(e.getValue()));
        }
        return out;
    }
}
