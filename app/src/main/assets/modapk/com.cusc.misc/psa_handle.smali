.method private static psaHandle(Lcom/cusc/misc/observer/KeyEventEmitter;IZ)Z
    .locals 4

    iget-object v0, p0, Lcom/cusc/misc/observer/KeyEventEmitter;->mContext:Landroid/content/Context;

    const/16 v1, 0x07

    if-eq p1, v1, :psa_r0

    const/16 v1, 0x09

    if-eq p1, v1, :psa_r1

    const/16 v1, 0x17

    if-eq p1, v1, :psa_r2

    const/16 v1, 0x0c

    if-eq p1, v1, :psa_r3

    const/16 v1, 0x0e

    if-eq p1, v1, :psa_r4

    const/16 v1, 0x0f

    if-eq p1, v1, :psa_r5

    const/16 v1, 0x10

    if-eq p1, v1, :psa_r6
    const/16 v1, 0x1b

    if-eq p1, v1, :psa_r7

    const/16 v1, 0x1c

    if-eq p1, v1, :psa_r8

    const/16 v1, 0x0b

    if-eq p1, v1, :psa_r9

    const/16 v1, 0x0d

    if-eq p1, v1, :psa_r10



    goto :psa_ret_false

:psa_r7
    if-eqz p2, :psa_r7s

    const-string v1, "persist.psa.membercenter.longclick"

    goto :psa_resolve

:psa_r7s
    const-string v1, "persist.psa.membercenter.shortclick"

    goto :psa_resolve

:psa_r8
    if-eqz p2, :psa_r8s

    goto :psa_ret_false

:psa_r8s
    const-string v1, "persist.psa.carsettings.shortclick"

    goto :psa_resolve

# 音乐键 0x0b / 地图键 0x0d：短按让给工具箱的 Hook.launchByProp（它读 .shortclick），
# 我们只接管长按（原厂 onKeyLongClick 不认这两个码，工具箱也不读 .longclick）。
:psa_r9
    if-eqz p2, :psa_ret_false

    const-string v1, "persist.bandwa.music.longclick"

    goto :psa_resolve

:psa_r10
    if-eqz p2, :psa_ret_false

    const-string v1, "persist.bandwa.map.longclick"

    goto :psa_resolve

:psa_ret_false
    const/4 v1, 0x0

    return v1

:psa_resolve
    # v1 = 命中的属性名；v0 = mContext
    const-string v2, "none"

    invoke-static {v1, v2}, Lcom/android/settingslib/os/SystemProperties;->get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    const-string v2, "PSA-Map"

    invoke-static {v2, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    const-string v2, "none"

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    # v3 != 0 表示属性值就是 "none"（未配置 / 已取消）⇒ 不接管
    if-nez v3, :psa_ret_false

    :try_start_0
    invoke-static {v0, v1}, Lcom/cusc/misc/observer/KeyEventEmitter;->psaLaunch(Landroid/content/Context;Ljava/lang/String;)Z

    move-result v3
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_map

    # v3 == 0 表示目标解析不到 / 启动失败 ⇒ 回落原厂，不吃键
    if-eqz v3, :psa_ret_false

    const-string v2, "PSA-Map"

    const-string v3, "handled"

    invoke-static {v2, v3}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v1, 0x1

    return v1

:psa_r0
    if-eqz p2, :psa_r0s

    const-string v1, "persist.bandwa.stalk.longclick"

    goto :psa_resolve

:psa_r0s
    const-string v1, "persist.bandwa.stalk.shortclick"

    goto :psa_resolve

:psa_r1
    if-eqz p2, :psa_r1s

    const-string v1, "persist.bandwa.btphone.longclick"

    goto :psa_resolve

:psa_r1s
    const-string v1, "persist.bandwa.btphone.shortclick"

    goto :psa_resolve

:psa_r2
    if-eqz p2, :psa_r2s

    const-string v1, "persist.bandwa.voice.longclick"

    goto :psa_resolve

:psa_r2s
    const-string v1, "persist.bandwa.voice.shortclick"

    goto :psa_resolve

:psa_r3
    if-eqz p2, :psa_r3s

    const-string v1, "persist.bandwa.ac.longclick"

    goto :psa_resolve

:psa_r3s
    const-string v1, "persist.bandwa.ac.shortclick"

    goto :psa_resolve

:psa_r4
    if-eqz p2, :psa_r4s

    const-string v1, "persist.bandwa.car.longclick"

    goto :psa_resolve

:psa_r4s
    const-string v1, "persist.bandwa.car.shortclick"

    goto :psa_resolve

:psa_r5
    if-eqz p2, :psa_r5s

    const-string v1, "persist.bandwa.connect.longclick"

    goto :psa_resolve

:psa_r5s
    const-string v1, "persist.bandwa.connect.shortclick"

    goto :psa_resolve

:psa_r6
    if-eqz p2, :psa_r6s

    const-string v1, "persist.bandwa.btpiano.longclick"

    goto :psa_resolve

:psa_r6s
    const-string v1, "persist.bandwa.btpiano.shortclick"

    goto :psa_resolve

# psaLaunch 抛任何 Throwable（某车型缺某 API 时）⇒ 不接管、回落原厂，绝不让 misc 崩
:catch_map
    move-exception v3

    goto :psa_ret_false
.end method
