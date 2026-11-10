// Derived from Gadgetbridge (AGPL-3.0-or-later; see NOTICE): service/devices/huawei/p2p/HuaweiP2PCannedRepliesService (module
// hw.unitedevice.smsquick, commands 7001 query / 7002 update / 7003 connect) and HuaweiDeviceStateManager.supportsCannedReplies.
package com.joyodigitama.huaweiwear

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiP2PManager
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p.HuaweiBaseP2PService

/** The wire format of the watch's quick replies and the limits the phone applies before sending. */
internal object QuickRepliesPacket {
  const val MODULE = "hw.unitedevice.smsquick"
  const val PACKAGE = "com.huawei.watch.home"
  const val FINGERPRINT = "603AC6A57E2023E00C9C93BB539CA653DF3003EBA4E92EA1904BA4AAA5D938F0"

  const val CMD_QUERY = 7001
  const val CMD_UPDATE = 7002

  /** ExpandCapability bit GB reads as `supportsCannedReplies`. */
  const val CAPABILITY_BIT = 82

  /** GB's `getCannedRepliesSlotCount`: 10 (its source says "TODO: find proper count"). */
  const val SLOTS = 10

  /** Not read from the watch (no packet carries it): a phone-side limit well under what Huawei Health's editor takes. */
  const val MAX_LENGTH = 30

  /** Why [replies] cannot be sent, or null when they can: the watch refuses an empty list, and GB sends nothing else. */
  fun problem(replies: List<String>): String? {
    if (replies.isEmpty()) return "At least one quick reply is required"
    if (replies.size > SLOTS) return "At most $SLOTS quick replies fit on the watch"
    for (reply in replies) {
      if (reply.isBlank()) return "A quick reply cannot be empty"
      if ('\n' in reply || '\r' in reply) return "A quick reply is a single line"
      if (reply.codePointCount(0, reply.length) > MAX_LENGTH) return "A quick reply is at most $MAX_LENGTH characters"
    }
    return null
  }

  /** `01 = 7001`: asks the watch for the replies it holds. */
  fun query(): ByteArray = HuaweiTLV().put(0x01, CMD_QUERY).serialize()

  /** `01 = 7002, 82 { 83 { 04 = text } ... }`: replaces the list, one `83` object per reply, in order. */
  fun update(replies: List<String>): ByteArray {
    val list = HuaweiTLV()
    for (reply in replies) list.put(0x83, HuaweiTLV().put(0x04, reply))
    return HuaweiTLV().put(0x01, CMD_UPDATE).put(0x82, list).serialize()
  }

  /** The replies in a query answer (`01 = 7001, 82 { 83 { 04 } ... }`); empty slots are skipped. */
  fun parseQuery(data: ByteArray): List<String>? {
    val tlv = HuaweiTLV()
    tlv.parse(data)
    if (!tlv.contains(0x01) || tlv.getInteger(0x01) != CMD_QUERY || !tlv.contains(0x82)) return null
    return tlv.getObject(0x82).getObjects(0x83).mapNotNull { slot ->
      if (!slot.contains(0x04)) null else slot.getString(0x04).takeIf { it.isNotEmpty() }
    }
  }

  /** Identity of a list, to tell whether the watch already got it. */
  fun fingerprint(replies: List<String>): String = replies.joinToString("\u0000").hashCode().toString(16) + ":" + replies.size
}

/**
 * P2P module `hw.unitedevice.smsquick`. On registration the phone sends the stored list when it differs from what this
 * watch last got ([stored]); the watch's answer to a query is only logged. GB's `sendConnect` is not used: its source notes
 * it can wipe the saved replies.
 */
internal class QuickRepliesService(
  manager: HuaweiP2PManager,
  private val events: WearEvents,
  private val stored: () -> List<String>?,
  private val alreadySent: (List<String>) -> Boolean,
  private val markSent: (List<String>) -> Unit,
) : HuaweiBaseP2PService(manager) {
  override fun getModule() = QuickRepliesPacket.MODULE

  override fun getPackage() = QuickRepliesPacket.PACKAGE

  override fun getFingerprint() = QuickRepliesPacket.FINGERPRINT

  override fun registered() {
    sendCommand(QuickRepliesPacket.query(), null)
    val replies = stored() ?: return
    if (alreadySent(replies)) log("Quick replies: the watch already has this list")
    else send(replies)
  }

  override fun unregister() = clearPending()

  override fun handleData(data: ByteArray) {
    val replies = try {
      QuickRepliesPacket.parseQuery(data)
    } catch (e: HuaweiPacket.MissingTagException) {
      null
    }
    log("Quick replies: the watch sent ${data.size} bytes" + (replies?.let { ", ${it.size} reply(ies) on the watch" } ?: ""))
  }

  /** Sends [replies] when they are valid; false when they are not. */
  fun send(replies: List<String>): Boolean {
    val problem = QuickRepliesPacket.problem(replies)
    if (problem != null) {
      events.log(LogDirection.ERROR, 0x34, 0x01, null, "Quick replies: not sent, $problem")
      return false
    }
    sendCommand(QuickRepliesPacket.update(replies)) { code, _ -> log("Quick replies: the watch answered the update with 0x%02x".format(code and 0xFF)) }
    markSent(replies)
    log("Quick replies: sent ${replies.size} reply(ies)")
    return true
  }

  private fun log(message: String) = events.log(LogDirection.INFO, 0x34, 0x01, null, message)
}
