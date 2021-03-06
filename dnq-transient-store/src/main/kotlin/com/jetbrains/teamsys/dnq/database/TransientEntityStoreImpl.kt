/**
 * Copyright 2006 - 2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.teamsys.dnq.database

import jetbrains.exodus.core.dataStructures.StablePriorityQueue
import jetbrains.exodus.database.*
import jetbrains.exodus.entitystore.*
import jetbrains.exodus.env.EnvironmentConfig
import jetbrains.exodus.query.QueryEngine
import jetbrains.exodus.query.metadata.ModelMetaData
import mu.KLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * @author Vadim.Gurov
 */
open class TransientEntityStoreImpl : TransientEntityStore {

    companion object : KLogging()

    private lateinit var _persistentStore: PersistentEntityStore

    /**
     * Must be injected.
     */
    override var persistentStore: PersistentEntityStore
        get() = _persistentStore
        set(persistentStore) {
            val ec = persistentStore.environment.environmentConfig
            if (ec.envTxnDowngradeAfterFlush == EnvironmentConfig.DEFAULT.envTxnDowngradeAfterFlush) {
                ec.envTxnDowngradeAfterFlush = false
            }
            ec.envTxnReplayMaxCount = Integer.MAX_VALUE
            ec.envTxnReplayTimeout = java.lang.Long.MAX_VALUE
            ec.gcUseExclusiveTransaction = true
            _persistentStore = persistentStore
        }

