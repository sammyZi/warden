package dev.warden.rules

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Why something the user gave (a WireGuard config, a list address) can't be used. */
enum class Problem { NotWireGuard, NoPeer, NoAddress, BadKey, NoEndpoint, NoPort, Ipv6NeedsBrackets, NotHttps, AlreadyAdded }

/**
 * Thrown for input the user can fix. Screens turn [problem] into a translated message; [detail] is a
 * config keyword (like "PrivateKey") the message may name. Still an IllegalArgumentException.
 */
class InvalidInput(val problem: Problem, val detail: String = "") : IllegalArgumentException(problem.name + " " + detail)

/** Like require(), but with a reason screens can translate. The contract keeps require()'s smart casts. */
@OptIn(ExperimentalContracts::class)
internal fun ensure(ok: Boolean, problem: Problem, detail: String = "") {
    contract { returns() implies ok }
    if (!ok) throw InvalidInput(problem, detail)
}
