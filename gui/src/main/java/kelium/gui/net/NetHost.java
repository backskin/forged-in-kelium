package kelium.gui.net;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import kelium.core.Choice;
import kelium.core.GameAborted;
import kelium.core.GameState;
import kelium.gui.HotSeatWindow;
import kelium.gui.kp.ChoiceWords;
import kelium.observe.PublicView;
import kelium.report.Json;
import kelium.report.ReplayRecord;

import static kelium.gui.net.NetProtocol.msg;

/**
 * СЕТЕВОЙ СТОЛ У ХОСТА — в процессе того, кто его создал.
 *
 * <p>Слушает порт, пускает игроков (сверка версии протокола и отпечатка
 * правил), раздаёт места, ведёт лобби и чат, а после «Начать» держит
 * {@link RemoteAgent} каждого сетевого места и рассылает кадры партии,
 * вырезанные под место ({@link Redact}).
 *
 * <p>Сама партия играется либо в обычном окне хоста ({@link NetSeats} —
 * места {@code net:N}), либо без окна ({@link NetGame}). Хост получает кадры
 * записи через {@link #onFrame} на потоке движка и копирует их к себе, чтобы
 * рассылать с любого потока, не трогая записи, которую движок продолжает
 * писать.
 */
public final class NetHost {

    /** Кто сидит на месте. */
    public enum Kind { HOST, OPEN, REMOTE, BOT }

    /** Одно место за столом. */
    public static final class Seat {
        public final int index;
        public volatile Kind kind;
        public volatile String name;
        /** Бот на этом месте (имя из справочника ботов). */
        public volatile String bot;
        public volatile boolean ready;
        volatile String token;
        volatile Wire wire;
        volatile RemoteAgent agent;
        /** Сколько кадров записи этому месту уже ушло. */
        int sentFrames;

        Seat(int index) {
            this.index = index;
        }

        public boolean online() {
            return kind == Kind.HOST || kind == Kind.BOT || wire != null && !wire.closed();
        }
    }

    private final HotSeatWindow.Options table;
    private final int hostSeat;
    private final Seat[] seats;
    private final SecureRandom random = new SecureRandom();
    private final List<String> chatLog = new CopyOnWriteArrayList<>();
    private volatile ServerSocket server;
    private volatile boolean started;
    private volatile boolean finished;
    private volatile HotSeatWindow.Options startedOptions;

    /** Изменилось лобби (места, готовность, связь) — перерисовать. Любой поток. */
    public volatile Runnable onChange = () -> { };
    /** Пришла строка чата: «кто: что». Любой поток. */
    public volatile Consumer<String> onChat = s -> { };
    /** Служебные строки (кто вошёл, кто отвалился). */
    public volatile Consumer<String> log = s -> System.out.println("[стол] " + s);
    /** Слушатели чата сверх {@link #onChat} — окно партии, тесты. Любой поток. */
    public final List<Consumer<String>> chatListeners = new CopyOnWriteArrayList<>();
    /**
     * Слушатели паузы: игрок вышел, хост решил ждать, пауза снята. Состояние —
     * {@link #awaySeat()}, {@link #waitingFor(int)}. Любой поток.
     */
    public final List<Runnable> pauseListeners = new CopyOnWriteArrayList<>();

    /** Бот, которому хост отдаёт место ушедшего игрока. */
    public static final String STAND_IN_BOT = "builder:2";

    // ---- пауза: кто вышел из идущей партии ----
    private final Object pauseLock = new Object();
    /** Места, вышедшие из партии, — пока есть хоть одно, партия стоит. Под pauseLock. */
    private final Set<Integer> away = new LinkedHashSet<>();
    /** Места, которых хост решил ждать. Под pauseLock. */
    private final Set<Integer> waiting = new LinkedHashSet<>();
    /** Хост закрыл стол посреди партии. */
    private volatile boolean closed;
    /** Ведущий партию (окно хоста или {@link NetGame}) — лента и отмена. */
    private volatile NetSeats.Link link;
    /** Сколько кадров пришло от движка — проверка паузы в тестах. */
    private volatile int framesSeen;

    // ---- зеркало записи партии ----
    private ReplayRecord head;
    private final List<ReplayRecord.Frame> frames = new ArrayList<>();
    private List<ReplayRecord.OrderPlay> plays = List.of();
    private long lastFlush;
    private boolean dirty;
    private Thread pump;

