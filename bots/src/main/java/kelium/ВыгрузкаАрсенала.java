package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.cards.arsenal.АрсеналСтартовые;
import kelium.cards.arsenal.Арсенал6;
import kelium.cards.arsenal.Арсенал7;
import kelium.cards.arsenal.КартаАрсеналаВКоде;
import kelium.cards.superarsenal.СуперАрсенал3;
import kelium.cards.superarsenal.СуперВКоде;
import kelium.engine.cards.Card;

/**
 * ВЫГРУЗКА КОЛОДЫ АРСЕНАЛА ИЗ КОДА В YAML.
 *
 * <p>ПОЧЕМУ ОТДЕЛЬНО ОТ {@link ВыгрузкаКаталога}, ровно как у рынка. Общая
 * выгрузка идёт по записям УЖЕ ПОДКЛЮЧЁННОГО набора и накрывает их классами:
 * новых карт, которых в подключённом наборе ещё нет, она не видит. Новый набор
 * рождается целиком в коде, поэтому пишется прямо из списка карт.
 *
 * <p>СОСТАВ ЗАПИСИ:
 * <ul>
 *   <li>6.0.0 — сорок обычных карт {@code a6_*} из {@link Арсенал6} и восемь
 *       стартовых {@code bs1}–{@code bs8};</li>
 *   <li>7.0.0 — тридцать две обычные {@code a7_*} и четыре начальные
 *       {@code bs7_*} из {@link Арсенал7} (печать дизайнера);</li>
 *   <li>{@code super 3.0.0} — четыре супер-войска {@code sa3_*} из
 *       {@link СуперАрсенал3} в {@code data/cards/super_arsenal.3.0.0.yaml}.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.ВыгрузкаАрсенала 7.0.0 "почему выгружено"} или
 * {@code kelium.ВыгрузкаАрсенала super 3.0.0 "почему выгружено"}.
 */
public final class ВыгрузкаАрсенала {

    private ВыгрузкаАрсенала() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length > 0 && "super".equals(args[0])) {
            String версия = args.length > 1 ? args[1] : "3.0.0";
            String зачем = args.length > 2 ? args[2]
                : "супер-арсенал 3.0.0: четыре супер-войска по печати дизайнера 17.09.2026";
            List<Map<String, Object>> записи = new ArrayList<>();
            for (Card c : СуперАрсенал3.карты()) {
                if (c instanceof СуперВКоде k) {
                    записи.add(new LinkedHashMap<>(k.data()));
                }
            }
            записать("super_arsenal", версия, зачем, записи,
                "kelium.cards.superarsenal.СуперАрсенал3");
            return;
        }
        String версия = args.length > 0 ? args[0] : "7.0.0";
        String зачем = args.length > 1 ? args[1]
            : "арсенал " + версия + ": колода по печати дизайнера";

        List<Card> все = new ArrayList<>();
        String класс;
        if (версия.startsWith("6.")) {
            все.addAll(Арсенал6.карты());
            все.addAll(стартовые6());
            класс = "kelium.cards.arsenal.Арсенал6";
        } else {
            все.addAll(Арсенал7.карты());
            все.addAll(Арсенал7.начальные());
            класс = "kelium.cards.arsenal.Арсенал7";
        }

        List<Map<String, Object>> записи = new ArrayList<>();
        for (Card c : все) {
            if (c instanceof КартаАрсеналаВКоде k) {
                записи.add(new LinkedHashMap<>(k.лицо().выгрузить(k.id())));
            }
        }
        записать("arsenal", версия, зачем, записи, класс);
    }

    private static void записать(String тип, String версия, String зачем,
                                 List<Map<String, Object>> записи, String класс)
            throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", версия);
        meta.put("type", тип);
        meta.put("выгружено", зачем);
        Map<String, Object> корень = new LinkedHashMap<>();
        корень.put("meta", meta);
        корень.put(тип, записи);

        var опции = new org.yaml.snakeyaml.DumperOptions();
        опции.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        опции.setAllowUnicode(true);
        опции.setWidth(110);
        String тело = new org.yaml.snakeyaml.Yaml(опции).dump(корень);

        String шапка = """
            # CONTENT: %s  version %s
            # ============================================================================
            #  ВЫГРУЖЕНО ИЗ КОДА — файл является ЗЕРКАЛОМ классов карт.
            #  %s
            #
            #  Править этот файл руками бессмысленно: движок накрывает запись каталога
            #  выгрузкой класса. Менять надо класс %s,
            #  а затем выгружать заново (kelium.ВыгрузкаАрсенала).
            # ============================================================================
            """.formatted(тип, версия, зачем, класс);

        Path цель = Path.of("data/cards/" + тип + "." + версия + ".yaml");
        Files.writeString(цель, шапка + тело, StandardCharsets.UTF_8);
        System.out.println("Записано: " + цель.toAbsolutePath()
            + " (карт " + записи.size() + ")");
    }

    /** Восемь стартовых карт набора 6.0.0: живут своими классами. */
    private static List<Card> стартовые6() {
        return List.of(
            new АрсеналСтартовые.ПолевойГенератор(),
            new АрсеналСтартовые.ОтветныйЗалп(),
            new АрсеналСтартовые.Мародёрка(),
            new АрсеналСтартовые.ШтабнаяПочта(),
            new АрсеналСтартовые.ПремияЗаГолову(),
            new АрсеналСтартовые.АварийныеЩиты(),
            new АрсеналСтартовые.Переформирование(),
            new АрсеналСтартовые.ВольнаяЗастройка());
    }
}
