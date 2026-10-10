# Сторонние материалы

Apache 2.0 распространяется на собственный код и документацию Lecturial 2. Сторонние материалы сохраняют свои условия использования.

## Manrope

Файлы `app/src/main/res/font/manrope_*.ttf` — шрифт Manrope.

- Правообладатель: Copyright 2018 The Manrope Project Authors.
- Источник: https://github.com/googlefonts/manrope.
- Лицензия: SIL Open Font License 1.1.
- Полный текст: [licenses/Manrope-OFL.txt](licenses/Manrope-OFL.txt).

## Названия, логотипы и расписание «Сириуса»

В проекте присутствует `app/src/main/res/drawable-nodpi/onboarding_sirius_logo.png`, а также вспомогательный файл `artwork/render-onboarding-logo.html`. Подтверждение разрешения на распространение логотипа в текущем комплекте отсутствует. Перед публичной публикацией получите разрешение правообладателя или замените логотип собственной графикой и обновите связанные упоминания. Apache 2.0 не предоставляет прав на этот логотип.

Расписание загружается с https://schedule.siriusuniversity.ru/. Лицензия проекта не перелицензирует данные сайта и не означает, что проект официально поддерживается университетом.

## Зависимости сборки

AndroidX, Compose, Room, DataStore, Kotlin, kotlinx, OkHttp и инструменты сборки подключаются через Gradle; версии перечислены в `app/build.gradle.kts` и `build.gradle.kts`. Они распространяются на условиях своих правообладателей. Gradle Wrapper находится в `gradle/wrapper/`; проект Gradle использует Apache 2.0: https://github.com/gradle/gradle/blob/master/LICENSE.

Этот файл перечисляет встроенные материалы и основные группы зависимостей; он не заменяет полный список лицензий транзитивных библиотек для распространения APK/AAB.
