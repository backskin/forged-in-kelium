package kelium.core;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/**
 * Восстановить объект «НА МЕСТЕ» (тот же экземпляр, не новый) из другого —
 * поле за полем, через рефлексию.
 *
 * <p>Нужно откату действия ({@link UndoableAgent}/{@link GameState#restoreFrom}):
 * замена {@link PlayerState}/{@link Hex} НОВЫМ объектом вместо восстановления
 * старого на месте ломает код действия, который держит свою ссылку на игрока
 * через несколько вызовов {@code Agent.choose} подряд (обнаружено тестом
 * {@code GameStateRestoreFromTest} — {@code GameEngine.playActions} держит
 * {@code PlayerState p} параметром на ВЕСЬ ход, не на одно действие).
 *
 * <p>Почему рефлексией, а не построчным списком полей вручную: у {@link
 * PlayerState} около полусотни полей, и новые правила прибавляют их регулярно
 * (см. историю коммитов) — пропущенное поле в написанном руками списке не
 * бросит исключение, оно просто молча не откатится, и такой баг не поймать
 * иначе, чем построчным сравнением при каждой новой карте/правиле. Рефлексия
 * копирует ВСЕ поля класса автоматически, включая те, что появятся позже.
 *
 * <p>Изменяемые контейнеры (List/Map/Set) очищаются и заполняются заново их
 * содержимым — сохраняя СВОЙ объект контейнера (на случай, если что-то ещё
 * держит на него ссылку); массивы копируются поэлементно по той же причине.
 * Скаляры и ссылки на прочие объекты — обычным присваиванием.
 */
final class StateRestore {

    private StateRestore() {
    }

    @SuppressWarnings("unchecked")
    static void copyFields(Object target, Object source) {
        if (target.getClass() != source.getClass()) {
            throw new IllegalArgumentException("не совпадают классы для восстановления: "
                + target.getClass() + " vs " + source.getClass());
        }
        for (Field f : target.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object sv = f.get(source);
                if (f.getType().isArray()) {
                    Object tv = f.get(target);
                    if (sv != null && tv != null) {
                        System.arraycopy(sv, 0, tv, 0, Array.getLength(tv));
                    }
                } else if (Map.class.isAssignableFrom(f.getType())) {
                    Map<Object, Object> tv = (Map<Object, Object>) f.get(target);
                    tv.clear();
                    if (sv != null) {
                        tv.putAll((Map<Object, Object>) sv);
                    }
                } else if (Collection.class.isAssignableFrom(f.getType())) {
                    Collection<Object> tv = (Collection<Object>) f.get(target);
                    tv.clear();
                    if (sv != null) {
                        tv.addAll((Collection<Object>) sv);
                    }
                } else {
                    f.set(target, sv);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("не удалось скопировать поле "
                    + f.getName() + " у " + target.getClass(), e);
            }
        }
    }
}
