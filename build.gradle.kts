// Корневой сборочный скрипт: только объявление плагинов, конфигурация — в модулях.
// build-logic с конвенциями намеренно не заводим (решение интервью, t_c01dd29aa103).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
