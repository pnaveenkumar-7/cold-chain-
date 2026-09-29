-if class com.coldchain.app.data.models.Batch
-keepnames class com.coldchain.app.data.models.Batch
-if class com.coldchain.app.data.models.Batch
-keep class com.coldchain.app.data.models.BatchJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.Batch
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.Batch
-keepclassmembers class com.coldchain.app.data.models.Batch {
    public synthetic <init>(java.lang.Long,java.lang.Long,java.lang.String,java.lang.String,java.lang.Integer,java.lang.Boolean,java.lang.Boolean,java.lang.Boolean,java.lang.String,java.lang.String,java.util.List,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
