# Правила R8 для SmotrishkaTV.
#
# Файл нужен потому, что release собирается с минификацией: на слабых ТВ-приставках
# необрезанный dex уезжает в swap, и интерфейс перестаёт отвечать. Правила держим
# минимальными — каждое «оставить всё» возвращает нам ту же проблему с размером.

# Gson читает и пишет поля по аннотациям: имена должны остаться прежними.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation @interface com.google.gson.annotations.SerializedName

# Room: сгенерированный код обращается к сущностям и DAO по именам.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}

# Media3: расширение FFmpeg для AC-3/E-AC-3 подключается через провайдеры декодера.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }

# Hilt и Dagger: аннотации читаются сгенерированным кодом.
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
