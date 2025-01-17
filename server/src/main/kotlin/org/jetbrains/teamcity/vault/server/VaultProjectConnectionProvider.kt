/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthProvider
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.isDefault

class VaultProjectConnectionProvider(private val descriptor: PluginDescriptor) : OAuthProvider() {
    override fun getType(): String = VaultConstants.FeatureSettings.FEATURE_TYPE

    override fun getDisplayName(): String = "HashiCorp Vault"

    override fun describeConnection(connection: OAuthConnectionDescriptor): String {
        val settings = VaultFeatureSettings(connection.parameters)
        return "Connection to HashiCorp Vault server at ${settings.url}" +
                if (isDefault(settings.namespace)) "" else ", namespace '${settings.namespace}'"
    }

    override fun getDefaultProperties(): Map<String, String> {
        return VaultFeatureSettings.getDefaultParameters()
    }

    override fun getEditParametersUrl(): String? {
        return descriptor.getPluginResourcesPath("editProjectConnectionVault.jsp")
    }

    override fun getPropertiesProcessor(): PropertiesProcessor? {
        return getParametersProcessor()
    }

    companion object {
        fun getParametersProcessor(): PropertiesProcessor {
            return PropertiesProcessor {
                val errors = ArrayList<InvalidProperty>()
                if (it[VaultConstants.FeatureSettings.URL].isNullOrBlank()) {
                    errors.add(InvalidProperty(VaultConstants.FeatureSettings.URL, "Should not be empty"))
                }
                // NAMESPACE can be empty, means default one
                val namespace = it[VaultConstants.FeatureSettings.NAMESPACE] ?: VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE
                val namespaceRegex = "[a-zA-Z0-9_-]+"
                if (namespace != "" && !namespace.matches(namespaceRegex.toRegex())) {
                    errors.add(InvalidProperty(VaultConstants.FeatureSettings.NAMESPACE, "Non-default namespace should match regex '$namespaceRegex'"))
                }

                when (it[VaultConstants.FeatureSettings.AUTH_METHOD]) {
                    VaultConstants.FeatureSettings.AUTH_METHOD_IAM -> {
                        it.remove(VaultConstants.FeatureSettings.ENDPOINT)
                        it.remove(VaultConstants.FeatureSettings.ROLE_ID)
                        it.remove(VaultConstants.FeatureSettings.SECRET_ID)
                        it.remove(VaultConstants.FeatureSettings.USERNAME)
                        it.remove(VaultConstants.FeatureSettings.PASSWORD)
                    }
                    VaultConstants.FeatureSettings.AUTH_METHOD_APPROLE -> {
                        it.remove(VaultConstants.FeatureSettings.USERNAME)
                        it.remove(VaultConstants.FeatureSettings.PASSWORD)

                        if (it[VaultConstants.FeatureSettings.ENDPOINT].isNullOrBlank()) {
                            errors.add(InvalidProperty(VaultConstants.FeatureSettings.ENDPOINT, "Should not be empty"))
                        }
                        if (it[VaultConstants.FeatureSettings.ROLE_ID].isNullOrBlank()) {
                            errors.add(InvalidProperty(VaultConstants.FeatureSettings.ROLE_ID, "Should not be empty"))
                        }
                        if (it[VaultConstants.FeatureSettings.SECRET_ID].isNullOrBlank()) {
                            errors.add(InvalidProperty(VaultConstants.FeatureSettings.SECRET_ID, "Should not be empty"))
                        }
                    }
                    VaultConstants.FeatureSettings.AUTH_METHOD_LDAP -> {
                        it.remove(VaultConstants.FeatureSettings.ENDPOINT)
                        it.remove(VaultConstants.FeatureSettings.ROLE_ID)
                        it.remove(VaultConstants.FeatureSettings.SECRET_ID)

                        if (it[VaultConstants.FeatureSettings.USERNAME].isNullOrBlank()) {
                            errors.add(InvalidProperty(VaultConstants.FeatureSettings.USERNAME, "Should not be empty"))
                        }
                        if (it[VaultConstants.FeatureSettings.PASSWORD].isNullOrBlank()) {
                            errors.add(InvalidProperty(VaultConstants.FeatureSettings.PASSWORD, "Should not be empty"))
                        }
                    }
                }

                // Convert slashes if needed of add new fields
                VaultFeatureSettings(it).toFeatureProperties(it)

                return@PropertiesProcessor errors
            }
        }
    }
}
