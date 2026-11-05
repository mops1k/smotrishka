package com.mops1k.smotrishka.tv.ui.player

/**
 * Ограничения памяти для воспроизведения.
 *
 * Телевизионные приставки часто имеют мало ОЗУ. ExoPlayer по умолчанию не ограничивает буфер
 * по байтам, поэтому на слабом устройстве поток мог «разъедаться» в памяти весь сеанс.
 */
object PlayerMemory {

    /** Потолок байтового буфера на устройствах с малым ОЗУ. */
    const val LOW_RAM_BUFFER_BYTES = 32L * 1024 * 1024

    /** Потолок байтового буфера на обычных устройствах. */
    const val NORMAL_BUFFER_BYTES = 64L * 1024 * 1024

    /**
     * Сколько байтов разрешено держать в буфере.
     *
     * @param lowRamDevice сообщает ли система, что устройство с малым ОЗУ.
     */
    fun targetBufferBytes(lowRamDevice: Boolean): Int =
        if (lowRamDevice) LOW_RAM_BUFFER_BYTES.toInt() else NORMAL_BUFFER_BYTES.toInt()
}
