import com.google.gson.Gson
import java.net.URL
import java.util.*
import kotlin.math.abs

val raw =
    URL("https://gist.githubusercontent.com/iciakky/cd98792f54e52b65b9cb2e30c1de9cbd/raw/e4e68a86391cd521a747c29bd8b48e7cd4f4ac88/chefIndi.json").readText()

fun main() {

    // # 準備階段
    // 讀取設定參數 // todo replace hardcoded config
    val forbidTags = setOf("Meat", "Seafood")
    val mustHave = mapOf(setOf("Egg") to 20, setOf("Vegetables") to 100)
    val perks = mapOf("" to 10, "Fruit" to -3, "Nut" to 80) // n means # ingredients is n, -m means portion is m
    // 讀取並解析食材資料
    val data: Raw = Gson().fromJson(raw, Raw::class.java)
    // 根據指定條件篩選掉絕對不會使用的食材
    val candidates = data.indi.filterNot {
        it.AromaNeutral || it.Tags.any { t -> forbidTags.contains(t) } || !data.match.contains(it.Name)
    }
    // 為了用 bits 表示食譜，將食材對應至 bit
    val requiredBits = candidates.count()
    val ingredBitsMap =
        candidates.mapIndexed { i, ingredient -> ingredient.Name to BitSet(requiredBits).apply { set(i) } }.toMap()

    val known = HashSet<BitSet>()
    // todo runtime change priority, migrate to newly configured priority queue, continue search
    val priority = compareByDescending<Recipe> { it.mustHaveCompleteRate }
        .thenBy { it.cookingTime }
        .thenByDescending { it.perkCompleteRate }
        .thenByDescending { it.flavor.coerceAtMost(50) }
        .thenBy { it.realCost }
    val suggestion = PriorityQueue<Recipe>(priority)
    Recipe.candidates = candidates
    Recipe.ingredBitsMap = ingredBitsMap
    Recipe.match = data.match
    Recipe.mustHave = mustHave
    Recipe.perks = perks

    // # 初始化搜尋起始狀態
    val beginRecipe = Recipe(emptyList(), 0, 0F)
    suggestion += beginRecipe
    known += beginRecipe.bits

    // 目標：風味滿50分的前提下，煮最快且成本最低
    val solutions = PriorityQueue<Recipe>(priority)

    // 開始搜尋
    println("start!")
    while (suggestion.any()) {
        val curr = suggestion.poll()!!
        solutions += curr

        // 刷新目前最佳
        if (solutions.peek() == curr) {
            println(
                "${solutions.count()} done, ${suggestion.count()} todo, best: " +
                        "${curr.mustHaveCompleteRate}/${curr.perkCompleteRate}/${curr.realCost}/${curr.ingredients.joinToString { it.Name }}"
            )
        }

        // 搜尋相鄰的 Recipes 意味著：新增或移除一種食材進 Recipes
        for ((index, _) in candidates.withIndex()) {
            val adjacentBits = BitSet.valueOf(curr.bits.toLongArray()).apply { flip(index) }
            // 不重複搜尋
            if (known.contains(adjacentBits)) {
                continue
            }
            known += adjacentBits

            val recipe = curr.evolveNew(index)
            suggestion += recipe
        }
    }
    println("end!") // probably never run to here
}

// todo consider mustHave and perk for cost and flavor
data class Recipe(
    val ingredients: List<Ingredient> = emptyList(),
    val flavor: Int,
    val cost: Float
) {
    companion object {
        lateinit var candidates: List<Ingredient>
        lateinit var ingredBitsMap: Map<String, BitSet>
        lateinit var match: Map<String, Match>
        lateinit var mustHave: Map<Set<String>, Int>
        lateinit var perks: Map<String, Int>
    }

    // todo calc a new cost based on mustHave and perks

    // 0 ~ 720
    val cookingTime by lazy { ingredients.maxBy { it.CookingTime }?.CookingTime ?: 0 }

    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val mustHaveCompleteRate: Int by lazy {
        if (mustHave.isEmpty()) 0
        else mustHave.count { (condition, _) ->
            ingredients.any {
                condition.contains(it.Name) || it.Tags.any { tag -> condition.contains(tag) }
            }
        }
    }
    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val perkCompleteRate: Int by lazy {
        if (perks.isEmpty()) 0
        else perks.count { (condition, quantity) ->
            // 總食材個數
            if (condition.isBlank()) {
                if (quantity < 0) {
                    // 以下
                    ingredients.count() <= abs(quantity)
                } else {
                    // 以上
                    ingredients.count() >= abs(quantity)
                }
            } else {
                // 特定食材個數或量
                if (quantity < 0) {
                    // 個數
                    ingredients.count {
                        it.Name == condition || it.Tags.contains(condition)
                    } >= abs(quantity)
                } else {
                    // 量
                    ingredients.any {
                        it.MaxPortion >= abs(quantity) && (it.Name == condition || it.Tags.contains(condition))
                    }
                }
            }
        }
    }
    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val realCost: Float by lazy {
        // 這裡假設在要求分量時上 mustHave 跟 perks 不會重複
        // 對每個分量要求，找到 Unit Cost 最低的選項，將原本的 cost 加上額外增加的成本 (unit*portion - minCost)
        mustHave.entries.fold(0F) { ret, (conditions, portion) ->
            ret + (ingredients.filter { conditions.contains(it.Name) || it.Tags.any { tag -> conditions.contains(tag) } }
                .minBy { it.UnitCost }?.let { it.UnitCost * portion - it.MinCost } ?: 0F)
        } + perks.filter { it.key.isNotBlank() && it.value >= 0 }.entries.fold(0F) { ret, (condition, portion) ->
            ret + (ingredients.filter { it.Name == condition || it.Tags.contains(condition) }
                .minBy { it.UnitCost }?.let { it.UnitCost * portion - it.MinCost } ?: 0F)
        } + cost
    }
    val bits: BitSet by lazy {
        ingredients.fold(BitSet()) { ret, ing ->
            ret.apply {
                or(ingredBitsMap[ing.Name] ?: error("missing bits mapping of ingredient ${ing.Name}"))
            }
        }
    }

    fun contains(ingredientIndex: Int) = this.bits.get(ingredientIndex)

    fun evolveNew(changedBitIndex: Int): Recipe {
        val changedIngredient = candidates[changedBitIndex]
        if (this.contains(changedBitIndex)) {
            return Recipe(
                ingredients = this.ingredients - changedIngredient,
                flavor = this.ingredients.fold(this.flavor) { newFlavor, ingredient ->
                    if (ingredient == changedIngredient)
                        newFlavor
                    else newFlavor - (match[ingredient.Name]!!.byName[changedIngredient.Name] ?: -1) * 2
                },
                cost = this.cost - changedIngredient.MinCost
            )
        } else {
            return Recipe(
                ingredients = this.ingredients + changedIngredient,
                flavor = this.ingredients.fold(this.flavor) { newFlavor, ingredient ->
                    newFlavor + (match[ingredient.Name]!!.byName[changedIngredient.Name] ?: -1) * 2
                },
                cost = this.cost + changedIngredient.MinCost
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
