package kelium.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link InteractiveAgent}, который умеет откатывать ПОСЛЕДНЕЕ завершённое
 * БЕЗОПАСНОЕ действие своего хода — заказ дизайнера (24.08.2026): «те действия
 * игрока, что не влияют на других игроков и не меняют состояние поля в смысле
 * уничтожения/подбора — можно пробовать в любом порядке и откатывать».
 *
 * <p><b>Что именно безопасно и почему</b> — разобрано вручную по коду, а не
 * угадано: {@code Actions.java}/{@code Modules.java} НЕ рассылают публичных
 * событий ВНУТРИ Стройки/Добычи/Манёвра/Смены энергии/Сборки (каждый выбор —
 * {@code build_hex}/{@code move}/{@code module_place_*} и т.п. — виден только
 * тому, кто выбирает). Наружу, в {@code GameEngine.playActions}, ОДНО обобщённое
 * событие {@code type=action} шлётся ПОСЛЕ того, как всё действие целиком
 * отыграно (строка "emit(ev("type", "action", ...))") — но ни один бот на это
 * событие не реагирует: {@code HumanLikeAgent.observePublicEvent} слушает
 * только {@code combat_hit}/{@code refresh}, {@code SearchAgent} — только
 * {@code reveal}. Значит откат ДО следующего такого события не оставляет следа
 * ни в чьём поведении. Бой (не входит в {@link #SAFE_ACTIONS}) — наоборот,
 * рассылает события ПРЯМО ПО ХОДУ боя ({@code combat_hit} и т.п., см.
 * {@code CombatResolver}), и {@code HumanLikeAgent} на них реагирует (обида) —
 * откатить бой значило бы врать боту про то, что он уже «видел».
 *
 * <p><b>Известный пробел (не критично, но честно):</b> если это действие
 * записывается ({@code GameRecorder}), то ОДНА строка журнала («сыграно
 * действие Х») переживёт откат как призрак — сам игровой РЕЗУЛЬТАТ откатывается
 * полностью, но кадр в записи об отменённой попытке останется. Подрезка записи
 * при откате — отдельная, не сделанная пока часть (живёт в GUI-слое, у
 * {@code UndoableAgent} нет и не должно быть ссылки на {@code ReplayRecord}).
 *
 * <p><b>Почему откат безопасен только НА ГРАНИЦЕ действия</b> (между вызовами
 * {@link #choose}, ответ на которые — {@code kind=="action"}), а не в середине:
 * код одного действия держит СВОЮ ссылку на {@code PlayerState} через
 * НЕСКОЛЬКО вложенных вызовов {@code choose} подряд (сперва «что строить»,
 * потом «где», потом «какой стороной») — подменить состояние ПОД этой ссылкой
 * значило бы, что дальнейшие правки того же вызова применятся к уже
 * отброшенному объекту и молча потеряются. К моменту, когда движок СНОВА
 * спрашивает {@code kind=="action"}, весь стек предыдущего действия уже
 * развёрнут — тут откат безопасен, см. {@link GameState#restoreFrom}.
 */
public final class UndoableAgent extends Agent {

    /**
     * Названия действий (сама строка — {@code Choice("action", name, name)} в
     * {@code GameEngine.playActions}), после которых остаётся смысл предлагать
     * откат. Список СОЗНАТЕЛЬНО не включает {@code combat}/{@code market}/
     * {@code science}: бой уже публично виден по ходу (см. javadoc класса);
     * рынок и наука могут задеть общие ячейки/треки, которые тоже читает
     * решение ДРУГОГО игрока в его будущий ход — цена ошибки там выше, чем
     * выгода, и в первую версию отката они сознательно не включены.
     */
    public static final Set<String> SAFE_ACTIONS = Set.of(
        "build", "mining", "movement", "energy_swap", "assembly");

    private final GameState state;
    private final InteractiveAgent delegate;

    private volatile GameState snapshot;
    private volatile long pinnedSeed;
    private volatile String snapshotLabel;

    public UndoableAgent(int seat, String name, GameState state,
                          Consumer<InteractiveAgent.PendingDecision> onDecision,
                          Consumer<Map<String, Object>> onPublicEvent) {
        super(seat, name);
        this.state = state;
        this.delegate = new InteractiveAgent(seat, name, onDecision, onPublicEvent);
    }

    @Override
    public Choice choose(GameState s, List<Choice> options, Map<String, Object> context) {
        Choice pick = delegate.choose(s, options, context);
        if ("action".equals(String.valueOf(context.get("kind")))
                && pick.payload() instanceof String actionName
                && SAFE_ACTIONS.contains(actionName)) {
            // Снимок — ПРЯМО ПЕРЕД тем, как это действие начнёт что-то менять
            // (само действие ещё не выполнилось: pick только сейчас уходит
            // обратно в движок). Один зафиксированный сид ГСЧ — чтобы снимок
            // и живая партия были синхронны и откат не сдвигал случайность
            // партии относительно её исходного сида (см. GameState#restoreFrom).
            pinnedSeed = state.rng.nextLong();
            state.rng.setSeed(pinnedSeed);
            snapshot = state.deepCopy(pinnedSeed);
            snapshotLabel = pick.label();
        }
        return pick;
    }

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        delegate.observePublicEvent(event);
    }

    public InteractiveAgent.PendingDecision pending() {
        return delegate.pending();
    }

    public void submitIndex(int index) {
        delegate.submitIndex(index);
    }

    public void submitChoice(Choice choice) {
        delegate.submitChoice(choice);
    }

    /** Есть ли что откатывать прямо сейчас. */
    public boolean canUndo() {
        return snapshot != null;
    }

    /** Название последнего безопасного действия — подпись на кнопку отмены. */
    public String undoLabel() {
        return snapshotLabel;
    }

    /**
     * Откатить последнее безопасное действие. Разово: второй {@link #undo()}
     * без нового безопасного действия между ними бросит исключение — стека
     * отмен пока нет, откатывается только самое последнее.
     *
     * <p>Вызывающему (GUI) на заметку: точка решения, ждущая ответа ПРЯМО
     * СЕЙЧАС (та, из-за которой вообще стало можно вызвать undo — движок
     * снова спросил {@code kind=="action"}), была посчитана ДО отката и может
     * не совсем точно отражать восстановленное состояние (например, действие,
     * которое стало снова доступным после отмены, в её вариантах не появится).
     * Расхождение живёт максимум один шаг: движок пересчитывает варианты с нуля
     * на следующей же точке решения.
     */
    public void undo() {
        GameState snap = snapshot;
        if (snap == null) {
            throw new IllegalStateException("Место " + seat + ": нет действия для отмены");
        }
        state.restoreFrom(snap, pinnedSeed);
        snapshot = null;
        snapshotLabel = null;
    }
}