    /**
     * @param table стол, как его собрали в «Штабе»: места {@code human} —
     *              живые (первое — сам хост, остальные открыты для друзей),
     *              прочие — боты
     */
    public NetHost(HotSeatWindow.Options table, String hostName) {
        this.table = table;
        this.seats = new Seat[table.players()];
        int me = -1;
        for (int i = 0; i < seats.length; i++) {
            Seat s = new Seat(i);
            String spec = table.seatSpecs().get(i);
            if ("human".equals(spec) && me < 0) {
                s.kind = Kind.HOST;
                s.name = hostName;
                s.ready = true;
                me = i;
            } else if ("human".equals(spec)) {
                s.kind = Kind.OPEN;
            } else {
                s.kind = Kind.BOT;
                s.bot = spec;
                s.name = kelium.agents.BotCatalog.label(spec);
                s.ready = true;
            }
            seats[i] = s;
        }
        if (me < 0) {
            // все места отданы ботам — хост садится на первое
            seats[0].kind = Kind.HOST;
            seats[0].name = hostName;
            seats[0].bot = null;
            me = 0;
        }
        this.hostSeat = me;
    }

    // ==================== приём подключений ====================

    /** Открыть порт (0 — любой свободный). Возвращает порт, который слушаем. */
    public int listen(int port) throws IOException {
        ServerSocket ss = new ServerSocket(port);
        server = ss;
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    Socket s = ss.accept();
                    accept(s);
                } catch (IOException e) {
                    if (ss.isClosed()) {
                        return;
                    }
                }
            }
        }, "net-host-accept");
        t.setDaemon(true);
        t.start();
        pump = new Thread(this::pumpLoop, "net-host-pump");
        pump.setDaemon(true);
        pump.start();
        return ss.getLocalPort();
    }

    private void accept(Socket socket) throws IOException {
        Wire w = new Wire(socket);
        int[] bound = {-1};
        w.start("host-" + socket.getPort(), m -> onMessage(w, bound, m), () -> onClosed(w, bound),
            null);
    }

    private void onMessage(Wire w, int[] bound, Map<String, Object> m) {
        String t = NetProtocol.type(m);
        if (bound[0] < 0) {
            if (NetProtocol.HELLO.equals(t)) {
                bound[0] = hello(w, m);
            }
            return;
        }
        Seat s = seats[bound[0]];
        switch (t) {
            case NetProtocol.SEAT -> moveSeat(bound, NetProtocol.i(m, "seat", -1));
            case NetProtocol.READY -> {
                s.ready = Boolean.TRUE.equals(m.get("ready"));
                changed();
            }
            case NetProtocol.CHAT -> chat(s.name, NetProtocol.s(m, "text"));
            case NetProtocol.ANSWER -> {
                RemoteAgent a = s.agent;
                if (a != null) {
                    a.answer(NetProtocol.i(m, "seq", -1), NetProtocol.i(m, "i", -1));
                }
            }
            case NetProtocol.RESYNC -> resync(s);
            case NetProtocol.UNDO -> undo(s, NetProtocol.i(m, "seq", -1),
                Boolean.TRUE.equals(m.get("all")));
            case NetProtocol.BYE -> w.close();
            default -> { }
        }
    }

    /** Вход за стол; возвращает место или −1 (отказ уже отправлен). */
    private int hello(Wire w, Map<String, Object> m) {
        int proto = NetProtocol.i(m, "proto", -1);
        if (proto != NetProtocol.VERSION) {
            reject(w, "у вас другая версия сетевой игры (" + proto + ", у хоста "
                + NetProtocol.VERSION + ") — обновите игру");
            return -1;
        }
        String content = NetProtocol.s(m, "content");
        if (!ContentHash.current().equals(content)) {
            reject(w, "у вас другая версия правил и карт — обновите игру до той же сборки, "
                + "что у хоста");
            return -1;
        }
        String name = NetProtocol.s(m, "name");
        name = name == null || name.isBlank() ? "Гость" : name.trim();
        String token = NetProtocol.s(m, "token");
        Seat seat = null;
        boolean reconnect = false;
        synchronized (this) {
            if (token != null) {
                for (Seat s : seats) {
                    if (token.equals(s.token) && s.kind == Kind.REMOTE) {
                        seat = s;
                        reconnect = true;
                        break;
                    }
                    if (token.equals(s.token) && s.kind == Kind.BOT) {
                        reject(w, "хост отдал ваше место боту");
                        return -1;
                    }
                }
            }
            if (seat == null) {
                if (started) {
                    reject(w, "партия уже идёт — войти можно только на своё место");
                    return -1;
                }
                for (Seat s : seats) {
                    if (s.kind == Kind.OPEN) {
                        seat = s;
                        break;
                    }
                }
                if (seat == null) {
                    reject(w, "стол полон");
                    return -1;
                }
                seat.kind = Kind.REMOTE;
                seat.token = HexFormat.of().formatHex(random.generateSeed(8));
                seat.ready = false;
            }
            Wire old = seat.wire;
            if (old != null && old != w) {
                old.close();
            }
            seat.wire = w;
            seat.name = name;
            seat.sentFrames = 0;
        }
        w.send(msg(NetProtocol.WELCOME, "seat", seat.index, "token", seat.token,
            "players", seats.length, "host", seats[hostSeat].name));
        log.accept((reconnect ? "вернулся: " : "вошёл: ") + name + " — место " + (seat.index + 1)
            + " (" + w.remote() + ")");
        changed();
        if (started) {
            w.send(startMessage(seat.index));
            resync(seat);
            if (reconnect) {
                back(seat.index);          // вернулся — пауза по нему снимается
            }
            Map<String, Object> p = pauseMessage();
            if (p != null) {
                w.send(p);                 // кто-то другой ещё не вернулся
            }
        }
        return seat.index;
    }

    private void reject(Wire w, String reason) {
        w.send(msg(NetProtocol.REJECT, "reason", reason));
        w.close();
    }

    private void onClosed(Wire w, int[] bound) {
        if (bound[0] < 0) {
            return;
        }
        Seat s = seats[bound[0]];
        synchronized (this) {
            if (s.wire != w) {
                return;          // уже переподключился новым проводом
            }
            s.wire = null;
            if (!started) {
                s.kind = Kind.OPEN;
                s.token = null;
                s.ready = false;
            }
        }
        log.accept((started ? "нет связи: " : "вышел: ") + s.name);
        changed();
        if (started && !finished && !closed && s.kind == Kind.REMOTE) {
            left(s.index);
        }
    }

    private void moveSeat(int[] bound, int to) {
        synchronized (this) {
            if (started || to < 0 || to >= seats.length || seats[to].kind != Kind.OPEN) {
                return;
            }
            Seat from = seats[bound[0]];
            Seat dst = seats[to];
            dst.kind = Kind.REMOTE;
            dst.name = from.name;
            dst.token = from.token;
            dst.wire = from.wire;
            dst.ready = false;
            from.kind = Kind.OPEN;
            from.name = null;
            from.token = null;
            from.wire = null;
            from.ready = false;
            bound[0] = to;
        }
        Wire w = seats[to].wire;
        if (w != null) {
            w.send(msg(NetProtocol.WELCOME, "seat", to, "token", seats[to].token,
                "players", seats.length, "host", seats[hostSeat].name));
        }
        changed();
    }

    // ==================== лобби ====================

    public int hostSeat() {
        return hostSeat;
    }

    public Seat[] seats() {
        return seats.clone();
    }

    public HotSeatWindow.Options table() {
        return table;
    }

    public boolean started() {
        return started;
    }

    /** Партия окончена или стол закрыт. */
    public boolean finished() {
        return finished;
    }

    /** Отдать открытое место боту (до старта). */
    public void setBot(int seat, String spec) {
        synchronized (this) {
            Seat s = seats[seat];
            if (started || s.kind == Kind.HOST || s.kind == Kind.REMOTE) {
                return;
            }
            s.kind = Kind.BOT;
            s.bot = spec;
            s.name = kelium.agents.BotCatalog.label(spec);
            s.ready = true;
        }
        changed();
    }

    /** Открыть место для друга (до старта). Сидящего по сети — высадить. */
    public void setOpen(int seat) {
        Wire kick = null;
        synchronized (this) {
            Seat s = seats[seat];
            if (started || s.kind == Kind.HOST) {
                return;
            }
            if (s.kind == Kind.REMOTE) {
                kick = s.wire;
            }
            s.kind = Kind.OPEN;
            s.bot = null;
            s.name = null;
            s.token = null;
            s.wire = null;
            s.ready = false;
        }
        if (kick != null) {
            reject(kick, "хост освободил ваше место");
        }
        changed();
    }

    /** Строка хоста в чат. */
    public void say(String text) {
        chat(seats[hostSeat].name, text);
    }

    private void chat(String from, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String t = text.length() > 300 ? text.substring(0, 300) : text;
        chatLog.add(from + ": " + t);
        broadcast(msg(NetProtocol.CHAT, "from", from, "text", t));
        onChat.accept(from + ": " + t);
        for (Consumer<String> l : chatListeners) {
            l.accept(from + ": " + t);
        }
    }

    /** Почему нельзя начинать; null — можно. */
    public String whyNotStart() {
        List<String> wait = new ArrayList<>();
        for (Seat s : seats) {
            if (s.kind == Kind.OPEN) {
                wait.add("место " + (s.index + 1) + " свободно");
            } else if (s.kind == Kind.REMOTE && !s.online()) {
                wait.add(s.name + " — нет связи");
            } else if (s.kind == Kind.REMOTE && !s.ready) {
                wait.add(s.name + " не готов");
            }
        }
        return wait.isEmpty() ? null : "ждём: " + String.join(", ", wait);
    }

    /** Состояние лобби — то же сообщение, что уходит игрокам. */
    public Map<String, Object> lobbyMessage() {
        List<Object> list = new ArrayList<>();
        for (Seat s : seats) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("seat", s.index);
            o.put("kind", s.kind.name().toLowerCase());
            o.put("name", s.name);
            o.put("ready", s.ready);
            o.put("online", s.online());
            Integer color = table.seatColors() == null || s.index >= table.seatColors().size()
                ? s.index : table.seatColors().get(s.index);
            o.put("color", color);
            list.add(o);
        }
        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("players", seats.length);
        tab.put("ruleset", table.rulesetId());
        tab.put("map", table.scenarioId());
        return msg(NetProtocol.LOBBY, "seats", list, "table", tab,
            "canStart", whyNotStart() == null, "started", started);
    }

    private void changed() {
        if (!started) {
            broadcast(lobbyMessage());
        }
        onChange.run();
    }

    // ==================== старт ====================

    /**
     * НАЧАТЬ: места закрываются, сетевым местам заводятся агенты, всем уходит
     * {@code start}. Возвращает настройки партии для окна хоста — места
     * {@code net:N} окно посадит через {@link NetSeats}.
     */
    public HotSeatWindow.Options begin() {
        List<String> specs = new ArrayList<>();
        synchronized (this) {
            if (started) {
                return startedOptions;
            }
            String why = whyNotStart();
            if (why != null) {
                throw new IllegalStateException(why);
            }
            for (Seat s : seats) {
                switch (s.kind) {
                    case HOST -> specs.add("human");
                    case REMOTE -> {
                        specs.add(NetSeats.PREFIX + s.index);
                        s.agent = new RemoteAgent(this, s.index, s.name);
                    }
                    default -> specs.add(s.bot);
                }
            }
            started = true;
            HotSeatWindow.Options t = table;
            startedOptions = new HotSeatWindow.Options(t.rulesetId(), t.players(), t.seed(), specs,
                t.scenarioId(), t.scenarioFile(), t.cuFacing(), t.seatColors(),
                t.startCoins(), t.startKelium(), t.startAmmo(), t.prepRound(), t.marketCards());
        }
        for (Seat s : seats) {
            if (s.kind == Kind.REMOTE && s.wire != null) {
                s.wire.send(startMessage(s.index));
            }
        }
        NetSeats.bind(this);
        log.accept("партия начата");
        onChange.run();
        return startedOptions;
    }

    private Map<String, Object> startMessage(int seat) {
        List<Object> names = new ArrayList<>();
        for (Seat s : seats) {
            names.add(s.name == null ? "Место " + (s.index + 1) : s.name);
        }
        return msg(NetProtocol.START, "seat", seat, "players", seats.length, "names", names);
    }

    /** Агент сетевого места (после {@link #begin}); null — место не сетевое. */
    public RemoteAgent agent(int seat) {
        return seat >= 0 && seat < seats.length ? seats[seat].agent : null;
    }

    /** Имя места для окна партии. */
    public String seatName(int seat) {
        Seat s = seats[seat];
        return s.name == null ? "Место " + (seat + 1) : s.name;
    }

    // ==================== партия: кадры и вопросы ====================

    /**
     * НОВЫЕ КАДРЫ ЗАПИСИ — зовётся на потоке движка на каждое событие. Кадры
     * копируются в зеркало; рассылка — не чаще раза в {@link #FLUSH_MS}.
     * Новая запись (окно хоста откатило партию и переигрывает её) — всем
     * заново вся запись.
     */
    public void onFrame(ReplayRecord r) {
        gate();
        framesSeen++;
        mirror(r);
    }

    /** Кадры — в зеркало и, если пора, по местам. */
    private void mirror(ReplayRecord r) {
        synchronized (this) {
            if (r != head) {
                head = r;
                frames.clear();
                for (Seat s : seats) {
                    s.sentFrames = 0;
                }
            }
            for (int i = frames.size(); i < r.frames.size(); i++) {
                frames.add(r.frames.get(i));
            }
            plays = new ArrayList<>(r.orderPlays);
            dirty = true;
            if (System.currentTimeMillis() - lastFlush >= FLUSH_MS) {
                flush();
            }
        }
    }

    /** Как часто рассылать кадры: чаще глаз не заметит, а трафик растёт. */
    static final long FLUSH_MS = 150;

    private void pumpLoop() {
        while (!finished) {
            try {
                Thread.sleep(FLUSH_MS);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (this) {
                if (dirty) {
                    flush();
                }
            }
        }
    }

    /** Разослать всем сетевым местам ещё не отправленные кадры. Под замком. */
    private void flush() {
        lastFlush = System.currentTimeMillis();
        dirty = false;
        if (head == null) {
            return;
        }
        for (Seat s : seats) {
            if (s.kind == Kind.REMOTE) {
                sendFrames(s);
            }
        }
    }

    private void sendFrames(Seat s) {
        Wire w = s.wire;
        if (w == null || head == null) {
            return;
        }
        int from = s.sentFrames;
        int to = frames.size();
        if (to <= from) {
            return;
        }
        Map<String, Object> chunk = Redact.chunk(head, frames.subList(from, to), plays, s.index,
            from == 0);
        if (w.send(msg(NetProtocol.RECORD, "reset", from == 0, "from", from, "chunk", chunk))) {
            s.sentFrames = to;
        }
    }

    /** Место просит всю запись заново (или вернулось после обрыва). */
    private void resync(Seat s) {
        synchronized (this) {
            s.sentFrames = 0;
            sendFrames(s);
            RemoteAgent a = s.agent;
            Map<String, Object> q = a == null ? null : a.pendingMessage();
            if (q != null && s.wire != null) {
                s.wire.send(q);
            }
        }
    }

    /** Вопрос месту: сначала досылаются кадры, потом сам вопрос. */
    void sendDecide(int seat, Map<String, Object> msg) {
        synchronized (this) {
            flush();
            Wire w = seats[seat].wire;
            if (w != null) {
                w.send(msg);
            }
        }
    }

    void sendError(int seat, String text) {
        Wire w = seats[seat].wire;
        if (w != null) {
            w.send(msg(NetProtocol.ERROR, "text", text));
        }
    }

    /** Вопрос словами — те же подписи, что в окне партии горячего стула. */
    Map<String, Object> decideMessage(int seat, int seq, GameState state, List<Choice> options,
                                      Map<String, Object> context) {
        String kind = String.valueOf(context.get("kind"));
        Map<String, String> names;
        synchronized (this) {
            names = head == null ? Map.of() : head.cardNames;
        }
        List<Object> opts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Choice c = options.get(i);
            String label;
            String sub;
            try {
                label = ChoiceWords.label(kind, c, id -> names.getOrDefault(id, id));
                sub = ChoiceWords.sub(kind, c);
            } catch (RuntimeException e) {
                label = c.label() == null || c.label().isEmpty() ? String.valueOf(c.payload())
                    : c.label();
                sub = null;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("i", i);
            o.put("kind", c.kind());
            o.put("label", label);
            if (sub != null && !sub.isBlank()) {
                o.put("sub", sub);
            }
            String hex = hexOf(kind, c);
            if (hex != null) {
                o.put("hex", hex);        // вариант выбирается щелчком по гексу
            }
            opts.add(o);
        }
        Map<String, Object> facing = facingOf(kind, options, context);
        Object view = null;
        try {
            view = Json.parse(PublicView.of(state, seat).toJson());
        } catch (RuntimeException e) {
            log.accept("вид стола для места " + (seat + 1) + " не собрался: " + e);
        }
        return msg(NetProtocol.DECIDE, "seq", seq, "kind", kind,
            "prompt", HotSeatWindow.kindLabel(kind), "round", state.round, "circle", state.circle,
            "undo", undoTargets(seat, state.round, state.circle).size(),
            "options", opts, "facing", facing, "view", view);
    }

    /**
     * ВЫБОР СЕКТОРОВ НА ГЕКСЕ (поворот здания, центра управления, сектор
     * войска): гекс, дуги секторов по вариантам и призрак — клиент ставит
     * щелчком по полю, как окно партии. null — вопрос не такой.
     */
    static Map<String, Object> facingOf(String kind, List<Choice> options,
                                        Map<String, Object> context) {
        if (!("build_facing".equals(kind) || "cu_sides".equals(kind)
                || "unit_sector".equals(kind)) || !(context.get("hex") instanceof String hex)) {
            return null;
        }
        List<Object> variants = new ArrayList<>();
        for (Choice c : options) {
            if (!(c.payload() instanceof List<?> l)) {
                return null;
            }
            List<Object> sides = new ArrayList<>();
            for (Object o : l) {
                if (!(o instanceof Number n)) {
                    return null;
                }
                sides.add(n.intValue());
            }
            variants.add(sides);
        }
        if (variants.isEmpty()) {
            return null;
        }
        String ghost = context.get("btype") instanceof String bt ? bt
            : context.get("utype") instanceof String ut ? ut
            : "cu_sides".equals(kind) ? "command_center" : null;
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("hex", hex);
        f.put("variants", variants);
        f.put("ghost", ghost);
        return f;
    }

    private static final java.util.regex.Pattern HEX_ID = java.util.regex.Pattern.compile(
        "h-?\\d+_-?\\d+");

    /** Гекс варианта — по тем же признакам, что в окне партии ({@code hexIdOf}). */
    static String hexOf(String kind, Choice c) {
        if (c.payload() instanceof String s && HEX_ID.matcher(s).matches()) {
            return s;
        }
        if ("move".equals(kind) && c.payload() instanceof Map<?, ?> m
                && m.get("to") instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * КОНЕЦ ПАРТИИ: последние кадры, итог, и теперь — сид и лента решений
     * (партию можно пересмотреть и проверить у себя).
     */
    public void finish(ReplayRecord rec, List<Integer> moves) {
        synchronized (this) {
            if (rec != null) {
                mirror(rec);
            }
            flush();
            finished = true;
        }
        Map<String, Object> over = msg(NetProtocol.OVER,
            "winner", rec == null ? null : rec.winner,
            "condition", rec == null ? null : rec.condition,
            "rounds", rec == null ? 0 : rec.rounds,
            "seed", table.seed(), "moves", moves == null ? List.of() : moves);
        broadcast(over);
        log.accept("партия окончена");
        onChange.run();
    }

    /** Закрыть стол: агенты размыкаются, провода рвутся, порт закрывается. */
    public void close() {
        boolean midGame = started && !finished;
        closed = true;
        finished = true;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        if (midGame) {
            broadcast(msg(NetProtocol.CLOSED));
        }
        for (Seat s : seats) {
            RemoteAgent a = s.agent;
            if (a != null) {
                a.abort();
            }
            Wire w = s.wire;
            if (w != null) {
                w.send(msg(NetProtocol.BYE));
                w.close();
            }
        }
        try {
            ServerSocket ss = server;
            if (ss != null) {
                ss.close();
            }
        } catch (IOException ignored) {
            // и так закрыт
        }
        NetSeats.unbind(this);
    }

    private void broadcast(Map<String, Object> m) {
        for (Seat s : seats) {
            Wire w = s.wire;
            if (s.kind == Kind.REMOTE && w != null) {
                w.send(m);
            }
        }
    }

    // ==================== отвал игрока: пауза ====================

    /**
     * ИГРОК ВЫШЕЛ ИЗ ИДУЩЕЙ ПАРТИИ (решение Влада 26.09.2026): партия встаёт на
     * паузу. У хоста — шторка с выбором «ждать / боту / закрыть», у остальных —
     * «игрок вышел, ждём решения хоста». Пауза держится на потоке движка:
     * каждый кадр партии проходит {@link #gate()}.
     */
    private void left(int seat) {
        synchronized (pauseLock) {
            if (!away.add(seat)) {
                return;
            }
        }
        log.accept("партия на паузе: " + seatName(seat) + " вышел из игры");
        pauseChanged(seat, null);
    }

    /** Игрок вернулся на своё место — пауза по нему снимается. */
    private void back(int seat) {
        synchronized (pauseLock) {
            waiting.remove(seat);
            if (!away.remove(seat)) {
                return;
            }
            pauseLock.notifyAll();
        }
        log.accept("вернулся в партию: " + seatName(seat));
        pauseChanged(seat, "back");
    }

    /** Хост решил ждать игрока: шторка остаётся с пометкой ожидания. */
    public void waitFor(int seat) {
        synchronized (pauseLock) {
            if (!away.contains(seat) || !waiting.add(seat)) {
                return;
            }
        }
        log.accept("ждём игрока: " + seatName(seat));
        pauseChanged(seat, null);
    }

    /**
     * ОТДАТЬ МЕСТО УШЕДШЕГО БОТУ — как окно партии сажает бота на место: тот
     * же справочник и то же зерно места. Бот отвечает и на уже висящий вопрос;
     * вернуться на это место игрок больше не сможет (по его токену — отказ
     * «место отдано боту»).
     */
    public void replaceWithBot(int seat) {
        Seat s = seats[seat];
        RemoteAgent a = s.agent;
        synchronized (this) {
            if (s.kind != Kind.REMOTE || a == null) {
                return;
            }
            s.kind = Kind.BOT;
            s.bot = STAND_IN_BOT;
            s.ready = true;
            Wire w = s.wire;
            s.wire = null;
            if (w != null) {
                reject(w, "хост отдал ваше место боту");
            }
        }
        a.useBot(kelium.agents.BotCatalog.create(STAND_IN_BOT, seat,
            new Random(table.seed() * 131 + seat + 1), seats.length));
        synchronized (pauseLock) {
            away.remove(seat);
            waiting.remove(seat);
            pauseLock.notifyAll();
        }
        log.accept("место " + (seat + 1) + " (" + s.name + ") отдано боту");
        pauseChanged(seat, "bot");
        onChange.run();
    }

    /** Хост закрыл партию: игрокам — «хост закрыл партию», стол закрывается. */
    public void closeGame() {
        log.accept("хост закрыл партию");
        close();
        firePause();
    }

    /** Первое вышедшее место (по нему шторка); null — паузы нет. */
    public Integer awaySeat() {
        synchronized (pauseLock) {
            return away.isEmpty() ? null : away.iterator().next();
        }
    }

    /** Хост решил ждать это место. */
    public boolean waitingFor(int seat) {
        synchronized (pauseLock) {
            return waiting.contains(seat);
        }
    }

    /** Закрыл ли хост стол посреди партии. */
    public boolean closedByHost() {
        return closed;
    }

    /** Сообщение о текущей паузе; null — паузы нет. */
    private Map<String, Object> pauseMessage() {
        Integer first = awaySeat();
        return first == null || closed ? null : msg(NetProtocol.PAUSE, "seat", first,
            "name", seatName(first), "waiting", waitingFor(first));
    }

    /** Разослать новое состояние паузы: следующее вышедшее место или «продолжаем». */
    private void pauseChanged(int seat, String how) {
        Map<String, Object> p = pauseMessage();
        broadcast(p != null ? p : msg(NetProtocol.RESUME, "seat", seat,
            "how", how == null ? "back" : how));
        firePause();
    }

    private void firePause() {
        for (Runnable r : pauseListeners) {
            r.run();
        }
    }

    /**
     * ПАУЗА НА ПОТОКЕ ДВИЖКА: пока кто-то вышел, кадр партии не проходит, и
     * движок стоит. Стол закрыт — партия размыкается {@link GameAborted}.
     */
    private void gate() {
        synchronized (pauseLock) {
            while (!away.isEmpty() && !closed) {
                try {
                    pauseLock.wait(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GameAborted("пауза прервана");
                }
            }
        }
        if (closed) {
            throw new GameAborted("хост закрыл партию");
        }
    }

    /** Сколько кадров движок уже отдал (для проверки паузы). */
    int framesSeen() {
        return framesSeen;
    }

    // ==================== отмена своих решений ====================

    /** Ведущий партию: лента решений и отмена (окно хоста или {@link NetGame}). */
    void link(NetSeats.Link l) {
        link = l;
    }

    /** Куда месту можно откатиться сейчас (номера решений в ленте). */
    List<Integer> undoTargets(int seat, int round, int circle) {
        NetSeats.Link l = link;
        return l == null ? List.of() : NetSeats.undoTargets(l.steps(), seat, round, circle);
    }

    /**
     * ОТМЕНА СО СТОРОНЫ КЛИЕНТА (решение Влада 26.09.2026): свои решения — как
     * за горячим стулом, но не глубже первого чужого после них. Отменять
     * можно, пока месту задан вопрос: партия откатывается к его решению и
     * переигрывается по ленте, всем уходит запись заново.
     */
    private void undo(Seat s, int seq, boolean all) {
        RemoteAgent a = s.agent;
        NetSeats.Link l = link;
        if (a == null || l == null || a.botted() || awaySeat() != null || closed || finished) {
            sendError(s.index, "отменить сейчас нельзя");
            return;
        }
        if (a.pendingSeq() != seq) {
            return;                      // вопрос уже не тот — запоздалая кнопка
        }
        List<Integer> t = undoTargets(s.index, a.pendingRound(), a.pendingCircle());
        if (t.isEmpty()) {
            sendError(s.index, "нечего отменять: после вашего решения ходил другой игрок");
            Map<String, Object> q = a.pendingMessage();
            if (s.wire != null && q != null) {
                s.wire.send(q);
            }
            return;
        }
        if (!a.claimUndo(seq)) {
            return;
        }
        int to = all ? t.get(0) : t.get(t.size() - 1);
        log.accept(s.name + " отменяет " + (all ? "ход до начала" : "шаг"));
        l.undoTo(to, () -> a.cancel(seq));
    }

    // ==================== окно партии хоста ====================

    /**
     * Окно партии хоста подключилось: лента и отмена — от него; поверх окна —
     * шторка паузы и чат. Закрыли окно посреди партии — стол закрывается,
     * игрокам уходит «хост закрыл партию».
     */
    void attachWindow(javax.swing.JFrame frame, NetSeats.Link l) {
        link(l);
        javax.swing.SwingUtilities.invokeLater(() -> {
            NetOverlay overlay = new NetOverlay(frame);
            NetChatDock chat = new NetChatDock(frame, this::say);
            overlay.allow(chat);
            // снизу окна партии — стол игрока: чат встаёт над ним, в угол поля
            javax.swing.JSplitPane split = findSplit(frame.getContentPane());
            if (split != null && split.getBottomComponent() != null) {
                chat.anchorAbove(split.getBottomComponent());
            }
            for (String line : chatLog()) {
                chat.add(line);
            }
            chatListeners.add(line -> javax.swing.SwingUtilities.invokeLater(() -> chat.add(line)));
            Runnable render = () -> javax.swing.SwingUtilities.invokeLater(
                () -> renderHostPause(overlay, l));
            pauseListeners.add(render);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    if (!finished) {
                        closeGame();
                    }
                }
            });
            render.run();
        });
    }

    /** Разделитель «поле / стол игрока» в окне партии; null — нет такого. */
    private static javax.swing.JSplitPane findSplit(java.awt.Container c) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.JSplitPane sp
                    && sp.getOrientation() == javax.swing.JSplitPane.VERTICAL_SPLIT) {
                return sp;
            }
            if (k instanceof java.awt.Container cc) {
                javax.swing.JSplitPane in = findSplit(cc);
                if (in != null) {
                    return in;
                }
            }
        }
        return null;
    }

    /** Шторка хоста: кто вышел и три выхода — ждать, бот, закрыть. */
    private void renderHostPause(NetOverlay overlay, NetSeats.Link l) {
        Integer seat = awaySeat();
        if (closed || seat == null) {
            overlay.dismiss();
            return;
        }
        int st = seat;
        boolean w = waitingFor(st);
        overlay.display("Игрок " + (st + 1) + " (" + seatName(st) + ") вышел из игры",
            w ? "Ждём, когда игрок вернётся. Можно передумать." : "Партия на паузе. Что делать?",
            List.of(
                new NetOverlay.Action("Ждать игрока",
                    w ? "ждём…" : "вернётся по тому же адресу", () -> waitFor(st), !w, w),
                new NetOverlay.Action("Заменить ботом", "место доиграет бот",
                    () -> replaceWithBot(st), false, false),
                new NetOverlay.Action("Закрыть партию", "у всех партия закончится",
                    () -> {
                        closeGame();
                        overlay.dismiss();
                        l.exit();
                    }, false, false)));
    }

    /** Трафик к месту (для замеров). */
    public long sentBytes(int seat) {
        Wire w = seats[seat].wire;
        return w == null ? 0 : w.sentBytes();
    }

    public List<String> chatLog() {
        return List.copyOf(chatLog);
    }

    /**
     * АДРЕСА ЭТОГО КОМПЬЮТЕРА для друзей: локальная сеть и виртуальные
     * (Radmin VPN, ZeroTier, Tailscale) — все IPv4, кроме петли.
     */
    public static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLinkLocalAddress()) {
                        out.add(a.getHostAddress());
                    }
                }
            }
        } catch (IOException e) {
            // без адресов — покажем петлю
        }
        if (out.isEmpty()) {
            out.add("127.0.0.1");
        }
        // Домашняя сеть и Radmin/Tailscale — вперёд; 172.16–31 чаще всего
        // виртуальные адаптеры Hyper-V/WSL/Docker — в конец.
        out.sort(java.util.Comparator.comparingInt(NetHost::addressRank));
        return out;
    }

    private static int addressRank(String a) {
        if (a.startsWith("192.168.") || a.startsWith("26.") || a.startsWith("100.")) {
            return 0;
        }
        if (a.startsWith("10.")) {
            return 1;
        }
        String[] p = a.split("\\.");
        if (p.length == 4 && "172".equals(p[0])) {
            int b = Integer.parseInt(p[1]);
            if (b >= 16 && b <= 31) {
                return 3;
            }
        }
        return 2;
    }
}
