# Ревизия java-sim — полный отчёт (2026-08-11)

**Кому адресовано:** агенту, работающему над исправлением багов java-sim. Отчёт самодостаточен: каждая находка содержит файл:строку, цитату кода, объяснение и рекомендацию. Пути — от `java-sim/src/main/java/`.

**Как проводилась ревизия:** три независимых прохода — (A) ядро движка и цикл хода, (B) агенты, полнота информации и свободы решений, (C) бой, геометрия, сетап, данные, тесты. Сверка с источниками правды: «Действия — полный свод.md», «СВОД — механика игры.md», «Бой — полные правила.md», `simulator/data/rulesets/1.4.0.yaml`, `simulator/data/cards/*.yaml`.

**Статус сборки:** `mvn -o test` — зелёный, 42 теста, 0 падений, 0 `@Disabled`. Ничто из перечисленного тестами не ловится.

**Пометка «××»** — находка независимо подтверждена двумя проходами.

---

## Вердикт

Каркас цельный: все 8 действий подключены, 33 точки выбора `agent.choose` (в `Actions.java` — 10, `GameEngine.java` — 7, `CombatResolver.java` — 3, `Modules.java` — 5, `Effects.java` — 2), бой-резолвер полный, геометрия even-r корректна и 180°-симметрична (проверено вручную на 2p v1 и 4p v2). Но:

1. **Боты не имеют полной свободы**: в ~12 местах решение живого игрока подменено захардкоженной эвристикой движка без `agent.choose`. Балансные прогоны в этих зонах измеряют баланс зашитых предпочтений, а не игры; RL-агенту эти решения недоступны в принципе.
2. **Информационного барьера нет**: агент получает живой `GameState` со всеми публичными полями, включая скрытое. Сегодня агенты не подсматривают (проверено), но это дисциплина, а не архитектура.
3. **Есть баги правил**, ломающие экономику, войну и условия конца партии.

Полный список kind-строк, которые движок присылает в `choose`:
`reveal_order, action, pay_power, mine, assemble, build_pick, build_hex, move_pick, move_hex, move, market, storage_side, sci_exchange, combat_source, combat_target, attack, place_damage, maneuver_unit, open_container, spec, mass_open, module_place_red, module_place_blue, module_move_pick`.

---

# 1. КРИТИЧНО — решения, отнятые у игрока (нет `agent.choose`)

## K1 ×× Слепой сброс приказа делает ГСЧ, а не игрок

`engine/GameEngine.java:288-292`
```java
if (p.orderHand.size() > 4) {
    int idx = s.rng.nextInt(p.orderHand.size());
    p.orderSetAside = p.orderHand.remove(idx);
```
По правилам (§2, шаг 2) игрок **осознанно откладывает** 1 из 5 карт приказов — какой приказ он выключает себе на весь раунд. Это одно из ключевых стратегических решений; здесь его нет вовсе — карта выбирается случайно. Ни один агент (включая будущий RL) не может на это влиять. Плюс `> 4` захардкожено при наличии `rounds.order_hand_size: 5` в ruleset.

**Фикс:** вывести в `Choice` (`kind = "blind_discard"`, опции — карты руки).

## K2 ×× Наука: трек выбирает движок, шаг всего один

`engine/Actions.java:1026-1054`
```java
// Выбираем шаг, дающий БОЛЬШЕ всего ПО за трофей ..., при равенстве — самый дешёвый.
for (String track : tech.tracks) { ... if (vp > bestVp || (vp == bestVp && cost < bestCost)) { bestTrack = track; ... } }
```
Агенту не предлагается ни одной опции. Треки не эквивалентны: разные награды (`prize_cube` / модули red/blue/storage / `permanent_ability` / `super_arsenal_card`, см. `Actions.java:1088-1146`), и только вершины трёх треков дают `peacefulEnd` (`GameEngine.java:931`). Живой игрок выбирает трек под стратегию; бот жёстко получает «максимум ПО за трофей» — характер бота на науку не влияет. Комментарий в коде честно признаёт: сделано, «чтобы боты не распыляли трофеи» — баг ИИ вылечен подменой правил в ядре.

Дополнительно: по §5.2 за одно действие можно продвинуться **на каждом треке** (один шаг на трек); код делает ровно один шаг всего. Ключ `tech.science_one_step_per_track_per_action` из ruleset не читается.

**Фикс:** предложить агенту список доступных шагов по трекам (`kind = "sci_track"`), реализовать «по шагу на трек».

## K3 ×× Смена энергии: полностью автоматическая и не по правилу

`engine/Actions.java:660-718`
```java
consumers.sort((a, b) -> Integer.compare(consumerPriority(a), consumerPriority(b)));
for (BuildingToken b : consumers) { b.energyPlaced = 0; }
int remaining = total;
for (BuildingToken b : consumers) {
    int place = Math.min(b.energySlots, remaining);
    b.energyPlaced = place;
...
case COMMAND_CENTER -> 0; case MINER -> 1; case BARRACKS, FACTORY, AIRBASE -> 2; default -> 3;
```
Три проблемы:
- **Ни одного `choose`** — распределение энергии, одно из самых содержательных решений игры, решает зашитый приоритет «ЦУ → добытчики → военные → прочее».
- **Правило нарушено концептуально.** По §3.2 действие = «выбери ОДИН свой гекс и перераспредели энергию, вытекающую ИЗ НЕГО»; второй гекс +1 МОН, третий +2 МОН. В коде понятия «гекс-исход» нет: собирается ВСЯ энергия поля и раскладывается заново бесплатно — действие идемпотентно-оптимально, ровно то, против чего написан абзац «Зачем ограничение по исходу».
- **Наценка `actions.energy_swap.surcharge_coins: [0, 1]` не взимается**: `ctx.recordOp("energy_swap")` вызывается (`:703`), но `nextOpSurcharge` для energy_swap не вызывается нигде.

Побочный эффект для баланса: раз добытчики всегда запитываются раньше военных зданий, стратегия «милитаризация в ущерб экономике» ботам физически недоступна. Вывод вида «военка слабее экономики» из прогонов отчасти предопределён этим приоритетом.

**Фикс:** реализовать модель «гекс-исход» с наценкой и вывести раскладку в цикл `Choice`.

## K4 ×× Бой: конкретную жертву выбирает движок

