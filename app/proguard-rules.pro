# Конфигурация R8/ProGuard.
# ВНИМАНИЕ: минификация сейчас ОТКЛЮЧЕНА в build.gradle.kts (isMinifyEnabled =
# false) — с ней приложение не запускалось. Правила ниже неактивны и сохранены
# для будущего возврата R8 (после диагностики краша по логу их нужно будет
# дополнить точными keep). Библиотеки (Compose, OkHttp, WorkManager, DataStore)
# дают свои consumer-правила; здесь — только защитные keep на наш код.

# WorkManager создаёт воркер рефлексией по имени класса.
-keep class net.yukh.currency.work.DailyUpdateWorker { *; }

# Application-класс (ручной DI) — инстанцируется системой по имени.
-keep class net.yukh.currency.CurrencyApp { *; }

# AndroidViewModel создаётся рефлексией (ViewModelProvider) — сохраняем
# конструктор (страховка, если consumer-правил lifecycle окажется мало).
-keepclassmembers class net.yukh.currency.ui.MainViewModel {
    <init>(...);
}

# org.json — часть Android SDK, но на всякий случай не трогаем имена.
-dontwarn org.json.**
