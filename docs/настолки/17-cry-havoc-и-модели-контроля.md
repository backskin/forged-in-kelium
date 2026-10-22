# Cry Havoc: понятия конфликтной игры за территории

Grant Rodiek, Michał Oracz, Michał Walczak; Portal Games, 2016.
Rulebook: Grant Rodiek, Paul Grogan. 16 полос, английский.
Прочитано подряд и полностью.

Из всей библиотеки это **ближайшая к нам игра по устройству**:
асимметричные фракции, борьба за регионы, кристаллы как источник очков,
здания, бои, колода у каждой фракции своя. Поэтому разбор — не про
подачу, а про **понятия**: что здесь считается контролем, занятостью,
смертью, и как эти понятия сцеплены.

---

## I. Занятость и контроль — два разных свойства клетки

Главный терминологический урок книги.

> **Occupied:** A Region is considered occupied if there are **Unit tokens**
> in the Region.
> • Regions with only Trog Nest or Trog War Party tokens are **not**
>   considered occupied.
> • Regions with only Structures are **not** considered occupied.
> • Regions with only a Control token are **not** considered occupied.

> **Control:** A Region is considered controlled by the player who has
> a **Control token** there.

Занятость создают **только фигурки**. Здание, маркер контроля и жетоны
туземцев занятости не создают — и каждый случай назван отдельно, потому
что каждый выглядит как занятость.

Контроль — это **факт наличия маркера**, а не вывод из расстановки.

Отсюда четыре возможных состояния региона, и все они законны:

| Занят | Контролируется | Что это значит |
|---|---|---|
| нет | нет | пустой регион |
| нет | да | ушёл, но оставил маркер — очки идут |
| да | нет | стоят чужие или ещё не взял контроль |
| да | да | нормальное владение |

Второе состояние — самое важное: **можно уйти и продолжать получать очки**.
Вся стратегия Humans на этом построена: «their Skills allow them to
quickly expand Control **without actually moving Units into the Regions**».

---

## II. Три модели контроля — и выбор, который придётся сделать нам

Библиотека даёт три несводимых решения одной задачи.

### Модель 1: контроль вычисляется из расстановки («Серп»)

> Считается, что вы контролируете территорию, если на ней находится хотя бы
> одна ваша фигурка **или** если на ней есть ваше здание, **но при этом нет
> ни героев, ни рабочих, ни роботов противника**.

- **Плюс:** ничего не нужно отмечать, состояние видно глазом.
- **Минус:** пересчитывается непрерывно; здание даёт контроль, пока враг
  не пришёл; требует оговорок про каждый тип фигурки.

### Модель 2: контроль = единственность присутствия («Циркадианцы»)

> Регионы, в которых присутствует **только одна** фракция, находятся под
> её контролем. Если в регионе присутствует несколько фракций,
> **он никем не контролируется**.

- **Плюс:** предельно просто, симметрично, без маркеров.
- **Минус:** любое вторжение мгновенно обнуляет контроль у обоих;
  «ничей» регион — частое состояние.

### Модель 3: контроль = маркер (Cry Havoc, «Эклипс»)

> A Region is considered controlled by the player who has a Control token
> there.

- **Плюс:** контроль виден, не пересчитывается, меняется **только
  по явному правилу**; позволяет уйти, сохранив контроль; позволяет
  контролю и занятости расходиться.
- **Минус:** нужны маркеры и нужно прописать **все** случаи их постановки
  и снятия.

Cry Havoc прописывает постановку маркера ровно в двух местах: шагом 3
в конце перемещения («if your Units are present in a Region with no Units
belonging to other players, **immediately** gain Control») и как награду
за победу в первой боевой цели. И снятие — тоже явно, включая курьёзный
случай:

> If for any reason during the Action phase, all Units from one player
> in a Region with a Battle token are eliminated prior to Battle Resolution,
> **a Battle does not take place.** Immediately remove the Battle token and
> place a Control token for the player with Units remaining.

**Для «Кристалла раздора»:** модель 3. Наша игра про накопление и про
выход из регионов; вычисляемый контроль заставит пересчитывать поле после
каждой фигурки, а маркер позволит отойти, не потеряв добычу, — и создаст
осмысленный выбор «держать или бросить».

