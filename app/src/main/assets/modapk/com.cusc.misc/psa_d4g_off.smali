    # ===== PSA 插入段：关闭「车机未连接到服务器，请重试」弹窗（v243，无条件） =====
    # 只插不改：摘掉本段后，现场类应逐字节还原。
    # showNetState() 直接返回 ⇒ 弹窗不再出现；
    # 上游 serverError() 的日志（"serverError: 车机未连接到服务器，请重试 …"）照旧会打，便于诊断。
    :psa_net_off
    return-void