`engine/CombatResolver.java:531-566`
```java
candidates.sort((a, b) -> { ... ra = a instanceof BuildingToken ? 0 : 1; ... Integer.compare(uidOf(a), uidOf(b)); });
return candidates.get(0);
```
Агент выбирает *(юнит-источник, строку атаки, категорию цели)*, но не конкретный жетон: внутри категории берётся первый по uid (здания вперёд). Правила («Порядок целей свободный», «цели назначаются **поимённо**») требуют выбора игрока. Последствия:
- нельзя добить раненого и нельзя намеренно «оставить раны» — а на этом построены задания `damaged_enemy_tokens_standing`, `enemy_building_damaged`, `damaged_distinct_enemy_this_turn`;
- **в 3–4p атакующий не может выбрать, какого из двух соперников на общем гексе бить** — а это определяет, кто получит ответный бой (§4: «отвечают оба»). Сейчас выбор соперника — побочный эффект порядка uid.

То же для нейтралов: `CombatResolver.java:320` — `nh.neutrals.get(0)`, всегда первая постройка, хотя большие/маленькие дают разные награды.

Важно: защитник по правилам и должен быть пассивен внутри чужого боя — его решения приходят в ответном бою, и они агенту даются (`CombatResolver.java:378`). Проблема именно в недоданной свободе атакующего.

**Фикс:** когда кандидатов больше одного — `Choice` (`kind = "combat_victim"`).

## K5 ×× Оплата трофеями: выбор за игрока, и выбор наихудший

`engine/Actions.java:1149-1170`
```java
player.destroyedTokens.sort((a, b) -> Integer.compare(b.trophyValue(), a.trophyValue()));
while (remaining > 0 && !player.destroyedTokens.isEmpty()) {
    kelium.core.Token tok = player.destroyedTokens.remove(0);
```
Сортировка **по убыванию** — первым тратится самый дорогой жетон. При стоимости шага 1 и танке на 3 ТО платится 3 ТО, излишек сгорает (перенос излишка правилом действительно запрещён, но выбор жетона принадлежит игроку и должен позволять минимизировать потери). Трофеи различаются и качественно: сдав чужое здание, ты отдаёшь его владельцу (см. B4 — сейчас код его вообще уничтожает).

**Фикс:** `Choice` по жетону из трофейного пространства; жадный дефолт — снизу, не сверху.

## K6 ×× Вклад в супер-задание: часть выбирает движок (и считает неверно — см. B5)

`engine/GameEngine.java:811-844`
```java
for (Object po : parts) {
    String kind = ...;
    if (!superPartAvailable(p, kind)) { continue; }
    ...
    p.superObjectiveProgress += 1;
    break;
}
```
Берётся первая доступная часть в порядке данных; игрок не выбирает, что вносить.

**Фикс:** `Choice` по доступным частям + честный учёт состава (см. B5).

## K7 Остальные отнятые решения (средняя серьёзность, единым списком)

| Где | Что решает движок вместо игрока |
|---|---|
| `Actions.java:205-211, 314-321` | Сборка и Добыча: **нельзя пропустить здание**, хотя правило прямо разрешает («Пропустить здание можно»). Каждое запитанное здание обязано произвести юнит или патроны; при переполнении лимита жетонов юнит молча конвертируется в 1 патрон (`:347`) |
| `Actions.java:264-276` | Добыча: добытчик между двумя грядками всегда доит **первую по порядку соседей** (заодно см. баг B1 в том же методе) |
| `Actions.java:278-290` | Контейнер добытчиком: только радиус 1 вместо «ближайший по числу гексов»; «на равном расстоянии — выбирает игрок» — не реализовано; «нет на поле — из запаса» — не реализовано; при отсутствии молча ничего |
| `Actions.java:420, 567` | Постройка: стороны гекса выбирает `firstFreeFootprint`, а раскладка сторон влияет на проходимость техники (`canEnterHex` требует `hasFreeAdjacentPair()`, `:874`) |
| `Actions.java:959-966` | Рынок: показан только `baseExchanges.get(0)` — 1 курс из 4 (см. D3) |
| `Actions.java:1218-1225` | «Тяни 2, оставь 1»: движок оставляет первую карту |
| `GameEngine.java:730-733` | Арсенал сверх лимита: молча сбрасывается **самая старая** из трёх (`remove(0)`); лимит `3` захардкожен при наличии `containers_storage.arsenal_cells` в ruleset |
| `Modules.java:115, 155` | Позолота модулей: вся уходит в красные, `gold` привязан к индексу цикла `i < goldRed` — если агент пасует на шаге i, позолота «съезжает» на другой модуль или теряется |
| `Effects.java:210-226` | `healBestHex` лечит гекс с максимальным суммарным уроном — без выбора |
| `Effects.java:288-308` | `deployUnits`: здания-источники выбирает движок |
| `CombatResolver.java:494-504` | Энергия уничтоженного здания: снимается с **первых попавшихся** зданий владельца. По правилам §5: сначала кубики самого здания, недостающее — по выбору владельца, остаток вернуть. В коде кубики самого здания (`bt.energyPlaced`) не трогаются и уезжают на место уничтоженных жетонов вместе с жетоном |
| `CombatResolver.java:232-238` | Нет «паса» при выборе цели боя: выбрав источник, отказаться уже нельзя (у всех прочих точек — `combat_source`, `attack`, `move`, `build_pick`, `market`, `spec` — пас есть) |
| `Setup.java:197-204` | Супер-задание раздаётся случайно, без драфта; стороны планшетов и стартовая раскладка фиксированы |

Образец того, как должно быть: манёвр (`GameEngine.java:433-513`) — выбор жетона + пошаговое движение с пасом на каждом шаге.

---

# 2. КРИТИЧНО — баги правил

## B1 ×× Перевёрнутый тайл зарождения навсегда недобываем

`engine/Actions.java:264-276` против `:219-244`
```java
// поиск цели добычи:
if (h.kind == HexKind.SPAWN && h.kelium > 0 && !h.spawnFlipped) { return nb; }
// исчерпание:
if (!gh.spawnFlipped) { gh.spawnFlipped = true; gh.kelium = gh.spawnBackKelium; ... }
```
Как только тайл перевёрнут (`spawnFlipped = true`), `adjacentGridWithKelium` его навсегда исключает — при том, что `GameEngine.refresh()` (`GameEngine.java:222-225`) каждый раунд честно восстанавливает `h.kelium = h.spawnBackKelium`. Каскад последствий:
- ветка второй выработки (`:233-243`: `claimedSpawnTiles += 1`, `spawnStack -= 1`, `spawnRemoved = true`) **недостижима**;
- ПО за снятые тайлы (`Scoring: claimedSpawnTiles`) всегда 0;
- условие конца партии `peacefulEnd()/"last_spawn_tile"` (`GameEngine.java:935-945`) практически не срабатывает;
- половина экономики келемия (обратные стороны тайлов) выключена.

Эту же ошибку копирует `HeuristicAgent.gridWithKeliumAt` (`HeuristicAgent.java:619-623`).

