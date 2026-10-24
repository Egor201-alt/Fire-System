# Fire!

Лёгкий античит для ванильных приватных серверов Paper 26.3+. Работает только на событиях Paper, без ProtocolLib и PacketEvents.

## Установка
1. Собрать `gradle build` (Java 25) или взять `Fire.jar` из Actions/Releases.
2. Положить в `plugins/`, запустить сервер.
3. Первое время держать `general.mode: monitor` в `config.yml`: плагин только оповещает. Когда ложных срабатываний нет, поставить `enforce` и `/fire reload`.

## Файлы
- `config.yml`: режим, исключения, grace-окна, оповещения, Heat, наказания.
- `checks.yml`: настройки каждой проверки.
- `messages.yml`: все сообщения (MiniMessage).

## Настройки проверки (checks.yml)
- `enabled`: включена ли проверка.
- `alert`: слать ли оповещения.
- `cancel`: отменять ли действие или откатывать игрока (только в режиме enforce).
- `vl`: сколько нарушений добавляется за срабатывание.
- `decay`: на сколько VL падает за минуту.
- `heat`: вес проверки в Heat.
- `buffer`: порог буфера. Выше значение: меньше ложных срабатываний и позже реакция.
- Остальные ключи: свои параметры проверки (допуски, лимиты).

## Heat
Общий уровень подозрительности игрока от 0 до `heat.max`. Растёт с каждым срабатыванием (VL × вес проверки) и падает со временем. Действия по Heat задаются в `heat.actions`.

## Наказания (actions)
Выполняются, когда VL проверки пересекает порог `vl`. Поле `check`: `*`, id проверки (`speed`) или категория (`movement`, `combat`, `world`, `player`).
Действия в `do`:
- `setback`: вернуть на последнюю безопасную позицию.
- `kick` или `kick:<текст>`: кик.
- `cmd:<команда>`: команда от консоли, плейсхолдеры `{player}`, `{check}`, `{vl}`, `{heat}`.
- `freeze:<секунды>`: заморозка.
- `message:<текст>`: сообщение игроку.
- `broadcast:<текст>`: сообщение персоналу.
- `reset`: сбросить VL и Heat.

## Команды (`/fire`)
help, alerts [on|off], status, checks, info <игрок>, history <игрок> [n], heat <игрок>, reset <игрок> [проверка], toggle <проверка>, watch <игрок>, exempt <игрок> <минуты|off>, debug <проверка|off>, tp <игрок>, reload.

## Права
- `fire.admin`: всё (по умолчанию op).
- `fire.alerts`, `fire.info`, `fire.reset`, `fire.reload`, `fire.toggle`, `fire.watch`, `fire.exempt`, `fire.debug`, `fire.tp`: отдельные команды.
- `fire.bypass`: игрок не проверяется.

## Проверки
- Movement: Timer, Aim, Speed, Flight, Hover, Ground, Phase, Sprint, Elytra, Velocity.
- Combat: Reach, Hitbox, MultiAura, Clicker, Criticals.
- World: FastBreak, Nuker, BlockVisible, InteractReach, Scaffold, FastPlace.
- Player: InvMove.

## Замечания
- Speed и Flight считают предсказание физики (трение блока, гравитация, атрибуты), а не фиксированные лимиты.
- Без пакетов нельзя увидеть часть читов (например, Blink). Hover ловит игрока, который висит в воздухе без движения.
- `general.leniency` умножает допуски. Если у игроков высокий пинг или лаги, поставить больше 1.0.
