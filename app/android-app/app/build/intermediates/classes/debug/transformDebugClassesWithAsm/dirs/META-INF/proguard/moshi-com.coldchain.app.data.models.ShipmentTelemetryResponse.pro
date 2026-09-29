-if class com.coldchain.app.data.models.ShipmentTelemetryResponse
-keepnames class com.coldchain.app.data.models.ShipmentTelemetryResponse
-if class com.coldchain.app.data.models.ShipmentTelemetryResponse
-keep class com.coldchain.app.data.models.ShipmentTelemetryResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.ShipmentTelemetryResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.ShipmentTelemetryResponse
-keepclassmembers class com.coldchain.app.data.models.ShipmentTelemetryResponse {
    public synthetic <init>(java.lang.String,com.coldchain.app.data.models.Shipment,java.lang.String,java.lang.Boolean,java.lang.Boolean,java.lang.Boolean,java.util.List,java.lang.Integer,java.lang.Integer,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
