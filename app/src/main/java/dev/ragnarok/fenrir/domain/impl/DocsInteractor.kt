package dev.ragnarok.fenrir.domain.impl

import dev.ragnarok.fenrir.api.interfaces.INetworker
import dev.ragnarok.fenrir.api.model.AccessIdPair
import dev.ragnarok.fenrir.api.model.VkApiDoc
import dev.ragnarok.fenrir.db.interfaces.IDocsStorage
import dev.ragnarok.fenrir.db.model.entity.DocumentEntity
import dev.ragnarok.fenrir.domain.IDocsInteractor
import dev.ragnarok.fenrir.domain.mappers.Dto2Entity.mapDoc
import dev.ragnarok.fenrir.domain.mappers.Dto2Model.transform
import dev.ragnarok.fenrir.domain.mappers.Entity2Model.buildDocumentFromDbo
import dev.ragnarok.fenrir.exception.NotFoundException
import dev.ragnarok.fenrir.fragment.search.criteria.DocumentSearchCriteria
import dev.ragnarok.fenrir.model.Document
import dev.ragnarok.fenrir.model.criteria.DocsCriteria
import dev.ragnarok.fenrir.util.Utils.listEmptyIfNull
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

class DocsInteractor(private val networker: INetworker, private val cache: IDocsStorage) :
    IDocsInteractor {
    override fun request(accountId: Int, ownerId: Int, filter: Int): Single<List<Document>> {
        return networker.vkDefault(accountId)
            .docs()[ownerId, null, null, filter]
            .map { items ->
                listEmptyIfNull<VkApiDoc>(
                    items.getItems()
                )
            }
            .flatMap { dtos ->
                val documents: MutableList<Document> = ArrayList(dtos.size)
                val entities: MutableList<DocumentEntity> = ArrayList(dtos.size)
                for (dto in dtos) {
                    documents.add(transform(dto))
                    entities.add(mapDoc(dto))
                }
                cache.store(accountId, ownerId, entities, true)
                    .andThen(Single.just<List<Document>>(documents))
            }
    }

    override fun getCacheData(accountId: Int, ownerId: Int, filter: Int): Single<List<Document>> {
        return cache[DocsCriteria(accountId, ownerId).setFilter(filter)]
            .map { entities ->
                val documents: MutableList<Document> = ArrayList(entities.size)
                for (entity in entities) {
                    documents.add(buildDocumentFromDbo(entity))
                }
                documents
            }
    }

    override fun add(accountId: Int, docId: Int, ownerId: Int, accessKey: String?): Single<Int> {
        return networker.vkDefault(accountId)
            .docs()
            .add(ownerId, docId, accessKey)
    }

    override fun findById(
        accountId: Int,
        ownerId: Int,
        docId: Int,
        accessKey: String?
    ): Single<Document> {
        return networker.vkDefault(accountId)
            .docs()
            .getById(listOf(AccessIdPair(docId, ownerId, accessKey)))
            .map { dtos ->
                if (dtos.isEmpty()) {
                    throw NotFoundException()
                }
                transform(dtos[0])
            }
    }

    override fun search(
        accountId: Int,
        criteria: DocumentSearchCriteria,
        count: Int,
        offset: Int
    ): Single<List<Document>> {
        return networker.vkDefault(accountId)
            .docs()
            .search(criteria.query, count, offset)
            .map { items ->
                val dtos = listEmptyIfNull<VkApiDoc>(
                    items.getItems()
                )
                val documents: MutableList<Document> = ArrayList()
                for (dto in dtos) {
                    documents.add(transform(dto))
                }
                documents
            }
    }

    override fun delete(accountId: Int, docId: Int, ownerId: Int): Completable {
        return networker.vkDefault(accountId)
            .docs()
            .delete(ownerId, docId)
            .flatMapCompletable { cache.delete(accountId, docId, ownerId) }
    }
}