package kelium.agents;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import kelium.core.GameState;
import kelium.observe.PublicView;

/**
 * ОЦЕНКА ПОЗИЦИИ ЧЕРЕЗ ОБУЧЕННУЮ В PYTORCH СЕТЬ (18.08.2026) — то же место в
 * системе, что и {@link ValueNet}, только вход не 33 признака
 * {@link StateFeatures}, а весь стол через {@link PublicView} (обучено
 * {@code training/kelium_ml/train.py} на выгрузке {@link TrainingDataExport},
 * экспортировано {@code training/kelium_ml/export_onnx.py}).
 *
 * <p>Java не переобучает и не меняет сеть — только исполняет уже готовый
 * граф. Обучение и подбор гиперпараметров остаются на Python: два места с
 * одной архитектурой расходятся молча, а .onnx-файл — контракт между ними.
 *
 * <p>Цель сети — {@code margin} (мои ПО минус ПО сильнейшего соперника),
 * нормированный на {@link #MARGIN_SCALE}: {@link #value} возвращает его
 * обратно в очки, чтобы число было сравнимо с {@link ValueNet#forward}.
 */
public final class ValueNetOnnx implements AutoCloseable {

    /** Тот же масштаб, что MARGIN_SCALE в training/kelium_ml/dataset.py. */
    private static final float MARGIN_SCALE = 15.0f;

    private final OrtEnvironment env;
    private final OrtSession session;

    private ValueNetOnnx(OrtEnvironment env, OrtSession session) {
        this.env = env;
        this.session = session;
    }

    public static ValueNetOnnx load(Path onnxFile) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session = env.createSession(onnxFile.toString(), new OrtSession.SessionOptions());
        return new ValueNetOnnx(env, session);
    }

    /** Оценка позиции {@code seat} — тот же смысл, что {@link ValueNet#value}. */
    public double value(GameState s, int seat) {
        return value(PublicView.of(s, seat));
    }

    public double value(PublicView v) {
        PublicViewEncoder.Encoded e = PublicViewEncoder.encode(v);
        try (OnnxTensor hexX = tensor(e.hexX(), 1, PublicViewEncoder.MAX_HEXES, PublicViewEncoder.HEX_FEATS);
             OnnxTensor hexMask = tensor(e.hexMask(), 1, PublicViewEncoder.MAX_HEXES);
             OnnxTensor seatX = tensor(e.seatX(), 1, 1 + PublicViewEncoder.MAX_OTHERS, PublicViewEncoder.SEAT_FEATS);
             OnnxTensor seatMask = tensor(e.seatMask(), 1, 1 + PublicViewEncoder.MAX_OTHERS);
             OnnxTensor scalars = tensor(e.scalars(), 1, PublicViewEncoder.N_SCALARS);
             OnnxTensor character = tensor(new float[PublicViewEncoder.N_CHARACTERS], 1, PublicViewEncoder.N_CHARACTERS)) {
            Map<String, OnnxTensor> inputs = Map.of(
                "hex_x", hexX, "hex_mask", hexMask,
                "seat_x", seatX, "seat_mask", seatMask,
                "scalars", scalars, "character", character);
            try (OrtSession.Result result = session.run(inputs)) {
                float[] out = (float[]) result.get(0).getValue();
                return out[0] * MARGIN_SCALE;
            }
        } catch (OrtException ex) {
            throw new IllegalStateException("сеть ValueNetOnnx не смогла оценить позицию", ex);
        }
    }

    private static OnnxTensor tensor(float[] data, long... shape) throws OrtException {
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(data), shape);
    }

    @Override
    public void close() throws OrtException {
        session.close();
        env.close();
    }
}
