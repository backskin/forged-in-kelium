package kelium.cards.objectives;

import java.util.Map;

import kelium.engine.Figures;
import kelium.engine.cards.CardContext;

/**
 * ЗАДАНИЕ-РИСУНОК — выложить своими жетонами ФИГУРУ на поле.
 *
 * <p>Фигуру можно ПОВОРАЧИВАТЬ и НЕЛЬЗЯ ОТРАЖАТЬ (правило дизайнера): живой
 * игрок карту крутит, но перевернуть её лицом вниз не может, и на гексовой сетке
 * зеркальный «уголок» — это другая фигура. Проверяются ровно шесть положений.
 *
 * <p>Геометрия целиком лежит в {@link Figures} и здесь не повторяется: фигура
 * задана списком путей от опорного гекса, поворот — это прибавка единицы к
 * номеру каждой стороны, перебор идёт по всем гексам поля как опорам.
 *
 * <p>ПРОГРЕСС У ЭТИХ КАРТ ЧЕСТНО ДВОИЧНЫЙ. «Почти фигура» — это не фигура, и
 * показывать боту 0.8 значило бы толкать его достраивать то, что может быть
 * недостроимо: недостающий гекс бывает занят чужим зданием или вообще
 * отсутствует на краю поля. Врать боту дороже, чем промолчать.
 */
public final class FigureObjective extends Objective {

    public FigureObjective(String id) {
        super(id);
    }

    /** Описание фигуры из данных карты ({@code requirement.params}). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> figure(CardContext ctx, String branch) {
        if (data().get(branch) instanceof Map<?, ?> req
                && req.get("params") instanceof Map<?, ?> p) {
            return (Map<String, Object>) p;
        }
        return null;
    }

    @Override
    public boolean satisfied(CardContext ctx) {
        Map<String, Object> f = figure(ctx, "requirement");
        return f != null && Figures.satisfied(ctx.state(), ctx.seat(), f);
    }

    @Override
    public boolean satisfiedEnhanced(CardContext ctx) {
        Map<String, Object> f = figure(ctx, "enhanced");
        return f != null && Figures.satisfied(ctx.state(), ctx.seat(), f);
    }

    @Override
    public double progress(CardContext ctx) {
        return satisfied(ctx) ? 1.0 : 0.0;
    }

    @Override
    public String needed(CardContext ctx) {
        if (satisfied(ctx)) {
            return "готово";
        }
        Map<String, Object> f = figure(ctx, "requirement");
        return f == null ? "фигура не описана в данных"
            : "выложить своими жетонами: " + Figures.describe(f);
    }
}
