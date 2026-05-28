# 💱 Конвертер валют — Android

Нативное Android-приложение (Kotlin + Jetpack Compose) — аналог Telegram-бота
из ветки `main`. Эта ветка (`android`) — **orphan-ветка**, содержит только код
приложения и не пересекается с кодом бота.

> Бот живёт в ветке `main`. Приложение — здесь, в `android`. Истории веток
> независимы.

## Возможности

- Два источника курсов: 🇷🇺 ЦБ РФ и 🌐 Google (рыночный курс).
- Выбор основной валюты (быстрые чипсы из избранного).
- Избранные валюты: поиск по коду/названию/стране, добавление и удаление.
- Конвертация суммы из основной или произвольной валюты (`USD 100`, `100 EUR`).
- Удобный формат курса для дешёвых валют («100 RSD = 73 RUB»).
- Кэш курсов на 3 часа (переживает перезапуск).
- Ежедневное уведомление-сводка около 17:00 МСК (WorkManager).

## Стек

- Kotlin, Jetpack Compose (Material 3)
- OkHttp + org.json (сеть), DataStore Preferences (настройки/кэш)
- WorkManager (фоновое обновление и уведомления)
- minSdk 24, compileSdk/targetSdk 34, JDK 17

## Сборка

### В GitLab CI (Docker-раннер)

`.gitlab-ci.yml` уже настроен: ставит Android SDK через cmdline-tools и
выполняет `./gradlew assembleDebug`. Готовый APK — в артефактах задачи
`assembleDebug` (`app/build/outputs/apk/debug/`).

### Локально (Android Studio)

1. Открой ветку `android` в Android Studio (File → Open → корень проекта).
2. Studio подтянет Gradle 8.9 (wrapper уже в репозитории) и зависимости.
3. Run ▶ на эмуляторе или устройстве, либо Build → Build APK(s).

### Локально (командная строка)

Нужен JDK 17 и переменная `ANDROID_HOME` на установленный Android SDK:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Структура

```
app/src/main/java/net/yukh/currency/
├─ CurrencyApp.kt          Application + ручной DI + планирование уведомлений
├─ data/
│  ├─ Currencies.kt        каталог валют + поиск
│  ├─ RateTable.kt         модель курсов + конвертация
│  ├─ RateSource.kt        источники: ЦБ РФ и Google
│  ├─ SettingsStore.kt     DataStore: настройки/избранное/кэш
│  ├─ RatesRepository.kt   кэш (TTL 3ч) + обновление
│  └─ Converter.kt         форматирование результатов
├─ ui/                     Compose: экраны и ViewModel
└─ work/DailyUpdateWorker  ежедневное обновление + уведомление
```
