package kelium;

import java.util.Set;

import kelium.agents.SelfPlayTrainer;

/**
 * Сравнение обучения: линия «аксиома» (Fitness.Goal.АКСИОМА — очки и победа
 * НЕ участвуют в отборе вообще, только три заповеди: высота на всех треках
 * науки разом, трофейная цена уничтожений + сырые попадания/убийства,
 * разнообразие жетонов на поле) против обычной линии «balanced» (отбор на
 * победу и отрыв по очкам). Заказ дизайнера 2026-08-15.
 *
 * <p>Запуск: {@code kelium.AxiomCompare [партий-на-характер] [популяция] [партий-на-геном]}.
 */
public final class AxiomCompare {

    private AxiomCompare() {
    }

    public static void main(String[] args) throws Exception {
        long perCharacter = args.length > 0 ? Long.parseLong(args[0]) : 8000L;
        int population = args.length > 1 ? Integer.parseInt(args[1]) : 16;
        int gamesPerGenome = args.length > 2 ? Integer.parseInt(args[2]) : 12;

        SelfPlayTrainer t = new SelfPlayTrainer(population, gamesPerGenome);
        t.setOnly(Set.of("axiom", "balanced"));
        t.run(perCharacter, Math.max(500L, perCharacter / 10));
        System.out.println("готово: геномы линий axiom/balanced обновлены в памяти ботов");
    }
}