---

## III. Владение зданием при потере контроля — третий ответ

Та же задача, что в «Серпе», решена иначе.

| Игра | Что происходит со зданием, когда регион потерян |
|---|---|
| «Серп» | Здание остаётся ваше и **работает всегда**; территорию контролирует враг, но свойством здания не пользуется. Исключение — мельница. |
| «Циркадианцы» | Здание **разрушается** в бою, возвращается на планшет, **с компенсацией**: вы получаете обратно всё, что оно закрывало. |
| **Cry Havoc** | Здание **остаётся и не разрушается, но замораживается**: «the Structure remains, but the owning player **cannot Activate it** … until they **regain Control** of the Region». |

Три разных ответа, и каждый задаёт свой характер игры:
в «Серпе» здания — вечный актив; у «Циркадианцев» — расходный;
в Cry Havoc — **заложник территории**.

Третья модель самая выразительная: она делает отвоевание региона осмысленным
(вернёшь — вернёшь и здание), не наказывая за потерю безвозвратно.

---

## IV. Смерть, резерв, плен — экономика фигурок

Здесь понятия сцеплены в одну систему, и это стоит разобрать целиком.

> **Kill:** Units that are Killed are placed in their owner’s **Reserve**.
> **Reserve:** A player’s supply of Units available for the Recruitment
> action. **The Reserve is limited!** If a player’s Reserve is empty because
> all of their Units are **Prisoners or on the board**, they cannot Recruit.

Смерть — **не удаление из игры**, а возврат в резерв. Фигурка убитая
и фигурка непостроенная лежат в одном месте и неразличимы.

Из этого следует: убийство врага **не наносит ему долговременного урона**,
оно лишь сбрасывает его с поля. Значит, нужен второй механизм, который
урон наносит, — и он есть:

> **CAPTURE PRISONERS:** The player with the most Units in this Objective
> immediately takes one enemy Unit involved in the Battle and places it
> in front of them. **This captured Unit cannot be Recruited by its owner
> as it is not a part of their Reserve.**

Плен — это изъятие фигурки **из экономики**, а не с поля. И выкуп стоит
очков:

> …each player may **lose 2 Victory Points** per Prisoner they want to return
> to their Reserve. <…> **You cannot return a Prisoner if you do not have
> enough VP to pay for their return.**

Плюс пленник приносит очки тому, кто его держит: «players Score 1VP for
every Prisoner they have».

Итого три разных состояния фигурки, и все различены словами:
**на поле** → **в резерве** (доступна вербовке) → **в плену** (недоступна,
приносит очки чужому, выкупается за очки).

**Для нас:** если у нас есть уничтожение фигурок, надо решить, возвращаются
ли они в запас (тогда бой — временное вытеснение) или выбывают (тогда бой
необратим), и нужен ли третий статус между ними.

---

## V. Карта как ресурс и карта как эффект

> **Important:** When discarding cards for the Movement, Recruit, or Build
> Actions, **ignore any Tactic Text on them!**

Одна и та же карта имеет два употребления:
- **сброшенная в оплату** — считаются только её символы, текст молчит;
- **разыгранная в бою** — работает текст.

Тот же приём у «Циркадианцев», но там он назван двумя глаголами
(«использование» и «розыгрыш»), а здесь — правилом об игнорировании
текста. Второй способ хуже: он требует помнить оговорку, тогда как разные
глаголы сами напоминают о различии.

Исключение из правила помечено **на самом компоненте**:

> Some cards provide additional bonuses when discarding them for Movement,
> Recruit, or Build Actions. Look for the ⚡.

То есть «текст молчит» — правило по умолчанию, а молния — видимая метка
исключения. Игроку не нужно помнить список.

---

## VI. Действие как обмен карт на очки действия

Устройство, стоящее внимания:

> Discard any number of cards from your hand and gain **Movement points**
> equal to the number of Movement icons on the discarded cards. Each Movement
> point allows you to Move one of your Units from any Region to an adjacent
> Region. **You can Move more than one Unit, and each Unit can be Moved
> multiple times.**

