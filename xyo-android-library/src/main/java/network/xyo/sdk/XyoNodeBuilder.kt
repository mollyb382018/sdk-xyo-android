package network.xyo.sdk

import android.content.Context
import network.xyo.base.XYBase
import network.xyo.sdkcorekotlin.hashing.XyoBasicHashBase
import network.xyo.sdkcorekotlin.hashing.XyoHash
import network.xyo.sdkcorekotlin.network.XyoProcedureCatalog
import network.xyo.sdkcorekotlin.network.XyoProcedureCatalogFlags
import network.xyo.sdkcorekotlin.node.XyoRelayNode
import network.xyo.sdkcorekotlin.persist.XyoKeyValueStore
import network.xyo.sdkcorekotlin.persist.repositories.XyoStorageBridgeQueueRepository
import network.xyo.sdkcorekotlin.persist.repositories.XyoStorageOriginBlockRepository
import network.xyo.sdkcorekotlin.persist.repositories.XyoStorageOriginStateRepository
import network.xyo.sdkcorekotlin.repositories.XyoBridgeQueueRepository
import network.xyo.sdkcorekotlin.repositories.XyoOriginBlockRepository
import network.xyo.sdkcorekotlin.repositories.XyoOriginChainStateRepository
import network.xyo.sdkcorekotlin.schemas.XyoSchemas
import java.lang.Exception

@kotlin.ExperimentalUnsignedTypes
class XyoNodeBuilder: XYBase() {
    private var networks = mutableListOf<XyoNetwork>()
    private var storage: XyoKeyValueStore? = null

    private var relayNode: XyoRelayNode? = null
    private var procedureCatalog: XyoProcedureCatalog? = null
    private var blockRepository: XyoOriginBlockRepository? = null
    private var stateRepository: XyoOriginChainStateRepository? = null
    private var bridgeQueueRepository: XyoBridgeQueueRepository? = null
    private var hashingProvider: XyoHash.XyoHashProvider? = null

    fun addNetwork(network: XyoNetwork) {
        networks.add(network)
    }

    fun setStorage(storage: XyoKeyValueStore) {
        this.storage = storage
    }

    fun build(context: Context): XyoNode {
        if (XyoSdk.nodes.isNotEmpty()) {
            throw Exception()
        }

        if (storage == null) {
            log.info("No storage specified, using default")
            setDefaultStorage(context)
        }

        if (hashingProvider == null) {
            log.info("No hashingProvider specified, using default")
            setDefaultHashingProvider()
        }

        if (blockRepository == null) {
            log.info("No blockRepository specified, using default")
            setDefaultBlockRepository()
        }

        if (stateRepository == null) {
            log.info("No stateRepository specified, using default")
            setDefaultStateRepository()
        }

        if (bridgeQueueRepository == null) {
            log.info("No bridgeQueueRepository specified, using default")
            setDefaultBridgeQueueRepository()
        }

        if (procedureCatalog == null) {
            log.info("No procedureCatalog specified, using default")
            setDefaultProcedureCatalog()
        }

        if (relayNode == null) {
            log.info("No relayNode specified, using default")
            setDefaultRelayNode()
        }

        if (networks.isEmpty()) {
            log.info("No networks specified, using default")
            setDefaultNetworks(context)
        }

        val node = XyoNode(storage!!, networks.toTypedArray())
        XyoSdk.nodes.add(node)
        return node
    }

    private fun setDefaultProcedureCatalog() {
        procedureCatalog = object : XyoProcedureCatalog {
            val canDoByte = XyoProcedureCatalogFlags.BOUND_WITNESS or XyoProcedureCatalogFlags.GIVE_ORIGIN_CHAIN or XyoProcedureCatalogFlags.TAKE_ORIGIN_CHAIN

            override fun canDo(byteArray: ByteArray): Boolean {
                if (byteArray.isEmpty()) {
                    return false
                }

                return byteArray.last().toInt() and canDoByte != 0
            }

            override fun choose(byteArray: ByteArray): ByteArray {
                if (byteArray.isEmpty()) {
                    return byteArrayOf(XyoProcedureCatalogFlags.BOUND_WITNESS.toByte())
                }

                val interestedIn = byteArray.last().toInt()

                if (interestedIn and XyoProcedureCatalogFlags.GIVE_ORIGIN_CHAIN != 0) {
                    return byteArrayOf(XyoProcedureCatalogFlags.TAKE_ORIGIN_CHAIN.toByte())
                }

                if (interestedIn and XyoProcedureCatalogFlags.TAKE_ORIGIN_CHAIN != 0) {
                    return byteArrayOf(XyoProcedureCatalogFlags.GIVE_ORIGIN_CHAIN.toByte())
                }

                return byteArrayOf(XyoProcedureCatalogFlags.BOUND_WITNESS.toByte())
            }

            override fun getEncodedCanDo(): ByteArray {

                return byteArrayOf(0x00, 0x00, 0x00, canDoByte.toByte())
            }
        }
    }

    private fun setDefaultBlockRepository() {
        storage?.let { storage ->
            hashingProvider?.let {hashingProvider ->
                blockRepository = XyoStorageOriginBlockRepository(storage, hashingProvider)
                return
            }
            log.error("Missing hashingProvider", true)
            return
        }
        log.error("Missing storage", true)
    }

    private fun setDefaultStateRepository() {
        storage?.let { storage ->
            stateRepository = XyoStorageOriginStateRepository(storage)
            return
        }
        log.error("Missing storage", true)
    }

    private fun setDefaultBridgeQueueRepository() {
        storage?.let { storage ->
            bridgeQueueRepository = XyoStorageBridgeQueueRepository(storage)
            return
        }
        log.error("Missing storage", true)
    }

    private fun setDefaultHashingProvider() {
        hashingProvider = XyoBasicHashBase.createHashType(XyoSchemas.SHA_256, "SHA-256")
    }

    private fun setDefaultRelayNode() {
        blockRepository?.let {blockRepository ->
            stateRepository?.let { stateRepository ->
                bridgeQueueRepository?.let { bridgeQueueRepository ->
                    hashingProvider?.let { hashingProvider ->
                        relayNode = XyoRelayNode(
                            blockRepository,
                            stateRepository,
                            bridgeQueueRepository,
                            hashingProvider
                        )
                        return
                    }
                    log.error("Missing hashingProvider", true)
                    return
                }
                log.error("Missing bridgeQueueRepository", true)
                return
            }
            log.error("Missing stateRepository", true)
            return
        }
        log.error("Missing blockRepository", true)
    }

    private fun setDefaultNetworks(context: Context) {
        relayNode?.let {relayNode ->
            procedureCatalog?.let { procedureCatalog ->
                addNetwork(XyoBleNetwork(context, relayNode, procedureCatalog))
                addNetwork(XyoTcpIpNetwork(relayNode, procedureCatalog))
                return
            }
            log.error("Missing procedureCatalog", true)
            return
        }
        log.error("Missing relayNode", true)
    }

    private fun setDefaultStorage(context: Context) {
        storage = XyoSnappyDbStorageProvider(context)
    }
}