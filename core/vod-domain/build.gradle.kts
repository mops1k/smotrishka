import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

// Домен медиатеки: ни сети, ни Android — только Kotlin и корутины.
// Модуль собран как android-library, как и остальные core-модули проекта: build-logic
// с конвенциями не заводим (решение интервью), а Android-зависимостей в коде нет.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mops1k.smotrishka.tv.core.vod.domain"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
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
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests.set(false)
}
