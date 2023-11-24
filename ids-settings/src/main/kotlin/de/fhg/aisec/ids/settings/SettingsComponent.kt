/*-
 * ========================LICENSE_START=================================
 * ids-settings
 * %%
 * Copyright (C) 2021 Fraunhofer AISEC
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package de.fhg.aisec.ids.settings

import de.fhg.aisec.ids.api.Constants
import de.fhg.aisec.ids.api.infomodel.ConnectorProfile
import de.fhg.aisec.ids.api.settings.ConnectionSettings
import de.fhg.aisec.ids.api.settings.ConnectorConfig
import de.fhg.aisec.ids.api.settings.Settings
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.FileSystems
import java.util.Collections
import java.util.concurrent.ConcurrentMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Component
class SettingsComponent : Settings {
    @PostConstruct
    private fun activate() {
        LOG.debug("Open Settings Database {}...", DB_PATH.toFile().absolutePath)

        DB_PATH.toFile().parentFile.mkdirs()

        // Use default reliable (non-mmap) mode and WAL for transaction safety
        mapDB = DBMaker.fileDB(DB_PATH.toFile()).transactionEnable().make()
        var dbVersion = settingsStore.getOrPut(DB_VERSION_KEY) { 1 } as Int
        // Check for unknown DB version
        if (dbVersion > CURRENT_DB_VERSION) {
            LOG.error(
                "Settings database is newer than supported version, data loss er errors are possible!"
            )
        }
        // Migrate old DB versions
        while (dbVersion < CURRENT_DB_VERSION) {
            LOG.info(
                "Migrating settings database from version $dbVersion to version $CURRENT_DB_VERSION..."
            )
            when (dbVersion) {
                1 -> {
                    // Checking ConnectorProfile for errors
                    try {
                        settingsStore[CONNECTOR_PROFILE_KEY]
                    } catch (x: Exception) {
                        // Serialization issue due to infomodel changes, need to rebuild settings
                        // store
                        val tempMap = HashMap<String, Any?>()
                        settingsStore.keys.forEach {
                            if (it != CONNECTOR_PROFILE_KEY) {
                                tempMap[it] = settingsStore[it]
                            }
                        }
                        settingsStore.clear()
                        settingsStore += tempMap
                    }
                    dbVersion = 2
                }

                2 -> {
                    settingsStore -= DAT_KEY
                    dbVersion = 3
                }

                3 -> {
                    if (connectorConfig.dapsUrl == "https://daps.aisec.fraunhofer.de") {
                        connectorConfig.let {
                            connectorConfig =
                                ConnectorConfig(
                                    it.appstoreUrl, it.brokerUrl, it.ttpHost, it.ttpPort, it.acmeServerWebcon,
                                    it.acmeDnsWebcon, it.acmePortWebcon, it.tosAcceptWebcon,
                                    "https://daps.aisec.fraunhofer.de/v2",
                                    it.keystoreName, it.keystorePassword, it.keystoreAliasName, it.truststoreName
                                )
                        }
                    }
                    dbVersion = 4
                }
            }
            settingsStore[DB_VERSION_KEY] = dbVersion
            mapDB.commit()
            LOG.info("Migration successful")
        }
    }

    @PreDestroy
    fun deactivate() {
        LOG.debug("Close Settings Database...")
        mapDB.close()
    }

    internal class NonNullSetting<T>(
        private val key: String,
        private val defaultProducer: () -> T
    ) : ReadWriteProperty<Settings, T> {
        override operator fun getValue(
            thisRef: Settings,
            property: KProperty<*>
        ): T {
            @Suppress("UNCHECKED_CAST")
            return settingsStore.getOrElse(key, defaultProducer) as T
        }

        override operator fun setValue(
            thisRef: Settings,
            property: KProperty<*>,
            value: T
        ) {
            settingsStore[key] = value
            mapDB.commit()
        }
    }

    internal class NullableSetting<T>(private val key: String) : ReadWriteProperty<Settings, T?> {
        override operator fun getValue(
            thisRef: Settings,
            property: KProperty<*>
        ): T? {
            @Suppress("UNCHECKED_CAST")
            return settingsStore[key] as T?
        }

        override operator fun setValue(
            thisRef: Settings,
            property: KProperty<*>,
            value: T?
        ) {
            if (value == null) {
                settingsStore -= key
            } else {
                settingsStore[key] = value
            }
            mapDB.commit()
        }
    }

    override var connectorConfig by NonNullSetting(CONNECTOR_SETTINGS_KEY, ::ConnectorConfig)

    override var connectorProfile by NonNullSetting(CONNECTOR_PROFILE_KEY, ::ConnectorProfile)

    override var connectorJsonLd by NullableSetting<String>(CONNECTOR_JSON_LD_KEY)

    override fun getConnectionSettings(connection: String): ConnectionSettings =
        if (connection == Constants.GENERAL_CONFIG) {
            connectionSettings.getOrElse(connection) { ConnectionSettings() }
        } else {
            connectionSettings.getOrPut(connection) {
                getConnectionSettings(Constants.GENERAL_CONFIG)
            }
        }

    override fun setConnectionSettings(
        connection: String,
        cSettings: ConnectionSettings
    ) {
        connectionSettings[connection] = cSettings
        mapDB.commit()
    }

    override val allConnectionSettings: MutableMap<String, ConnectionSettings>
        get() = Collections.unmodifiableMap(connectionSettings)

    override fun isUserStoreEmpty() = userStore.isEmpty()

    override fun getUserHash(username: String) = userStore[username]

    override fun saveUser(
        username: String,
        hash: String
    ) {
        userStore += username to hash
        mapDB.commit()
    }

    override fun getUsers(): Map<String, String> {
        return userStore.toMap()
    }

    override fun removeUser(username: String) {
        userStore.remove(username)
        mapDB.commit()
    }

    override fun storeContract(
        key: String,
        contract: String
    ) {
        contractStore[key] = contract
        mapDB.commit()
    }

    override fun loadContract(key: String) = contractStore[key]

    companion object {
        internal const val DB_VERSION_KEY = "db_version"
        internal const val CURRENT_DB_VERSION = 4
        internal const val CONNECTOR_SETTINGS_KEY = "main_config"
        internal const val CONNECTOR_PROFILE_KEY = "connector_profile"
        internal const val CONNECTOR_JSON_LD_KEY = "connector_json_ld"
        internal const val DAT_KEY = "dynamic_attribute_token"
        internal val DB_PATH = FileSystems.getDefault().getPath("etc", "settings.mapdb")
        private val LOG = LoggerFactory.getLogger(SettingsComponent::class.java)
        private lateinit var mapDB: DB
        private val settingsStore: ConcurrentMap<String, Any> by lazy {
            mapDB
                .hashMap("settings_store")
                .keySerializer(Serializer.STRING)
                .valueSerializer(ElsaSerializer())
                .createOrOpen()
        }
        private val connectionSettings: ConcurrentMap<String, ConnectionSettings> by lazy {
            mapDB
                .hashMap("connection_settings")
                .keySerializer(Serializer.STRING)
                .valueSerializer(ElsaSerializer<ConnectionSettings>())
                .createOrOpen()
        }
        private val userStore: ConcurrentMap<String, String> by lazy {
            mapDB
                .hashMap("user_store")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .createOrOpen()
        }
        private val contractStore: ConcurrentMap<String, String> by lazy {
            mapDB
                .hashMap("contract_store")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .createOrOpen()
        }
    }
}
