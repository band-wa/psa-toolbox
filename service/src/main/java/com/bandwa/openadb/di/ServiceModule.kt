package com.bandwa.openadb.di

import com.bandwa.openadb.service.ToolBoxServiceWrapper
import com.bandwa.openadb.service.ToolBoxServiceWrapperImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    abstract fun bindsToolBoxServiceWrapper(implementation: ToolBoxServiceWrapperImpl): ToolBoxServiceWrapper
}
