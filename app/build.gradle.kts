import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Секреты подписи берём из локального keystore.properties (для Android Studio)
// либо из переменных окружения (для CI). В репозиторий ничего не попадает.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

android {
    namespace = "net.yukh.currency"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.yukh.currency"
        minSdk = 24
        targetSdk = 35
        // ВАЖНО: только литералы (не переменные!) — сканер F-Droid (checkupdates)
        // читает эти строки регэкспом; значение через val он не видит (проверено:
        // job checkupdates падал с «Couldn't find any version information»).
        // versionName — ручной SemVer, единый с Telegram-ботом. Схема versionCode:
        //   major*1_000_000 + minor*10_000 + patch*100   (0.5.8 → 50800)
        // Бампить оба синхронно с APP_VERSION в .gitlab-ci.yml.
        versionCode = 50800
        versionName = "0.5.8"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingValue("storeFile", "KEYSTORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    // Две линии дистрибуции:
    //  • direct — sideload/GitLab: встроенный апдейтер тянет APK из публичного
    //    релиза GitLab (нужно REQUEST_INSTALL_PACKAGES, см. src/direct/…);
    //  • play — Google Play: обновляет сам магазин, апдейтер и разрешение НЕ нужны
    //    (важно для проверки Play — минимум разрешений).
    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATE_ENABLED", "true")
            buildConfigField(
                "String", "UPDATE_RELEASES_URL",
                "\"https://git.home.yukh.net/api/v4/projects/6/releases?per_page=1\"",
            )
            // raw CHANGELOG (публичный) — для показа реальных изменений в модалке
            buildConfigField(
                "String", "UPDATE_CHANGELOG_URL",
                "\"https://git.home.yukh.net/yukh/netadm-currency-bot/-/raw/android/CHANGELOG.md\"",
            )
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATE_ENABLED", "false")
            buildConfigField("String", "UPDATE_RELEASES_URL", "\"\"")
            buildConfigField("String", "UPDATE_CHANGELOG_URL", "\"\"")
        }
        // fdroid — сборка для каталога F-Droid: как play (обновляет каталог,
        // апдейтер и REQUEST_INSTALL_PACKAGES запрещены политикой F-Droid).
        // Отдельный флейвор, чтобы play мог когда-нибудь разойтись (billing и т.п.),
        // а метаданные F-Droid (gradle: [fdroid]) остались стабильными.
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATE_ENABLED", "false")
            buildConfigField("String", "UPDATE_RELEASES_URL", "\"\"")
            buildConfigField("String", "UPDATE_CHANGELOG_URL", "\"\"")
        }
    }

    // Воспроизводимая сборка для F-Droid: не встраивать Play-блок «dependency
    // metadata» в подпись APK/AAB (F-Droid'овский `check apk` его отвергает).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            // R8 ВРЕМЕННО ОТКЛЮЧЁН: с минификацией приложение не запускалось
            // (R8/shrinkResources вырезал нужное на старте), а собрать/протестировать
            // локально нельзя. Вернёмся к R8 позже, добавив точные keep-правила по
            // логу краша — тогда получим и рабочую сборку, и маленький размер.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Воспроизводимая сборка для F-Droid: без git-hash textproto в APK
            // (иначе байт-матч ломается на каждом чекауте).
            vcsInfo {
                include = false
            }
            // Подписываем release только если keystore сконфигурирован.
            if (signingValue("storeFile", "KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Имя выходных файлов: currency-converter-<версия>-release.apk / .aab.
// После блока android — версия берётся из литерала в defaultConfig.
base {
    archivesName.set("currency-converter-${android.defaultConfig.versionName}")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
