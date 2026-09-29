-if class com.coldchain.app.data.models.AuthResponse
-keepnames class com.coldchain.app.data.models.AuthResponse
-if class com.coldchain.app.data.models.AuthResponse
-keep class com.coldchain.app.data.models.AuthResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