**Фикс:** убрать `!h.spawnFlipped` из условия поиска (и из эвристики агента); логика исчерпания уже написана правильно.

## B2 Уничтоженное здание навсегда блокирует постройку такого же

`engine/Actions.java:593-598, 624-627`
```java
for (BuildingToken b : player.buildings) {          // все, включая уничтоженные
    if (b.type == BuildingType.MINER) { minerLvls.add(b.level); }
...
for (BuildingToken b : player.buildings) { builtMil.add(b.type); }
```
`CombatResolver.destroy()` ставит `hexId = null`, но **оставляет жетон в `player.buildings`**; `returnStep()` (`GameEngine.java:885-897`) чистит только трофейное пространство. `buildable()` считает уничтоженную казарму «уже построенной» — потеряв казарму, игрок никогда не построит новую.

**Фикс:** в `buildable()` итерировать `buildingsOnField()` (метод уже существует).

## B3 Погибшие войска навсегда занимают лимит жетонов

`engine/Actions.java:332-338`
```java
for (UnitToken u : player.units) { if (u.hexId == null) { reserve++; } }
if (player.unitsOnField().size() + reserve < state.tokenStats.unitTokensPerColor) {
    UnitToken u = state.tokenStats.makeUnit(...);   // создаётся НОВЫЙ жетон
```
Убитые юниты возвращаются с `hexId == null` и считаются «резервом», но сборка их не переиспользует — создаёт новые токены. Резерв растёт с каждой смертью до `unitTokensPerColor`, после чего вся сборка молча конвертируется в патроны (`:347`). Игрок необратимо теряет производственную мощность за каждую потерю — в правилах этого нет.

**Фикс:** переиспользовать жетон из резерва (сброс урона + `hexId`), а не создавать новый.

## B4 Наука уничтожает чужие жетоны вместо возврата владельцу

`engine/Actions.java:1158-1165`
```java
PlayerState owner = state.player(tok.owner());
if (tok instanceof BuildingToken bt) { owner.buildings.remove(bt); }
else if (tok instanceof UnitToken ut) { owner.units.remove(ut); }
```
Правило §5.2 прямо: «Сдавая трофеи, **верни уничтоженные жетоны владельцам**». Код удаляет их из пула жертвы физически. Вкупе с B2/B3 война необратимо разорительна для проигравшего, чего в правилах нет.

**Фикс:** возврат в пул владельца (жетон уже с `hexId == null` — достаточно НЕ удалять из списков; для зданий согласовать с фиксом B2).

## B5 Супер-задание: неверный учёт состава и бесплатный прогресс

`engine/GameEngine.java:811-844`
- `total` = сумма `amount` всех частей, но прогресс инкрементируется **на 1 за вклад независимо от требуемого количества конкретной части** — супер-задание «1 келемий + 4 трофейных жетона» закрывается сдачей 5×1 келемия.
- Для частей `own_miner_bordering_grid` и `own_building_adjacent_enemy` **нет ни одной ветки оплаты** (if/else if их не покрывает), но `superObjectiveProgress += 1` выполняется — бесплатный прогресс к мгновенной победе.

**Фикс:** прогресс вести по частям (map kind→внесено), вклад доступен только если по этой части ещё есть недобор и оплата реализована.

## B6 ×× Пас по действию отбирает SPEC-действие

`engine/GameEngine.java:407-424`
```java
Choice ch = agents.get(p.seat).choose(s, opts, ev("kind","action", ...));
if (ch.payload() == null) { break; }        // offerSpec ниже уже не выполнится
...
offerSpec(p, ctx);                          // только после успешного действия
```
Игрок, спасовавший оба действия (или с пустым `candidates`), теряет СПЕЦ-действие хода: не может завершить задание, установить арсенал, внести вклад в супер-задание и даже **развернуть готовое супер-задание ради мгновенной победы**. По правилам СПЕЦ — независимый ресурс хода (требуется лишь порядок «сначала основное, потом СПЕЦ», а не наличие основного).

**Фикс:** вызывать `offerSpec` и при пасе (перед/вместо `break`).

## B7 Неубиваемые фантомы: `alive()` vs `Passives.effectiveHp()`

`engine/CombatResolver.java:355`
```java
boolean destroyed = damageOf(victim) >= Passives.effectiveHp(s, victim);
```
а фильтрация целей и все прочие проверки — по печатному HP: `CombatResolver.java:95,105,111` (`u.alive()`, `b.alive()`), `PlayerState.java:110,121`, `BuildingToken.java:32` (`damage < hp`).

С пассивом `buildings_plus1_hp` (карта a3) или `cu_plus2_hp` (a18): казарма hp=2 при damage=2 → `alive()==false` — жетон исчезает из `allTokensOn`, `hexClosedAgainst`, `unitHidden`, `canEnterHex` (по нему нельзя бить, он не закрывает гекс, на него можно зайти), но `destroyed==false` — он **никогда не будет уничтожен** и остаётся на поле. То же для ЦУ с +2 HP.

**Фикс:** живость везде считать через `Passives.effectiveHp` (или инкапсулировать в `alive(state)`).

## B8 Тихая заглушка поля + версия сценария из версии досок

`engine/Setup.java:110-134`
```java
} catch (Scenario.ScenarioError e) {
    // упасть в заглушку
}
Field field = buildRingField(n);
```
Нет файла сценария, битая транскрипция, не хватает мест — партия **молча** играется на синтетическом кольце из 3 гексов на игрока. Это другая игра, и наружу не выходит ни одного сигнала. `FieldLayoutTest` ловит только дефолтный ruleset и эвристикой по префиксу id (`FieldLayoutTest.java:81`).

Хуже: `Setup.java:111` — `String version = config.ruleset.getStr("content_versions.boards", "1.0.0")`. Сценарии и доски — независимые наборы данных (`ContentSet.ALL_TYPES` про сценарии не знает). Как только `content_versions.boards` станет `1.1.0`, файла `scenario_Np.1.1.0.yaml` не будет — и все партии тихо уедут на заглушку.

**Фикс:** убрать молчаливый catch (падать или громко логировать + флаг в телеметрию); завести отдельный ключ `content_versions.scenarios`.

## B9 HeuristicAgent: контейнеры всю дорогу вскрывались наугад

`agents/HeuristicAgent.java:156-157`
```java
case "open_container" -> null;   // используется container_variant
case "container_variant" -> (s, o) -> scoreContainerVariant(s, o);
```
`scorerFor` матчится по **context kind**, а движок присылает `kind = "open_container"` (`GameEngine.java:536`); `container_variant` — это `Choice.kind` опций, не контекст. Ветка `"container_variant"` не срабатывает никогда; `"open_container" -> null` уводит в fallback:
```java
// HeuristicAgent.java:110-118
if (scorer == null) { ... return reals.get(rng.nextInt(reals.size())); }
```
Итог: вариант A/B контейнера выбирается **случайно**, 25 строк готовой оценки (`:837-861`) — мёртвый код, а поскольку fallback отфильтровывает pass — бот **никогда** не оставляет контейнер закрытым.

