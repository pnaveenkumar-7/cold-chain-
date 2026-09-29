-if class com.coldchain.app.data.models.AuthRequest
-keepnames class com.coldchain.app.data.models.AuthRequest
-if class com.coldchain.app.data.models.AuthRequest
-keep class com.coldchain.app.data.models.AuthRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
