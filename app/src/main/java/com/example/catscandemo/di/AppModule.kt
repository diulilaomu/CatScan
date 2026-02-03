package com.example.catscandemo.di

import android.content.Context
import com.example.catscandemo.data.network.CatScanClient
import com.example.catscandemo.data.network.NetworkDiscovery
import com.example.catscandemo.data.repository.DefaultNetworkRepository
import com.example.catscandemo.data.repository.DefaultScanRepository
import com.example.catscandemo.data.repository.DefaultTemplateRepository
import com.example.catscandemo.domain.use_case.NetworkRepository
import com.example.catscandemo.domain.use_case.ScanRepository
import com.example.catscandemo.domain.use_case.TemplateRepository
import com.example.catscandemo.domain.use_case.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块
 * 提供应用所需的依赖项
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Repository 提供 ====================

    /**
     * 提供模板仓库实例
     */
    @Provides
    @Singleton
    fun provideTemplateRepository(
        @ApplicationContext context: Context
    ): TemplateRepository {
        return DefaultTemplateRepository(context)
    }

    /**
     * 提供扫描数据仓库实例
     */
    @Provides
    @Singleton
    fun provideScanRepository(
        @ApplicationContext context: Context
    ): ScanRepository {
        return DefaultScanRepository(context)
    }

    /**
     * 提供网络仓库实例
     */
    @Provides
    @Singleton
    fun provideNetworkRepository(
        @ApplicationContext context: Context,
        catScanClient: CatScanClient
    ): NetworkRepository {
        return DefaultNetworkRepository(context, catScanClient)
    }

    /**
     * 提供 CatScanClient 实例
     */
    @Provides
    @Singleton
    fun provideCatScanClient(): CatScanClient {
        return CatScanClient()
    }

    /**
     * 提供 NetworkDiscovery 实例
     */
    @Provides
    @Singleton
    fun provideNetworkDiscovery(
        @ApplicationContext context: Context
    ): NetworkDiscovery {
        return NetworkDiscovery(context)
    }

    // ==================== Use Case 提供 ====================

    /**
     * 提供模板相关的 Use Case 实例
     */
    @Provides
    @Singleton
    fun provideTemplateUseCases(
        templateRepository: TemplateRepository
    ): TemplateUseCases {
        val addTemplate = AddTemplateUseCase(templateRepository)
        val deleteTemplate = DeleteTemplateUseCase(templateRepository)
        val updateTemplate = UpdateTemplateUseCase(templateRepository)
        val getTemplateById = GetTemplateByIdUseCase(templateRepository)
        val getActiveTemplate = GetActiveTemplateUseCase(templateRepository)
        val setActiveTemplate = SetActiveTemplateUseCase(templateRepository)
        val clearTemplateScans = ClearTemplateScansUseCase(templateRepository, updateTemplate)
        val deleteTemplateScan = DeleteTemplateScanUseCase(templateRepository, updateTemplate)
        val loadTemplates = LoadTemplatesUseCase(templateRepository)
        val saveTemplates = SaveTemplatesUseCase(templateRepository)

        return TemplateUseCases(
            addTemplate = addTemplate,
            deleteTemplate = deleteTemplate,
            updateTemplate = updateTemplate,
            getTemplateById = getTemplateById,
            getActiveTemplate = getActiveTemplate,
            setActiveTemplate = setActiveTemplate,
            clearTemplateScans = clearTemplateScans,
            deleteTemplateScan = deleteTemplateScan,
            loadTemplates = loadTemplates,
            saveTemplates = saveTemplates
        )
    }

    /**
     * 提供扫描相关的 Use Case 实例
     */
    @Provides
    @Singleton
    fun provideScanUseCases(
        scanRepository: ScanRepository,
        templateRepository: TemplateRepository,
        updateTemplate: UpdateTemplateUseCase
    ): ScanUseCases {
        val addScan = AddScanUseCase(scanRepository)
        val deleteScan = DeleteScanUseCase(scanRepository)
        val updateScan = UpdateScanUseCase(scanRepository)
        val getScanById = GetScanByIdUseCase(scanRepository)
        val getAllScans = GetAllScansUseCase(scanRepository)
        val getPendingScans = GetPendingScansUseCase(scanRepository)
        val markScanAsUploaded = MarkScanAsUploadedUseCase(scanRepository)
        val addScanToTemplate = AddScanToTemplateUseCase(templateRepository, updateTemplate)
        val clearAllScans = ClearAllScansUseCase(scanRepository)
        val replaceAll = ReplaceAllScansUseCase(scanRepository)

        return ScanUseCases(
            addScan = addScan,
            deleteScan = deleteScan,
            updateScan = updateScan,
            getScanById = getScanById,
            getAllScans = getAllScans,
            getPendingScans = getPendingScans,
            markScanAsUploaded = markScanAsUploaded,
            addScanToTemplate = addScanToTemplate,
            clearAllScans = clearAllScans,
            replaceAll = replaceAll,
            scanRepository = scanRepository
        )
    }

    /**
     * 提供网络相关的 Use Case 实例
     */
    @Provides
    @Singleton
    fun provideNetworkUseCases(
        networkRepository: NetworkRepository
    ): NetworkUseCases {
        val uploadScanData = UploadScanDataUseCase(networkRepository)
        val uploadBatchScanData = UploadBatchScanDataUseCase(networkRepository)
        val uploadTemplateData = UploadTemplateDataUseCase(networkRepository)
        val checkServerConnectivity = CheckServerConnectivityUseCase(networkRepository)
        val startNetworkDiscovery = StartNetworkDiscoveryUseCase(networkRepository)
        val stopNetworkDiscovery = StopNetworkDiscoveryUseCase(networkRepository)
        val selectDiscoveredServer = SelectDiscoveredServerUseCase(networkRepository)
        val startHeartbeatDetection = StartHeartbeatDetectionUseCase(networkRepository)
        val stopHeartbeatDetection = StopHeartbeatDetectionUseCase(networkRepository)

        return NetworkUseCases(
            uploadScanData = uploadScanData,
            uploadBatchScanData = uploadBatchScanData,
            uploadTemplateData = uploadTemplateData,
            checkServerConnectivity = checkServerConnectivity,
            startNetworkDiscovery = startNetworkDiscovery,
            stopNetworkDiscovery = stopNetworkDiscovery,
            selectDiscoveredServer = selectDiscoveredServer,
            startHeartbeatDetection = startHeartbeatDetection,
            stopHeartbeatDetection = stopHeartbeatDetection
        )
    }

    /**
     * 提供 UpdateTemplateUseCase 实例
     */
    @Provides
    @Singleton
    fun provideUpdateTemplateUseCase(
        templateRepository: TemplateRepository
    ): UpdateTemplateUseCase {
        return UpdateTemplateUseCase(templateRepository)
    }
}