> Example: The Human player discards two cards showing a total of four
> Movement Points. They may Move **one Unit four times, four Units one time,
> or any combination of this.**

Правило + **исчерпывающий перечень способов потратить** одним примером.
Ровно то же в «Серпе» («1 действие, 2 действия или 0 действий»). Это
приём, снимающий главный вопрос новичка: «а можно ли делить?».

Три действия (Move / Recruit / Build) устроены **по одной формуле**:
сбрось карты → получи очки соответствующего вида → трать по одному очку
на одну операцию. Различаются только операцией. Один раз понял — понял
все три.

---

## VII. Боевой регион — термин как пучок ограничений

> A Region with a Battle token is known as a **Battle Region**.

И дальше — список из шести следствий, каждое отдельной строкой:

> • **No further Units** from any player may enter any Region with a Battle
>   token.
> • Place one of the **Attacker’s Units on top of the Battle token** to
>   remember who the Attacker is.
> • Structures **cannot be Built** in a Battle Region.
> • Structures **cannot be activated** in a Battle Region unless explicitly
>   noted.
> • The defending player **may** use a Movement Action to leave a Battle
>   Region. However, they may only Move Units out of the Region **in excess
>   of twice the number of Units belonging to the Attacker**.

Вот как надо вводить термин: одно предложение определения, затем
**закрытый список прав и запретов**, которые из него следуют. После этого
слово «Battle Region» можно употреблять где угодно — оно самодостаточно.

Отдельно стоит правило удержания через **удвоение**:

> Example: The Pilgrim player Moves 3 Units into a Region with 8 Human Units.
> On the Humans’ turn, they may Move up to **2 Units** out of the Region
> (as 3 Pilgrim Units ‘block’ 6 Human Units).

Три атакующих связывают шестерых. Числовая асимметрия, делающая нападение
малыми силами осмысленным.

---

## VIII. Порядок разрешения как самостоятельное правило

Бой разрешается по трём целям **строго сверху вниз**, и порядок значит
больше, чем кажется:

> Resolve the Battle Objectives from top to bottom:
> 1. **REGION CONTROL** — The player with the most Units in this Objective
>    gains Control and immediately scores 2 VP. **The Region Control remains
>    with that player even if all of their Units are eliminated during the
>    subsequent Objectives.**
> 2. **CAPTURE PRISONERS** — …
> 3. **ATTRITION** — Each player kills one enemy Unit for each of their own
>    Units placed on this Objective. Players resolve Attrition
>    **simultaneously as it does not affect the resolution of the Battle**.

Победа в первом пункте **не отменяется гибелью в третьем**. Это не
техническая деталь, а суть механики: игрок распределяет силы между
«взять регион», «взять пленного» и «нанести урон», и может выиграть
регион ценой полного истребления.

И у каждой цели **своя ничья**:

| Цель | Ничья |
|---|---|
| Region Control | побеждает **защитник** |
| Capture Prisoners | **никто** не берёт пленного |
| Attrition | не возникает — разрешается одновременно |

Плюс отдельно оговорён случай пустоты:

> If **neither** player places Units in the Region Control Objective,
> the **defending** player wins the Objective.

А одновременность Attrition **обоснована**: она законна именно потому, что
не влияет на исход. Это важная мысль: одновременность допустима там, где
результат не зависит от порядка, — и стоит это проговаривать.

Для третьей стороны, вмешавшейся извне:

> If a player who is not the Attacker or Defender participates in the
> Attrition Objective, their Units or tokens are **always resolved last**.

---

## IX. Момент, к которому привязано последствие

Тонкое и очень полезное правило:

> A player may choose to **continue Movement through** Regions with no enemy
> Units and no Trog tokens, even if the Region is **under an opponent’s
> Control**. Players **do not take Control** of Regions or **resolve
> Exploration tokens** in Regions their Units **do not end their Movement
> phase in**.

Проход через регион и остановка в нём — разные события. Контроль и разведка
привязаны к **окончанию перемещения**, а не к факту входа. Без этой оговорки
неясно, срабатывает ли всё по дороге.