    /**
     * Must be injected.
     */
    override lateinit var queryEngine: QueryEngine
    override var modelMetaData: ModelMetaData? = null
    override var eventsMultiplexer: IEventsMultiplexer? = null
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<TransientStoreSession, Boolean>(200))
    private val currentSession = ThreadLocal<TransientStoreSession>()
    private val listeners = StablePriorityQueue<Int, TransientStoreSessionListener>()

    @field:Volatile
    override var isOpen = true
        protected set
    private var closed = false
    private val enumCache = ConcurrentHashMap<String, Entity>()
    private val persistentClassInstanceCache = ConcurrentHashMap<String, BasePersistentClassImpl>()
    private val persistentClassInstances = ConcurrentHashMap<Class<*>, BasePersistentClassImpl>()

    // fair flushLock
    internal val flushLock = ReentrantLock(true)

    /**
     * It's guaranteed that current thread session is Open, if exists
     */
    override val threadSession: TransientStoreSession?
        get() = currentSession.get()

    private val transientSessionOrThrow: TransientSessionImpl
        get() = threadSessionOrThrow as TransientSessionImpl

    override fun getName() = "transient store"

    override fun getLocation(): String {
        throw UnsupportedOperationException("Not supported by transient store.")
    }

    override fun <T> transactional(
            readonly: Boolean,
            queryCancellingPolicy: QueryCancellingPolicy?,
            isNew: Boolean,
            block: (TransientStoreSession) -> T
    ): T = TransientEntityStoreExt.transactional(this, readonly, queryCancellingPolicy, isNew, block)

    override fun beginTransaction(): StoreTransaction {
        throw UnsupportedOperationException()
    }

    override fun beginExclusiveTransaction(): StoreTransaction {
        throw UnsupportedOperationException()
    }

    override fun beginReadonlyTransaction(): TransientStoreSession {
        return registerStoreSession(TransientSessionImpl(this, readonly = true))
    }

    override fun getCurrentTransaction(): StoreTransaction? {
        throw UnsupportedOperationException()
    }

    override fun beginSession(): TransientStoreSession {
        assertOpen()

        logger.debug { "Begin new session" }

        val currentSession = this.currentSession.get()
        if (currentSession != null) {
            logger.debug { "Return session already associated with the current thread $currentSession" }
            return currentSession
        }

        return registerStoreSession(TransientSessionImpl(this, readonly = false))
    }

    override fun resumeSession(session: TransientStoreSession?) {
        if (session != null) {
            assertOpen()

            val current = currentSession.get()
            if (current != null && current != session) {
                throw IllegalStateException("Another open transient session is already associated with current thread")
            }
            currentSession.set(session)
        }
    }

    override fun close() {
        isOpen = false

        eventsMultiplexer?.onClose(this)

        logger.info { "Close transient store." }
        closed = true

        val sessionsSize = sessions.size
        if (sessionsSize > 0) {
            logger.warn { "There're $sessionsSize open transient sessions. Print." }
            if (logger.isDebugEnabled) {
                sessions.asSequence()
                        .filterIsInstance<TransientSessionImpl>()
                        .mapNotNull { session -> session.stack }
                        .forEach { sessionStackTrace ->
                            logger.warn(sessionStackTrace) { "Not closed session stack trace: " }
                        }
            }
        }
    }


    override fun entityTypeExists(entityTypeName: String): Boolean {
        return try {
            _persistentStore.getEntityTypeId(entityTypeName) >= 0
        } catch (e: Exception) {
            false
        }
    }

    override fun renameEntityTypeRefactoring(oldEntityTypeName: String, newEntityTypeName: String) {
        val transientSession = transientSessionOrThrow
        transientSession.addChangeAndRun {
            (transientSession.persistentTransaction.store as PersistentEntityStore).renameEntityType(oldEntityTypeName, newEntityTypeName)
            true
        }
    }

    override fun deleteEntityTypeRefactoring(entityTypeName: String) {
        val transientSession = transientSessionOrThrow
        transientSession.addChangeAndRun {
            transientSession.persistentTransaction.store.deleteEntityType(entityTypeName)
            true
        }
    }

    override fun deleteEntityRefactoring(entity: Entity) {
        val transientSession = transientSessionOrThrow

        if (entity is TransientEntity) {
            transientSession.deleteEntity(entity)
        } else {
            val persistentEntity = entity.unwrapEntity()
            transientSession.addChangeAndRun {
                persistentEntity.delete()
                true
            }
        }
    }

    override fun deleteLinksRefactoring(entity: Entity, linkName: String) {
        val transientSession = transientSessionOrThrow

        val persistentEntity = entity.unwrapEntity()
        transientSession.addChangeAndRun {
            persistentEntity.deleteLinks(linkName)
            true
        }
    }

    override fun deleteLinkRefactoring(entity: Entity, linkName: String, link: Entity) {
        val transientSession = transientSessionOrThrow

        val persistentEntity = entity.unwrapEntity()
        val persistentLink = link.unwrapEntity()

        transientSession.addChangeAndRun {
            persistentEntity.deleteLink(linkName, persistentLink)
            true
        }
    }

    private fun Entity.unwrapEntity(): PersistentEntity {
        return when (this) {
            is TransientEntity -> this.persistentEntity
            is PersistentEntity -> this
            else -> throw IllegalArgumentException("Cannot unwrap entity")
        }
    }

    fun registerStoreSession(storeSession: TransientStoreSession): TransientStoreSession {
        if (!sessions.add(storeSession)) {
            throw IllegalArgumentException("Session is already registered.")
        }
        currentSession.set(storeSession)
        return storeSession
    }

    fun unregisterStoreSession(storeSession: TransientStoreSession) {
        if (!sessions.remove(storeSession)) {
            throw IllegalArgumentException("Transient session wasn't previously registered.")
        }
        currentSession.remove()
    }

    override fun suspendThreadSession(): TransientStoreSession? {
        assertOpen()

        val current = threadSession
        if (current != null) {
            currentSession.remove()
        }

        return current
    }

    override fun addListener(listener: TransientStoreSessionListener) {
        listeners.push(0, listener)
    }

    override fun addListener(listener: TransientStoreSessionListener, priority: Int) {
        listeners.push(priority, listener)
    }

    override fun removeListener(listener: TransientStoreSessionListener) {
        listeners.remove(listener)
    }

    internal fun forAllListeners(action: (TransientStoreSessionListener) -> Unit) {
        listeners.forEach(action)
    }

    fun sessionsCount(): Int {
        return sessions.size
    }

    fun dumpSessions(sb: StringBuilder) {
        sessions.joinTo(sb, "\n")
    }

    override fun getCachedEnumValue(className: String, propName: String): Entity? {
        return enumCache[getEnumKey(className, propName)]
    }

    fun setCachedEnumValue(className: String, propName: String, entity: Entity) {
        enumCache[getEnumKey(className, propName)] = entity
    }

    private fun getEnumKey(className: String, propName: String) = "$propName@$className"

    fun getCachedPersistentClassInstance(entityType: String): BasePersistentClassImpl? {
        return persistentClassInstanceCache[entityType]
    }

    fun getCachedPersistentClassInstance(entityType: Class<out BasePersistentClassImpl>): BasePersistentClassImpl? {
        return persistentClassInstances[entityType]
    }

    fun setCachedPersistentClassInstance(entityType: String, instance: BasePersistentClassImpl) {
        persistentClassInstanceCache[entityType] = instance
        val clazz = instance.javaClass
        if (persistentClassInstances[clazz] != null) {
            logger.warn { "Persistent class instance already registered for: ${clazz.simpleName}" }
        }
        persistentClassInstances[clazz] = instance
    }

    private fun assertOpen() {
        // this flag isn't even volatile, but this is legacy behavior
        if (closed) throw IllegalStateException("Transient store is closed.")
    }

}
