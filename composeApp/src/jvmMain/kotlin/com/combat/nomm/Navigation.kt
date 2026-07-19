package com.combat.nomm

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
sealed interface MainNavigation : NavKey {
    companion object {
        val config = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Search::class)
                    subclass(Libraries::class)
                    subclass(Servers::class)
                    subclass(Settings::class)
                    subclass(Mod::class)
                    subclass(Server::class)
                }
            }
        }
    }

    @Serializable
    data object Search : MainNavigation

    @Serializable
    data object Libraries : MainNavigation

    @Serializable
    data object Servers : MainNavigation

    @Serializable
    data object Settings : MainNavigation

    @Serializable
    data class Mod(val modName: String) : MainNavigation

    @Serializable
    data class Server(val ip: String, val port: Long) : MainNavigation
}

@Serializable
sealed interface ModNavigation : NavKey {
    companion object {
        val config = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Details::class)
                    subclass(Versions::class)
                    subclass(Dependencies::class)
                }
            }
        }
    }

    @Serializable
    data object Details : ModNavigation

    @Serializable
    data object Versions : ModNavigation

    @Serializable
    data class Dependencies(val version: Version) : ModNavigation
}


@Serializable
sealed interface ServerNavigation : NavKey {
    companion object {
        val config = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Details::class)
                    subclass(Modpack::class)
                }
            }
        }
    }

    @Serializable
    data object Details : ServerNavigation

    @Serializable
    data object Modpack : ServerNavigation
}