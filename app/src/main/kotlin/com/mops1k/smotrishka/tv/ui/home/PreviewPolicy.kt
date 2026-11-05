package com.mops1k.smotrishka.tv.ui.home

/**
 * Политика живого превью на главном экране.
 *
 * Базовый вариант — статичный визуал карточки из макета. Живое превью (без звука) включается
 * только на телефонах и планшетах: на ТВ-приставке лишний декодер отнимает ресурсы у плеера
 * (решение Александра, вариант «A + живое на телефонах»).
 */
object PreviewPolicy {

    /**
     * Считать ли устройство телевизором.
     *
     * Android TV и ТВ-приставки объявляют `FEATURE_LEANBACK`; малопамятные устройства
     * (приставки без Leanback) тоже считаем «без превью», чтобы не грузить их декодером.
     *
     * @param hasLeanback объявлена ли возможность Leanback;
     * @param isLowRam малопамятное ли устройство.
     */
    fun isTelevision(hasLeanback: Boolean, isLowRam: Boolean): Boolean = hasLeanback || isLowRam

    /**
     * Показывать ли живое превью.
     *
     * @param isTv устройство считается телевизором;
     * @param isLowRam малопамятное ли устройство;
     * @param dataSaver включена ли экономия трафика: она гасит превью на любом устройстве.
     */
    fun showLive(isTv: Boolean, isLowRam: Boolean, dataSaver: Boolean = false): Boolean =
        !isTv && !isLowRam && !dataSaver
}
