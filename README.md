# 💱 Конвертер валют — Android

Нативное Android-приложение (Kotlin + Jetpack Compose) — аналог Telegram-бота
из ветки `main`. Эта ветка (`android`) — **orphan-ветка**, содержит только код
приложения и не пересекается с кодом бота.

> Бот живёт в ветке `main`. Приложение — здесь, в `android`. Истории веток
> независимы.

## Возможности

- Два источника курсов: 🇷🇺 ЦБ РФ и 🌐 Google (рыночный курс).
- Выбор исходной валюты (быстрые чипсы из избранного).
- Избранные валюты: поиск по коду/названию/стране, добавление и удаление.
- Конвертация суммы (цифровая клавиатура; исходная валюта выбирается чипсом).
- Удобный формат курса для дешёвых валют («100 RSD = 73 RUB»).
- Кэш курсов на 3 часа (переживает перезапуск) + ручное обновление кнопкой
  в настройках и время последнего обновления.
- Ежедневное уведомление-сводка около 17:00 МСК (WorkManager).
- Вкладка «О программе»: версия, ссылки на yukh.net и Telegram-бот.

## Стек

- Kotlin, Jetpack Compose (Material 3)
- OkHttp + org.json (сеть), DataStore Preferences (настройки/кэш)
- WorkManager (фоновое обновление и уведомления)
- minSdk 24, compileSdk/targetSdk 34, JDK 17

## Сборка

### В GitLab CI (Docker-раннер)

`.gitlab-ci.yml` ставит Android SDK через cmdline-tools и собирает:
- `assembleDebug` — отладочный APK (артефакт задачи);
- `assembleRelease` — подписанные APK + AAB (при заданном `KEYSTORE_BASE64`).

**Версия растёт автоматически:** `versionCode`/`versionName` = `0.1.<номер
пайплайна CI>` (видно во вкладке «О программе»). Каждая сборка ветки `android`
заливает APK/AAB в Package Registry и создаёт **GitLab Release** с тегом
`v0.1.<номер>` — качать удобнее из раздела **Releases**, а не из артефактов задачи.

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

## Подпись релиза

Релизный APK/AAB подписывается из секретов, которых нет в репозитории. Gradle
берёт их из `keystore.properties` (локально) или из переменных окружения (в CI).

### 1. Создать keystore (один раз, хранить вечно!)

В Android Studio: **Build → Generate Signed App Bundle / APK → Create new…**
Либо командой (нужен JDK):

```bash
keytool -genkeypair -v -keystore release.keystore -alias currency \
  -keyalg RSA -keysize 2048 -validity 10000
```

> ⚠️ Потеря keystore или пароля делает невозможным обновление приложения в
> Google Play. Сделай резервную копию файла и паролей в надёжном месте.

### 2. Локальная подпись (Android Studio)

Создай `keystore.properties` в корне (он в `.gitignore`):

```properties
storeFile=/абсолютный/путь/release.keystore
storePassword=ПАРОЛЬ_ХРАНИЛИЩА
keyAlias=currency
keyPassword=ПАРОЛЬ_КЛЮЧА
```

### 3. Подпись в GitLab CI

Добавь переменные в **Settings → CI/CD → Variables** (Protected):

| Переменная | Значение |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i release.keystore` (одной строкой) |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | `currency` |
| `KEY_PASSWORD` | пароль ключа |

Задача `assembleRelease` появляется в пайплайне только при заданном
`KEYSTORE_BASE64` и собирает подписанные `*.apk` (sideload) и `*.aab` (Google Play).

## Публикация в магазины

Материалы карточек и политика конфиденциальности — в каталоге `store/`
(тексты RU/EN, `privacy-policy-*.md`). Политику нужно разместить по публичному
URL (магазины требуют ссылку).

Автопубликация настроена в `.gitlab-ci.yml` отдельными ручными задачами
(`stage: deploy`), они берут собранный AAB/APK из `assembleRelease` и не влияют
на сборку. Запускаются вручную и только при наличии креденшелов:

**Google Play** — задача `publish-googleplay` (скрипт `store/publish_googleplay.py`,
Android Publisher API). Переменная CI:
- `PLAY_SA_JSON_B64` = `base64` от JSON сервис-аккаунта Play Console.

**RuStore** — задача `publish-rustore` (скрипт `store/rustore_publish.py`, RuStore API).
Переменные CI:
- `RUSTORE_KEY_ID` — идентификатор ключа из консоли RuStore;
- `RUSTORE_KEY_B64` = `base64` от приватного RSA-ключа (PEM).

Первую отправку (создание приложения, заполнение карточки, модерация) делают
вручную в консолях магазинов; CI-задачи автоматизируют загрузку новых сборок.
Версию вручную поднимать не нужно — `versionCode`/`versionName` инкрементятся
автоматически из номера пайплайна CI.

## История изменений

См. [CHANGELOG.md](CHANGELOG.md).

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
