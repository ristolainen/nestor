package nestor

// ── CPU instruction trace ─────────────────────────────────────────────────────

/**
 * Formats a single-line trace of the instruction at the current PC.
 * Call this BEFORE cpu.step(). Format matches nestest.log for easy diffing.
 *
 * Example:
 *   C000  4C F5 C5  JMP $C5F5                       A:00 X:00 Y:00 P:24 SP:FD PPU:  0, 21 CYC:7
 */
fun CPU.traceLine(): String {
    val opByte = memory.read(pc)
    val opcode = Opcode.fromByte(opByte)
    val mode = opcode?.mode ?: AddrMode.IMP
    val b1 = if (mode.operandBytes >= 1) memory.read((pc + 1) and 0xFFFF) else null
    val b2 = if (mode.operandBytes >= 2) memory.read((pc + 2) and 0xFFFF) else null

    val bytesStr = buildString {
        append(opByte.hex8())
        b1?.let { append(' '); append(it.hex8()) }
        b2?.let { append(' '); append(it.hex8()) }
    }.padEnd(8)

    val mnemonic = opcode?.mnemonic ?: "???"
    val operandStr = when (mode) {
        AddrMode.IMP, AddrMode.ACC -> ""
        AddrMode.IMM -> "#\$${b1!!.hex8()}"
        AddrMode.ZP  -> "\$${b1!!.hex8()}"
        AddrMode.ZPX -> "\$${b1!!.hex8()},X"
        AddrMode.ZPY -> "\$${b1!!.hex8()},Y"
        AddrMode.REL -> "\$${((pc + 2 + b1!!.toByte().toInt()) and 0xFFFF).hex16()}"
        AddrMode.ABS -> "\$${word(b1!!, b2!!).hex16()}"
        AddrMode.ABX -> "\$${word(b1!!, b2!!).hex16()},X"
        AddrMode.ABY -> "\$${word(b1!!, b2!!).hex16()},Y"
        AddrMode.IND -> "(\$${word(b1!!, b2!!).hex16()})"
        AddrMode.INX -> "(\$${b1!!.hex8()},X)"
        AddrMode.INY -> "(\$${b1!!.hex8()}),Y"
    }
    val disasm = if (operandStr.isEmpty()) mnemonic else "$mnemonic $operandStr"

    val ppu = memory.ppu
    return buildString {
        append(pc.hex16())
        append("  "); append(bytesStr)
        append("  "); append(disasm.padEnd(31))
        append(" A:"); append(a.hex8())
        append(" X:"); append(x.hex8())
        append(" Y:"); append(y.hex8())
        append(" P:"); append(status.hex8())
        append(" SP:"); append(sp.hex8())
        append(" PPU:"); append(ppu.scanline.toString().padStart(3))
        append(","); append(ppu.cycle.toString().padStart(3))
        append(" CYC:"); append(cycles)
    }
}

// ── PPU event trace ───────────────────────────────────────────────────────────

/**
 * Formats a PPU event line for a register write or notable state change.
 * Call from cpuWrite/cpuRead with a descriptive label, e.g. "WRITE $2006=3F".
 *
 * Example:
 *   [PPU CYC=1240] WRITE $2006=3F | SL=  0 DOT=  0 v=0000 t=3F00 w=0 ctrl=80 mask=00
 */
fun PPU.traceEvent(label: String, cpuCycles: Long): String = buildString {
    append("[PPU CYC="); append(cpuCycles); append("] ")
    append(label.padEnd(16))
    append(" | SL="); append(scanline.toString().padStart(3))
    append(" DOT="); append(cycle.toString().padStart(3))
    append(" v="); append(vramAddr.hex16())
    append(" t="); append(tempAddr.hex16())
    append(" w="); append(if (writeToggle) 1 else 0)
    append(" ctrl="); append(control.hex8())
    append(" mask="); append(mask.hex8())
    append(" status="); append(status.hex8())
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Int.hex8()  = (this and 0xFF).toString(16).uppercase().padStart(2, '0')
private fun Int.hex16() = (this and 0xFFFF).toString(16).uppercase().padStart(4, '0')
