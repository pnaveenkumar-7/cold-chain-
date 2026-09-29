-if class com.coldchain.app.data.models.RpcRequest
-keepnames class com.coldchain.app.data.models.RpcRequest
-if class com.coldchain.app.data.models.RpcRequest
-keep class com.coldchain.app.data.models.RpcRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.coldchain.app.data.models.RpcRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.coldchain.app.data.models.RpcRequest
-keepclassmembers class com.coldchain.app.data.models.RpcRequest {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
