package com.quran.labs.androidquran.database

import com.quran.data.dao.BookmarksDao
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BookmarksDaoImpl @Inject constructor(
  private val bookmarksDBAdapter: BookmarksDBAdapter
) : BookmarksDao {

  override suspend fun bookmarks(): List<Bookmark> {
    return withContext(Dispatchers.IO) {
      bookmarksDBAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED)
    }
  }

  override suspend fun replaceBookmarks(bookmarks: List<Bookmark>) {
    withContext(Dispatchers.IO) {
      bookmarksDBAdapter.updateBookmarks(bookmarks)
    }
  }

  override suspend fun recentPages(): List<RecentPage> {
    return withContext(Dispatchers.IO) {
      bookmarksDBAdapter.getRecentPages()
    }
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    withContext(Dispatchers.IO) {
      bookmarksDBAdapter.replaceRecentPages(pages)
    }
  }
}
