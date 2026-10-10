# Lecturial 2

<img src="artwork/play-store-icon-512.png" alt="Иконка Lecturial 2" width="112">

Неофициальное Android-приложение для просмотра расписания Университета «Сириус»: занятия, преподаватели и аудитории в тёмном интерфейсе, с локальным кэшем, виджетом и уведомлениями.

**Android 8.0+ · Kotlin · Jetpack Compose · Material 3 · Apache 2.0**

## Возможности

- Расписание по дням, переход между неделями и возврат к сегодняшней дате.
- Время занятий, тип пары, преподаватели и аудитории; выделение текущей пары и оставшееся время.
- Обновление жестом pull-to-refresh и cache-first загрузка через Room. Офлайн доступны сохранённые недели.
- Выбор группы и преподавателя иностранного языка, собственные названия предметов.
- Виджет «Сегодня»: цвета полос, состав строк, размеры текста и отступы.
- Напоминания, уведомления о начале и завершении пары, уведомление с прогрессом текущего занятия.
- Четыре сохраняемые тёмные цветовые темы: «Сириус», «Океан», «Лес» и «Закат»; шрифт Manrope.

Доступные группы и результаты поиска загружаются с сайта расписания Университета «Сириус».

## Стек и версия

Параметры текущего комплекта исходников:

| Компонент | Значение |
| --- | --- |
| Версия приложения | 1.4.4, versionCode 23 |
| Минимальная версия Android | API 26 / Android 8.0 |
| compileSdk / targetSdk | 37 / 37 |
| JDK | 17 |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.1, встроенная поддержка Kotlin |
| Compose Compiler plugin | 2.3.21 |
| Compose BOM | 2026.08.00 |
| Material 3 | 1.5.0-alpha27 |
| Хранение | Room 2.8.4, DataStore 1.2.1 |
| Сеть | OkHttp 5.1.0, JSON |

## Быстрый запуск

1. Скачайте исходники или клонируйте свой репозиторий. Откройте корневую папку в Android Studio, совместимой с AGP 9.3.1.
2. Выберите JDK 17 для Gradle. Через SDK Manager установите Android SDK Platform 37.0 (API 37) и Build Tools 36.0.0.
3. Дождитесь Gradle Sync. Android Studio создаст локальный `local.properties` с путём к SDK. Для командной строки можно задать `ANDROID_HOME`.
4. Запустите конфигурацию `app` на устройстве или эмуляторе Android 8.0+.

PowerShell из корня проекта:

```powershell
.\gradlew.bat assembleDebug
```

Linux/macOS с установленными JDK и Android SDK:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

Результат: `app/build/outputs/apk/debug/app-debug.apk`.

