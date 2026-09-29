-if class com.coldchain.app.data.models.RpcResponse
-keepnames class com.coldchain.app.data.models.RpcResponse
-if class com.coldchain.app.data.models.RpcResponse
-keep class com.coldchain.app.data.models.RpcResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.RpcResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.RpcResponse
-keepclassmembers class com.coldchain.app.data.models.RpcResponse {
    public synthetic <init>(java.lang.Integer,java.lang.String,com.coldchain.app.data.models.RpcError,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