**Фикс (одна строка):** `case "open_container" -> (s, o) -> scoreContainerVariant(s, o);`

## B10 Стройка: одна операция за действие, наценка и снос мертвы

`engine/Actions.java:377-378, 437-438`
```java
int surcharge = ctx.nextOpSurcharge("build", schedule);
...
ctx.recordOp("build");
ctx.actionsPlayed.add(name());
```
По §3.1 за одно действие Стройка делается **несколько** операций (построить/перенести/снести; 2-я +1 МОН, 3-я +2 МОН). Код после одной операции ставит `actionsPlayed.add(name())`, и `playActions` (`GameEngine.java:392-396`) больше это действие не предложит — `opCounts["build"]` никогда не превышает 0, `actions.build.surcharge_coins: [0,1]` не применяется никогда. Аналогично мертвы наценки `movement`/`combat`.

Также не реализованы: **снос** (`actions.build.demolish_refund_coins: 1` не читается нигде), поворот здания внутри гекса, проверка «не более одного здания каждого типа в гексе». И: `Actions.java:426-436` — новая энергостанция/ЦУ **не получают энергию при постройке** (правило: «немедленно получают кубики ЭНР по номиналу»; ср. `Setup.java:175` и `CombatResolver.java:693`, где `energyPlaced` выставляется явно). Перенос здания не облагается наценкой (`movable()`/`moveCost()` `:449-457` без surcharge — а правило: «надбавка по числу операций, независимо от типа операции», и «бесплатный перенос ЦУ считается операцией»).

**Фикс:** цикл операций внутри действия с наценкой из ruleset; снос; энергия новым станциям.

---

# 3. Бой — остальное

## C1 §6 «второй бой в одном действии» не реализован

`GameEngine.java:393` (`!ctx.actionsPlayed.contains(nname)`) + `CombatAction` безусловно `ctx.actionsPlayed.add(name())` и ровно один `runBattle`. Следствия: `nextOpSurcharge("combat", ...)` всегда возвращает [0]=0; ключ `actions.combat.open_battle_surcharge_ammo: [0,1]` и пассив `no_second_battle_surcharge` (карта a10) — мёртвые.

## C2 Латентный краш: наценка боя проверяется до, платится после

`Actions.java:897-911`
```java
if (surcharge > 0 && !player.resources.canPay(Resource.AMMO, surcharge)) return ActionResult.fail(...);
... boolean did = resolver.runBattle(player.seat, agent);
if (surcharge > 0) player.resources.pay(Resource.AMMO, surcharge);
```
`Resources.pay` (`Resources.java:52`) бросает `IllegalStateException` при нехватке; бой между проверкой и оплатой съедает БП. Недостижимо только потому, что surcharge всегда 0 (см. C1) — упадёт сразу после починки C1. По правилам платится **до** («плата за право провести ещё один бой»).

## C3 Телеметрия боя накручивается до боя

`Actions.java:902-907`
```java
journal(state).of(player.seat).battlesOpened += 1;
boolean did = resolver.runBattle(player.seat, agent);
if (!did) { ctx.actionsPlayed.add(name()); return ActionResult.ok("combat: no battle"); }
```
`battlesOpened` растёт даже если боя не было — предикаты заданий «открой N боёв» выполняются пустыми боями. Симметрично: при `fail` из-за наценки `ctx.actionsPlayed` не помечается, но `playActions` всё равно делает `played += 1` (`GameEngine.java:418`) — слот действия сгорает.

## C4 Уничтожение ЦУ: три версии правды

`CombatResolver.java:633-652` + ruleset `command_center`:
- документ §5: «4 трофея и жетон... две звезды — 2 ПО»;
- ruleset 1.4.0: «CU gives NO trophy points... destruction_token_vp: 3»;
- код: ТО не даёт (по ruleset), ПО = 3 (по ruleset).

И условие военной победы: ruleset — «would-be 2nd token = instant win» (второй **жетон**), код — `attacker.cuKills >= 2` (второе **убийство**):
```java
attacker.cuKills += 1;
if (owner.ownCuTokenAvailable) { owner.ownCuTokenAvailable = false; attacker.cuDestructionTokens += 1; }
if (attacker.cuKills >= 2 && ...) { s.finished = true; ... }
```
В 2p второе убийство того же ЦУ даёт победу без второго жетона. **Нужно решение дизайнера, какая версия верна.**

## C5 Компенсация контейнерами расходится с документом

`CombatResolver.java:665-673`: авиабаза 2, ЭС ур.1 — 0, остальное 1. Документ §5: «обычные — 1 · авиабаза **и ЦУ** — 2 · ЭС №3 — 1 · ЭС №1 — без». ЦУ идёт мимо этой функции (берёт `command_center.owner_compensation_containers = 2` — совпало), ЭС №2/№4 в документе не описаны — код молча даёт 1. Требует подтверждения дизайнером.

## C6 Мелочи боя

- `CombatResolver.java:400-402`: пол стоимости атаки несогласован — супер-арсенал `Math.max(1, cost - 1)`, обычные скидки `Math.max(0, cost)`: при `first_attack_minus1_ammo` + `anti_armor_minus1_ammo` атака бесплатна. Вероятно, пол должен быть 1 везде.
- `CombatResolver.java:229-231`: если у выбранного источника нет валидных целей — `return false` без права выбрать другой гекс, а действие Бой уже потрачено. Для RandomAgent — регулярно сгорающее действие.
- `CombatResolver.java:716-718`: `agentFor()` без проверки `agents != null` — NPE, если `bindAgents` не вызван (публичный API этого не гарантирует).
- `Passives.java:154`: `retaliation_strikes_first` определён, вызовов ноль — карта a17 «Глубокая оборона» не делает ничего. `contested_cards.attack_first_initiative_enabled` из ruleset не читается — «спорные» карты не отбраковываются.

---

# 4. Эффекты и пассивы карт

## E1 Неизвестный эффект — молчаливый no-op, ресурс не возвращается

`engine/Effects.java:71-73`
```java
case "noop" -> Map.of("noop", p.getOrDefault("note", "unimplemented"));
default -> Map.of();   // ещё не портированный эффект = noop
```
Все пять мест вызова обёрнуты в проглатывающий catch (`GameEngine.java:545-550`, `:691-696` арсенал, `:713-719` верх задания, `Actions.java:989-994` рынок):
```java
} catch (Effects.EffectError e) { got = new HashMap<>(); }
```
Ресурс (келемий на рынке, контейнер, карта) списывается **до** применения эффекта и при отказе не возвращается. В контенте 9 вариантов с `effect: noop` (контейнеры c14/c20/c26/c28, рынок corps_hq/civil_contract и др.) — карта тратится, эффекта нет.

