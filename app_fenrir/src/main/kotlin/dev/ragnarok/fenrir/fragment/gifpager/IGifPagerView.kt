package dev.ragnarok.fenrir.fragment.gifpager

import androidx.annotation.StringRes
import dev.ragnarok.fenrir.fragment.absdocumentpreview.IBasicDocumentView
import dev.ragnarok.fenrir.model.Document

interface IGifPagerView : IBasicDocumentView {
    fun displayData(mDocuments: List<Document>, selectedIndex: Int)
    fun setupAddRemoveButton(addEnable: Boolean)
    fun toolbarTitle(@StringRes titleRes: Int, vararg params: Any?)
    fun toolbarSubtitle(@StringRes titleRes: Int, vararg params: Any?)
}