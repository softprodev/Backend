package io.raspberrywallet.ktor

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.html.HtmlContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.raspberrywallet.Manager
import io.raspberrywallet.ktor.Paths.availableBalance
import io.raspberrywallet.ktor.Paths.cpuTemp
import io.raspberrywallet.ktor.Paths.currentAddress
import io.raspberrywallet.ktor.Paths.estimatedBalance
import io.raspberrywallet.ktor.Paths.freshAddress
import io.raspberrywallet.ktor.Paths.isLocked
import io.raspberrywallet.ktor.Paths.moduleState
import io.raspberrywallet.ktor.Paths.modules
import io.raspberrywallet.ktor.Paths.nextStep
import io.raspberrywallet.ktor.Paths.ping
import io.raspberrywallet.ktor.Paths.restoreFromBackupPhrase
import io.raspberrywallet.ktor.Paths.sendCoins
import io.raspberrywallet.ktor.Paths.unlockWallet
import io.raspberrywallet.server.Server
import kotlinx.html.*
import org.slf4j.event.Level

private lateinit var manager: Manager
fun startKtorServer(newManager: Manager) {
    manager = newManager
    embeddedServer(Netty, configure = {
        requestQueueLimit = 6
        runningLimit = 4
    }, port = Server.PORT, module = Application::mainModule).start(wait = true)
}

object Paths {
    const val prefix = "/api/"
    const val ping = prefix + "ping"
    const val modules = prefix + "modules"
    const val moduleState = prefix + "moduleState/{id}"
    const val nextStep = prefix + "nextStep/{id}"
    const val restoreFromBackupPhrase = prefix + "restoreFromBackupPhrase"
    const val unlockWallet = prefix + "unlockWallet"
    const val isLocked = prefix + "isLocked"
    const val currentAddress = prefix + "currentAddress"
    const val freshAddress = prefix + "freshAddress"
    const val estimatedBalance = prefix + "estimatedBalance"
    const val availableBalance = prefix + "availableBalance"
    const val sendCoins = prefix + "sendCoins"
    const val cpuTemp = prefix + "cpuTemp"
}

fun Application.mainModule() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(CallLogging) {
        level = Level.INFO
    }
    install(DefaultHeaders)

    routing {
        get(ping) {
            manager.tap()
            call.respond(mapOf("ping" to manager.ping()))
        }
        get(cpuTemp) {
            manager.tap()
            call.respond(mapOf("cpuTemp" to manager.cpuTemperature))
        }
        get(modules) {
            manager.tap()
            call.respond(manager.modules)
        }
        get(moduleState) {
            manager.tap()
            val id = call.parameters["id"]!!
            val moduleState = manager.getModuleState(id)
            call.respond(mapOf("state" to moduleState.name, "message" to moduleState.message))
        }
        post(nextStep) {
            manager.tap()
            val id = call.parameters["id"]!!
            val input = call.receiveText()
            val inputMap: Map<String, String> = jacksonObjectMapper().readValue(input, object : TypeReference<Map<String, String>>() {})
            val response = manager.nextStep(id, inputMap)
            call.respond(mapOf("response" to response.status))
        }
        post(sendCoins) {
            manager.tap()
            val (amount, recipient) = call.receive<SendCoinBody>()
            manager.sendCoins(amount, recipient)
            call.respond(HttpStatusCode.OK)
        }
        get(isLocked) {
            call.respond(mapOf("isLocked" to manager.isLocked))
        }
        get(unlockWallet) {
            manager.tap()
            call.respond(manager.unlockWallet())
        }
        post(restoreFromBackupPhrase) {
            manager.tap()
            val (mnemonicWords, modules, required) = call.receive<RestoreFromBackup>()
            call.respond(manager.restoreFromBackupPhrase(mnemonicWords, modules, required))
        }
        get(currentAddress) {
            manager.tap()
            call.respond(mapOf("currentAddress" to manager.currentReceiveAddress))
        }
        get(freshAddress) {
            manager.tap()
            call.respond(mapOf("freshAddress" to manager.freshReceiveAddress))
        }
        get(estimatedBalance) {
            manager.tap()
            call.respond(mapOf("estimatedBalance" to manager.estimatedBalance))
        }
        get(availableBalance) {
            manager.tap()
            call.respond(mapOf("availableBalance" to manager.availableBalance))
        }
        get("/") {
            manager.tap()
            call.respond(indexPage)
        }
        static("/") {
            resources("assets")
        }
    }
}

data class RestoreFromBackup(val mnemonicWords: List<String>, val modules: Map<String, Map<String, String>>, val required: Int)
data class SendCoinBody(val amount: String, val recipient: String)

val indexPage = HtmlContent {
    head {
        title { +"Raspberry Wallet" }
    }
    body {
        h1 { a(href = "/index.html") { +"Webapp" } }
        h2 { +"Utils" }
        ul {
            li {
                a(href = Paths.ping) { +Paths.ping }
            }
            li {
                a(href = Paths.cpuTemp) { +Paths.cpuTemp }
            }
        }
        h2 { +"Modules" }
        ul {

            li {
                a(href = modules) { +modules }
            }
            li {
                a(href = moduleState) { +moduleState }
            }
            li {
                a(href = nextStep) { +nextStep }
            }
            li {
                a(href = unlockWallet) { +unlockWallet }
            }
        }
        h2 { +"Bitcoin" }
        ul {

            li {
                a(href = currentAddress) { +currentAddress }
            }
            li {
                a(href = freshAddress) { +freshAddress }
            }
            li {
                a(href = estimatedBalance) { +estimatedBalance }
            }
            li {
                a(href = availableBalance) { +availableBalance }
            }
        }
    }
}
