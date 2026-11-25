# Keep annotations & Room/Hilt/Glance entry points for R8 release shrinking
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Glance AppWidget receivers and action callbacks
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# Hilt EntryPoints
-keep @dagger.hilt.EntryPoint interface * { *; }
