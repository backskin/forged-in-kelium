package kelium.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Поля записи, добавленные под живой экран (концепт «Командный пункт», §8:
 * подложенные символы, щиты, задержанные трофеи), обязаны ПЕРЕЖИВАТЬ
 * сохранение и чтение. Классический тихий баг здесь — поле пишется в JSON,
 * но не читается обратно: журнал выглядит полным, а проигрыватель видит
 * пустоту. Ловится только круговой проверкой.
 */
class ReplayRecordNewFieldsRoundTripTest {

    @Test
    void tuckedShieldsAndHeldTrophiesSurviveSaveLoad() throws Exception {
        ReplayRecord rec = new ReplayRecord();
        rec.ruleset = "1.24.0";
        rec.players = 2;
        rec.seed = 7;
        rec.seatIds.add("human");
        rec.seatIds.add("balanced");

        ReplayRecord.Snapshot snap = new ReplayRecord.Snapshot();
        ReplayRecord.Player p = new ReplayRecord.Player();
        p.seat = 0;

        ReplayRecord.Tucked closed = new ReplayRecord.Tucked();
        closed.kind = "container";
        closed.cardId = "c7";
        ReplayRecord.Tucked open = new ReplayRecord.Tucked();
        open.kind = "arsenal";
        open.cardId = "bs3";
        open.revealed = true;
        p.tucked.add(closed);
        p.tucked.add(open);

        p.shieldedKinds.add("infantry");
        p.shieldedKinds.add("tank");

        ReplayRecord.TrophyToken held = new ReplayRecord.TrophyToken();
        held.uid = 42;
        held.owner = 1;
        held.type = "infantry";
        held.value = 1;
        p.trophyHeld.add(held);

        snap.players.add(p);
        ReplayRecord.Frame f = new ReplayRecord.Frame();
        f.type = "test";
        f.snapshot = snap;
        rec.frames.add(f);

        Path file = Files.createTempFile("kelium-roundtrip", ".json");
        try {
            rec.save(file);
            ReplayRecord back = ReplayRecord.load(file);
            ReplayRecord.Player q = back.frames.get(0).snapshot.players.get(0);

            assertEquals(2, q.tucked.size());
            assertEquals("container", q.tucked.get(0).kind);
            assertEquals("c7", q.tucked.get(0).cardId);
            assertTrue(!q.tucked.get(0).revealed);
            assertEquals("arsenal", q.tucked.get(1).kind);
            assertEquals("bs3", q.tucked.get(1).cardId);
            assertTrue(q.tucked.get(1).revealed);

            assertEquals(java.util.List.of("infantry", "tank"), q.shieldedKinds);

            assertEquals(1, q.trophyHeld.size());
            assertEquals(42, q.trophyHeld.get(0).uid);
            assertEquals(1, q.trophyHeld.get(0).owner);
            assertEquals("infantry", q.trophyHeld.get(0).type);
            assertEquals(1, q.trophyHeld.get(0).value);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
