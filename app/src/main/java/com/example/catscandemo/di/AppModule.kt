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
 * Hilt 妯″潡
 * 鎻愪緵搴旂敤鎵€闇€鐨勪緷璧栭」
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Repository 鎻愪緵 ====================

    /**
     * 鎻愪緵妯℃澘浠撳簱瀹炰緥
     */
    @Provides
    @Singleton
    fun provideTemplateRepository(
        @ApplicationContext context: Context
    ): TemplateRepository {
        return DefaultTemplateRepository(context)
    }

    /**
     * 鎻愪緵鎵弿鏁版嵁浠撳簱瀹炰緥
     */
    @Provides
    @Singleton
    fun provideScanRepository(
        @ApplicationContext context: Context
    ): ScanRepository {
        return DefaultScanRepository(context)
    }

    /**
     * 鎻愪緵缃戠粶浠撳簱瀹炰緥
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
     * 鎻愪緵 CatScanClient 瀹炰緥
     */
    @Provides
    @Singleton
    fun provideCatScanClient(): CatScanClient {
        return CatScanClient()
    }

    /**
     * 鎻愪緵 NetworkDiscovery 瀹炰緥
     */
    @Provides
    @Singleton
    fun provideNetworkDiscovery(
        @ApplicationContext context: Context
    ): NetworkDiscovery {
        return NetworkDiscovery(context)
    }

    // ==================== Use Case 鎻愪緵 ====================

    /**
     * 鎻愪緵妯℃澘鐩稿叧鐨?Use Case 瀹炰緥
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
     * 鎻愪緵鎵弿鐩稿叧鐨?Use Case 瀹炰緥
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
     * 鎻愪緵缃戠粶鐩稿叧鐨?Use Case 瀹炰緥
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
     * 鎻愪緵 UpdateTemplateUseCase 瀹炰緥
     */
    @Provides
    @Singleton
    fun provideUpdateTemplateUseCase(
        templateRepository: TemplateRepository
    ): UpdateTemplateUseCase {
        return UpdateTemplateUseCase(templateRepository)
    }
}