## E2 11 пассивов арсенала полностью инертны

В `arsenal.1.1.0.yaml` объявлены, в Java нет ни одного обращения (grep по `src/main`):
`build_minus2_coin`, `commit_1_trophy_to_track`, `deploy_1_unit`, `grab_adjacent_container`, `heal_one_damage`, `miner_takes_container`, `move_one_module`, `move_one_unit_1`, `unit_makes_one_attack`, плюс `retaliation_strikes_first` (см. C6). Ещё два имеют геттер в `Passives.java`, который никто не вызывает: `marketSecondKeliumFull` (`:174`), `storageCellBonus` (`:189`). Игрок ставит карту, занимает слот из трёх — и не получает ничего.

## E3 `Effects.moveUnit` двигает юниты в обход всех правил проходимости

`engine/Effects.java:249-286`
```java
for (UnitToken u : pl.unitsOnField()) {
    for (String nb : s.field.neighbors(u.hexId)) { ... opts.add(new Choice("move", mp, ...)); }
}
```
`Actions.MovementAction.canEnterHex` (специально сделанный `static` для переиспользования) не вызывается: эффект с карты ставит юнит на запретный гекс, грядку, чужое здание, нейтралку; техника проходит без 2 свободных смежных секторов; контейнеры по пути не подбираются (в отличие от `Actions.java:805-809`). Это нарушение легальности, которую движок обязан гарантировать агенту.

## E4 `Effects.freeAction` ломает контекст хода

`engine/Effects.java:243-244`
```java
TurnContext ctx = new TurnContext(seat, 0);
Actions.create(name, s).perform(s.player(seat), ctx, agent);
```
Свежий `TurnContext` обнуляет `opCounts` (наценки) и `actionsPlayed` (можно повторить уже сыгранное действие); `emit` не испускается, `TurnJournal.onAction` не вызывается — телеметрия и лог теряют действие. Затронуто ~50 карт (`free_action` — самый частый эффект после `gain`).

## E5 `Effects.deployUnits` — другая формула лимита

`Effects.java:300`: `if (pl.units.size() < s.tokenStats.unitTokensPerColor)` против `unitsOnField().size() + reserve` в сборке (`Actions.java:338`). Две формулы одного лимита; свободная ячейка гекса не проверяется.

## E6 Оплата «жертвы» задания проглатывается

`engine/Objectives.java:107-113`
```java
try {
    p.resources.pay(Resource.fromCode((String) res), amt);
} catch (RuntimeException e) {
    // цена не по карману — игнорируем (защитно)
}
```
Задание выдаёт награду, не заплатив цену. `canPaySacrifice` (`:58-65`) возвращает `true` для `"container"` и `true` при любом исключении; жертва контейнерами нигде не списывается (`p.containers` не трогается) — задания с жертвой-контейнером бесплатны. Там же `grantBase`/`grantSpecial` имеют `default -> { }` (`:161, 206`) — неизвестный ключ награды молча игнорируется.

---

# 5. Геометрия, поле, сетап

Хорошее: even-r преобразование каноническое (`Scenario.java:125`: `int q = col0 - (row0 + (row0 & 1)) / 2;`), `Field.AXIAL_DIRS` валиден (`dir[i+3] == -dir[i]`), `link` через `(sideA+3)%6` корректен. Раскладки 2p v1 и 4p v2 180°-симметричны и в offset-, и в осевых координатах; ошибки чётных/нечётных рядов нет. Предупреждения `FieldLayoutTest` парные по зеркальным местам — это свойство данных, не смещение сетки.

## G1 `forbidden`-гексы остаются полноценными узлами графа

`Scenario.java:247` — гекс добавляется и линкуется. Движение блокирует (`Actions.java:836`), геометрия заданий — нет:
- `Predicates.bfsDist` (`Predicates.java:836`) ходит **сквозь** запретные гексы → `unit_far_from_own_buildings` занижает дистанции;
- `buildings_ring_around_hex` (`:446`), `sp_four_buildings_around_common_hex` (`:597`), `sp_three_unit_hexes_adjacent_one_enemy_hex`, `sp_line_of_three...` могут выбрать центром **запретный** гекс.

Реально задействовано: `scenario_4p.1.0.0.yaml`, вариант `field_4p_claude` — 4 гекса `forbidden`.

## G2 `blocked_edges` рвёт `neighbors`, но не `neighborBySide`

`Scenario.java:336-339` — `neighborBySide[i]` продолжает указывать на отрезанного соседа, поэтому `Hex.sidesFacing` и `Predicates.shareWallWithAny` (`:792-803`) считают здания через разорванное ребро «примыкающими стенкой». Пока в данных `blocked_edges` не используется — латентно.

## G3 Воздушная ячейка не работает

`core/Hex.java:27` — `airToken` **никогда не присваивается** (grep: только чтение в `Actions.java:843-855`). Правило «в воздушной ячейке один жетон авиации» не действует: любая авиация стекается на один гекс без ограничений.

## G4 Наземные войска не занимают стороны гекса

`occupySides` вызывается только для зданий (`Actions.java:432,571`; `Setup.java:177,184`; `CombatResolver.java:700`). Лимит «6 наземных ячеек на гекс» для войск не действует; проверка техники `if (unit.type == VEHICLE && !h.hasFreeAdjacentPair())` (`Actions.java:874`) видит только здания — техника «протискивается» на гекс, забитый пехотой.

## G5 Раскладка поля зависит от общего сида партии

`Setup.java:113` → `Scenario.loadScenario(..., config.seed)` → `Math.floorMod(variantSeed, list.size())` (`Scenario.java:189`). Один сид крутит и колоды, и вариант поля — «сравнить два ruleset на одной карте» в батчах невозможно без ручного форса. Нужен отдельный `layoutSeed`/`variantId`.

## G6 Данные: в `field_2p_v2` старты заперты

Вывод `FieldLayoutTest`:
```
2p seed=1: место0: у старта h3_1 только 0 свободных соседей, норма >=2
           место1: у старта h-1_3 только 0 свободных соседей, норма >=2
```
Оба стартовых гекса окружены только грядками/нейтралами — стартовая пехота на первом ходу не может выйти никуда. Тест печатает, но сознательно не роняет (`FieldLayoutTest.java:108`). **Решение за дизайнером** (правка данных, не кода).

## G7 Мелочи сетапа

