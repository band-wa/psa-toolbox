    # PSA-MARK-KEYLONGCLICK 按键映射插桩（onKeyLongClick）
    const/4 v0, 0x1

    invoke-static {p0, p1, v0}, Lcom/cusc/misc/observer/KeyEventEmitter;->psaHandle(Lcom/cusc/misc/observer/KeyEventEmitter;IZ)Z

    move-result v0

    # v0 == 0（未接管）⇒ 继续原厂逻辑；v0 != 0（已接管）⇒ 直接结束
    if-eqz v0, :psa_map_cont
    return-void
    :psa_map_cont
