<div align="center">
  <img src="./artwork/readme-header.svg" width="100%" alt="Lecturial 2 — расписание «Сириуса», оценки из личного кабинета и план дня" />

<a href="https://github.com/neckmnull/Lecturial-2/releases/latest"><img src="https://img.shields.io/github/v/release/neckmnull/Lecturial-2?style=flat-square&amp;label=%D0%B2%D0%B5%D1%80%D1%81%D0%B8%D1%8F&amp;labelColor=11141A&amp;color=345EBC" alt="Версия" /></a> <img src="https://img.shields.io/badge/Android-8.0%2B-345EBC?style=flat-square&amp;labelColor=11141A" alt="Android 8.0+" /> <a href="./LICENSE"><img src="https://img.shields.io/badge/лицензия-Apache%202.0-345EBC?style=flat-square&amp;labelColor=11141A" alt="Лицензия Apache 2.0" /></a>

**Неофициальное приложение с расписанием Университета «Сириус» для Android.**<br />
Работает без интернета, показывает оценки из личного кабинета и само раскладывает домашку по свободному времени.

<a href="https://github.com/neckmnull/Lecturial-2/releases/download/v1.13.3/Lecturial-2-v1.13.3.apk"><img src="https://img.shields.io/badge/Скачать_APK-v1.13.3-345EBC?style=for-the-badge&amp;logo=android&amp;logoColor=11141A&amp;labelColor=EEF1F7" alt="Скачать Lecturial 2 v1.13.3" /></a>
<a href="https://t.me/lecturial_2"><img src="https://img.shields.io/badge/Telegram--канал-11141A?style=for-the-badge&amp;logo=telegram&amp;logoColor=8AABFF" alt="Telegram-канал @lecturial_2" /></a>

<sub><a href="./README.en.md">English version</a></sub>

</div>

<p align="center">
  <img src="./artwork/demo.gif" width="300" alt="Короткий ролик: текущая пара в ленте, свайп по дням, оценки и план дня" />
</p>

<details>
<summary><b>Скриншоты</b></summary>
<br />
<p align="center">
<img src="./artwork/screenshots/1-schedule.png" width="48%" alt="Расписание лентой: текущая пара с полосой прогресса" /> <img src="./artwork/screenshots/2-by-day.png" width="48%" alt="Расписание по дням: полоса недели и пары выбранного дня" />
</p>
<p align="center">
<img src="./artwork/screenshots/3-study.png" width="48%" alt="Вкладка «Учёба»: средний балл, посещаемость и оценки по предметам" /> <img src="./artwork/screenshots/4-day-plan.png" width="48%" alt="План дня: пары, домашка и свободное время" />
</p>
</details>

<p align="center"><sub>В ролике и на скриншотах выдуманные группа, преподаватели и оценки.</sub></p>

## Что умеет

- **Расписание** лентой или по дням: какая пара сейчас, сколько до конца, где окна. Тьюторские часы и прочие мероприятия не мешают.
- **Личный кабинет.** Оценки и пропуски прямо на карточках пар, а во вкладке «Учёба» — посещаемость, домашние задания, сессия, уведомления и новости. Новые оценки приходят пушем.
- **План дня.** Домашка к следующей паре, дедлайны и подготовка к завтрашним парам раскладываются по свободному времени и перестраиваются, когда отмечаешь сделанное.
- **Без интернета.** Загруженные недели хранятся на телефоне.
- **Виджет «Сегодня»**, напоминания перед парой и уведомление с текущей парой.
- **Календари.** Пары и планы попадают в Google Календарь или Outlook.
- **Статистика.** Свободные аудитории, нагрузка групп и преподавателей, общие окна.
- **Оформление.** Material You, 16 готовых тем, свои палитры и живые обои.

## Установка

1. Скачай [`Lecturial-2-v1.13.3.apk`](https://github.com/neckmnull/Lecturial-2/releases/download/v1.13.3/Lecturial-2-v1.13.3.apk). Если приложение уже стоит, обнови его прямо в нём: **Настройки → О приложении → Обновления**.
2. Если Android спросит, разреши установку из браузера или файлового менеджера.
3. Открой APK и установи. При первом запуске приложение поможет выбрать группу и всё настроить.

<details>
<summary>Как проверить, что APK настоящий</summary>

```text
SHA-256  e66ed3184b80a0cfe7abe70aec00505801f059bdf0c3efc336aadd130fa11d70
Пакет    com.rassvet.lecturial2
```

Все релизы подписаны одним ключом, поэтому обновления ставятся поверх и данные сохраняются.
Если Android отказывается обновлять, значит, стоящая сейчас версия подписана другим ключом.
Lecturial 1 (`com.rassvet.lecturial`) — отдельное приложение, оба могут стоять рядом.
Все версии и списки изменений — на [странице релизов](https://github.com/neckmnull/Lecturial-2/releases).

</details>

## Вопросы

Подробно обо всех функциях — в [руководстве пользователя](./docs/USER_GUIDE.md).

**Безопасно ли входить в личный кабинет?**
Вход идёт на странице lks.siriusuniversity.ru внутри приложения, пароль видит только сайт университета.
Сессия хранится на телефоне в зашифрованном виде, оценки — только на телефоне. Выйти можно во вкладке «Учёба»,
подробности — в [SECURITY.md](./docs/SECURITY.md).

**Это официальное приложение?**
Нет, это независимый проект. Расписание берётся с сайта университета, и если сайт изменится,
загрузка может сломаться до следующего обновления.

**Есть ли версия для iPhone?**
Нет, только Android 8.0 и новее.

**Где новости и куда писать?**
Новые версии выходят в [Telegram-канале @lecturial_2](https://t.me/lecturial_2). Ошибки и идеи —
в [Issues](https://github.com/neckmnull/Lecturial-2/issues).

## Для разработчиков

Kotlin, Jetpack Compose и Material 3, Room, DataStore, OkHttp. Собрать debug-версию:

```bash
git clone https://github.com/neckmnull/Lecturial-2.git
cd Lecturial-2
./gradlew assembleDebug
```

Как всё устроено и где что менять — в [руководстве разработчика](./docs/DEVELOPER_GUIDE.md).
Сборка, проверки и требования к изменениям — в [CONTRIBUTING.md](./CONTRIBUTING.md), правила интерфейса — в [DESIGN.md](./docs/DESIGN.md),
выпуск релизов и встроенное обновление — в [PUBLISHING.md](./docs/PUBLISHING.md) и [UPDATER.md](./docs/UPDATER.md).
Какие данные хранятся, куда уходят и как защищены — в [SECURITY.md](./docs/SECURITY.md).

---

<div align="center">
<sub>
Код и документация — под лицензией <a href="./LICENSE">Apache 2.0</a>, шрифт Manrope — SIL OFL 1.1,
сторонние материалы — в <a href="./docs/THIRD_PARTY_NOTICES.md">THIRD_PARTY_NOTICES.md</a>.<br />
<a href="https://t.me/lecturial_2">Telegram</a> · <a href="https://github.com/neckmnull">@neckmnull</a>
</sub>
</div>
