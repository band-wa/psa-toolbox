.method public stopAVMDisplay()Z
    .locals 9

    invoke-virtual {p0}, Lcom/android/car/manager/CarPropertyManager;->isBinder()Z

    move-result v0

    const/4 v1, 0x0

    if-eqz v0, :psa_cpm_end

    :try_start_0
    sget-boolean v0, Lcom/android/car/manager/CarPropertyManager;->DEBUG:Z

    const v2, 0x2141eeb0

    const/4 v3, 0x1

    const/4 v4, 0x2

    if-eqz v0, :psa_cpm_go

    iget-object v0, p0, Lcom/android/car/manager/CarPropertyManager;->TAG:Ljava/lang/String;

    const-string v5, "psa stopAVMDisplay([%d, %d]): %d -> 0x%s"

    const/4 v6, 0x4

    new-array v6, v6, [Ljava/lang/Object;

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v6, v3

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v6, v4

    const/4 v7, 0x3

    invoke-static {v2}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v8

    aput-object v8, v6, v7

    invoke-static {v5, v6}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v5

    invoke-static {v0, v5}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :psa_cpm_go
    iget-object v0, p0, Lcom/android/car/manager/CarPropertyManager;->mService:Landroid/car/hardware/property/ICarProperty;

    new-instance v5, Landroid/car/hardware/CarPropertyValue;

    const/4 v6, 0x2

    new-array v6, v6, [Ljava/lang/Integer;

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v6, v3

    invoke-direct {v5, v2, v1, v6}, Landroid/car/hardware/CarPropertyValue;-><init>(IILjava/lang/Object;)V

    invoke-interface {v0, v5}, Landroid/car/hardware/property/ICarProperty;->setProperty(Landroid/car/hardware/CarPropertyValue;)V
    :try_end_0
    .catch Landroid/os/DeadObjectException; {:try_start_0 .. :try_end_0} :catch_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0

    return v3

    :catch_0
    move-exception v0

    invoke-virtual {p0, v0}, Lcom/android/car/manager/CarPropertyManager;->onException(Ljava/lang/Exception;)V

    :psa_cpm_end
    return v1
.end method
