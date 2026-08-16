package kelium.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Один гекс поля: 6 наземных сторон + воздушная ячейка, соседи и занятость.
 *
 * <p>Незыблемое ядро: у каждого гекса 6 наземных ячеек (сторон) 0..5,
 * соответствующих шести осевым направлениям ({@link Field#AXIAL_DIRS}), плюс одна
 * центральная воздушная ячейка для авиации. Хранит, кто занимает каждую сторону
 * (наземка/здания), кто в воздухе, а также состояние грядки (тайла зарождения):
 * келемий, переворот, снятие тайлов и т.п.
 */
public final class Hex {

    public final String id;
    public HexKind kind = HexKind.NORMAL;
    public int sectors = 6;                       // у гекса 6 наземных ячеек (сторон)
    public final List<String> neighbors = new ArrayList<>();
    // neighborBySide[i] = id соседа по стороне i (0..5), либо null
    public final String[] neighborBySide = new String[6];
    // sideOwner[i] = uid жетона, занимающего сторону i, либо null
    public final Integer[] sideOwner = new Integer[6];
    /**
     * ПЕЧАТНЫЙ КОНТЕЙНЕР УЖЕ СОБРАН с этой ячейки (правило-вариант
     * {@code containers.printed_cell_burns_out}). На картоне контейнер нарисован
     * навсегда, поэтому за столом это помечается маркером; в движке — этим флагом.
     * Нужен потому, что без него ячейка отдаёт карту каждому новому приходу, и
     * замер 13.08.2026 показал бездонный источник: 83 контейнера за партию против 16.6 у прежних
     * жетонов.
     */
    public boolean containerTaken = false;
    // uid'ы наземных жетонов (здания/наземные войска) на гексе
    public final List<Integer> groundTokens = new ArrayList<>();
    public Integer airToken = null;
    /**
     * Нейтральное здание на гексе. На одном гексе их может быть НЕСКОЛЬКО —
     * сносятся по отдельности. corners — углы гекса, которых касается здание
     * (1 = север, по часовой 1..6; одинарное = 2 угла/одно ребро, двойное =
     * 3 угла/два ребра); null, если углы не заданы.
     */
    public static final class NeutralBuilding {
        public final int uid;                 // отрицательный, чтобы не пересекаться с жетонами
        public final boolean big;
        public int hp;
        public final List<Integer> corners;

        public NeutralBuilding(int uid, boolean big, List<Integer> corners) {
            this.uid = uid;
            this.big = big;
            this.hp = big ? 2 : 1;
            this.corners = corners;
        }

        /** Точная копия постройки (для копии состояния при просчёте вперёд). */
        public NeutralBuilding copy() {
            NeutralBuilding n = new NeutralBuilding(uid, big,
                corners == null ? null : new ArrayList<>(corners));
            n.hp = hp;
            return n;
        }

        public int trophyReward() {
            return big ? 2 : 1;
        }

        public int containerReward() {
            return 1;
        }

        /**
         * Стороны гекса (0..5, индексы {@link Field#AXIAL_DIRS}), закрытые
         * СТЕНКОЙ этого нейтрала. Углы нумеруются 1..6 от верхнего северного по
         * часовой (pointy-top); ребро между углами c и c+1 соответствует
         * стороне (2-c) mod 6 (вывод: угол1-угол2 = северо-восточное ребро =
         * направление {+1,-1} = сторона 1). Без углов стенок нет — нейтрал
         * стоит в центре и прохода не закрывает (§12.3: блокировка по-сторонняя).
         */
        public List<Integer> wallSides() {
            if (corners == null || corners.size() < 2) {
                return List.of();
            }
            java.util.Set<Integer> cs = new java.util.HashSet<>(corners);
            List<Integer> sides = new ArrayList<>();
            for (int c : corners) {
                int next = c % 6 + 1;
                if (cs.contains(next)) {
                    sides.add(Math.floorMod(2 - c, 6));
                }
            }
            return sides;
        }
    }

    // Нейтральные здания на гексе (пока список не пуст — гекс закрыт).
    public final List<NeutralBuilding> neutrals = new ArrayList<>();

    /** Есть ли на гексе хоть одно нейтральное здание. */
    public boolean hasNeutral() {
        return !neutrals.isEmpty();
    }

    /** Есть ли среди нейтралов большое (для рендеров). */
    public boolean anyNeutralBig() {
        for (NeutralBuilding n : neutrals) {
            if (n.big) {
                return true;
            }
        }
        return false;
    }

    /**
     * Тайл зарождения — физический ЖЕТОН, лежащий на гексе (null = нет).
     * Гекс сам ничего не знает про келемий: всё состояние — в {@link SpawnTile}.
     * Пока тайл лежит, заняты все наземные ячейки и воздушная (пролёт можно).
     */
    public SpawnTile spawnTile = null;

    /** Лежит ли на гексе тайл зарождения. */
    public boolean hasSpawnTile() {
        return spawnTile != null;
    }

    /**
     * ПЕЧАТНЫЙ КОНТЕЙНЕР: номер ячейки, где на картонном блоке нарисован
     * контейнер. 0..5 — наземные стороны, 6 — воздушная ячейка, −1 — нет.
     *
     * <p>Жетонов контейнеров на поле больше не бывает (правило «Контейнеры
     * 2.0», 12.08.2026): контейнер напечатан на блоке и достаётся тому, чей
     * жетон встал на эту ячейку — каждый раз, когда встаёт.
     */
    public int containerCell = -1;

    /**
     * ЖЕТОН КОНТЕЙНЕРА НА ГЕКСЕ — СТАРЫЙ механизм (режим правил
     * {@code containers.mode: tokens}, версия 1.6.0-c1 от 12.08.2026).
     *
     * <p>Так контейнеры работали до «Контейнеров 2.0»: жетон лежит НА ГЕКСЕ, его
     * подбирает войско, вошедшее на гекс, а стройка на этом гексе жетон СЖИГАЕТ.
     * Раскладываются жетоны при подготовке и каждое Обновление. В основном режиме
     * ({@code printed}) поле не используется и всегда 0.
     */
    public int containerTokens = 0;

    public Hex(String id) {
        this.id = id;
    }

    /**
     * Точная копия гекса — вся изменяемая обстановка (занятость сторон, жетоны,
     * нейтралы, тайл зарождения). Нужна копии состояния для просчёта вперёд.
     */
    public Hex copy() {
        Hex h = new Hex(id);
        h.kind = kind;
        h.sectors = sectors;
        h.neighbors.addAll(neighbors);
        System.arraycopy(neighborBySide, 0, h.neighborBySide, 0, 6);
        System.arraycopy(sideOwner, 0, h.sideOwner, 0, 6);
        h.groundTokens.addAll(groundTokens);
        h.airToken = airToken;
        for (NeutralBuilding n : neutrals) {
            h.neutrals.add(n.copy());
        }
        h.spawnTile = spawnTile == null ? null : spawnTile.copy();
        h.containerCell = containerCell;
        h.containerTaken = containerTaken;
        h.containerTokens = containerTokens;
        return h;
    }

    /** Число свободных наземных сторон (из 6). */
    public int freeSectors() {
        int n = 0;
        for (Integer o : sideOwner) {
            if (o == null) {
                n++;
            }
        }
        return n;
    }

    /** Индексы всех свободных наземных сторон гекса. */
    public List<Integer> freeSideIndices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (sideOwner[i] == null) {
                out.add(i);
            }
        }
        return out;
    }

    /** Есть ли две СМЕЖНЫЕ свободные стороны (нужно технике). */
    public boolean hasFreeAdjacentPair() {
        for (int i = 0; i < 6; i++) {
            if (sideOwner[i] == null && sideOwner[(i + 1) % 6] == null) {
                return true;
            }
        }
        return false;
    }

    /** Занять указанные стороны жетоном uid. false, если заняты. */
    public boolean occupySides(int uid, List<Integer> sides) {
        for (int i : sides) {
            if (sideOwner[i] != null) {
                return false;
            }
        }
        for (int i : sides) {
            sideOwner[i] = uid;
        }
        if (!groundTokens.contains(uid)) {
            groundTokens.add(uid);
        }
        return true;
    }

    /** Освободить все стороны, занятые жетоном uid. */
    public void freeSidesByToken(int uid) {
        for (int i = 0; i < 6; i++) {
            if (sideOwner[i] != null && sideOwner[i] == uid) {
                sideOwner[i] = null;
            }
        }
        groundTokens.remove(Integer.valueOf(uid));
    }

    /** Индексы сторон, обращённых к соседнему гексу neighborId. */
    public List<Integer> sidesFacing(String neighborId) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (neighborId.equals(neighborBySide[i])) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * Найти {@code size} смежных свободных сторон подряд (след здания). null если
     * нет места. Для size=1 — любая свободная сторона.
     */
    public List<Integer> firstFreeFootprint(int size) {
        for (int start = 0; start < 6; start++) {
            boolean ok = true;
            List<Integer> block = new ArrayList<>();
            for (int k = 0; k < size; k++) {
                int i = (start + k) % 6;
                block.add(i);
                if (sideOwner[i] != null) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return block;
            }
        }
        return null;
    }

    // ================= умная переупаковка (нежёсткие войска) =================
    // Войска не приколочены к ячейкам: занимают МЕСТО и всегда могут
    // подвинуться внутри гекса. Жёсткие только здания/стенки нейтралов
    // (sideOwner). Технике нужны 2 СМЕЖНЫЕ ячейки. Отсюда честная проверка:
    // «влезет ли новичок с footprint fp, если стоящие войска переупакуются».

    /**
     * Поместится ли новичок из {@code fp} СМЕЖНЫХ свободных ячеек (здание или
     * техника fp=2, одиночный жетон fp=1) при переупаковке стоящих войск:
     * {@code vehicles} единиц техники (по 2 смежные ячейки) и {@code singles}
     * одиночных (пехота/вышки, по 1). Тайл зарождения закрывает гекс целиком.
     */
    public boolean fitsWithRepack(int fp, int vehicles, int singles) {
        if (spawnTile != null) {
            return false;
        }
        if (fp == 0) {
            return feasible(freeMask(), vehicles, singles);
        }
        return chooseFootprint(fp, vehicles, singles) != null;
    }

    /**
     * Выбрать {@code fp} смежных свободных сторон так, чтобы ПОСЛЕ размещения
     * стоящие войска всё ещё умещались переупаковкой. null = не влезает никак.
     * Для fp=1 вернёт одну сторону, для 2-3 — дугу подряд.
     */
    /**
     * След из {@code fp} смежных ячеек, НАЧИНАЯ С ЗАДАННОЙ стороны, если он
     * годится: все ячейки свободны и стоящие войска после постановки ещё
     * умещаются переупаковкой. null — такой поворот невозможен.
     *
     * <p>Нужен, чтобы игрок (или бот) выбирал ПОВОРОТ здания сам: от поворота
     * зависит, куда растёт зона стройки, какую жилу достаёт добытчик и какую
     * ячейку с печатным контейнером накрывает след.
     */
    public List<Integer> footprintAt(int start, int fp, int vehicles, int singles) {
        if (spawnTile != null || fp <= 0) {
            return null;
        }
        boolean[] free = freeMask();
        List<Integer> run = new ArrayList<>();
        for (int k = 0; k < fp; k++) {
            int i = (start + k) % 6;
            if (!free[i]) {
                return null;
            }
            run.add(i);
        }
        boolean[] after = free.clone();
        for (int i : run) {
            after[i] = false;
        }
        return feasible(after, vehicles, singles) ? run : null;
    }

    public List<Integer> chooseFootprint(int fp, int vehicles, int singles) {
        if (spawnTile != null || fp <= 0) {
            return null;
        }
        boolean[] free = freeMask();
        for (int start = 0; start < 6; start++) {
            boolean ok = true;
            List<Integer> run = new ArrayList<>();
            for (int k = 0; k < fp; k++) {
                int i = (start + k) % 6;
                run.add(i);
                if (!free[i]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            boolean[] f2 = free.clone();
            for (int i : run) {
                f2[i] = false;
            }
            if (feasible(f2, vehicles, singles)) {
                return run;
            }
        }
        return null;
    }

    private boolean[] freeMask() {
        boolean[] free = new boolean[6];
        for (int i = 0; i < 6; i++) {
            free[i] = sideOwner[i] == null;
        }
        return free;
    }

    /**
     * Умещаются ли войска (vehicles×2 смежных + singles×1) на свободных ячейках
     * маски. Свободные ячейки образуют ДУГИ между жёсткими жетонами; в дуге
     * длины L помещается floor(L/2) техники (пары кладутся подряд), остальные
     * ячейки добирает пехота. Полностью свободный гекс = цикл длины 6.
     */
    private static boolean feasible(boolean[] free, int vehicles, int singles) {
        int total = 0;
        for (boolean b : free) {
            if (b) {
                total++;
            }
        }
        if (total < 2 * vehicles + singles) {
            return false;
        }
        if (vehicles == 0) {
            return true;
        }
        if (total == 6) {
            return vehicles <= 3;   // полный цикл: 3 пары
        }
        // дуги: сканируем с любой ЗАНЯТОЙ ячейки по кругу
        int anchor = -1;
        for (int i = 0; i < 6; i++) {
            if (!free[i]) {
                anchor = i;
                break;
            }
        }
        int cap = 0;
        int runLen = 0;
        for (int k = 1; k <= 6; k++) {
            int i = (anchor + k) % 6;
            if (free[i]) {
                runLen++;
            } else {
                cap += runLen / 2;
                runLen = 0;
            }
        }
        cap += runLen / 2;
        return cap >= vehicles;
    }
}
