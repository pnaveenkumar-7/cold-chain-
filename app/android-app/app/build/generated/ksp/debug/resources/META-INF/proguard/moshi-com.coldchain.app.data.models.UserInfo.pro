-if class com.coldchain.app.data.models.UserInfo
-keepnames class com.coldchain.app.data.models.UserInfo
-if class com.coldchain.app.data.models.UserInfo
-keep class com.coldchain.app.data.models.UserInfoJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
