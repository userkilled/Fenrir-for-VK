package dev.ragnarok.fenrir.upload.impl

import android.content.Context
import dev.ragnarok.fenrir.api.PercentagePublisher
import dev.ragnarok.fenrir.api.interfaces.INetworker
import dev.ragnarok.fenrir.api.model.server.UploadServer
import dev.ragnarok.fenrir.api.model.server.VkApiVideosUploadServer
import dev.ragnarok.fenrir.db.AttachToType
import dev.ragnarok.fenrir.db.interfaces.IMessagesStorage
import dev.ragnarok.fenrir.domain.IAttachmentsRepository
import dev.ragnarok.fenrir.exception.NotFoundException
import dev.ragnarok.fenrir.model.Video
import dev.ragnarok.fenrir.upload.IUploadable
import dev.ragnarok.fenrir.upload.Upload
import dev.ragnarok.fenrir.upload.UploadResult
import dev.ragnarok.fenrir.upload.UploadUtils
import dev.ragnarok.fenrir.util.RxUtils.safelyCloseAction
import dev.ragnarok.fenrir.util.Utils.safelyClose
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class VideoToMessageUploadable(
    private val context: Context,
    private val networker: INetworker,
    private val attachmentsRepository: IAttachmentsRepository,
    private val messagesStorage: IMessagesStorage
) : IUploadable<Video> {
    override fun doUpload(
        upload: Upload,
        initialServer: UploadServer?,
        listener: PercentagePublisher?
    ): Single<UploadResult<Video>> {
        val accountId = upload.accountId
        val messageId = upload.destination.id
        val serverSingle = networker.vkDefault(accountId)
            .docs()
            .getVideoServer(1, null, UploadUtils.findFileName(context, upload.fileUri))
            .map<UploadServer> { s: VkApiVideosUploadServer -> s }
        return serverSingle.flatMap { server ->
            val `is` = arrayOfNulls<InputStream>(1)
            try {
                val uri = upload.fileUri
                val file = File(uri!!.path!!)
                if (file.isFile) {
                    `is`[0] = FileInputStream(file)
                } else {
                    `is`[0] = context.contentResolver.openInputStream(uri)
                }
                if (`is`[0] == null) {
                    return@flatMap Single.error<UploadResult<Video>>(
                        NotFoundException(
                            "Unable to open InputStream, URI: $uri"
                        )
                    )
                }
                val filename = UploadUtils.findFileName(
                    context, uri
                )
                networker.uploads()
                    .uploadVideoRx(server.url, filename, `is`[0]!!, listener)
                    .doFinally(safelyCloseAction(`is`[0]))
                    .flatMap { dto ->
                        val video = Video().setId(dto.video_id).setOwnerId(dto.owner_id).setTitle(
                            UploadUtils.findFileName(
                                context, upload.fileUri
                            )
                        )
                        val result = UploadResult(server, video)
                        if (upload.isAutoCommit) {
                            attachIntoDatabaseRx(
                                attachmentsRepository, messagesStorage, accountId, messageId, video
                            )
                                .andThen(Single.just(result))
                        } else {
                            Single.just(result)
                        }
                    }
            } catch (e: Exception) {
                safelyClose(`is`[0])
                Single.error(e)
            }
        }
    }

    companion object {
        fun attachIntoDatabaseRx(
            repository: IAttachmentsRepository, storage: IMessagesStorage,
            accountId: Int, messageId: Int, video: Video
        ): Completable {
            return repository
                .attach(accountId, AttachToType.MESSAGE, messageId, listOf(video))
                .andThen(storage.notifyMessageHasAttachments(accountId, messageId))
        }
    }
}