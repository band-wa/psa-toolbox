.method private static psaLaunch(Landroid/content/Context;Ljava/lang/String;)Z
    .locals 4

    const-string v0, "com.cusc.misc"

    invoke-virtual {p1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :psa_build

    # 目标以 com.cusc.misc 开头 ⇒ 360/AVM：正在显示就关闭，否则按包名启动 AVM Activity
    # （旧版 408 基座载荷的语义，实车日志：关 -> avm showing -> close / 开 -> LL: intent resolved）
    invoke-static {p0}, Lcom/android/car/manager/CarPropertyManager;->get(Landroid/content/Context;)Lcom/android/car/manager/CarPropertyManager;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/car/manager/CarPropertyManager;->getAvmState()I

    move-result v0

    const/4 v1, 0x2

    # state == 2 表示 360 正在显示（ID_AVM_DISPLAY = ON）⇒ 关闭；
    # state != 2 ⇒ 去启动 AVM Activity（它内部调 startAVMDisplay，值是 on_off=2 开）
    if-ne v0, v1, :psa_avm_open

    const-string v0, "PSA-Map"

    const-string v1, "avm state==2 -> stopAVMDisplay"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {p0}, Lcom/android/car/manager/CarPropertyManager;->get(Landroid/content/Context;)Lcom/android/car/manager/CarPropertyManager;

    move-result-object v0

    # stopAVMDisplay() 基线里没有 —— 由本生成器补进 CarPropertyManager（写 [1,1] 关闭 360）
    invoke-virtual {v0}, Lcom/android/car/manager/CarPropertyManager;->stopAVMDisplay()Z

    move-result v0

    const-string v1, "stop -> false(!! isBinder/异常)"

    # v0 == 0（调用失败）⇒ 保持上面那条；v0 != 0（成功）⇒ 换成 true
    if-eqz v0, :psa_avm_stop_log

    const-string v1, "stop -> true"

    :psa_avm_stop_log
    const-string v0, "PSA-Map"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v0, 0x1

    return v0

:psa_avm_open
    const-string v0, "PSA-Map"

    const-string v1, "avm state!=2 -> launch AVM activity"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

:psa_build
    const-string v0, "/"

    invoke-virtual {p1, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result v0

    # v0 <= 0 ⇒ 没有「包名/Activity」形式 ⇒ 按包名启动
    if-lez v0, :psa_by_pkg

    new-instance v1, Landroid/content/Intent;

    invoke-direct {v1}, Landroid/content/Intent;-><init>()V

    const/4 v2, 0x0

    invoke-virtual {p1, v2, v0}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object v2

    add-int/lit8 v0, v0, 0x1

    invoke-virtual {p1, v0}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v1

    goto :psa_have_intent

:psa_by_pkg
    invoke-virtual {p0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v0

    invoke-virtual {v0, p1}, Landroid/content/pm/PackageManager;->getLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v1

:psa_have_intent
    if-eqz v1, :psa_fail

    # setClassName 拼出来的 Intent 没有 NEW_TASK，补上（service/application context 必需）
    const/high16 v0, 0x10000000

    invoke-virtual {v1, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    move-result-object v0

    :try_start_0
    invoke-virtual {p0, v1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    const/4 v0, 0x1

    return v0

:catch_0
    move-exception v2

:psa_fail
    const/4 v0, 0x0

    return v0
.end method
