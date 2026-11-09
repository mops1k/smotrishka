import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Properties

// Ключ релиза лежит вне репозитория: ~/.android-keystores/smotrishka-release.properties.
// В CI ключ приходит секретом GitHub: workflow распаковывает base64 в файл и передаёт путь
// в RELEASE_KEYSTORE_FILE, пароли — переменными окружения. Нет ни того, ни другого —
// release подписывается отладочным ключом, как раньше (APK всё равно устанавливается).
val releaseKeystoreProps: Properties? = run {
    val file = File(System.getProperty("user.home"), ".android-keystores/smotrishka-release.properties")
    if (!file.exists()) null else Properties().apply { file.inputStream().use { load(it) } }
}

/** Подпись релиза: сначала окружение CI, затем локальный файл ключей, иначе `null`. */
val releaseSigning: Properties? = System.getenv("RELEASE_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { path ->
        Properties().apply {
            setProperty("storeFile", path)
            setProperty("storePassword", System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty())
            setProperty("keyAlias", System.getenv("RELEASE_KEY_ALIAS").orEmpty())
            setProperty("keyPassword", System.getenv("RELEASE_KEY_PASSWORD").orEmpty())
        }
    }
    ?: releaseKeystoreProps

/** Локальный постоянный отладочный ключ: ~/.android-keystores/smotrishka-debug.properties. */
val debugKeystoreProps: Properties? = run {
    val file = File(System.getProperty("user.home"), ".android-keystores/smotrishka-debug.properties")
    if (!file.exists()) null else Properties().apply { file.inputStream().use { load(it) } }
}

/**
 * Подпись debug-сборок постоянным ключом: сначала окружение CI (`DEBUG_KEYSTORE_FILE`),
 * затем локальный файл ключей, иначе — штатный отладочный ключ Gradle.
 *
 * Зачем: отладочный ключ Gradle генерирует сам, и он уникален для каждой машины и каждого
 * прогона CI. Из-за этого debug-APK из CI не устанавливался поверх предыдущего выпуска —
 * Android отказывал с «ключи приложений разные» (0.1.0-rc.3 против 0.1.0-rc.4).
 */
val debugSigning: Properties? = System.getenv("DEBUG_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { path ->
        Properties().apply {
            setProperty("storeFile", path)
            setProperty("storePassword", System.getenv("DEBUG_KEYSTORE_PASSWORD").orEmpty())
            setProperty("keyAlias", System.getenv("DEBUG_KEY_ALIAS").orEmpty())
            setProperty("keyPassword", System.getenv("DEBUG_KEY_PASSWORD").orEmpty())
        }
    }
    ?: debugKeystoreProps

/**
 * Версия приложения: из CI (`-PversionName`/`-PversionCode` или переменные окружения),
 * иначе — значения по умолчанию этой машины. Релизный workflow берёт версию из тега.
 */
val appVersionName: String = (project.findProperty("versionName") as String?)
    ?: System.getenv("VERSION_NAME")
    ?: "0.1.0"

val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull()
    ?: System.getenv("VERSION_CODE")?.toIntOrNull()
    ?: 1

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mops1k.smotrishka.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mops1k.smotrishka.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        debug {
            // Постоянный отладочный ключ (см. debugSigning): без него debug-APK из CI
            // подписывались одноразовым ключом раннера и не обновлялись на устройстве.
            signingConfig = debugSigning?.let { props ->
                signingConfigs.create("debugPersistent") {
                    storeFile = File(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            } ?: signingConfigs.getByName("debug")
        }
        release {
            // R8 и вырезание неиспользуемых ресурсов: на слабых приставках это главный
            // рычаг — в debug-сборке dex раздут настолько, что код уезжает в swap.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Свой ключ релиза, если он есть на этой машине или пришёл секретом в CI;
            // иначе отладочный ключ, иначе APK нельзя установить на приставку.
            signingConfig = releaseSigning?.let { props ->
                signingConfigs.create("release") {
                    storeFile = File(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            } ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:playlist"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)

    debugImplementation(libs.compose.ui.tooling)
}

// Hilt генерирует исходники и в debugUnitTest, поэтому Gradle 9 считает, что тестовые
// исходники есть, а тестов нет, и валит задачу. Пустой прогон до появления тестов допустим.
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests.set(false)
}
