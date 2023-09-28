package dev.ragnarok.fenrir.db.impl

import android.content.ContentValues
import android.database.Cursor
import android.provider.BaseColumns
import dev.ragnarok.fenrir.crypt.AesKeyPair
import dev.ragnarok.fenrir.db.FenrirContentProvider.Companion.getKeysContentUriFor
import dev.ragnarok.fenrir.db.column.EncryptionKeysForMessagesColumns
import dev.ragnarok.fenrir.db.interfaces.IKeysStorage
import dev.ragnarok.fenrir.exception.DatabaseException
import dev.ragnarok.fenrir.getInt
import dev.ragnarok.fenrir.getLong
import dev.ragnarok.fenrir.getString
import dev.ragnarok.fenrir.util.Optional
import dev.ragnarok.fenrir.util.Optional.Companion.wrap
import dev.ragnarok.fenrir.util.Utils.safeCountOf
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.CompletableEmitter
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.MaybeEmitter
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter

internal class KeysPersistStorage(context: AppStorages) : AbsStorage(context), IKeysStorage {
    private fun map(cursor: Cursor): AesKeyPair {
        return AesKeyPair()
            .setVersion(cursor.getInt(EncryptionKeysForMessagesColumns.VERSION))
            .setPeerId(cursor.getLong(EncryptionKeysForMessagesColumns.PEER_ID))
            .setSessionId(cursor.getLong(EncryptionKeysForMessagesColumns.SESSION_ID))
            .setDate(cursor.getLong(EncryptionKeysForMessagesColumns.DATE))
            .setStartMessageId(cursor.getInt(EncryptionKeysForMessagesColumns.START_SESSION_MESSAGE_ID))
            .setEndMessageId(cursor.getInt(EncryptionKeysForMessagesColumns.END_SESSION_MESSAGE_ID))
            .setHisAesKey(cursor.getString(EncryptionKeysForMessagesColumns.IN_KEY))
            .setMyAesKey(cursor.getString(EncryptionKeysForMessagesColumns.OUT_KEY))
    }

    override fun saveKeyPair(pair: AesKeyPair): Completable {
        return Completable.create { e: CompletableEmitter ->
            val alreadyExist = findKeyPairFor(pair.accountId, pair.sessionId)
                .blockingGet()
            if (alreadyExist != null) {
                e.tryOnError(DatabaseException("Key pair with the session ID is already in the database"))
                return@create
            }
            val cv = ContentValues()
            cv.put(EncryptionKeysForMessagesColumns.VERSION, pair.version)
            cv.put(EncryptionKeysForMessagesColumns.PEER_ID, pair.peerId)
            cv.put(EncryptionKeysForMessagesColumns.SESSION_ID, pair.sessionId)
            cv.put(EncryptionKeysForMessagesColumns.DATE, pair.date)
            cv.put(EncryptionKeysForMessagesColumns.START_SESSION_MESSAGE_ID, pair.startMessageId)
            cv.put(EncryptionKeysForMessagesColumns.END_SESSION_MESSAGE_ID, pair.endMessageId)
            cv.put(EncryptionKeysForMessagesColumns.OUT_KEY, pair.myAesKey)
            cv.put(EncryptionKeysForMessagesColumns.IN_KEY, pair.hisAesKey)
            val uri = getKeysContentUriFor(pair.accountId)
            context.contentResolver.insert(uri, cv)
            e.onComplete()
        }
    }

    override fun getAll(accountId: Long): Single<List<AesKeyPair>> {
        return Single.create { e: SingleEmitter<List<AesKeyPair>> ->
            val uri = getKeysContentUriFor(accountId)
            val cursor = context.contentResolver.query(uri, null, null, null, BaseColumns._ID)
            val pairs: MutableList<AesKeyPair> = ArrayList(safeCountOf(cursor))
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed) {
                        break
                    }
                    pairs.add(map(cursor).setAccountId(accountId))
                }
                cursor.close()
            }
            e.onSuccess(pairs)
        }
    }

    override fun getKeys(accountId: Long, peerId: Long): Single<List<AesKeyPair>> {
        return Single.create { e: SingleEmitter<List<AesKeyPair>> ->
            val uri = getKeysContentUriFor(accountId)
            val cursor = context.contentResolver
                .query(
                    uri,
                    null,
                    EncryptionKeysForMessagesColumns.PEER_ID + " = ?",
                    arrayOf(peerId.toString()),
                    BaseColumns._ID
                )
            val pairs: MutableList<AesKeyPair> = ArrayList(safeCountOf(cursor))
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed) {
                        break
                    }
                    pairs.add(map(cursor).setAccountId(accountId))
                }
                cursor.close()
            }
            e.onSuccess(pairs)
        }
    }

    override fun findLastKeyPair(accountId: Long, peerId: Long): Single<Optional<AesKeyPair>> {
        return Single.create { e: SingleEmitter<Optional<AesKeyPair>> ->
            val uri = getKeysContentUriFor(accountId)
            val cursor = context.contentResolver
                .query(
                    uri,
                    null,
                    EncryptionKeysForMessagesColumns.PEER_ID + " = ?",
                    arrayOf(peerId.toString()),
                    BaseColumns._ID + " DESC LIMIT 1"
                )
            var pair: AesKeyPair? = null
            if (cursor != null) {
                if (cursor.moveToNext()) {
                    pair = map(cursor).setAccountId(accountId)
                }
                cursor.close()
            }
            e.onSuccess(wrap(pair))
        }
    }

    override fun findKeyPairFor(accountId: Long, sessionId: Long): Maybe<AesKeyPair> {
        return Maybe.create { e: MaybeEmitter<AesKeyPair> ->
            val uri = getKeysContentUriFor(accountId)
            val cursor = context.contentResolver
                .query(
                    uri,
                    null,
                    EncryptionKeysForMessagesColumns.SESSION_ID + " = ?",
                    arrayOf(sessionId.toString()),
                    null
                )
            var pair: AesKeyPair? = null
            if (cursor != null) {
                if (cursor.moveToNext()) {
                    pair = map(cursor).setAccountId(accountId)
                }
                cursor.close()
            }
            if (pair != null) {
                e.onSuccess(pair)
            }
            e.onComplete()
        }
    }

    override fun deleteAll(accountId: Long): Completable {
        return Completable.create { e: CompletableEmitter ->
            val uri = getKeysContentUriFor(accountId)
            context.contentResolver.delete(uri, null, null)
            e.onComplete()
        }
    }
}