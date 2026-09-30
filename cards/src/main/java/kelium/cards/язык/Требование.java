package kelium.cards.язык;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.TurnJournal;
import kelium.engine.cards.CardContext;

/**
 * ТРЕБОВАНИЕ КАРТЫ — ДАННЫМИ (Карты 2.0, 30.09.2026).
 *
 * <p>Каждый узел умеет четыре вещи: проверить стол, сказать близость (0..1 —
 * по ней ищет ходы измеритель), напечатать себя словами игры и записать себя в
 * данные. Текст карты ПЕЧАТАЕТСЯ из узлов, руками не пишется: иначе он
 * разойдётся с проверкой, как расходился у прежних карт.
 *
 * <p>Происшествия («В ЭТОТ ХОД …») читают журнал хода; состояния — стол.
 */
public interface Требование {

    /** Выполнено ли требование сейчас. */
    boolean выполнено(CardContext ctx);

    /** Доля пути до выполнения, 0..1. */
    double близость(CardContext ctx);

    /** Суть словами, со строчной буквы и без «В ЭТОТ ХОД»: «имей 6 монет». */
    String суть();

    /** Событие этого хода (печатается с «В ЭТОТ ХОД»), а не расстановка. */
    boolean происшествие();

    /** Запись в данные карты. */
    Map<String, Object> запись();

    /** Действие-подсказка боту (код ветки) или {@code null}. */
    default String действие() {
        return null;
    }

    /** Печатный текст целиком, с заглавной и с «В ЭТОТ ХОД» у происшествий. */
    default String текст() {
        String с = суть();
        if (происшествие()) {
            return "В ЭТОТ ХОД " + с;
        }
        return Character.toUpperCase(с.charAt(0)) + с.substring(1);
    }

    // ==================================================================
    //  ОБЩЕЕ
    // ==================================================================

    static TurnJournal.TurnFacts ход(CardContext ctx) {
        TurnJournal j = ctx.state().journal;
        return j == null ? new TurnJournal(ctx.state().numPlayers()).of(ctx.seat())
            : j.of(ctx.seat());
    }

