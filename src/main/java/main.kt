import com.google.gson.Gson
import java.net.URL
import java.util.*
import kotlin.math.abs

val raw =
    URL("https://gist.githubusercontent.com/iciakky/cd98792f54e52b65b9cb2e30c1de9cbd/raw/e4e68a86391cd521a747c29bd8b48e7cd4f4ac88/chefIndi.json").readText()

val rawAvailable =
    URL("https://gist.githubusercontent.com/iciakky/f0192781a907247bb5d667f8ec597428/raw/cacc19c7fe2f8ec592c49c6bbfa0fbdaeae962ce/defaultIngredients.json").readText()

enum class Tag {
    Vegetables,
    Spice,
    Nut,
    Seafood,
    Fruit,
    Meat,
    Legume,
    Dairy,
    Other,
    Carbs,
    Alcohol,
    Beer,
    Fat,
    Wine
}

// todo support CLI
fun main() {

    // # 準備階段
    // 讀取設定參數 // todo replace hardcoded config
    val mustHave: Map<Set<String>, Pair<Int, Int>> = mapOf(setOf(Tag.Fat.name) to (1 to 20))
    val mustNotHave: Set<String> = setOf(Tag.Vegetables.name, Tag.Seafood.name, Tag.Meat.name, Tag.Dairy.name)
    val boughtIngredients = listOf("Beet", "Ginger", "Pickles")
    val canBuyIngredientCount = 3
    val cookingTimeModifier = 0
    val perks: Map<String, Pair<Int, Int>> =
        emptyMap() // mapOf("" to (10 to 0), "Fruit" to (3 to 0), "Nut" to (1 to 80))
    // 讀取並解析食材資料
    val data: Raw = Gson().fromJson(raw, Raw::class.java)
    val available: HashSet<String> = Gson().fromJson(rawAvailable, mutableListOf<String>().javaClass).toHashSet()
    available += boughtIngredients
    // 根據指定條件篩選掉絕對不會使用的食材 (沒有任何 match 的食材)，但 mustHave 必須留下
    val candidates = data.indi
        // todo fix: rm following filter condition will break my program...
        // but if all ingredient is involve in search then all solutions can be unified
        .filterNot { ingredient ->
            !mustHave.any { (condition, _) ->
                condition.contains(ingredient.Name) || ingredient.Tags.any { tag -> condition.contains(tag) }
            } && (ingredient.AromaNeutral || !data.match.contains(ingredient.Name))
        }
    // 為了用 bits 表示食譜，將食材對應至 bit
    val requiredBits = candidates.count()
    val ingredBitsMap =
        candidates.mapIndexed { i, ingredient -> ingredient.Name to BitSet(requiredBits).apply { set(i) } }.toMap()

    val known = HashSet<BitSet>()
    // todo runtime change priority, migrate to newly configured priority queue, continue search
    val priority = compareBy<Recipe> { it.toBuyIngredientCount.coerceAtLeast(canBuyIngredientCount) }
        .thenByDescending { it.mustHaveCompleteRate }
        .thenByDescending { it.mustNotHaveCompleteRate }
        .thenBy { it.cookingTime * cookingTimeModifier }
        .thenByDescending { it.perkCompleteRate }
        .thenByDescending { it.flavor.coerceAtMost(50) }
        .thenBy { it.realCost }
    val suggestion = PriorityQueue<Recipe>(priority)
    Recipe.candidates = candidates
    Recipe.available = available
    Recipe.ingredBitsMap = ingredBitsMap
    Recipe.match = data.match
    Recipe.mustHave = mustHave
    Recipe.mustNotHave = mustNotHave
    Recipe.perks = perks

    // # 初始化搜尋起始狀態
    val beginRecipe = Recipe(emptyList(), 0, 0F)
    suggestion += beginRecipe
    known += beginRecipe.bits

    // todo save/load solutions
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
                        "${curr.toBuyIngredientCount}/${curr.mustHaveCompleteRate}/${curr.perkCompleteRate}/${curr.cookingTime}/${curr.realCost}/${curr.flavor}/${curr.ingredients.joinToString { it.Name }}"
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
        lateinit var available: Set<String>
        lateinit var ingredBitsMap: Map<String, BitSet>
        lateinit var match: Map<String, Match>
        lateinit var mustHave: Map<Set<String>, Pair<Int, Int>>
        lateinit var mustNotHave: Set<String>
        lateinit var perks: Map<String, Pair<Int, Int>>
    }

    val toBuyIngredientCount by lazy { ingredients.count { !available.contains(it.Name) } }

    // 0 ~ 720
    val cookingTime by lazy { ingredients.maxBy { it.CookingTime }?.CookingTime ?: 0 }

    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val mustHaveCompleteRate: Int by lazy {
        if (mustHave.isEmpty()) 0
        else mustHave.map { (condition, numberAndPortion) ->
            val (number, portion) = numberAndPortion
            ingredients.filter {
                it.MaxPortion >= portion && (condition.contains(it.Name) || it.Tags.any { tag -> condition.contains(tag) })
            }.take(number).count()
        }.sum()
    }
    val mustNotHaveCompleteRate: Int by lazy {
        if (mustNotHave.isEmpty()) 0
        else mustNotHave.count { condition ->
            ingredients.any {
                condition.contains(it.Name) || it.Tags.any { tag -> condition.contains(tag) }
            }
        } * -1
    }
    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val perkCompleteRate: Int by lazy {
        if (perks.isEmpty()) 0
        else perks.count { (condition, numberAndPortion) ->
            val (number, portion) = numberAndPortion
            // 總食材個數
            if (condition.isBlank()) {
                if (number < 0) {
                    // 以下
                    ingredients.count() <= abs(number)
                } else {
                    // 以上
                    ingredients.count() >= abs(number)
                }
            } else {
                // 特定食材個數或量
                ingredients.count {
                    it.MaxPortion >= portion && (it.Name == condition || it.Tags.contains(condition))
                } >= number
            }
        }
    }
    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val realCost: Float by lazy {
        // 這裡假設在要求分量時上 mustHave 跟 perks 不會重複
        // 對每個分量要求，找到 Unit Cost 最低的選項，將原本的 cost 加上額外增加的成本 (unit*portion - minCost)
        mustHave.entries.fold(0F) { ret, (conditions, numberAndPortion) ->
            val (number, portion) = numberAndPortion
            if (portion < 0) ret
            else ret + (ingredients.asSequence().filter {
                conditions.contains(it.Name) || it.Tags.any { tag -> conditions.contains(tag) }
            }.sortedBy { it.UnitCost }.take(number).map { it.UnitCost * portion - it.MinCost }.sum())
        } + perks.filter { it.key.isNotBlank() && it.value.second > 0 }.entries.fold(0F) { ret, (condition, numberAndPortion) ->
            val (number, portion) = numberAndPortion
            ret + (ingredients.asSequence().filter { it.Name == condition || it.Tags.contains(condition) }
                .sortedBy { it.UnitCost }.take(number).map { it.UnitCost * portion - it.MinCost }.sum())
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
                    when {
                        ingredient == changedIngredient -> newFlavor
                        changedIngredient.AromaNeutral -> newFlavor
                        else -> newFlavor - (match[ingredient.Name]?.byName?.get(changedIngredient.Name) ?: -1) * 2
                    }
                },
                cost = this.cost - changedIngredient.MinCost
            )
        } else {
            return Recipe(
                ingredients = this.ingredients + changedIngredient,
                flavor = this.ingredients.fold(this.flavor) { newFlavor, ingredient ->
                    when {
                        changedIngredient.AromaNeutral -> newFlavor
                        else -> newFlavor + (match[ingredient.Name]?.byName?.get(changedIngredient.Name) ?: -1) * 2
                    }
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
