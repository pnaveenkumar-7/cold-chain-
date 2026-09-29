-if class com.coldchain.app.data.models.RpcError
-keepnames class com.coldchain.app.data.models.RpcError
-if class com.coldchain.app.data.models.RpcError
-keep class com.coldchain.app.data.models.RpcErrorJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.RpcError
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.RpcError
-keepclassmembers class com.coldchain.app.data.models.RpcError {
    public synthetic <init>(java.lang.Integer,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