    static double доля(double есть, double надо) {
        return надо <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, есть / надо));
    }

    /** Жетоны на поле: свои или чужие (любого соперника), живые. */
    static List<Token> жетоны(GameState s, int seat, boolean свои) {
        List<Token> out = new ArrayList<>();
        for (PlayerState p : s.players) {
            if ((p.seat == seat) != свои) {
                continue;
            }
            for (var b : p.buildingsOnField()) {
                out.add(b);
            }
            for (var u : p.unitsOnField()) {
                out.add(u);
            }
        }
        return out;
    }

    /** Название ветки так, как оно печатается. */
    static String ветка(String код) {
        return switch (код) {
            case "mining" -> "«Добыть»";
            case "build_miner" -> "«Построить добытчик»";
            case "energy_swap" -> "«Переложить энергию»";
            case "build_plant" -> "«Построить энергостанцию»";
            case "assembly" -> "«Выпустить»";
            case "build_military" -> "«Построить военное здание»";
            case "movement" -> "«Манёвр»";
            case "combat" -> "«Бой»";
            case "market" -> "«Рынок»";
            case "science" -> "«Наука»";
            default -> код;
        };
    }

    /** Развилка в родительном: «обе ветки Командования». */
    static String развилкиРод(String код) {
        return switch (код) {
            case "extract" -> "Добычи";
            case "power" -> "Питания";
            case "supply" -> "Снабжения";
            case "command" -> "Командования";
            case "develop" -> "Развития";
            default -> код;
        };
    }

    static String порядковое(int k) {
        return switch (k) {
            case 2 -> "вторым";
            case 3 -> "третьим";
            case 4 -> "четвёртым";
            default -> k + "-м";
        };
    }

    static String числом(int n, String одна, String две, String пять) {
        int п = n % 10;
        int сотня = n % 100;
        String слово = (п == 1 && сотня != 11) ? одна
            : (п >= 2 && п <= 4 && (сотня < 12 || сотня > 14)) ? две : пять;
        return n + " " + слово;
    }

    // ==================================================================
    //  УЗЛЫ-СОСТОЯНИЯ
    // ==================================================================

    /** «имей на поле N своих запитанных добытчиков [на разных гексах]». */
    record Жетоны(Группа группа, int сколько, boolean разныхГексов) implements Требование {
        private int есть(CardContext ctx) {
            Set<String> гексы = new HashSet<>();
            int n = 0;
            for (Token t : жетоны(ctx.state(), ctx.seat(), группа.свои())) {
                if (группа.подходит(t)) {
                    n++;
                    гексы.add(t.hexId());
                }
            }
            return разныхГексов ? гексы.size() : n;
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(есть(ctx), сколько);
        }

        @Override public String суть() {
            return "имей на поле " + группа.фраза(сколько) + (разныхГексов && сколько > 1
                ? " на разных гексах" : "");
        }

        @Override public boolean происшествие() {
            return false;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "жетоны", "группа", группа.запись(), "сколько", сколько,
                "разных_гексов", разныхГексов);
        }
    }

    /**
     * «имей своё военное здание на гексе, соседнем с гексом, где есть войско
     * врага» — N разных своих гексов, где выполнено отношение.
     */
    record Рядом(Группа а, Группа б, boolean соседний, int гексов) implements Требование {
        private int есть(CardContext ctx) {
            GameState s = ctx.state();
            Set<String> где = new HashSet<>();
            for (Token t : жетоны(s, ctx.seat(), б.свои())) {
                if (б.подходит(t) && t.hexId() != null) {
                    где.add(t.hexId());
                }
            }
            Set<String> мои = new HashSet<>();
            for (Token t : жетоны(s, ctx.seat(), а.свои())) {
                if (!а.подходит(t) || t.hexId() == null) {
                    continue;
                }
                if (соседний) {
                    for (String n : s.field.neighbors(t.hexId())) {
                        if (где.contains(n)) {
                            мои.add(t.hexId());
                            break;
                        }
                    }
                } else if (где.contains(t.hexId())) {
                    мои.add(t.hexId());
                }
            }
            return мои.size();
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= гексов;
        }

        @Override public double близость(CardContext ctx) {
            int n = есть(ctx);
            if (n >= гексов) {
                return 1.0;
            }
            boolean естьА = false;
            for (Token t : жетоны(ctx.state(), ctx.seat(), а.свои())) {
                естьА |= а.подходит(t);
            }
            return Math.max(доля(n, гексов), естьА ? 0.3 : 0.0);
        }

        @Override public String суть() {
            String кого = а.фраза(1);
            String где = соседний
                ? "гексе, соседнем с гексом, где есть " + б.гдеЕсть()
                : "гексе, где есть " + б.гдеЕсть();
            if (гексов == 1) {
                return "имей " + кого + " на " + где;
            }
            // Несколько гексов — несколько жетонов: «свои жетоны техники на 2 разных гексах».
            String своиМн = (а.свои() ? "свои " : "")
                + (а.сост().мн.isEmpty() ? "" : а.сост().мн + " ") + а.кто().винМн()
                + (а.свои() ? "" : " врага");
            String гексыСлово = соседний ? " разных гексах, соседних с гексами, где есть "
                + б.гдеЕсть() : " разных гексах, где есть " + б.гдеЕсть();
            return "имей " + своиМн + " на " + гексов + гексыСлово;
        }

        @Override public boolean происшествие() {
            return false;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "рядом", "а", а.запись(), "б", б.запись(),
                "соседний", соседний, "гексов", гексов);
        }
    }

    /** «имей не меньше N монет». */
    record Ресурс(Resource ресурс, int сколько) implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            return ctx.me().resources.get(ресурс) >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(ctx.me().resources.get(ресурс), сколько);
        }

        @Override public String суть() {
            return "имей не меньше " + switch (ресурс) {
                case COIN -> числом(сколько, "монеты", "монет", "монет")
                    .replaceFirst("^1 монеты$", "1 монеты");
                case AMMO -> числом(сколько, "боеприпаса", "боеприпасов", "боеприпасов");
                case TROPHY -> числом(сколько, "трофея", "трофеев", "трофеев");
                case KELIUM -> сколько + " келемия";
            };
        }

        @Override public boolean происшествие() {
            return false;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "ресурс", "ресурс", ресурс.name(), "сколько", сколько);
        }
    }

    /** «имей N установленных карт арсенала». */
    record Арсенал(int сколько) implements Требование {
        private static int есть(CardContext ctx) {
            return ctx.me().allInstalledArsenal().size();
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(есть(ctx), сколько);
        }

        @Override public String суть() {
            return сколько == 1 ? "имей установленную карту арсенала"
                : "имей " + числом(сколько, "установленную карту арсенала",
                    "установленные карты арсенала", "установленных карт арсенала");
        }

        @Override public boolean происшествие() {
            return false;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "арсенал", "сколько", сколько);
        }
    }

    /** «имей на свалке 2 жетона врага». */
    record Свалка(Группа группа, int сколько) implements Требование {
        private int есть(CardContext ctx) {
            int n = 0;
            for (Token t : ctx.me().destroyedTokens) {
                if (группа.кто().подходит(t)) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(есть(ctx), сколько);
        }

        @Override public String суть() {
            return "имей на свалке " + группа.фраза(сколько);
        }

        @Override public boolean происшествие() {
            return false;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "свалка", "группа", группа.запись(), "сколько", сколько);
        }

        @Override public String действие() {
            return "combat";
        }
    }

    // ==================================================================
    //  УЗЛЫ-ПРОИСШЕСТВИЯ (журнал хода)
    // ==================================================================

    /** «сыграй «Добыть» и Бой». */
    record Ветки(List<String> ветки) implements Требование {
        private int есть(CardContext ctx) {
            List<String> сыграно = new ArrayList<>(ход(ctx).веткиХода);
            int n = 0;
            for (String в : ветки) {
                if (сыграно.remove(в)) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= ветки.size();
        }

        @Override public double близость(CardContext ctx) {
            return доля(есть(ctx), ветки.size());
        }

        @Override public String суть() {
            List<String> слова = new ArrayList<>();
            for (String в : ветки) {
                слова.add(ветка(в));
            }
            if (слова.size() == 1) {
                return "сыграй " + слова.get(0);
            }
            return "сыграй " + String.join(", ", слова.subList(0, слова.size() - 1))
                + " и " + слова.get(слова.size() - 1);
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "ветки", "ветки", List.copyOf(ветки));
        }

        @Override public String действие() {
            return ветки.isEmpty() ? null : ветки.get(0);
        }
    }

    /** «сыграй обе ветки Командования». */
    record ОбеВетки(String развилка) implements Требование {
        private List<String> нужные() {
            return switch (развилка) {
                case "extract" -> List.of("mining", "build_miner");
                case "power" -> List.of("energy_swap", "build_plant");
                case "supply" -> List.of("assembly", "build_military");
                case "command" -> List.of("movement", "combat");
                case "develop" -> List.of("market", "science");
                default -> List.of();
            };
        }

        @Override public boolean выполнено(CardContext ctx) {
            return ход(ctx).веткиХода.containsAll(нужные());
        }

        @Override public double близость(CardContext ctx) {
            int n = 0;
            for (String в : нужные()) {
                if (ход(ctx).веткиХода.contains(в)) {
                    n++;
                }
            }
            return доля(n, 2);
        }

        @Override public String суть() {
            return "сыграй обе ветки " + развилкиРод(развилка);
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "обе_ветки", "развилка", развилка);
        }
    }

    /** «выполни это задание вторым» — k-е выполненное в этом ходу. */
    record ОчередьЗадания(int k) implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            return ход(ctx).заданийВыполнено >= k - 1;
        }

        @Override public double близость(CardContext ctx) {
            return доля(ход(ctx).заданийВыполнено, k - 1);
        }

        @Override public String суть() {
            return "выполни это задание " + порядковое(k);
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "очередь_задания", "k", k);
        }
    }

    /** «сожги карту» / «сожги 2 карты». */
    record Сожги(int сколько) implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            return ход(ctx).картСожжено >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(ход(ctx).картСожжено, сколько);
        }

        @Override public String суть() {
            return сколько == 1 ? "сожги карту" : "сожги " + числом(сколько, "карту", "карты", "карт");
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "сожги", "сколько", сколько);
        }
    }

    /** «установи карту арсенала». */
    record Установи() implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            return ход(ctx).картУстановлено >= 1;
        }

        @Override public double близость(CardContext ctx) {
            return выполнено(ctx) ? 1.0 : (ctx.me().arsenalHand.isEmpty() ? 0.0 : 0.5);
        }

        @Override public String суть() {
            return "установи карту арсенала";
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "установи");
        }
    }

    /** «выполни это задание третьим спец-действием хода». */
    record ОчередьСпец(int k) implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            return ход(ctx).спецИспользовано >= k - 1;
        }

        @Override public double близость(CardContext ctx) {
            return доля(ход(ctx).спецИспользовано, k - 1);
        }

        @Override public String суть() {
            return "выполни это задание " + порядковое(k) + " спец-действием хода";
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "очередь_спец", "k", k);
        }
    }

    /** «уничтожь здание врага техникой». */
    record Уничтожь(Группа цель, Кто кем, int сколько) implements Требование {
        private int есть(CardContext ctx) {
            int n = 0;
            for (TurnJournal.Убитый у : ход(ctx).killLog) {
                if (у.owner() == ctx.seat()) {
                    continue;
                }
                if (!цель.кто().подходитКод(у.kind(), у.building())) {
                    continue;
                }
                if (кем != null && !кем.подходитКод(у.кем(), false)) {
                    continue;
                }
                n++;
            }
            return n;
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(есть(ctx), сколько);
        }

        @Override public String суть() {
            return "уничтожь " + цель.фраза(сколько) + (кем == null ? "" : " " + кем.твор);
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("узел", "уничтожь");
            m.put("цель", цель.запись());
            if (кем != null) {
                m.put("кем", кем.name());
            }
            m.put("сколько", сколько);
            return m;
        }

        @Override public String действие() {
            return "combat";
        }
    }

    /** «построй добытчик» / «построй 2 здания» — ветками «построить». */
    record Построй(String вид, int сколько) implements Требование {
        private int есть(CardContext ctx) {
            int n = 0;
            for (String в : ход(ctx).веткиХода) {
                if (в.startsWith("build_") && (вид == null || в.equals("build_" + вид))) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean выполнено(CardContext ctx) {
            return есть(ctx) >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(есть(ctx), сколько);
        }

        @Override public String суть() {
            String что = вид == null ? (сколько == 1 ? "здание"
                : числом(сколько, "здание", "здания", "зданий"))
                : switch (вид) {
                    case "miner" -> сколько == 1 ? "добытчик"
                        : числом(сколько, "добытчик", "добытчика", "добытчиков");
                    case "plant" -> сколько == 1 ? "энергостанцию"
                        : числом(сколько, "энергостанцию", "энергостанции", "энергостанций");
                    default -> сколько == 1 ? "военное здание"
                        : числом(сколько, "военное здание", "военных здания", "военных зданий");
                };
            return "построй " + что;
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("узел", "построй");
            if (вид != null) {
                m.put("вид", вид);
            }
            m.put("сколько", сколько);
            return m;
        }

        @Override public String действие() {
            return вид == null ? "build_miner" : "build_" + вид;
        }
    }

    /** «найми 2 войска». */
    record Найми(int сколько) implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            return ход(ctx).hiredUids.size() >= сколько;
        }

        @Override public double близость(CardContext ctx) {
            return доля(ход(ctx).hiredUids.size(), сколько);
        }

        @Override public String суть() {
            return "найми " + (сколько == 1 ? "войско" : числом(сколько, "войско", "войска", "войск"));
        }

        @Override public boolean происшествие() {
            return true;
        }

        @Override public Map<String, Object> запись() {
            return Map.of("узел", "найми", "сколько", сколько);
        }

        @Override public String действие() {
            return "assembly";
        }
    }

    /** Все части сразу. Происшествие, если хоть одна часть — происшествие. */
    record И(List<Требование> части) implements Требование {
        @Override public boolean выполнено(CardContext ctx) {
            for (Требование т : части) {
                if (!т.выполнено(ctx)) {
                    return false;
                }
            }
            return true;
        }

        @Override public double близость(CardContext ctx) {
            double сумма = 0;
            for (Требование т : части) {
                сумма += т.близость(ctx);
            }
            return части.isEmpty() ? 1.0 : сумма / части.size();
        }

        @Override public String суть() {
            List<String> слова = new ArrayList<>();
            for (Требование т : части) {
                слова.add(т.суть());
            }
            return String.join(" и ", слова);
        }

        @Override public boolean происшествие() {
            for (Требование т : части) {
                if (т.происшествие()) {
                    return true;
                }
            }
            return false;
        }

        @Override public Map<String, Object> запись() {
            List<Object> ч = new ArrayList<>();
            for (Требование т : части) {
                ч.add(т.запись());
            }
            return Map.of("узел", "и", "части", ч);
        }

        @Override public String действие() {
            for (Требование т : части) {
                if (т.действие() != null) {
                    return т.действие();
                }
            }
            return null;
        }
    }

    // ==================================================================
    //  ЧТЕНИЕ ИЗ ДАННЫХ
    // ==================================================================

    /** Требование из записи карты. */
    static Требование из(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("требование — не запись: " + o);
        }
        String узел = String.valueOf(m.get("узел"));
        return switch (узел) {
            case "жетоны" -> new Жетоны(Группа.из(m.get("группа")), число(m, "сколько", 1),
                Boolean.TRUE.equals(m.get("разных_гексов")));
            case "рядом" -> new Рядом(Группа.из(m.get("а")), Группа.из(m.get("б")),
                !Boolean.FALSE.equals(m.get("соседний")), число(m, "гексов", 1));
            case "ресурс" -> new Ресурс(Resource.valueOf(String.valueOf(m.get("ресурс"))),
                число(m, "сколько", 1));
            case "арсенал" -> new Арсенал(число(m, "сколько", 1));
            case "свалка" -> new Свалка(Группа.из(m.get("группа")), число(m, "сколько", 1));
            case "ветки" -> new Ветки(строки(m.get("ветки")));
            case "обе_ветки" -> new ОбеВетки(String.valueOf(m.get("развилка")));
            case "очередь_задания" -> new ОчередьЗадания(число(m, "k", 2));
            case "сожги" -> new Сожги(число(m, "сколько", 1));
            case "установи" -> new Установи();
            case "очередь_спец" -> new ОчередьСпец(число(m, "k", 2));
            case "уничтожь" -> new Уничтожь(Группа.из(m.get("цель")),
                m.get("кем") == null ? null : Кто.valueOf(String.valueOf(m.get("кем"))),
                число(m, "сколько", 1));
            case "построй" -> new Построй(m.get("вид") == null ? null : String.valueOf(m.get("вид")),
                число(m, "сколько", 1));
            case "найми" -> new Найми(число(m, "сколько", 1));
            case "и" -> {
                List<Требование> ч = new ArrayList<>();
                if (m.get("части") instanceof List<?> l) {
                    for (Object x : l) {
                        ч.add(из(x));
                    }
                }
                yield new И(ч);
            }
            default -> throw new IllegalArgumentException("неизвестный узел требования: " + узел);
        };
    }

    private static int число(Map<?, ?> m, String ключ, int поУмолчанию) {
        return m.get(ключ) instanceof Number n ? n.intValue() : поУмолчанию;
    }

    private static List<String> строки(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        }
        return out;
    }
}
