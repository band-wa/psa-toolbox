.method public static psaBoot(Landroid/content/Context;)V
    .locals 4

    # ---------- ① 拉起 PSA工具箱（同 system uid，startService 直达） ----------
    :try_start_0
    new-instance v0, Landroid/content/ComponentName;

    const-string v1, "com.bandwa.openadb"

    const-string v2, "com.bandwa.openadb.service.PSAToolBoxService"

    invoke-direct {v0, v1, v2}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    new-instance v1, Landroid/content/Intent;

    invoke-direct {v1}, Landroid/content/Intent;-><init>()V

    invoke-virtual {v1, v0}, Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;

    invoke-virtual {p0, v1}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;

    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :psa_clean

    :catch_0
    move-exception v0

    :psa_clean
    # ---------- ② 清空 /data/log_temp（属主 system:system，misc 同身份可删） ----------
    :try_start_1
    invoke-static {}, Ljava/lang/Runtime;->getRuntime()Ljava/lang/Runtime;

    move-result-object v0

    const/4 v1, 0x3

    new-array v1, v1, [Ljava/lang/String;

    const/4 v2, 0x0

    const-string v3, "sh"

    aput-object v3, v1, v2

    const/4 v2, 0x1

    const-string v3, "-c"

    aput-object v3, v1, v2

    const/4 v2, 0x2

    const-string v3, "rm -rf /data/log_temp/*"

    aput-object v3, v1, v2

    invoke-virtual {v0, v1}, Ljava/lang/Runtime;->exec([Ljava/lang/String;)Ljava/lang/Process;

    :try_end_1
    .catch Ljava/lang/Throwable; {:try_start_1 .. :try_end_1} :catch_1

    :catch_1
    # ---------- v229：开机把 WiFi 打开（svc 是系统命令，system 身份可直接调） ----------
    :try_start_2
    invoke-static {}, Ljava/lang/Runtime;->getRuntime()Ljava/lang/Runtime;

    move-result-object v0

    const/4 v1, 0x3

    new-array v1, v1, [Ljava/lang/String;

    const/4 v2, 0x0

    const-string v3, "sh"

    aput-object v3, v1, v2

    const/4 v2, 0x1

    const-string v3, "-c"

    aput-object v3, v1, v2

    const/4 v2, 0x2

    const-string v3, "test \"$(getprop persist.psa.boot.wifi)\" != \"0\" && svc wifi enable"

    aput-object v3, v1, v2

    invoke-virtual {v0, v1}, Ljava/lang/Runtime;->exec([Ljava/lang/String;)Ljava/lang/Process;

    :try_end_2
    .catch Ljava/lang/Throwable; {:try_start_2 .. :try_end_2} :catch_2

    :catch_2
    return-void
.end method