Сравните с «Серпом», где привязка обратная и тоже явная:

> Если вы перемещаете героя на территорию с жетоном приключения,
> **его перемещение заканчивается** и на текущем ходу он уже больше
> не может переместиться.

Там вход **прерывает** движение, здесь — нет. Оба решения законны,
но каждое требует прямого высказывания.

---

## X. Подсчёт очков как предмет решения игрока

Редкая и сильная механика:

> **ENABLE SCORING ACTION.** Each player has a **single card** in their deck
> that, when played, allows them to take the Enable Scoring Action. <…>
> **Scoring can only be enabled once per Round!**
> At the end of the Round, the player who Enabled Scoring will score
> **1VP for every Region they Control**. Then, **all players** will score
> 1VP for each Crystal in every Region they Control.

Момент подсчёта назначает игрок, и тот, кто его назначил, получает
дополнительную награду — но подсчёт происходит **для всех**. Игрок
выбирает время, когда его собственное положение лучше чужого.

И плата за это решение названа прямо: карта, использованная для включения
подсчёта, **не может быть использована как ресурс** — «the card cannot be
used for the Movement, Recruit, or Build Actions».

В финальном раунде выбор отбирается:

> Scoring is **always Enabled in the final Round and cannot be Enabled by
> players**. <…> Players will score VP for Crystals, but **nobody will score
> VP for Region Control.**

---

## XI. Конец игры, назначаемый гонкой очков

Механика, прямо относящаяся к нашей гонке за звёздами:

> Reveal the next stack of unrevealed Event token(s)… if a player’s Score
> token **passes any unresolved Event tokens** on the track, the passed Event
> tokens are **immediately stacked on top of** the next unresolved Event
> token on the track.

> If, during the game, **no player’s Score token passes an unresolved Event**,
> then the game will last 5 Rounds. However, if an unresolved Event token is
> **passed during Scoring, the game will end sooner**.

То есть: чем быстрее кто-то набирает очки, **тем раньше кончается партия**.
Лидер сам сокращает себе время. Это встроенный догоняющий механизм,
не отнимающий у лидера очки, — он лишь меняет длину игры.

Ничья — каскадом, и вторая ступень тематична:

> The player with the most Victory Points is the winner! In the case of a tie,
> the player with **the most Prisoners** wins. If still tied, the player who
> went **later in Initiative order** wins.

---

## XII. Состояние «истощено» — и что оно у них значит

Прямо относится к нашему термину.

> During your turn, you can use any number of your Skills **that have not yet
> been exhausted**. Using a Skill **does not count as an Action** and each
> Skill can only be used **once per Round**, unless explicitly stated.
> Once a Skill is used, **turn it 90 degrees counterclockwise** to indicate
> it has been used. Exhausted Skills are **refreshed at the beginning of each
> Round**. Skills can only be used during the Actions phase. Skills **must be
> used before or after an Action, but never during**.

Шесть высказываний об одном состоянии:
1. условие использования (не истощено);
2. использование умения **не является действием**;
3. квота — раз за раунд;
4. физический носитель состояния — поворот на 90°;
5. когда состояние снимается — в начале раунда;
6. **когда именно можно использовать** — до или после действия, но
   не внутри него.

Шестой пункт — тот, который обычно забывают. «Никогда во время действия»
снимает целый класс вопросов вроде «можно ли применить умение после того,
как я уже начал перемещение».

**Для нас:** наше «истощение» плитки — это **другое** состояние. У Cry Havoc
exhausted снимается каждый раунд, у нас истощение постоянно. Значит, слово
надо либо развести («истощена» — временно, «исчерпана» — навсегда), либо
выбрать одно и держаться его.

---

## XIII. Правила-затычки, каждое в одну строку

Раздел CLARIFICATIONS — это набор ответов на вопросы, которые
обязательно возникнут:

> **Golden Rule:** If the text of any card, Event token, or Exploration token
> contradicts the rule book, **the card or token takes priority**.

> **A player can play a card even if they cannot apply its effect.**

