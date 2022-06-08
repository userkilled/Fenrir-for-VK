package dev.ragnarok.fenrir.mvp.presenter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.CallSuper
import dev.ragnarok.fenrir.Includes.voicePlayerFactory
import dev.ragnarok.fenrir.R
import dev.ragnarok.fenrir.domain.Repository
import dev.ragnarok.fenrir.fromIOToMain
import dev.ragnarok.fenrir.media.voice.IVoicePlayer
import dev.ragnarok.fenrir.media.voice.IVoicePlayer.IPlayerStatusListener
import dev.ragnarok.fenrir.model.LastReadId
import dev.ragnarok.fenrir.model.Message
import dev.ragnarok.fenrir.model.VoiceMessage
import dev.ragnarok.fenrir.mvp.presenter.base.PlaceSupportPresenter
import dev.ragnarok.fenrir.mvp.view.IBasicMessageListView
import dev.ragnarok.fenrir.settings.Settings
import dev.ragnarok.fenrir.util.Lookup
import dev.ragnarok.fenrir.util.RxUtils
import dev.ragnarok.fenrir.util.Utils

abstract class AbsMessageListPresenter<V : IBasicMessageListView> internal constructor(
    accountId: Int,
    savedInstanceState: Bundle?
) : PlaceSupportPresenter<V>(accountId, savedInstanceState), IPlayerStatusListener {
    protected val lastReadId = LastReadId(0, 0)
    val data: ArrayList<Message> = ArrayList()
    private var mVoicePlayer: IVoicePlayer? = null
    private var mVoiceMessageLookup: Lookup?
    private fun syncVoiceLookupState() {
        val needLookup = mVoicePlayer?.isSupposedToPlay == true && guiIsReady
        if (needLookup) {
            mVoiceMessageLookup?.start()
        } else {
            mVoiceMessageLookup?.stop()
        }
    }

    private fun resolveListView() {
        view?.displayMessages(data, lastReadId)
    }

    protected fun indexOf(messageId: Int): Int {
        return Utils.indexOf(data, messageId)
    }

    protected fun findById(messageId: Int): Message? {
        return Utils.findById(data, messageId)
    }

    protected fun clearSelection(): Boolean {
        var hasChanges = false
        for (message in data) {
            if (message.isSelected) {
                message.isSelected = false
                hasChanges = true
            }
        }
        return hasChanges
    }

    protected open fun resolveActionMode() {
        val selectionCount = Utils.countOfSelection(data)
        if (selectionCount > 0) {
            view?.showActionMode(
                selectionCount.toString(),
                canEdit = false,
                canPin = false,
                canStar = false,
                doStar = false,
                canSpam = true
            )
        } else {
            view?.finishActionMode()
        }
    }

    override fun onGuiCreated(viewHost: V) {
        super.onGuiCreated(viewHost)
        syncVoiceLookupState()
        resolveListView()
        resolveActionMode()
    }

    protected fun safeNotifyDataChanged() {
        view?.notifyDataChanged()
    }

    private fun safeNotifyItemChanged(index: Int) {
        view?.notifyItemChanged(index)
    }

    fun fireMessageLongClick(message: Message, position: Int) {
        message.isSelected = !message.isSelected
        resolveActionMode()
        safeNotifyItemChanged(position)
    }

    fun fireMessageClick(message: Message, position: Int) {
        val actionModeActive = Utils.countOfSelection(data)
        if (actionModeActive > 0) {
            message.isSelected = !message.isSelected
            resolveActionMode()
            safeNotifyItemChanged(position)
        } else {
            onMessageClick(message)
        }
    }

    protected open fun onMessageClick(message: Message) {}
    fun fireActionModeDestroy() {
        onActionModeDestroy()
    }

    @CallSuper
    protected fun onActionModeDestroy() {
        if (clearSelection()) {
            safeNotifyDataChanged()
        }
    }

    fun fireActionModeDeleteClick() {
        onActionModeDeleteClick()
    }

    protected open fun onActionModeDeleteClick() {}
    fun fireActionModeSpamClick() {
        onActionModeSpamClick()
    }

    protected open fun onActionModeSpamClick() {}
    fun fireActionModeCopyClick() {
        onActionModeCopyClick()
    }

    private fun onActionModeCopyClick() {
        val selected: List<Message> = Utils.getSelected(
            data, true
        )
        if (selected.isEmpty()) return
        val result = StringBuilder()
        var firstTime = true
        for (message in selected) {
            val body =
                if (message.decryptedBody.isNullOrEmpty()) message.body else message.decryptedBody
            result.append(if (!firstTime) "\n" else "")
            result.append(body)
            if (message.isVoiceMessage) {
                if (!body.isNullOrEmpty()) {
                    result.append("\n\n")
                }
                var firstTimeVoice = true
                for (vs in message.attachments?.voiceMessages.orEmpty()) {
                    if (!vs.getTranscript().isNullOrEmpty()) {
                        result.append(if (!firstTimeVoice) "\n" else "")
                        result.append(vs.getTranscript())
                        firstTimeVoice = false
                    }
                }
            }
            firstTime = false
        }
        val clipboard = applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("messages", result)
        clipboard?.setPrimaryClip(clip)
        view?.customToast?.setDuration(Toast.LENGTH_LONG)?.showToast(R.string.copied_to_clipboard)
    }

    fun fireForwardClick() {
        onActionModeForwardClick()
    }

    protected open fun onActionModeForwardClick() {}

    @Suppress("UNUSED_PARAMETER")
    fun fireVoicePlayButtonClick(
        voiceHolderId: Int,
        voiceMessageId: Int,
        messageId: Int,
        peerId: Int,
        voiceMessage: VoiceMessage
    ) {
        val player = mVoicePlayer ?: return
        try {
            val messageChanged = player.toggle(voiceMessageId, voiceMessage)
            if (messageChanged) {
                if (!voiceMessage.wasListened()) {
                    if (!Utils.isHiddenCurrent && Settings.get().other().isMarkListenedVoice) {
                        appendDisposable(
                            Repository.messages.markAsListened(accountId, messageId)
                                .fromIOToMain()
                                .subscribe({
                                    voiceMessage.setWasListened(true)
                                    resolveVoiceMessagePlayingState()
                                }, RxUtils.ignore())
                        )
                    }
                }
                resolveVoiceMessagePlayingState()
            } else {
                val paused = !player.isSupposedToPlay
                val progress = player.progress
                val isSpeed = player.isPlaybackSpeed
                view?.bindVoiceHolderById(
                    voiceHolderId,
                    true,
                    paused,
                    progress,
                    false,
                    isSpeed
                )
            }
        } catch (ignored: Exception) {
        }
        syncVoiceLookupState()
    }

    fun fireVoicePlaybackSpeed() {
        mVoicePlayer?.togglePlaybackSpeed()
    }

    private fun resolveVoiceMessagePlayingState(anim: Boolean = false) {
        val player = mVoicePlayer ?: return
        val optionalVoiceMessageId = player.playingVoiceId
        if (optionalVoiceMessageId.isEmpty) {
            view?.disableVoicePlaying()
        } else {
            val progress = player.progress
            val paused = !player.isSupposedToPlay
            val isSpeed = player.isPlaybackSpeed

            view?.configNowVoiceMessagePlaying(
                optionalVoiceMessageId.get() ?: return,
                progress,
                paused,
                anim,
                isSpeed
            )
        }
    }

    public override fun onGuiDestroyed() {
        syncVoiceLookupState()
        super.onGuiDestroyed()
    }

    fun fireVoiceHolderCreated(voiceMessageId: Int, voiceHolderId: Int) {
        val player = mVoicePlayer ?: return
        val currentVoiceId = player.playingVoiceId
        val play = currentVoiceId.nonEmpty() && currentVoiceId.get() == voiceMessageId
        val paused = play && !player.isSupposedToPlay
        val isSpeed = player.isPlaybackSpeed
        view?.bindVoiceHolderById(
            voiceHolderId,
            play,
            paused,
            player.progress,
            false,
            isSpeed
        )
    }

    override fun onPlayerStatusChange(status: Int) {
        //Optional<Integer> voiceMessageId = mVoicePlayer.getPlayingVoiceId();
    }

    override fun onDestroyed() {
        super.onDestroyed()
        mVoicePlayer?.setCallback(null)
        mVoicePlayer?.release()
        mVoicePlayer = null
        mVoiceMessageLookup?.stop()
        mVoiceMessageLookup?.setCallback(null)
        mVoiceMessageLookup = null
    }

    private fun createVoicePlayer() {
        mVoicePlayer = voicePlayerFactory.createPlayer()
        mVoicePlayer?.setCallback(this)
    }

    init {
        createVoicePlayer()
        mVoiceMessageLookup = Lookup(500)
        mVoiceMessageLookup?.setCallback(object : Lookup.Callback {
            override fun onIterated() {
                resolveVoiceMessagePlayingState(true)
            }
        })
    }
}