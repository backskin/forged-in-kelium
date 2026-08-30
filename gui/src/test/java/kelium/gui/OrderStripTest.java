package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.report.ReplayRecord;

/**
 * РАЗЫГРАННЫЕ ПРИКАЗЫ на экране (просьба дизайнера 12.08.2026): запись должна
 * нести раскладку каждой карты — что доступно сверху, срезало ли совпадение два
 * действия до одного, открылась ли нижняя половина, — и всё это должно
 * сохраняться вместе с партией.
 */
class OrderStripTest {

    private static ReplayRecord recordWithOneTurn() {
        ReplayRecord rec = new ReplayRecord();
        rec.players = 4;

        ReplayRecord.OrderPlay op = new ReplayRecord.OrderPlay();
        op.seat = 0;
        op.round = 2;
        op.circle = 1;
        op.revealFrame = 0;
        op.turnFrame = 0;
        op.card = "o07";
        op.top = "development";
        op.topActions.addAll(List.of("assembly", "mining"));
        op.topAllowed = 1;
        op.coincided = true;
        op.bottom = "acquisitions";
        op.bottomActions.addAll(List.of("market", "science"));
        op.bottomOpen = true;
        op.maneuver = true;
        rec.orderPlays.add(op);

        // кадр начала хода + одно сыгранное действие
        ReplayRecord.Frame f0 = new ReplayRecord.Frame();
        f0.type = "turn_orders";
        f0.round = 2;
        f0.seat = 0;
        f0.snapshot = new ReplayRecord.Snapshot();
        rec.frames.add(f0);

        ReplayRecord.Frame f1 = new ReplayRecord.Frame();
        f1.type = "action";
        f1.round = 2;
        f1.seat = 0;
        f1.log = "   ▪ СНАРЯЖЕНИЕ: собрал пехоту";
        f1.snapshot = new ReplayRecord.Snapshot();
        rec.frames.add(f1);
        return rec;
    }

    @Test
    void stripShowsOnlyThisRoundAndOnlyAlreadyRevealedCards() {
        ReplayRecord rec = recordWithOneTurn();
        OrderStrip strip = new OrderStrip(0);
        strip.setSize(400, 120);

        // на нулевом кадре карта уже вскрыта — рисуем её
        strip.update(rec, 0);
        assertTrue(strip.cardsShown() == 1, "карта текущего раунда показана");

        // у чужого места своих карт нет
        OrderStrip other = new OrderStrip(1);
        other.update(rec, 1);
        assertEquals(0, other.cardsShown(), "карты соседа сюда попадать не должны");
    }

    @Test
    void playedActionIsMarkedAndTheRestStayAvailable() {
        ReplayRecord rec = recordWithOneTurn();
        OrderStrip strip = new OrderStrip(0);

        strip.update(rec, 0);
        assertTrue(strip.usedOn(0).isEmpty(), "до действий не сыграно ничего");

        strip.update(rec, 1);
        assertEquals(List.of("assembly"), strip.usedOn(0),
            "сборка отмечена как сыгранная, добыча осталась доступной");
        assertFalse(strip.usedOn(0).contains("mining"));
    }

    @Test
    void orderLayoutSurvivesSaveAndLoad() throws Exception {
        ReplayRecord rec = recordWithOneTurn();
        Path tmp = Files.createTempFile("kelium-orders", ".json");
        try {
            Files.writeString(tmp, kelium.report.Json.write(rec.toMap()));
            ReplayRecord back = ReplayRecord.load(tmp);
            assertEquals(1, back.orderPlays.size());
            ReplayRecord.OrderPlay op = back.orderPlays.get(0);
            assertEquals("development", op.top);
            assertEquals(1, op.topAllowed, "совпадение срезало два действия до одного");
            assertTrue(op.coincided);
            assertEquals("acquisitions", op.bottom);
            assertTrue(op.bottomOpen, "нижняя половина открылась");
            assertTrue(op.maneuver);
            assertEquals(List.of("assembly", "mining"), new ArrayList<>(op.topActions));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
