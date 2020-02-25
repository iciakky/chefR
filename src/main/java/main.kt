import com.google.gson.Gson
import java.net.URL
import java.util.*
import kotlin.Comparator
import kotlin.collections.HashMap
import kotlin.math.log2

val raw =
    URL("https://gist.githubusercontent.com/iciakky/cd98792f54e52b65b9cb2e30c1de9cbd/raw/e4e68a86391cd521a747c29bd8b48e7cd4f4ac88/chefIndi.json").readText()

fun main() {
    // requirement config
    val mustHave = setOf("Egg", "Beet")
    // todo allow specify conditions to inference potential calculation

    // 讀取並解析食材資料
    val data: Raw = Gson().fromJson(raw, Raw::class.java)

    // 根據指定條件篩選掉絕對不會使用的食材
    val fixedIngredients = data.indi.filter { mustHave.contains(it.Name) }

    // error handling: missing ingredient
    val missing = mustHave.filterNot { fixed -> fixedIngredients.any { it.Name == fixed } }
    if (missing.any()) {
        println("some of specified ingredient is missing: ${missing.joinToString(",")}")
        return
    }
    val maxCookingTime = fixedIngredients.maxBy { it.CookingTime }?.CookingTime ?: Int.MAX_VALUE
    val candidates =
        data.indi.filterNot { it.CookingTime > maxCookingTime || it.AromaNeutral || !data.match.contains(it.Name) }

    // 為了用 bits 表示食譜，將食材對應至 bit
    val requiredBits = kotlin.math.ceil(log2(candidates.count().toDouble())).toInt()
    val ingredBitsMap =
        candidates.mapIndexed { i, ingredient -> ingredient.Name to BitSet(requiredBits).apply { set(i) } }.toMap()

    // 能用 bits 表示食材和食譜後，就能指定從一個食譜出發

    val open = HashSet<BitSet>()
    val closed = HashMap<BitSet, Recipe>()
    val suggestion = PriorityQueue<Recipe>(Comparator.comparing<Recipe, Float> { it.potential }.reversed())

    // 從指定的 Recipe 作為起點開始搜尋最佳解
    val beginRecipe = Recipe(
        fixedIngredients,
        2, // todo calculate flavor from fixedIngredients
        fixedIngredients.sumByDouble { it.MinCost.toDouble() }.toFloat(),
        candidates, ingredBitsMap, data.match
    )

    // 以風味滿50分且成本最低為目標
    val over50 = PriorityQueue<Recipe>(Comparator.comparing<Recipe, Float> { it.cost })

    open += beginRecipe.bits
    suggestion += beginRecipe

    println("start!")
    while (open.any()) {
        val curr = suggestion.poll()!!
        open.remove(curr.bits)
        closed[curr.bits] = curr

        // 搜尋相鄰的 Recipes 意味著：新增一種食材進 Recipes
        for ((index, candidate) in candidates.withIndex()) {
            val adjacentBits = BitSet.valueOf(curr.bits.toLongArray()).apply { flip(index) }
            if (closed.contains(adjacentBits) || open.contains(adjacentBits) || mustHave.contains(candidate.Name)) {
                continue
            }

            val recipe = curr.evolveNew(index)
            open += adjacentBits
            suggestion += recipe

            if (recipe.flavor >= 50) {
                over50 += recipe
                if (over50.peek() == recipe) {
                    val bestNow = over50.peek()
                    println("best recipe now : " + bestNow.ingredients.joinToString(",") { it.Name })
                    println("(flavor/cost)   : ${bestNow.flavor}/${bestNow.cost}")
                }
            }
        }
    }
}

fun List<Ingredient>.toBits(mapping: Map<String, BitSet>): BitSet {
    return this.fold(BitSet()) { ret, ing ->
        ret.apply {
            or(mapping[ing.Name] ?: error("missing bits mapping of ingredient ${ing.Name}"))
        }
    }
}

data class Recipe(
    val ingredients: List<Ingredient> = emptyList(),
    val flavor: Int,
    val cost: Float,
    val candidates: List<Ingredient>,
    val ingredBitsMap: Map<String, BitSet>,
    val match: Map<String, Match>
) {
    val bits: BitSet by lazy {
        ingredients.toBits(ingredBitsMap)
    }

    val potential: Float by lazy {
        // heuristic
        flavor.coerceAtMost(50) * 10000 + flavor / cost
    }

    fun evolveNew(changedBitIndex: Int): Recipe {
        val changedIngredient = candidates[changedBitIndex]
        if (this.bits.get(changedBitIndex)) {
            return Recipe(
                ingredients = this.ingredients - changedIngredient,
                flavor = this.ingredients.fold(this.flavor) { newFlavor, ingredient ->
                    if (ingredient == changedIngredient)
                        newFlavor
                    else newFlavor - (this.match[ingredient.Name]!!.byName[changedIngredient.Name] ?: -1) * 2
                },
                cost = this.cost - changedIngredient.MinCost,
                candidates = this.candidates,
                ingredBitsMap = this.ingredBitsMap,
                match = this.match
            )
        } else {
            return Recipe(
                ingredients = this.ingredients + changedIngredient,
                flavor = this.ingredients.fold(this.flavor) { newFlavor, ingredient ->
                    newFlavor + (this.match[ingredient.Name]!!.byName[changedIngredient.Name] ?: -1) * 2
                },
                cost = this.cost + changedIngredient.MinCost,
                candidates = this.candidates,
                ingredBitsMap = this.ingredBitsMap,
                match = this.match
            )
        }
    }
}

data class Ingredient(
    val Name: String,
    val CookingTime: Int,
    val Calories: Int,
    val MinPortion: Int,
    val MaxPortion: Int,
    val UnitCost: Float,
    val MinCost: Float,
    val AromaNeutral: Boolean,
    val Tags: List<String>,

    val isVegetables: Boolean,
    val isSpice: Boolean,
    val isNut: Boolean,
    val isSeafood: Boolean,
    val isFruit: Boolean,
    val isMeat: Boolean,
    val isLegume: Boolean,
    val isDairy: Boolean,
    val isOther: Boolean,
    val isCarbs: Boolean,
    val isAlcohol: Boolean,
    val isBeer: Boolean,
    val isFat: Boolean,
    val isWine: Boolean
)

data class MatchVal(
    val m1: List<String>,
    val m2: List<String>,
    val m3: List<String>
)

data class Match(
    val byVal: MatchVal,
    val byName: Map<String, Int>
)

data class Raw(
    val match: Map<String, Match>,
    val indi: List<Ingredient>
)