- `Setup.java:177`: `sh.occupySides(cu.uid, cuFp != null ? cuFp : List.of(0, 1))` — при занятых сторонах `occupySides` вернёт `false` и не добавит жетон в `groundTokens`; возвращаемое значение игнорируется здесь, в `:184` и в `CombatResolver.java:700`. ЦУ «висит» на гексе, не занимая сторон.
- `Setup.java:41`: стартовые ресурсы зашиты в код (`START_COINS = {3,4,4,5}`, `START_KELIUM=1`, `START_AMMO=1`) — весь остальной сетап декларативный.
- Противоречие javadoc: `Field.java:17` «плоский верх» vs `Scenario.java:51` «pointy-top, even-r». Математике безразлично, но `corners` нейтралов и рендеры завязаны на ориентацию — убрать, пока не породило ошибку в макетах.
- `Hex.sectors` парсится (`Scenario.java:301`), но всё проверяется по массиву из 6 — поле декоративное.

---

# 6. Информация и порядок хода

## I1 Барьера скрытой информации нет

`core/GameState.java` — все поля public, `choose(...)` получает живой стейт. Технически агент читает у соперников `orderHand` (руку приказов), `orderSetAside` (слепой сброс), `objectiveHand`, `arsenalHand`, `superObjective`, и порядок колод через `state.decks`. `WorldView.java` — добросовестная дисциплина, не барьер: javadoc (`:20-26`) обещает «бот не видит чужие руки», но использовать WorldView никто не обязан; `NeuralAgent`/`ChoiceFeatures` работают с сырым `GameState` (`ChoiceFeatures.java:35`).

**Проверено: текущие агенты скрытым не пользуются** — `HeuristicAgent`, `StrategicAgent`, `ChoiceFeatures`, `Scoring.scorePlayer` (`Scoring.java:32-111`) читают только своё и открытое. Но гарантии нет: любая правка или обученная сеть может начать подсматривать, и ни компилятор, ни тест этого не заметят. Для RL критично — сеть охотно выучит утечку.

**Рекомендация:** либо санитизированная копия стейта для `choose`, либо тест-стражник, что фичи/скореры не читают скрытые поля.

## I2 Вскрытие приказов последовательное, а не одновременное

`GameEngine.java:300-313` — агенты опрашиваются в `seatsInOrder()`, выбранная карта немедленно удаляется из `p.orderHand`. Поздние места наблюдают изменившиеся руки. По правилам вскрытие одновременное. Скрытая асимметрия в пользу поздних мест (в связке с I1).

## I3 Контейнер: подглядывание и утечка карты

`GameEngine.java:522-540`
```java
String cid = s.decks.get("containers").draw(s.rng);
if (cid == null) { p.containers -= 1; return; }      // жетон исчезает молча
...
Choice ch = agent.choose(...);                        // агент уже ВИДИТ карту
if (ch.payload() == null) { s.decks.get("containers").discard(cid); return; }
```
(а) при пустой колоде жетон уничтожается без сигнала; (б) агент видит содержимое **до** решения «открывать ли» — бесплатное подглядывание с правом отказа; (в) при отказе карта уходит в сброс (колода истощается), а жетон остаётся.

## I4 Игра продолжается после мгновенной победы

`playActions` (`GameEngine.java:390-425`) и `resolveTurn` (`:340-384`) не проверяют `s.finished`. Военная победа (`destroyCu`) или `deploySuper` (`:676-682`) на первом действии — игрок доигрывает второе действие, нижний приказ, манёвр; `run()` (`:117`) выполняет `returnStep()`, начисляя ПО после конца партии.

## I5 То, что человек видит и использует, боты не читают

- `orderPlayed` соперников (`PlayerState.java:35`) не смотрит ни один агент — а правило совпадения (`GameEngine.java:356`) и нижний приказ (`:366-374`) зависят от вскрытого другими; человек считает карты по цветной колоде соперника (`orderColor`).
- Сбросы колод (`Deck.discard`) не анализируются.
- Счёт соперников читает только `StrategicAgent.rivalLeaderSeat` (`StrategicAgent.java:470-484`); `HeuristicAgent` соперников не смотрит вообще.
- Накопленный урон жетонов учитывает только StrategicAgent (`:430`); `HeuristicAgent.scoreCombatTarget` (`:687-708`) считает лишь количество жетонов.

---

# 7. Слабости самих агентов

- **`scoreAttack` игнорирует цель**: `HeuristicAgent.java:711-718` — `return 2.0 + (2 - ammo);` — единственный критерий «дешевле патронов»; ни тип жертвы, ни HP, ни «добью ли». `StrategicAgent` `"attack"` не переопределяет (`StrategicAgent.java:236-246`) — его анализ обрывается в момент нажатия на курок. В связке с K4 тактика внутри боя у ботов отсутствует.
- **Fallback «random среди не-pass»** (`HeuristicAgent.java:110-118`) накрывает без скорера: `move_hex` (куда переносить здание), `module_move_pick`, `place_damage` (кому урон — все label «hit», `Effects.java:351-353`), `maneuver_unit` (у Heuristic; у Strategic переопределён). Всё это решается монеткой, и pass никогда не выбирается.
- **Несуществующие ключи весов**: `HeuristicAgent.java:917, 921-922` — `wget("defense")`, `wget("economy")`; таких ключей нет ни в `DEFAULT_WEIGHTS` (`:43-65`), ни в `PERSONALITIES`, ни в `Genome.defaults()` (`Genome.java:53-85`) → всегда 1.0; эволюция их не настроит; оценка арсенала не зависит от характера.
- **Падение на пустом списке**: `HeuristicAgent.java:117` (`opts.get(0)`), `:131`, `RandomAgent.java:31` — упадут на пустом `options`. Сегодня движок пустое не шлёт (проверены все 33 точки), но контракт не зафиксирован; `Modules.java:235, 248` — choose на потенциально пустых `slots`.
- Мёртвый код: `HeuristicAgent.hasOpenTechStep` (`:428-442`) не вызывается.
- Несоответствий kind-строк, кроме B9, не найдено; ClassCastException-рисков в скорерах не найдено (проверено).

---

# 8. Данные ↔ код

