package kelium.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;

/**
 * СТРАТЕГИЯ НА ПАРТИЮ И ТАКТИКА НА РАУНД (заказ дизайнера 07.10.2026).
 *
 * <p>«Боты должны вести себя как люди: реализовывать большие планы, которые
 * приближают их к победе, и понимать, чем полезна каждая карта в их плане».
 * До этого у бота был только характер — постоянный набор весов на всю партию —
 * и одно задание в фокусе. Плана крупнее хода не было: бот выбирал лучший ход
 * «вообще», и одна и та же карта задания стоила ему одинаково, вёл он войну или
 * копал келемий.
 *
 * <p>ПУТЬ — как бот собирается побеждать, на всю партию:
 * <ul>
 *   <li><b>ХОЗЯЙСТВО</b> — добытчики и станции, келемий в Науку;</li>
 *   <li><b>ВОЙНА</b> — армия, снос чужих жетонов, трофеи в Науку;</li>
 *   <li><b>ТЕХНОЛОГИИ</b> — арсенал, модули, задания, ступени;</li>
 *   <li><b>ДАВЛЕНИЕ</b> — штурм чужого ЦУ, военная победа.</li>
 * </ul>
 * Путь выбирается по характеру и по тому, что даёт поле, и держится, пока
 * другой путь не станет заметно выгоднее (гистерезис): люди не меняют план
 * каждый ход.
 *
 * <p>ТАКТИКА — что делать в ЭТОМ раунде ради пути: строиться, добывать,
 * собирать армию, наступать, обороняться, рвануть в Науке. Меняется каждый
 * раунд по обстановке.
 *
 * <p>Путь и тактика умеренно сдвигают веса оценки позиции ({@link #применить})
 * и цену каждой карты задания ({@link #сродство}): награда, работающая на мой
 * путь, дороже; карта, тянущая в чужую сторону, дешевле — её проще сжечь ради
 * верха. Всё считается по открытой информации и своей руке.
 */
public final class Стратегия {

    public enum Путь {
        ХОЗЯЙСТВО("хозяйство: добыча келемия и Наука"),
        ВОЙНА("война: армия, снос, трофеи"),
        ТЕХНОЛОГИИ("технологии: арсенал, модули, задания"),
        ДАВЛЕНИЕ("давление: штурм чужого ЦУ");

        public final String словами;

        Путь(String с) {
            словами = с;
        }
    }

    public enum Тактика {
        СТРОЙКА("строюсь"),
        ДОБЫЧА("добываю"),
        АРМИЯ("собираю армию"),
        НАСТУПЛЕНИЕ("наступаю"),
        ОБОРОНА("обороняюсь"),
        НАУКА("иду в Науку");

        public final String словами;

        Тактика(String с) {
            словами = с;
        }
    }

    private final String характер;
    public Путь путь;
    public Тактика тактика = Тактика.СТРОЙКА;
    /** Сколько раз путь менялся за партию. */
    public int смен;
    private int раундВыбора = -1;
    private final Map<String, Double> кэшСродства = new HashMap<>();

    /** Запас, на который другой путь должен обогнать текущий, чтобы сменить план. */
    static final double ГИСТЕРЕЗИС = 0.8;

    public Стратегия(String характер) {
        this.характер = характер == null ? "balanced" : характер;
    }

    /** Склонность характера к пути — отправная точка, а не приговор. */
    double склонность(Путь п) {
        return switch (характер) {
            case "builder" -> switch (п) {
                case ХОЗЯЙСТВО -> 1.2;
                case ТЕХНОЛОГИИ -> 0.8;
                case ВОЙНА -> 0.2;
                case ДАВЛЕНИЕ -> 0.0;
            };
            case "supplier" -> switch (п) {
                case ТЕХНОЛОГИИ -> 1.2;
                case ХОЗЯЙСТВО -> 0.8;
                case ВОЙНА -> 0.3;
                case ДАВЛЕНИЕ -> 0.0;
            };
            case "punisher" -> switch (п) {
                case ВОЙНА -> 1.1;
                case ДАВЛЕНИЕ -> 0.7;
                case ХОЗЯЙСТВО -> 0.3;
                case ТЕХНОЛОГИИ -> 0.2;
            };
            case "stalker" -> switch (п) {
                case ВОЙНА -> 1.0;
                case ДАВЛЕНИЕ -> 0.9;
                case ТЕХНОЛОГИИ -> 0.3;
                case ХОЗЯЙСТВО -> 0.3;
            };
            default -> 0.6;
        };
    }

