package dev.ragnarok.fenrir.fragment.gifpager

import android.content.Context
import android.os.Bundle
import android.view.View
import com.google.android.material.snackbar.BaseTransientBottomBar
import dev.ragnarok.fenrir.R
import dev.ragnarok.fenrir.fragment.absdocumentpreview.BaseDocumentPresenter
import dev.ragnarok.fenrir.model.Document
import dev.ragnarok.fenrir.util.AppPerms.hasReadWriteStoragePermission
import dev.ragnarok.fenrir.util.DownloadWorkUtils.doDownloadDoc
import dev.ragnarok.fenrir.util.toast.CustomSnackbars

class GifPagerPresenter(
    accountId: Int,
    private val mDocuments: ArrayList<Document>,
    index: Int,
    savedInstanceState: Bundle?
) : BaseDocumentPresenter<IGifPagerView>(accountId, savedInstanceState) {
    private var mCurrentIndex = 0
    override fun saveState(outState: Bundle) {
        super.saveState(outState)
        outState.putInt(SAVE_PAGER_INDEX, mCurrentIndex)
    }

    fun selectPage(position: Int) {
        if (mCurrentIndex == position) {
            return
        }
        mCurrentIndex = position
        resolveToolbarSubtitle()
    }

    private fun resolveToolbarTitle() {
        view?.toolbarTitle(R.string.gif_player)
    }

    private fun resolveToolbarSubtitle() {
        view?.toolbarSubtitle(
            R.string.image_number,
            mCurrentIndex + 1,
            mDocuments.size
        )
    }

    private fun resolveAddDeleteButton() {
        view?.setupAddRemoveButton(!isMy)
    }

    private val isMy: Boolean
        get() = mDocuments[mCurrentIndex].ownerId == accountId

    override fun onGuiCreated(viewHost: IGifPagerView) {
        super.onGuiCreated(viewHost)
        viewHost.displayData(mDocuments, mCurrentIndex)
        resolveAddDeleteButton()
        resolveToolbarTitle()
        resolveToolbarSubtitle()
    }

    fun fireAddDeleteButtonClick() {
        val document = mDocuments[mCurrentIndex]
        if (isMy) {
            delete(document.id, document.ownerId)
        } else {
            addYourself(document)
        }
    }

    fun fireShareButtonClick() {
        view?.shareDocument(
            accountId,
            mDocuments[mCurrentIndex]
        )
    }

    fun fireDownloadButtonClick(context: Context, view: View?) {
        if (!hasReadWriteStoragePermission(context)) {
            resumedView?.requestWriteExternalStoragePermission()
            return
        }
        downloadImpl(context, view)
    }

    public override fun onWritePermissionResolved(context: Context, view: View?) {
        if (hasReadWriteStoragePermission(context)) {
            downloadImpl(context, view)
        }
    }

    private fun downloadImpl(context: Context, view: View?) {
        val document = mDocuments[mCurrentIndex]
        if (doDownloadDoc(context, document, false) == 1) {
            CustomSnackbars.createCustomSnackbars(view)
                ?.setDurationSnack(BaseTransientBottomBar.LENGTH_LONG)
                ?.themedSnack(R.string.audio_force_download)
                ?.setAction(
                    R.string.button_yes
                ) {
                    doDownloadDoc(
                        context, document, true
                    )
                }?.show()
        }
    }

    companion object {
        private const val SAVE_PAGER_INDEX = "save_pager_index"
    }

    init {
        mCurrentIndex = savedInstanceState?.getInt(SAVE_PAGER_INDEX) ?: index
    }
}