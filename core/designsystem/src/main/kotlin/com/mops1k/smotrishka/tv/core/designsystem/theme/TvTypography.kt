package com.mops1k.smotrishka.tv.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографика редизайна: размеры и насыщенности из `docs/design/tokens.md`.
 *
 * Системный шрифт (`FontFamily.Default` — Roboto на Android) по решению Д7: свой шрифт
 * в приложение не добавляется.
 */
object TvType {

    /** Заголовок раздела (h1): 29/600, `letter-spacing -.8`. */
    val h1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.8).sp,
    )

    /** Название канала в карточке: 22/600, `letter-spacing -.4`. */
    val cardChannel = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    )

    /** Заголовок панели и карточки настроек: 17/600. */
    val panelTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Название канала в строке: 13/600. */
    val rowChannel = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Кнопка «Смотреть»: 13/700. */
    val watchButton = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )

    /** Пункт меню сайдбара: 14/600. */
    val menuItem = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Категория: 12/600. */
    val category = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Бренд: 19/700, `letter-spacing -.5`. */
    val brand = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )

    /** Кнопка «Телепрограмма канала»: 11/600. */
    val programmeButton = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Передача в карточке и «Далее»: 11/500. */
    val programme = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )

    /** Часы в шапке: 15/600. */
    val clock = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Дата в шапке: 10. */
    val date = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Название передачи в строке канала: 10. */
    val rowProgramme = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Категория канала в карточке: 9/700 uppercase, `letter-spacing 1.3`. */
    val eyebrow = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp,
    )

    /** Мелкая подпись 9 sp. */
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Статистика плеера: 9, полупрозрачная. */
    val stats = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Бейдж «Архив»: 8/700 uppercase. */
    val badge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
    )

    /** `HD`: 8/700. */
    val hd = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
    )

    /** Цифра «10» у ⏪/⏩: 7/700. */
    val seekDigit = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 7.sp,
        fontWeight = FontWeight.Bold,
    )

    /** Заголовок онбординга: 26/600, `letter-spacing -.7`. */
    val onboardingTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.7).sp,
    )

    /** Подзаголовок онбординга: 12. */
    val onboardingSubtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Поле ввода: 13. */
    val field = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Подпись поля ввода: 10/600. */
    val fieldLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Типографика Material3, переведённая на размеры макета.
 *
 * Нужна, чтобы экраны, ещё не переписанные на `TvType`, не выбивались из ритма: они
 * используют `MaterialTheme.typography`, и после редизайна получают те же размеры.
 */
val TvMaterialTypography: Typography = Typography(
    displaySmall = TvType.onboardingTitle,
    headlineMedium = TvType.cardChannel,
    headlineSmall = TvType.panelTitle,
    titleLarge = TvType.panelTitle,
    titleMedium = TvType.clock,
    titleSmall = TvType.rowChannel,
    bodyLarge = TvType.field,
    bodyMedium = TvType.category,
    bodySmall = TvType.programme,
    labelLarge = TvType.watchButton,
    labelMedium = TvType.fieldLabel,
    labelSmall = TvType.caption,
)