    /** Чего стоит каждый путь сейчас — по полю и запасам. */
    Map<Путь, Double> оценитьПути(GameState s, int seat) {
        PlayerState me = s.player(seat);
        Map<Путь, Double> v = new HashMap<>();
        // ХОЗЯЙСТВО: живые тайлы зарождения у моих зданий, работающие добытчики
        int тайлов = тайловРядом(s, me);
        int добытчиков = 0;
        int станций = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type == BuildingType.MINER && b.powered()) {
                добытчиков++;
            }
            if (b.type == BuildingType.POWER_PLANT) {
                станций++;
            }
        }
        v.put(Путь.ХОЗЯЙСТВО, склонность(Путь.ХОЗЯЙСТВО) + 0.35 * Math.min(3, тайлов)
            + 0.3 * Math.min(3, добытчиков) + 0.1 * Math.min(3, станций));
        // ВОЙНА: армия, чужие здания в досягаемости, трофеи
        Угрозы.Соперник я = Угрозы.соперник(s, seat);
        int армия = me.unitsOnField().size();
        int военных = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE) {
                военных++;
            }
        }
        v.put(Путь.ВОЙНА, склонность(Путь.ВОЙНА) + 0.15 * Math.min(6, армия)
            + 0.2 * Math.min(3, военных) + 0.2 * Math.min(4, я.войскДоЗданий())
            + 0.1 * Math.min(6, me.resources.trophy() + me.destroyedTokens.size()));
        // ТЕХНОЛОГИИ: арсенал, ступени, задания
        int ступеней = me.techSteps.values().stream().mapToInt(Integer::intValue).sum();
        v.put(Путь.ТЕХНОЛОГИИ, склонность(Путь.ТЕХНОЛОГИИ) + 0.35 * me.allInstalledArsenal().size()
            + 0.15 * me.arsenalHand.size() + 0.12 * Math.min(8, ступеней)
            + 0.1 * Math.min(4, me.objectivesCompleted));
        // ДАВЛЕНИЕ: уже снесённый чужой ЦУ — полпути к военной победе
        boolean военнаяПобеда = Ctx.rules(s).getInt("command_center.military_win_min_players", 2)
            <= s.numPlayers();
        v.put(Путь.ДАВЛЕНИЕ, склонность(Путь.ДАВЛЕНИЕ)
            + (военнаяПобеда ? 2.2 * Math.min(1, me.cuDestructionTokens) : 0)
            + 0.3 * Math.min(3, я.войскДоЦу()) + 0.08 * Math.min(6, армия));
        return v;
    }

    /** Живые тайлы зарождения на моих гексах и рядом с ними. */
    static int тайловРядом(GameState s, PlayerState me) {
        java.util.Set<String> рядом = new java.util.HashSet<>();
        for (BuildingToken b : me.buildingsOnField()) {
            рядом.add(b.hexId);
            рядом.addAll(s.field.neighbors(b.hexId));
        }
        int n = 0;
        for (String h : рядом) {
            var hex = s.field.get(h);
            if (hex != null && hex.spawnTile != null && hex.spawnTile.kelium > 0) {
                n++;
            }
        }
        return n;
    }

    /** Раз в раунд: подтвердить или сменить путь, выбрать тактику. */
    public void новыйРаунд(GameState s, int seat) {
        if (s.round == раундВыбора) {
            return;
        }
        раундВыбора = s.round;
        кэшСродства.clear();
        Map<Путь, Double> v = оценитьПути(s, seat);
        Путь лучший = путь;
        double лучшая = путь == null ? Double.NEGATIVE_INFINITY : v.get(путь) + ГИСТЕРЕЗИС;
        for (var e : v.entrySet()) {
            if (e.getValue() > лучшая) {
                лучшая = e.getValue();
                лучший = e.getKey();
            }
        }
        if (путь != null && лучший != путь) {
            смен++;
        }
        путь = лучший;
        тактика = тактика(s, seat);
    }

    Тактика тактика(GameState s, int seat) {
        PlayerState me = s.player(seat);
        if (Угрозы.цуПодУгрозой(s, seat)) {
            return Тактика.ОБОРОНА;
        }
        int зданий = me.buildingsOnField().size();
        int голодных = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.energySlots > 0 && b.energyPlaced < b.energySlots) {
                голодных++;
            }
        }
        boolean наукаПоКарману = Угрозы.наукаДоступно(s, me) > 0;
        int армия = me.unitsOnField().size();
        Угрозы.Соперник я = Угрозы.соперник(s, seat);
        return switch (путь) {
            case ХОЗЯЙСТВО -> зданий < 5 ? Тактика.СТРОЙКА
                : наукаПоКарману ? Тактика.НАУКА : Тактика.ДОБЫЧА;
            case ТЕХНОЛОГИИ -> наукаПоКарману ? Тактика.НАУКА
                : зданий < 4 || голодных > 1 ? Тактика.СТРОЙКА : Тактика.ДОБЫЧА;
            case ВОЙНА -> армия < 3 ? (зданий < 4 ? Тактика.СТРОЙКА : Тактика.АРМИЯ)
                : наукаПоКарману && me.destroyedTokens.size() >= 2 ? Тактика.НАУКА
                : я.войскДоЗданий() > 0 ? Тактика.НАСТУПЛЕНИЕ : Тактика.АРМИЯ;
            case ДАВЛЕНИЕ -> армия < 4 ? Тактика.АРМИЯ : Тактика.НАСТУПЛЕНИЕ;
        };
    }

    /**
     * ВЕСА ОЦЕНКИ ПОД ПЛАН. Множители умеренные: путь и тактика направляют, но
     * не переписывают обученные веса — хороший ход вне плана всё ещё играется.
     */
    public Genome применить(Genome g) {
        if (путь == null) {
            return g;
        }
        Map<String, Double> м = new HashMap<>();
        switch (путь) {
            case ХОЗЯЙСТВО -> {
                м.put("pl.economy", 1.3);
                м.put("pl.war", 0.8);
                м.put("pl.army", 0.85);
                м.put("pl.tech", 1.15);
            }
            case ВОЙНА -> {
                м.put("pl.war", 1.3);
                м.put("pl.army", 1.25);
                м.put("pl.ammo", 1.2);
                м.put("pl.trophy", 1.2);
                м.put("pl.economy", 0.9);
            }
            case ТЕХНОЛОГИИ -> {
                м.put("pl.arsenal", 1.4);
                м.put("pl.objective", 1.3);
                м.put("pl.tech", 1.2);
                м.put("pl.war", 0.85);
            }
            case ДАВЛЕНИЕ -> {
                м.put("pl.war", 1.4);
                м.put("pl.approach", 1.6);
                м.put("pl.army", 1.2);
                м.put("pl.target_bias", 1.5);
            }
        }
        if (!"нет".equals(System.getProperty("kelium.план.тактика"))) switch (тактика) {
            case СТРОЙКА, ДОБЫЧА -> м.merge("pl.economy", 1.12, (a, b) -> a * b);
            case АРМИЯ -> {
                м.merge("pl.army", 1.2, (a, b) -> a * b);
                м.merge("pl.ammo", 1.1, (a, b) -> a * b);
            }
            case НАСТУПЛЕНИЕ -> {
                м.merge("pl.war", 1.2, (a, b) -> a * b);
                м.merge("pl.approach", 1.3, (a, b) -> a * b);
            }
            case ОБОРОНА -> м.merge("pl.caution", 1.5, (a, b) -> a * b);
            case НАУКА -> м.merge("pl.tech", 1.3, (a, b) -> a * b);
        }
        // СИЛА ПЛАНА — для замеров: 0 — веса характера, 1 — полные множители.
        double сила = Double.parseDouble(System.getProperty("kelium.план.сила", "1"));
        Genome out = g;
        for (var e : м.entrySet()) {
            double база = g.get(e.getKey(), 1.0);
            double к = e.getValue();
            out = out.with(e.getKey(), база * (1 + сила * (к - 1)));
        }
        return out;
    }

    /**
     * ПОЛЬЗА КАРТЫ ЗАДАНИЯ ДЛЯ МОЕГО ПЛАНА — множитель её цены (0.5…1.8).
     *
     * <p>Складывается из двух вопросов, которые задаёт себе человек:
     * <ol>
     *   <li>на что работает НАГРАДА — на мой путь или на чужой? Келемий и
     *       монеты — хозяйству; боеприпасы, трофеи, урон, войска — войне и
     *       давлению; арсенал, модули, спец-действия, шаг по треку — технологиям;</li>
     *   <li>куда тянет ТРЕБОВАНИЕ — по пути ли мне выполнять его (действие,
     *       которое просит карта, входит в мою тактику) или придётся
     *       сворачивать?</li>
     * </ol>
     */
    public double сродство(GameState s, int seat, String cid) {
        if (путь == null || "нет".equals(System.getProperty("kelium.план.карты"))) {
            return 1.0;
        }
        Double c = кэшСродства.get(cid);
        if (c != null) {
            return c;
        }
        Map<String, Object> card = Ctx.cards(s, "objectives").byId(cid);
        double награда = 1.0;
        if (card != null) {
            Map<Путь, Double> сумма = new HashMap<>();
            учестьНаграду(card.get("base_reward"), сумма, 1.0);
            учестьНаграду(card.get("special_reward"), сумма, 0.5);
            double всего = сумма.values().stream().mapToDouble(Double::doubleValue).sum();
            if (всего > 0) {
                // доля награды на мой путь: ничего → 0.7, половина → 1.05, всё → 1.4
                награда = 0.7 + 0.7 * сумма.getOrDefault(путь, 0.0) / всего;
            }
        }
        double требование = 1.0;
        try {
            var oc = CardRegistry.objective(cid);
            if (oc != null) {
                String д = oc.suggestedAction(new EngineCardContext(s, seat));
                if (д != null) {
                    требование = поПути(д) ? 1.2 : 0.85;
                }
            }
        } catch (RuntimeException e) {
            // карта не ответила — нейтрально
        }
        double итог = Math.max(0.5, Math.min(1.8, награда * требование));
        кэшСродства.put(cid, итог);
        return итог;
    }

    /** Входит ли действие, о котором просит карта, в мой путь. */
    boolean поПути(String действие) {
        return switch (путь) {
            case ХОЗЯЙСТВО -> List.of("mining", "build_miner", "energy_swap", "build_plant", "market")
                .contains(действие);
            case ВОЙНА, ДАВЛЕНИЕ -> List.of("assembly", "build_military", "movement", "combat")
                .contains(действие);
            case ТЕХНОЛОГИИ -> List.of("market", "science", "energy_swap", "build_plant")
                .contains(действие);
        };
    }

    @SuppressWarnings("unchecked")
    private static void учестьНаграду(Object node, Map<Путь, Double> сумма, double вес) {
        if (!(node instanceof Map<?, ?> r)) {
            return;
        }
        for (var e : ((Map<String, Object>) r).entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            switch (k) {
                case "kelium", "coin" -> сумма.merge(Путь.ХОЗЯЙСТВО, вес, Double::sum);
                case "ammo", "trophy" -> {
                    сумма.merge(Путь.ВОЙНА, вес, Double::sum);
                    сумма.merge(Путь.ДАВЛЕНИЕ, 0.6 * вес, Double::sum);
                }
                case "arsenal", "arsenal_from_display", "spec_actions", "gild", "objective_card" ->
                    сумма.merge(Путь.ТЕХНОЛОГИИ, вес, Double::sum);
                case "module" -> {
                    if ("attack".equals(String.valueOf(v))) {
                        сумма.merge(Путь.ВОЙНА, вес, Double::sum);
                    } else {
                        сумма.merge(Путь.ТЕХНОЛОГИИ, 0.6 * вес, Double::sum);
                        сумма.merge(Путь.ХОЗЯЙСТВО, 0.4 * вес, Double::sum);
                    }
                }
                case "action" -> {
                    for (String д : String.valueOf(v).split("\\|")) {
                        Путь п = switch (д) {
                            case "mining", "build_miner", "energy_swap", "build_plant" -> Путь.ХОЗЯЙСТВО;
                            case "assembly", "build_military", "movement", "combat" -> Путь.ВОЙНА;
                            default -> Путь.ТЕХНОЛОГИИ;
                        };
                        сумма.merge(п, вес / 2, Double::sum);
                    }
                }
                case "effects" -> {
                    if (v instanceof List<?> l) {
                        for (Object o : l) {
                            if (!(o instanceof Map<?, ?> эф)) {
                                continue;
                            }
                            String id = String.valueOf(эф.get("id"));
                            String д = String.valueOf(эф.get("action"));
                            Путь п = switch (id) {
                                case "place_damage", "move_unit" -> Путь.ВОЙНА;
                                case "power_building_free", "permanent_energy" -> Путь.ХОЗЯЙСТВО;
                                case "free_action" -> switch (д) {
                                    case "science" -> Путь.ТЕХНОЛОГИИ;
                                    case "assembly", "combat", "movement" -> Путь.ВОЙНА;
                                    default -> Путь.ХОЗЯЙСТВО;
                                };
                                default -> Путь.ТЕХНОЛОГИИ;
                            };
                            сумма.merge(п, вес, Double::sum);
                            if (п == Путь.ВОЙНА) {
                                сумма.merge(Путь.ДАВЛЕНИЕ, 0.6 * вес, Double::sum);
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /** План словами — для хроники и окна партии. */
    public String словами() {
        return путь == null ? "" : путь.словами + "; в этом раунде " + тактика.словами;
    }
}
