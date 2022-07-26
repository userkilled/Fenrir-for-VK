package dev.ragnarok.filegallery.mvp.core

import android.os.Bundle

interface IPresenter<V : IMvpView> {
    fun saveState(outState: Bundle)

    fun destroy()
    fun resumeView()
    fun pauseView()
    fun attachViewHost(view: V)
    fun detachViewHost()
    fun createView(view: V)
    fun destroyView()
}