| Находка | Серьёзность |
|---|---|
| Данных в java-sim нет: `src/main/resources` отсутствует, всё из `../simulator/data` (`GameConfig.resolveDataRoot`, `GameConfig.java:54`). Работает, но хрупко к переносам | мелочь |
| `economy.leftover_destroyed_vp_per` читается с дефолтом 0 (`GameEngine.java:873`), в ruleset 1.4.0 отсутствует — «военный трек» всегда 0 в `Scoring` | мелочь |
| Ключи ruleset без читателя (крутилка есть, поведение зашито): `actions.combat.surcharge_model / retaliation_is_free / retaliation_to_retaliation`, `combat_model.damage_persists_until_refresh`, `actions.movement.first_hex_free`, `actions.build.demolish_refund_coins / move_building_repays_full_price / cu_free_move_per_turn`, `tech.science_one_step_per_track_per_action`, `tech.first_arriver_prizes`, `return_step.trophy_to_upgrade_exchange_enabled / refill_objectives_to_limit`, `rounds.min/max`, `contested_cards.*`, `market.cell_cost_kelium`, `asymmetry.token_overrides`, `boards: tower_hits_buildings` | средне |
| `boards.yaml` помечает себя `draft_fields: ["troop_side.speeds","troop_side.building_prices"]` — скорости/цены не сверены с печатными планшетами; `speeds.tower: 0` у A/Б1–Б3 значит вышка не двигается вообще (`Actions.java:756`) | средне (данные) |
| Рынок реализован на четверть: `Actions.java:959-966` — только `baseExchanges.get(0)` (келемий→монеты); курсы `kelium_to_ammo/objective/energy` игнорируются; нет сдачи 2 келемиев, нескольких печатных курсов, общего пула ячеек на раунд, замены активной карты за 1 келемий; `pay(Resource.KELIUM, 1)` захардкожен (`:973`), фолбэк `: 3` — захардкоженный баланс | средне |
| Загрузчики строгие в правильных местах: `Ruleset.getRaw` бросает, `ContentSet.load` проверяет id/дубли, `Scenario.expandedHexes` громко падает. Исключения — только `Setup.java:111,125` (см. B8) | ок |

---

# 9. Мелочи (сводно)

| Место | Проблема |
|---|---|
| `GameEngine.java:54, 97` | `RESERVE_ROUND_CAP = 8` захардкожен; `rounds.max: 7` / `rounds.min: 6` не читаются; `maxRounds` всегда = константе |
| `GameEngine.java:353, 360, 373` | Числа действий за ход (2/1/1) захардкожены — при том, что `actions.spec_per_turn` рядом читается из данных |
| `GameEngine.java:386-387` | Параметр `boolean distinct` в `playActions` не используется |
| `GameEngine.java:625` | `guard < 24` в massOpen — магический предохранитель, молча обрезает право открывать |
| `GameEngine.java:237-239` | «+1 келемий» супер-арсенала захардкожен, не из данных карты |
| `GameEngine.java:257-273` | `refillContainers`: `h.containers = 1` захардкожено |
| `GameEngine.java:774-781` | `superPartAvailable` зовёт `Predicates.check` без try/catch (в отличие от `superDeployReady` `:669-673` и `Objectives.requirementMet` `:44-48`) — `PredicateError` уронит партию |
| `GameEngine.java:660, 741, 803` | `content.get("super_objectives").byId(...)` без null-чека — NPE при рассинхроне контента |
| `Actions.java:600` | `for (int lv = 1; lv <= 4; lv++)` — число уровней захардкожено |
| `Actions.java:794-804` | `UnitToken unit = null; ... unit.hexId` — NPE при несуществующем uid в payload; патроны уже списаны (`:791`) и не возвращаются |
| `Actions.java:780-785` | «Первый ход бесплатен» реализован как «один бесплатный шаг любым жетоном», а правило §4.1 — «выбери ОДИН гекс: войска в нём двигаются бесплатно на свою скорость». Это другое правило. Возврат войска в запас не реализован |
| `Actions.java:339-341` | Размещение произведённых войск: не проверяется свободная ячейка, «2 сектора для техники»; вышки должны ставиться не на ЦУ, а на свободный сектор гекса стройки; «нет места → вариант недоступен» не реализовано |
| `Actions.java:1092-1094` | Награда шага науки за границей массива — `reward = null`, шаг молча пуст |
| `Actions.java:1097` | Ранг для `prize_cube` всегда считается по шагу 1 (`occupancy.get(track).get(0)`), независимо от шага приза |
| `Modules.java:105-107` | `moduleSwap` безусловно чистит `redPlacements`/`bluePlacements` в начале каждого Обновления — если агент затем пасует, размещённые модули потеряны (смена должна перекладывать, не сбрасывать) |
| `Scoring.java:46-48` | NPE, если в ruleset нет ни `kelium_per_vp`, ни `kelium_vp_each` |
| `Scoring.java:68-71` | Значения 1/2 ПО за оборот тайла продублированы фолбэками при наличии `economy.spawn_flip_start_vp/normal_vp` |
| `Action.java:29, 39` | `implemented()` и `legal()` не вызываются нигде — мёртвый API |
| `TurnContext.java:26` | `cuFreeMoveUsed` мёртв; лимит переноса ЦУ живёт в `journal.movedBuildingUids` (`Actions.java:507-516`); `actions.build.cu_free_move_per_turn` не читается |
| `CombatResolver.java:663-669` | `buildingCompensation` — захардкоженные 2/1/0 вместо ruleset |
| `Actions.java:29` | Комментарий «разрешение боя — заглушка» устарел (резолвер на 719 строк готов) |
| `GameEngine.java:42-44` | Javadoc-TODO про порт из Python устарел — всё портировано |

---

# 10. Тесты: покрытие

Отключённых/закомментированных проверок нет; проблема обратная — проверки мягкие и редкие:

- **CombatTest** (2 теста): «атака убила пехоту», «убийство ЦУ даёт жетон + отстройка». Не покрыто: ответный бой целиком (порядок по часовой, цели только атакующего, нет ответки на ответку, нет ответки за нейтралов), закрытый гекс, спрятанные войска, красные модули в бою (одна цель из двух / золото = обе), «строка не более раза за бой», скидки/наценки БП (`effCost`), супер-войско, снос нейтралов, компенсации и трофеи за здания.
- **FieldLayoutTest**: жёстко — только «сценарий загрузился» и «грядок ≥ players/2»; правила дизайнера (7–10 гексов на игрока, грядки = игрокам, ≥3 соседей старта, ≥2 свободных, дистанция ≥3) — печатные предупреждения, которые реально нарушаются в v2 (см. G6).
- **GeometryLiveTest**: позитив+негатив только для 5 предикатов из ~20 геометрических; сценарные `forbidden`/`blocked_edges` не покрыты; «общая стенка» тестится на самодельных гексах, минуя сценарную `neighborBySide`.
- **GameE2ETest / DesignerFixesTest**: статистические дымовые — не поймают ничего из разделов 1–6.
- Ноль покрытия: `Storage`, `Objectives`, `Passives` (числовые модификаторы), `Deck.cullForPlayers`, `Scenario` c `blocked_edges`/`forbidden`, `TokenStats` (`minerRaw(level)` — IndexOutOfBounds при уровне вне 1..4).

