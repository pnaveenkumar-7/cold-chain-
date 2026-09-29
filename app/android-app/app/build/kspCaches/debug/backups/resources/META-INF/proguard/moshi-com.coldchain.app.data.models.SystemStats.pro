-if class com.coldchain.app.data.models.SystemStats
-keepnames class com.coldchain.app.data.models.SystemStats
-if class com.coldchain.app.data.models.SystemStats
-keep class com.coldchain.app.data.models.SystemStatsJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.SystemStats
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.SystemStats
-keepclassmembers class com.coldchain.app.data.models.SystemStats {
    public synthetic <init>(int,int,int,int,int,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
