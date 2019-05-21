@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.jetbrains.rd.framework.test.cases.demo

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.RdReactiveBase
import com.jetbrains.rd.framework.test.util.NetUtils
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rd.util.spinUntil
import com.jetbrains.rd.util.string.PrettyPrinter
import com.jetbrains.rd.util.string.println
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import demo.*
import java.io.File

lateinit var scheduler: IScheduler

fun server(lifetime: Lifetime, port: Int? = null): Protocol {
    scheduler = SingleThreadScheduler(lifetime, "SingleThreadScheduler")
    return Protocol(Serializers(), Identities(IdKind.Server), scheduler,
            SocketWire.Server(lifetime, scheduler, port, "DemoServer"), lifetime)
}

var finished = false

fun main() {
    val lifetimeDef = Lifetime.Eternal.createNested()
    val socketLifetimeDef = Lifetime.Eternal.createNested()

    val lifetime = lifetimeDef.lifetime
    val socketLifetime = socketLifetimeDef.lifetime

    val protocol = server(socketLifetime, NetUtils.findFreePort(0))
    val tmpDir = File(System.getProperty("java.io.tmpdir"))
    val file = File(tmpDir, "/rd/port.txt")
    file.parentFile.mkdirs()
    file.printWriter().use { out ->
        out.println((protocol.wire as SocketWire.Server).port)
    }

    val printer = PrettyPrinter()

    scheduler.queue {
        val model = DemoModel.create(lifetime, protocol)
        val extModel = model.extModel

        adviseAll(lifetime, model, extModel, printer)
        fireAll(model, extModel)
    }

    spinUntil(10_000) { finished }

    socketLifetimeDef.terminate()
    lifetimeDef.terminate()

    println(printer)
}

private fun <T> PrettyPrinter.printIfRemoteChange(entity: ISource<T>, entityName: String, vararg values: Any) {
    if (!entity.isLocalChange) {
        println("***")
        println("$entityName:")
        values.forEach { value -> value.println(this) }
    }
}

private val <T> ISource<T>.isLocalChange
    get() = (this as? RdReactiveBase)?.isLocalChange == true

private fun adviseAll(lifetime: Lifetime, model: DemoModel, extModel: ExtModel, printer: PrettyPrinter) {
    model.boolean_property.advise(lifetime) {
        printer.printIfRemoteChange(model.boolean_property, "boolean_property", it)
    }

    model.scalar.advise(lifetime) {
        printer.printIfRemoteChange(model.scalar, "scalar", it)
    }

    model.list.advise(lifetime) {
        printer.printIfRemoteChange(model.list, "list", it)
    }

    model.set.advise(lifetime) { e, x ->
        printer.printIfRemoteChange(model.set, "set", e, x)
    }

    model.mapLongToString.advise(lifetime) {
        printer.printIfRemoteChange(model.mapLongToString, "mapLongToString", it)
    }

/*
    model.call.set { c ->
        printer.print("RdTask:")
        c.print(printer)

        c.toUpperCase().toString()
    }
*/

    model.interned_string.advise(lifetime) {
        printer.printIfRemoteChange(model.interned_string, "interned_string", it)
    }

    model.polymorphic.advise(lifetime) {
        val entity = model.polymorphic;
        printer.printIfRemoteChange(entity, "polymorphic", it)
        if (!entity.isLocalChange) {
            finished = true
        }
    }

    extModel.checker.advise(lifetime) {
        printer.printIfRemoteChange(extModel.checker, "extModel.checker", it)
    }
}

fun fireAll(model: DemoModel, extModel: ExtModel) {
    model.boolean_property.set(false)

    val scalar = MyScalar(false,
            13,
            32000,
            1_000_000_000,
            -2_000_000_000_000_000_000,
            3.14f,
            -123456789.012345678,
            UShort.MAX_VALUE.minus(1u).toUShort(),
            UInt.MAX_VALUE.minus(1u),
            ULong.MAX_VALUE.minus(1u)
    )
    model.scalar.set(scalar)

    model.list.add(1)
    model.list.add(3)

    model.set.add(13)

    model.mapLongToString[13] = "Kotlin"

    val valA = "Kotlin"
    val valB = "protocol"

//    val sync = model.callback.sync("Unknown")

    model.interned_string.set(valA)
    model.interned_string.set(valA)
    model.interned_string.set(valB)
    model.interned_string.set(valB)
    model.interned_string.set(valA)

    val derived = Derived("Kotlin instance")
    model.polymorphic.set(derived)

    extModel.checker.fire()
}