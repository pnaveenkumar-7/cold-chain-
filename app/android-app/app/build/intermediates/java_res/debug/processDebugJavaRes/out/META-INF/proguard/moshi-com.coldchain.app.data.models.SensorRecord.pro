-if class com.coldchain.app.data.models.SensorRecord
-keepnames class com.coldchain.app.data.models.SensorRecord
-if class com.coldchain.app.data.models.SensorRecord
-keep class com.coldchain.app.data.models.SensorRecordJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.SensorRecord
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.SensorRecord
-keepclassmembers class com.coldchain.app.data.models.SensorRecord {
    public synthetic <init>(java.lang.Integer,java.lang.Integer,java.lang.String,double,double,java.lang.Double,java.lang.String,java.lang.Boolean,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Long,java.lang.Long,java.lang.Boolean,java.lang.Boolean,java.lang.Boolean,java.lang.Boolean,java.lang.Boolean,java.lang.Boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
