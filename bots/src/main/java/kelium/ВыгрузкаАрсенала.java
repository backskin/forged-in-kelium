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
import kelium.cards.arsenal.КартаАрсеналаВКоде;
import kelium.engine.cards.Card;

/**
 * ВЫГРУЗКА КОЛОДЫ АРСЕНАЛА ИЗ КОДА В YAML.
 *
 * <p>ПОЧЕМУ ОТДЕЛЬНО ОТ {@link ВыгрузкаКаталога}, ровно как у рынка. Общая
 * выгрузка идёт по записям УЖЕ ПОДКЛЮЧЁННОГО набора и накрывает их классами:
 * восьми новых карт, которых в подключённом наборе ещё нет, она не видит.
 * Новый набор рождается целиком в коде, поэтому пишется прямо из списка карт.
 *
 * <p>СОСТАВ ЗАПИСИ: сорок обычных карт {@code a6_*} из {@link Арсенал6} и восемь
 * стартовых {@code bs1}–{@code bs8}, которые не менялись и лежат своим классом.
 *
 * <p>Запуск: {@code kelium.ВыгрузкаАрсенала 6.0.0 "почему выгружено"}
 */
public final class ВыгрузкаАрсенала {

    private ВыгрузкаАрсенала() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        String версия = args.length > 0 ? args[0] : "6.0.0";
        String зачем = args.length > 1 ? args[1]
            : "арсенал 6.0.0: сорок карт, одно имя на один низ";

        List<Card> все = new ArrayList<>(Арсенал6.карты());
        все.addAll(стартовые());

        List<Map<String, Object>> записи = new ArrayList<>();
        for (Card c : все) {
            if (c instanceof КартаАрсеналаВКоде k) {
                записи.add(new LinkedHashMap<>(k.лицо().выгрузить(k.id())));
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", версия);
        meta.put("type", "arsenal");
        meta.put("выгружено", зачем);
        Map<String, Object> корень = new LinkedHashMap<>();
        корень.put("meta", meta);
        корень.put("arsenal", записи);

        var опции = new org.yaml.snakeyaml.DumperOptions();
        опции.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        опции.setAllowUnicode(true);
        опции.setWidth(110);
        String тело = new org.yaml.snakeyaml.Yaml(опции).dump(корень);

        String шапка = """
            # CONTENT: arsenal  version %s
            # ============================================================================
            #  ВЫГРУЖЕНО ИЗ КОДА — файл является ЗЕРКАЛОМ классов карт.
            #  %s
            #
            #  Править этот файл руками бессмысленно: движок накрывает запись каталога
            #  выгрузкой класса. Менять надо класс kelium.cards.arsenal.Арсенал6,
            #  а затем выгружать заново (kelium.ВыгрузкаАрсенала).
            # ============================================================================
            """.formatted(версия, зачем);

        Path цель = Path.of("data/cards/arsenal." + версия + ".yaml");
        Files.writeString(цель, шапка + тело, StandardCharsets.UTF_8);
        System.out.println("Записано: " + цель.toAbsolutePath()
            + " (карт " + записи.size() + ")");
    }

    /** Восемь стартовых карт: они не менялись и живут своими классами. */
    private static List<Card> стартовые() {
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
