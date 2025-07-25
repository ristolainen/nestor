package nestor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class TileParserTest : FreeSpec({
    "parseTiles should decode both checkerboard and stripe tiles" {
        val tileData = makeCheckerboardTileData() + makeStripeTileData()
        val tiles = TileParser.parseTiles(tileData)
        tiles.size shouldBe 2

        val checker = tiles[0]
        val stripe = tiles[1]

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val expectedChecker = if ((row + col) % 2 == 0) 1 else 0
                val expectedStripe = if ((col % 2) == 0) 3 else 2
                checker[row][col] shouldBe expectedChecker
                stripe[row][col] shouldBe expectedStripe
            }
        }
    }
})