Второе — драгоценное. Оно разрешает **бесполезный ход** и тем самым снимает
вопрос «законно ли играть карту ради сброса». Без него каждая такая ситуация
превращается в спор.

> When revealing the Trog War Party/Nest token, **if there are not enough
> Trog Unit miniatures**, place as many as there are available and the full
> number of Crystals. **If there are no Trogs at all**, place all Crystals
> and no Battle is declared. The player moving into the Region **must return
> one Unit to their Reserve**.

Двухступенчатое исчерпание запаса с разными последствиями на каждой
ступени — и на второй ступени игрок всё равно несёт потерю.

> **You can never place Crystals or Build Structures in a Headquarters
> Region!**

---

## XIV. Описание фракций через стиль игры, а не через правила

FACTIONS PRIMERS — отдельная глава, где **нет ни одного правила**. Есть
только ответ на вопрос «во что я буду играть, если возьму эту фракцию»:

> A successful Trog player **must carefully manage their Reserves**. Trogs
> can use their Skills to remove Units from Regions in order to place, move,
> and resolve Trog Nest tokens. In doing so, Trogs are able to **ambush the
> invading species at the time and place of their choosing**, which makes up
> for their **lack of powerful Structures**.

> Whereas other races must **maintain Control** of a Region to score its
> Crystals, the Pilgrims are able to **extract the Crystals**, then use them
> to score Victory Points or activate powerful Skills.

Каждый абзац называет: чем фракция сильна, чем слаба, и **какое отношение
к базовому правилу она меняет**. Пилигримы — единственные, кто нарушает
связку «контроль → очки за кристаллы»: они кристалл забирают.

Вот это и есть асимметрия по-настоящему: не «+1 к чему-то», а **иное
отношение к центральному правилу игры**.

---

## XV. Что отсюда идёт в «Кристалл раздора»

### Понятия, которые надо развести

1. **Занятость** и **контроль** — два независимых свойства клетки.
   Занятость создают только фигурки; перечислить всё, что занятости
   **не** создаёт (здание, маркер, кристалл, истощённая плитка).
2. **Контроль — маркером**, а не вычислением. Прописать **все** случаи
   постановки и снятия маркера, включая курьёзный (все чужие фигурки
   исчезли до боя).
3. **Владение зданием** и **право его использовать** — разные вещи.
   Взять модель Cry Havoc: здание остаётся владельцу, но **заморожено**,
   пока регион не под его контролем.
4. **Истощена** (снимается) против **исчерпана** (навсегда) — выбрать
   одно слово на одно состояние и не смешивать.

### Правила, которые надо написать

5. **Куда уходит уничтоженная фигурка** — в запас (вытеснение) или из игры
   (необратимо). Если в запас — нужен второй механизм настоящего урона.
6. **Закрытый список прав и запретов** для каждого состояния клетки
   («спорная», «истощённая»), как у Battle Region.
7. **К чему привязано последствие** — к входу в клетку или к окончанию
   перемещения в ней. Сказать прямо.
8. **Порядок разрешения** при нескольких одновременных исходах, и явно:
   что уже полученное не отменяется последующими шагами.
9. **Своя ничья на каждую развязку**, а не одна на всю игру.
10. **Одновременность допустима только там, где порядок не влияет
    на результат** — и это стоит написать как обоснование.
11. **Можно совершать действие, не получая от него выгоды** — разрешить
    явно.
12. **Двухступенчатое исчерпание** любого запаса, с разными последствиями
    на ступенях.
13. **Правило приоритета** текста компонента над книгой — одной строкой.

### Устройство, которое стоит перенять

14. **Одна формула для всех основных действий**: трата ресурса → очки
    действия → по очку на операцию. Плюс пример с перечнем всех способов
    потратить.
15. **Момент подсчёта очков как решение игрока** — с наградой тому, кто
    его назначил, и с платой за это решение.
16. **Ускорение конца партии лидером**: чем быстрее кто-то набирает,
    тем короче игра. Догоняющий механизм, не отнимающий очков.
17. **Удержание через кратность**: малые силы связывают большие.
18. **Глава о фракциях без правил** — чем сильна, чем слаба, **какое
    общее правило она нарушает**.
