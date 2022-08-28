package com.quran.mobile.feature.downloadmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.downloadmanager.di.DownloadManagerComponentInterface
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import com.quran.mobile.feature.downloadmanager.presenter.SheikhAudioPresenter
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.SheikhDownloadToolbar
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.SheikhSuraInfoList
import com.quran.page.common.data.QuranNaming
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class SheikhAudioDownloadsActivity : ComponentActivity() {
  @Inject
  lateinit var quranNaming: QuranNaming

  @Inject
  lateinit var sheikhAudioPresenter: SheikhAudioPresenter

  private var qariId: Int = -1
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    qariId = intent.getIntExtra(EXTRA_QARI_ID, -1)
    if (qariId < 0) {
      finish()
      return
    }

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? DownloadManagerComponentInterface
    injector?.downloadManagerComponentBuilder()?.build()?.inject(this)

    val sheikhDownloadsFlow = sheikhAudioPresenter.sheikhInfo(qariId)

    setContent {
      val selectionState = remember { mutableStateOf(listOf<SuraForQari>()) }
      val sheikhDownloadsState = sheikhDownloadsFlow.collectAsState(null)
      QuranTheme {
        Column(modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .fillMaxSize()
        ) {
          val currentDownloadState = sheikhDownloadsState.value
          val titleResource = currentDownloadState?.qariItem?.nameResource ?: R.string.audio_manager

          val selectionInfo = selectionState.value
          SheikhDownloadToolbar(
            titleResource = titleResource,
            isContextual = selectionInfo.isNotEmpty(),
            selectionInfo.isEmpty() || selectionInfo.any { !it.isDownloaded },
            selectionInfo.any { it.isDownloaded },
            downloadAction = { onDownloadSelected(selectionInfo) },
            eraseAction = { onRemoveSelected(selectionInfo) },
            onBackAction = {
              if (selectionInfo.isEmpty()) {
                finish()
              } else {
                // end contextual mode on back with suras selected
                selectionState.value = emptyList()
              }
            }
          )

          if (currentDownloadState != null) {
            SheikhSuraInfoList(
              sheikhUiModel = currentDownloadState,
              currentSelection = selectionState.value,
              quranNaming = quranNaming,
              onSelectionInfoChanged = { selectionState.value = it },
              onSuraActionRequested = ::onActionRequested
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun onActionRequested(item: SuraForQari) {
    if (item.isDownloaded) {
      onRemoveSelected(listOf(item))
    } else {
      onDownloadSelected(listOf(item))
    }
  }

  private fun onDownloadSelected(selectedSuras: List<SuraForQari>) {
    val surasToDownload = selectedSuras
      .filter { !it.isDownloaded }
      .map { it.sura }
      .ifEmpty { (1..114).toList() }
    scope.launch {
      sheikhAudioPresenter.downloadSuras(qariId, surasToDownload)
    }
  }

  private fun onRemoveSelected(selectedSuras: List<SuraForQari>) {
    val surasToRemove = selectedSuras.filter { it.isDownloaded }.map { it.sura }
    scope.launch {
      sheikhAudioPresenter.removeSuras(qariId, surasToRemove)
    }
  }

  companion object {
    const val EXTRA_QARI_ID = "SheikhAudioDownloadsActivity.extra_qari_id"
  }
}
