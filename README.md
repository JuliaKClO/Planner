# Рабочая неделя

Локальный ежедневник на русском языке для Android 8.0 и новее. На главном экране можно открыть любой из семи дней недели или сразу создать запись кнопкой «+». Нажатие на диапазон дат открывает календарь с ручным вводом. Из поиска можно открыть найденную запись и перейти к её неделе.

## Проект и среда

Один модуль `app`. Код находится в `app/src/main/java/ru/workweek/planner`. В `domain` находятся правила календаря и нормализации, в `data` — Room и DataStore, в `ui` — экраны Compose Material 3, в `notifications` — расчёт сводки и адаптер Android. Исходная схема базы находится в `app/schemas`.

Версия приложения 1.0.0 (код 1), идентификатор `ru.workweek.planner`. Для сборки используются JDK 17, Gradle Wrapper 9.3.1, Android Gradle Plugin 9.1.1 со встроенным Kotlin 2.2.10, Compose BOM 2025.10.01 и Room 2.8.4. Минимальный API 26, compileSdk и targetSdk 37 (Android 17). [Совместимость Android SDK и AGP](https://developer.android.com/build/releases/agp-9-1-0-release-notes)

Установите JDK 17 и Android SDK Command-line Tools. Укажите каталог JDK в `JAVA_HOME`, каталог SDK в `ANDROID_HOME`, примите лицензии SDK. Понадобятся Platform-Tools, платформа Android 37.0 и Android Build-Tools. Первая сборка загружает Gradle и зависимости из официальных репозиториев.

```powershell
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" "build-tools;37.0.0"
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

На Linux и macOS используйте `./gradlew` вместо `.\gradlew.bat`. Версии библиотек зафиксированы в файлах Gradle. Собранный APK находится в `app/build/outputs/apk/debug/app-debug.apk`.

На компьютере, где подготовлена эта поставка, инструменты уже находятся в `C:\Temp\workweek-build`. Следующие значения действуют в текущем окне PowerShell. `ANDROID_USER_HOME` указывает на каталог того же отладочного ключа, которым подписан переданный APK.

```powershell
$env:JAVA_HOME = 'C:\Temp\workweek-build\jdk\jdk-17.0.21.1+2'
$env:ANDROID_HOME = 'C:\Temp\workweek-build\sdk'
$env:ANDROID_USER_HOME = 'C:\Temp\workweek-build\android-user'
$env:GRADLE_USER_HOME = 'C:\Temp\workweek-build\gradle-cache'
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
.\gradlew.bat :app:assembleDebug
```

При запуске JVM-тестов из каталога с кириллицей в Windows сочетание Gradle 9 и JDK 17 может передать загрузчику классов неверно декодированный путь. Для этого проекта создано дополнительное имя той же папки — `C:\Temp\workweek-build\source` (NTFS junction). Изменения через оба пути относятся к одним исходникам. Выполняйте полную проверку из PowerShell так:

```powershell
$project = 'C:\Temp\workweek-build\source'
& "$project\gradlew.bat" -p $project clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

На другом компьютере можно сразу распаковать исходники по пути с латиницей, например `C:\Projects\WorkWeek`.

## Установка

Передаваемый APK предназначен для личного тестирования и подписан отладочным ключом. Включите отладку по USB, подключите телефон и подтвердите разрешение для компьютера.

Для установки готового файла из поставки выполните `adb install -r delivery/WorkWeek-1.0.0-debug.apk`. Следующие команды используют APK собственной сборки.

```powershell
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ru.workweek.planner/.MainActivity
```

Обновление поверх установленного приложения требует того же идентификатора и ключа подписи. Храните свой ключ для следующих сборок. Удаление приложения или очистка данных удаляет записи, настройки и журнал сводок. Публикационная подпись и резервный экспорт в первую версию не входят.

## Данные, время и поиск

Room хранит записи и журнал доставки в закрытом каталоге приложения. Настройки сохраняются в Preferences DataStore. Обычный запуск открывает текущую неделю. При пересоздании открытой задачи Android восстанавливает навигацию и черновик через saved state. Недельный обзор показывает первую запись дня в порядке выполнения, времени, создания и ID.

Дата и время записи относятся к местному календарю. При смене пояса дело на 09:00 остаётся на тот же день и час. Диапазон планирования охватывает 01.01.1900–31.12.2100. Поиск учитывает все слова запроса, нормализует Unicode, регистр и «ё/е». Знаки `%`, `_` и `*` ищутся буквально. Поисковая копия хранится отдельно от исходного текста.

Сводка на завтра по умолчанию выключена. Включите её в настройках и разрешите уведомления. По умолчанию время — около 20:00. При доставке приложение перечитывает незавершённые записи на завтра с включённым флажком «Напомнить накануне». Уже опубликованная сводка открывает указанный в ней день.

Android может задержать одноразовый inexact alarm, особенно в режиме экономии энергии. После полуночи устаревшая сводка пропускается. Во время принудительной остановки приложения доставка приостановлена. При следующем открытии расписание пересчитывается. [Планирование AlarmManager](https://developer.android.com/develop/background-work/services/alarms)

## Проверки

Локальные JUnit и Robolectric тесты используют свои временные данные. Перечень сценариев приёмки и фактические результаты содержатся в [TEST_REPORT.md](TEST_REPORT.md). Для instrumented tests используйте выделенный эмулятор или телефон с отладкой. Параметр `isolatedTest` меняет идентификатор тестируемого приложения на `ru.workweek.planner.qa`.

```powershell
.\gradlew.bat -PisolatedTest :app:connectedDebugAndroidTest
```

После запуска тестов отчёты доступны в `app/build/reports/tests`, `app/build/reports/androidTests` и `app/build/reports/lint-results-debug.html`. Визуальный тест пишет PNG в `app/build/reports/visual`. Условия для реального уведомления и измерений на телефоне приведены в отчёте о проверках.

К поставке приложен архив `WorkWeek-1.0.0-checks.zip`. Распакуйте его в каталог `delivery` рядом с APK, чтобы открыть из отчёта исходные PNG, результаты JUnit, Android Lint и журналы Gradle. [Отчёт о приёмке](TEST_REPORT.md)
