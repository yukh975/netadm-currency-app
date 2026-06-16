# Конфигурация R8/ProGuard для release (isMinifyEnabled = true).
# Большинство библиотек (Compose, OkHttp, WorkManager, DataStore) поставляют
# свои consumer-правила, поэтому здесь только защитные keep на наш код.

# WorkManager создаёт воркер рефлексией по имени класса.
-keep class net.yukh.currency.work.DailyUpdateWorker { *; }

# Application-класс (ручной DI) — инстанцируется системой по имени.
-keep class net.yukh.currency.CurrencyApp { *; }

# org.json — часть Android SDK, но на всякий случай не трогаем имена.
-dontwarn org.json.**
