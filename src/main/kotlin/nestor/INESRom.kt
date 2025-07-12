@file:Suppress("ArrayInDataClass")

package nestor

data class INESRom(
    val header: Header,
    val prgData: ByteArray,
    val chrData: ByteArray,
) {
    data class Header(
        val mapperNumber: Int,
        val prgSize: Int,
        val chrSize: Int,
    )
}
