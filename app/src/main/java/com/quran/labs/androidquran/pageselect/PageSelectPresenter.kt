package com.quran.labs.androidquran.pageselect

import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.ImageUtil
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.UrlUtil
import dagger.Reusable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@Reusable
class PageSelectPresenter @Inject
constructor(
  private val imageUtil: ImageUtil,
  private val quranFileUtils: QuranFileUtils,
  private val mainThreadScheduler: Scheduler,
  private val urlUtil: UrlUtil,
  private val bookmarksDao: BookmarksDao,
  // unfortunately needed for now due to the old Rx code
  // not knowing about changes from BookmarksDao, etc.
  private val bookmarkModel: BookmarkModel,
  private val pageTypes:
  Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>
) :
  Presenter<PageSelectActivity> {
  private val baseUrl = "https://android.quran.com/data/pagetypes/snips"
  private val compositeDisposable = CompositeDisposable()
  private val downloadingSet = mutableSetOf<String>()
  private var currentView: PageSelectActivity? = null

  private fun generateData() {
    val base = quranFileUtils.quranBaseDirectory
    if (base != null) {
      val outputPath = File(File(base, "pagetypes"), "snips")
      if (!outputPath.exists()) {
        outputPath.mkdirs()
        File(outputPath, ".nomedia").createNewFile()
      }

      val data = pageTypes.map {
        val provider = it.value
        val previewImage = File(outputPath, "${it.key}.png")
        val downloadedImage = if (previewImage.exists()) {
          previewImage
        } else if (!downloadingSet.contains(it.key)) {
          downloadingSet.add(it.key)
          val url = "$baseUrl/${it.key}.png"
          compositeDisposable.add(
            imageUtil.downloadImage(url, previewImage)
              .onErrorResumeWith(
                imageUtil.downloadImage(urlUtil.fallbackUrl(url), previewImage)
              )
              .subscribeOn(Schedulers.io())
              .observeOn(mainThreadScheduler)
              .subscribe({ generateData() }, { e -> Timber.e(e) })
          )
          null
        } else {
          // already downloading
          null
        }
        PageTypeItem(
          it.key,
          downloadedImage,
          provider.getPreviewTitle(),
          provider.getPreviewDescription()
        )
      }
      currentView?.onUpdatedData(data)
    }
  }

  /**
   * Migrate bookmark and recent page data between two page types
   * Consider a page set like madani (604 pages) versus one like Shemerly (521 pages).
   * When switching between them, bookmarks need to be mapped so that the same bookmark
   * retains its meaning.
   *
   * Note that this does not support non-Hafs qira'at yet, where the ayah numbers may
   * have changed due to kufi versus madani counting.
   */
  suspend fun migrateBookmarksData(sourcePageType: String, destinationPageType: String) {
    val source = pageTypes[sourcePageType]?.getDataSource()
    val destination = pageTypes[destinationPageType]?.getDataSource()
    if (source != null && destination != null && source.getNumberOfPages() != destination.getNumberOfPages()) {
      val sourcePageSuraStart = source.getSuraForPageArray()
      val sourcePageAyahStart = source.getAyahForPageArray()
      val destinationQuranInfo = QuranInfo(destination)

      val suraAyahFromPage = { page: Int ->
        sourcePageSuraStart[page - 1] to sourcePageAyahStart[page - 1]
      }

      // update the bookmarks
      val updatedBookmarks = bookmarksDao.bookmarks()
        .map {
          val page = it.page
          val (pageSura, pageAyah) = suraAyahFromPage(page)
          val sura = it.sura ?: pageSura
          val ayah = it.ayah ?: pageAyah

          val mappedPage = destinationQuranInfo.getPageFromSuraAyah(sura, ayah)

          // we only copy the page because sura and ayah are the same.
          it.copy(page = mappedPage)
        }

      if (updatedBookmarks.isNotEmpty()) {
        bookmarksDao.replaceBookmarks(updatedBookmarks)
        bookmarkModel.notifyBookmarksUpdated()
      }

      // and update the recents
      val updatedRecentPages = bookmarksDao.recentPages()
        .sortedBy { it.timestamp }
        .map {
          val page = it.page
          val (pageSura, pageAyah) = suraAyahFromPage(page)

          val mappedPage = destinationQuranInfo.getPageFromSuraAyah(pageSura, pageAyah)
          it.copy(page = mappedPage)
        }

      if (updatedRecentPages.isNotEmpty()) {
        bookmarksDao.replaceRecentPages(updatedRecentPages)
        bookmarkModel.notifyRecentPagesUpdated()
      }
    }
  }

  override fun bind(what: PageSelectActivity) {
    currentView = what
    generateData()
  }

  override fun unbind(what: PageSelectActivity) {
    if (currentView === what) {
      currentView = null
      compositeDisposable.clear()
      downloadingSet.clear()
    }
  }
}
