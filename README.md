# Fire!

Античит для ванильных приватных серверов Paper 26.3+. Работает на событиях Paper и, если установлен, на PacketEvents 2.14.0 или новее (первая версия с поддержкой 26.3). Без PacketEvents плагин запускается, но проверки на уровне пакетов отключены.

## Установка
1. Поставить PacketEvents 2.14.0+ в `plugins/`.
2. Поставить `Fire.jar` (сборка: `gradle build`, Java 25, или Artifacts в GitHub Actions).
3. Режим по умолчанию: `general.mode: enforce` (откаты, отмены действий, наказания). Для сухого прогона поставить `monitor` и сделать `/fire reload`.

ProtocolLib не нужен. Он делает то же, что PacketEvents, а два пакетных слоя только добавят нагрузки и конфликтов.

## Файлы
- `config.yml`: режим, исключения, grace-окна, оповещения, Heat, наказания.
- `checks.yml`: настройки каждой проверки.
- `messages.yml`: все сообщения (MiniMessage).

## Настройки проверки (checks.yml)
- `enabled`, `alert`, `cancel` (отменять действие или откатывать игрока в режиме enforce).
- `vl`: сколько нарушений за срабатывание. `decay`: сколько VL уходит за минуту. `heat`: вес в Heat.
- `buffer`: порог буфера. Выше значение: меньше ложных срабатываний, позже реакция.
- Остальные ключи: допуски и лимиты проверки.
- `general.leniency` умножает все допуски. Меньше 1.0 строже (по умолчанию 0.9), больше 1.0 мягче.

## Наказания (actions в config.yml)
Срабатывают, когда VL проверки пересекает порог `vl`. `check`: `*`, id проверки (`speed`) или категория (`movement`, `combat`, `world`, `player`, `packet`).
Действия в `do`: `setback`, `kick`, `kick:<текст>`, `cmd:<команда>` (плейсхолдеры `{player}`, `{check}`, `{vl}`, `{heat}`), `freeze:<секунды>`, `message:<текст>`, `broadcast:<текст>`, `reset`.
Heat считается отдельно и имеет свои пороги в `heat.actions`.

## Команды (`/fire`)
help, alerts [on|off], status, checks, info <игрок>, history <игрок> [n], heat <игрок>, reset <игрок> [проверка], toggle <проверка>, watch <игрок>, exempt <игрок> <минуты|off>, debug <проверка|off>, tp <игрок>, reload.

## Права
`fire.admin` (всё, по умолчанию op), `fire.alerts`, `fire.info`, `fire.reset`, `fire.reload`, `fire.toggle`, `fire.watch`, `fire.exempt`, `fire.debug`, `fire.tp`, `fire.bypass`.

## Что ловит (по функциям читов вроде Meteor Client)
| Функция клиента | Проверка |
| --- | --- |
| Flight, Glide, Spider | Flight, Hover, Climb, Ground |
| AirJump | AirJump |
| HighJump | HighJump |
| Step | Step |
| Speed, Strafe | Speed, Strafe |
| Sprint (омни) | Sprint (по нажатию W из PlayerInput) |
| NoSlow | NoSlow |
| Jesus, быстрая вода | Jesus (check `liquid`) |
| NoFall, AntiHunger | Ground |
| Timer, Blink | Timer (по времени прихода пакетов) |
| Velocity / AntiKB | Velocity |
| Phase, PacketFly, Teleport | Phase, BadPackets |
| ElytraFly, ElytraBoost | Elytra |
| Boat Fly, EntitySpeed | Vehicle |
| Reach, Hitboxes, KillAura | Reach, Hitbox, MultiAura, NoSwing, Aim |
| Criticals | Criticals |
| AutoClicker, TriggerBot | Clicker (по пакетам с точностью до микросекунд) |
| AutoTotem, ChestStealer, InventoryTweaks | InvSpeed |
| InvMove | InvMove |
| AirPlace | AirPlace |
| Scaffold, Tower | Scaffold, Tower, FastPlace, BlockVisible |
| Nuker, Reach по блокам | Nuker, FastBreak, InteractReach |
| XRay | XRay (статистика скрытых руд) |
| Fly через abilities | BadPackets |

Не ловится по определению: ESP, Fullbright, Freecam, чистый Xray через текстурпаки (кроме статистики добычи), FakePlayer.

## Замечания
- Speed, Strafe и Flight считают предсказание физики (трение блока, гравитация, атрибуты). Это точнее лимитов, но чувствительнее к необычным блокам и механикам. Если игроки жалуются на откаты, смотри `/fire history <игрок>` и поднимай `buffer` или `tolerance` конкретной проверки.
- Исключения: креатив, спектатор, полёт, транспорт, элитры, возрождение, телепорты, взрывы, поршни, ветряной заряд, булава и т.д. (grace-окна в `config.yml`).
