-if class com.coldchain.app.data.models.Shipment
-keepnames class com.coldchain.app.data.models.Shipment
-if class com.coldchain.app.data.models.Shipment
-keep class com.coldchain.app.data.models.ShipmentJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.Shipment
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.Shipment
-keepclassmembers class com.coldchain.app.data.models.Shipment {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,boolean,java.lang.String,int,java.lang.String,java.lang.String,java.lang.Boolean,java.lang.Boolean,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
