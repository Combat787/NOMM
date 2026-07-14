package com.combat.nomm.steamworker

import com.combat.nomm.*

fun main() {
    val ipc = SteamWorkerIPC(System.`in`, System.out)
    val service = SteamWorkerService(ipc)

    while (true) {
        val command = try {
            ipc.readCommand()
        } catch (e: Exception) {
            System.err.println("[SteamWorker] Error reading command: ${e.message}")
            null
        }

        if (command == null) {
            System.err.println("[SteamWorker] EOF reached, exiting")
            break
        }

        try {
            when (command) {
                is WorkerCommand.Init -> service.init()
                is WorkerCommand.RequestServerList -> service.requestServerList()
                is WorkerCommand.CancelQuery -> service.cancelQuery()
                is WorkerCommand.PingServer -> service.pingServer(
                    command.ip, command.queryPort, command.requestId
                )
                is WorkerCommand.Shutdown -> {
                    service.shutdown()
                    break
                }
            }
        } catch (e: Exception) {
            System.err.println("[SteamWorker] Error processing command: ${e.message}")
            runCatching { ipc.sendEvent(WorkerEvent.Error(e.message ?: "Unknown error")) }
        }
    }

    kotlin.system.exitProcess(0)
}