---

# 11. Рекомендуемый порядок починки (цена/эффект)

1. **B9** — одна строка в `HeuristicAgent.scorerFor`: оживляет готовую логику контейнеров.
2. **B6** — `offerSpec` и при пасе: пас не должен стоить победы.
3. **B1** — снять `!h.spawnFlipped` (в `Actions.adjacentGridWithKelium` и `HeuristicAgent.gridWithKeliumAt`): включает половину экономики и условие конца партии.
4. **B7** — живость через `Passives.effectiveHp` везде: убирает неубиваемых фантомов.
5. **B2 + B3 + B4** — «на поле vs в пуле» и возврат жетонов: война перестаёт быть необратимо разорительной.
6. **B8** — убрать тихую заглушку поля; отдельный ключ `content_versions.scenarios`.
7. **B5** — честный учёт состава частей супер-задания (закрывает бесплатную мгновенную победу).
8. **K1–K6** — вывести отнятые решения в `Choice`: `blind_discard`, `sci_track`, энергия (с моделью «гекс-исход»), `combat_victim`, оплата трофеями, часть супер-задания. Без этого и балансные прогоны, и RL упираются в потолок.
9. **E3 + E4** — эффекты через `canEnterHex` и реальный `TurnContext`.
10. **B10 + C1/C2** — многооперационные действия с наценками (стройка, бой) — согласованно, иначе C2 уронит партию.
11. Далее: E1/E2 (noop-эффекты и инертные пассивы — либо реализовать, либо изъять карты из колод до реализации), G1–G4 (forbidden в графе, воздушная ячейка, стороны для войск), D-раздел (рынок), I1 (санитизация стейта перед RL), I3, I4.

~~Отдельно — **вопросы к дизайнеру**~~ — все закрыты, см. раздел 12.

---

# 12. РЕШЕНИЯ ДИЗАЙНЕРА (11.08.2026) — обязательны к исполнению

Дизайнер ответил на все открытые вопросы. Источники правды (md-доки, `ruleset 1.4.0`, `boards.1.0.0.yaml`) уже обновлены под эти решения — код надо привести к ним.

## 12.1 Уничтожение ЦУ (закрывает C4) — новое каноническое правило

- **Прочность ЦУ — 3** (было 4; `boards.1.0.0.yaml` уже обновлён: `hp: 3`, `trophy: 0`).
- **ЦУ не даёт трофеев и не идёт в трофейное пространство.** Вся награда — жетон.
- **Жетон уничтожения ЦУ — по одному на игрока, лежит перед владельцем с начала партии.** Уничтоживший чужое ЦУ забирает **жетон владельца** себе; на жетоне **перманентные 3 ПО**.
- **Свой жетон тоже очковый:** если ЦУ игрока так и не уничтожили до конца партии, его собственный жетон приносит ему 3 ПО в подсчёте. → `Scoring` должен считать 3 ПО за КАЖДЫЙ жетон на руках (и чужой, и свой сохранённый), ключ `command_center.own_token_vp_if_cu_never_destroyed`.
- **Жертва немедленно: забирает своё ЦУ себе В ЗАПАС** (не авто-отстройка на том же гексе — `CombatResolver.java:693` переделать) **и получает 2 контейнера** (напечатаны на обороте жетона).
- **Проверка победы — в момент любого уничтожения ЦУ:** если у уничтожившего **уже есть на руках чей-либо чужой жетон** — немедленная военная победа. НЕ счётчик убийств (`cuKills >= 2` — убрать): проверяется факт владения чужим жетоном. Повторное уничтожение ЦУ, чей жетон уже забран третьим игроком, жетона не даёт, но проверка победы всё равно выполняется.

Уточнено дизайнером: **никакой особой механики возврата нет.** ЦУ — военное здание и возвращается в тот же запас, что и остальные военные здания; на поле оно возвращается **обычным действием Стройка по обычным правилам стройки**, как любое военное здание. Реализация: `destroy()` кладёт ЦУ в резерв владельца (hexId=null, урон снят), а `buildable()` предлагает ЦУ из резерва наравне с казармой/заводом/авиабазой. Авто-отстройку в `CombatResolver.java:693` убрать. Пока ЦУ в запасе — повторно уничтожить его нельзя (его нет на поле), что естественно тормозит «ферму жетонов».

## 12.2 Компенсация контейнерами (закрывает C5)

| Здание | Контейнеров |
|---|---|
| Казарма, Завод, **Авиабаза** | **1** (в коде авиабаза давала 2 — исправить) |
| Добытчики и энергостанции **№1, №3** | 1 |
| Добытчики и энергостанции **№2, №4** | **0** |
| ЦУ | 2 |

Данные добавлены в `ruleset 1.4.0` → `building_compensation_containers`; `CombatResolver.buildingCompensation` переписать на чтение из ruleset.

## 12.3 «Запертые старты» field_2p_v2 (закрывает G6) — это баг ДВИЖКА, не данных

Дизайнер: здание закрывает проход на гекс **только своей стенкой** (занятыми сторонами). Нейтрал на соседнем гексе, повёрнутый стенкой к грядке, мешает лишь **поставить добытчик с той стороны** — а пехота свободно заходит на гекс и встаёт в свободную ячейку. Если движок считает гекс с нейтралом непроходимым целиком — движок понял правило блокировки неверно. Починить:
- `canEnterHex` / логику «закрытого гекса»: блокировка — по-сторонняя (через занятые стороны зданий), не по-гексовая;
- предупреждение FieldLayoutTest «0 свободных соседей у старта» — ложная тревога, пересчитать «свободность» с учётом по-сторонней блокировки.

## 12.4 Вышка статична — подтверждено

`speeds.tower: 0` — задумано: вышки дёшевы и живучи, но не двигаются. Убрать `speeds` вышки из draft-опасений; код (`Actions.java:756` — юнит со скоростью 0 не двигается) корректен.

## 12.5 Добыча контейнера — правило УПРОЩЕНО (заменяет находки 3.14 / строку «контейнер» в K7)

Новое правило: **добытчик берёт 1 жетон контейнера ИЗ ЗАПАСА** — поиск ближайшего по полю, выбор при равенстве и прочее упразднены полностью. Контейнеры на поле подбираются только Движением. Доки уже обновлены («Действия — полный свод», СВОД). Код: выкинуть `takeAdjacentContainer`-поиск, ветка «контейнер» = `p.containers += 1` (жетон из общего запаса) + обычный цикл вскрытия.

## 12.6 Слепой сброс, наука, энергия и т.п. (K1–K6) — решений не требуют

Это баги реализации против уже записанных правил; чинить по разделам 1–2 без дополнительных согласований.