Установка через Android Platform Tools:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.rassvet.lecturial.debug/com.rassvet.lecturial.MainActivity
```

Debug использует пакет `com.rassvet.lecturial.debug`, release — `com.rassvet.lecturial`. Они устанавливаются рядом и хранят данные отдельно. Для обновления установленного release нужна прежняя подпись.

## Проверки

В проекте уже есть модульные и инструментальные тесты:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Последняя команда требует устройства или эмулятора. Тест реального сайта по умолчанию отключён; для отдельного запуска:

```powershell
$env:LECTURIAL_LIVE_TEST = '1'
.\gradlew.bat testDebugUnitTest --tests '*SiriusLiveIntegrationTest' --rerun-tasks
Remove-Item Env:LECTURIAL_LIVE_TEST
```

Этот тест зависит от сети и текущего контракта сайта. Сборка сама по себе не подтверждает работу интерфейса и уведомлений на устройстве.

## Release-сборка

Скопируйте шаблон и заполните его значениями своего ключа локально:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

После заполнения `keystore.properties`:

```powershell
.\gradlew.bat assembleRelease bundleRelease
```

Выходные каталоги: `app/build/outputs/apk/release/` и `app/build/outputs/bundle/release/`. Без настройки подписи release не готов для установки или загрузки в магазин. Ключи и пароли исключены из Git; debug их не требует.

## Подробная структура

```text
Lecturial/
├── README.md                      # Описание, запуск, сборка и структура
├── LICENSE                        # Apache License 2.0
├── NOTICE                         # Авторство и сторонние материалы
├── THIRD_PARTY_NOTICES.md          # Шрифт, логотип и зависимости
├── CONTRIBUTING.md                # Правила участия
├── DESIGN.md                      # Правила интерфейса
├── docs/
│   ├── ARCHITECTURE.md             # Слои и поток данных
│   └── PUBLISHING.md               # GitHub и выпуск версии
├── licenses/Manrope-OFL.txt        # Лицензия встроенного шрифта
├── .github/
│   ├── ISSUE_TEMPLATE/             # Ошибка и предложение
│   └── pull_request_template.md    # Описание PR
├── .gitignore                     # Исключения для секретов и сборки
├── .gitattributes                 # Переносы строк и бинарные файлы
├── build.gradle.kts               # Версии Gradle-плагинов
├── settings.gradle.kts            # Репозитории и модуль app
├── gradle.properties              # Настройки Gradle и Android
├── gradlew / gradlew.bat           # Запуск закреплённой версии Gradle
├── gradle/wrapper/                 # Wrapper JAR и настройки
├── keystore.properties.example    # Шаблон подписи без секретов
├── artwork/                       # Иконка и материалы оформления
└── app/
    ├── build.gradle.kts            # SDK, версии, варианты и зависимости
    ├── proguard-rules.pro          # Оптимизация release
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/rassvet/lecturial/
        │   │   ├── LecturialApplication.kt   # Создание зависимостей
        │   │   ├── MainActivity.kt          # Вход в Compose
        │   │   ├── data/
        │   │   │   ├── ScheduleRepository.kt
        │   │   │   ├── local/
        │   │   │   │   ├── ScheduleDatabase.kt  # Room-кэш недель
        │   │   │   │   └── UserPreferences.kt   # DataStore-настройки
        │   │   │   └── network/
        │   │   │       └── SiriusScheduleDataSource.kt # Livewire и парсер
        │   │   ├── model/
        │   │   │   ├── ScheduleModels.kt       # Группы, занятия, дни и недели
        │   │   │   ├── ScheduleDisplay.kt      # Форматирование
        │   │   │   ├── LanguageTeacher.kt      # Выбор преподавателя языка
        │   │   │   ├── LessonAlertPlanning.kt  # Расчёт уведомлений
        │   │   │   └── WidgetAppearance.kt     # Настройки виджета
        │   │   ├── ui/
        │   │   │   ├── LecturialApp.kt         # Экраны и компоненты
        │   │   │   ├── ScheduleViewModel.kt    # Состояние и действия
        │   │   │   └── theme/LecturialTheme.kt  # Тема
        │   │   ├── notify/
        │   │   │   ├── LessonAlerts.kt         # Планирование и уведомления
        │   │   │   ├── LessonAlertReceiver.kt  # Системные события
        │   │   │   └── LessonLiveService.kt    # Прогресс текущей пары
        │   │   └── widget/TodayScheduleWidget.kt # Виджет и RemoteViewsService
        │   └── res/                  # Разметки виджета, шрифт, иконки, строки
        ├── debug/                    # Activity и manifest для Compose-тестов
        ├── test/                     # JVM-тесты моделей и сетевого адаптера
        └── androidTest/              # Compose UI и тесты репозитория на Android
```

Локальные `local.properties`, `keystore.properties`, `keystore/`, `.gradle/`, `.kotlin/`, `build/` и `publication/` не входят в публикуемые исходники.

## Архитектура и источник данных

`SiriusScheduleDataSource → ScheduleRepository → ScheduleViewModel → Compose UI`.

Адаптер работает с cookies, CSRF и структурированным Livewire-ответом [сайта расписания](https://schedule.siriusuniversity.ru/). Репозиторий сохраняет недели в Room, настройки хранятся в DataStore. Обновление кэша синхронизирует виджет и уведомления.

Подробнее: [архитектура](docs/ARCHITECTURE.md), [дизайн](DESIGN.md), [публикация](docs/PUBLISHING.md).

## Ограничения

- Приложение не является официальным проектом университета.
- Livewire-контракт сайта не является публичным API и может измениться.
- Офлайн доступны только ранее сохранённые недели; кэш может устареть.
- Уведомления зависят от разрешений и ограничений фоновой работы Android.
- Используется alpha-версия Material 3.

## Участие и лицензия

Предложения и ошибки принимаются через Issues. Правила изменений: [CONTRIBUTING.md](CONTRIBUTING.md).

Собственный код и документация — [Apache License 2.0](LICENSE). Copyright 2026 Lecturial 2 contributors. Лицензия допускает коммерческое использование и закрытые производные проекты при соблюдении её условий. Если требуется обязательное открытие распространяемых производных версий, рассмотрите GPL-3.0 до первой публикации.


Manrope — SIL OFL 1.1. Названия, логотипы и данные третьих лиц не перелицензируются: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Перед публичной публикацией проверьте разрешение на логотип «Сириуса».
