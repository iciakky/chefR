import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.float
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.google.gson.Gson
import java.net.URL
import java.util.*
import java.util.function.Function
import kotlin.math.abs
import kotlin.math.max

val divider = "-".repeat(40)

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

val tagPercentage = mapOf(
    "Spice" to 5,
    "Fruit" to 15,
    "Nut" to 15,
    "Dairy" to 15,
    "Alcohol" to 15
)

fun main(args: Array<String>) = ChefR().versionOption("0.1(beta)").main(args)

class ChefR : CliktCommand(printHelpOnEmptyArgs = true) {
    val mustHaveOpt: List<List<String>> by option(
        "-mh", "--mustHave",
        help = """n ingredient names or tags followed by number(default 1) and portion(default 0). 
                  e.g. -mh Nut[,Apple,...[,3[,20]]] means searching for recipe having 3 Nut or Apple or ... with portion >= 20, such as {Apple, Almonds, Peanuts, ...}""".trimIndent()
    ).split(",").multiple()

    val perksOpt: List<List<String>> by option(
        "-pk", "--perks",
        help = """1 ingredient name or tags followed by number(default 1) and portion(default 0). 
                  e.g. -mh Nut[,3[,20]] means searching for recipe having 3 Nut with portion >= 20, such as {Pine_Nuts, Almonds, Peanuts, ...}""".trimIndent()
    ).split(",").multiple()

    val tagsOpt: List<String> by option(
        "-t", "--tags",
        help = "algorithm will try add ingredients or increase portion to make recipe having these tags"
    ).split(",").default(emptyList())

    val avoidOpt: List<String> by option(
        "-a", "--avoid",
        help = "n ingredient names or tags that you want to avoid. e.g. for vegan, try `--avoid Meat,Seafood,Dairy`"
    ).split(",").default(emptyList())

    val ownIngredientsOpt: List<String> by option(
        "-i", "--ingredients",
        help = "n ingredient names that you've purchased"
    ).split(",").default(emptyList())

    val ingredientPointsOpt: Int by option(
        "-ip", "--ingredientPoints",
        help = "Number of your free ingredient points. Default: 0"
    ).int().restrictTo(min = 0, clamp = true).default(0)

    val cookingTimeOpt: Float by option(
        "-tf", "--cookingTimeFactor",
        help = "Useful when you search for something like salad or pie, which is 0.0. Default: 1.0"
    ).float().restrictTo(min = 0F, clamp = true).default(1F)

    val maxScoreOpt: Int by option(
        "-ms", "--maxScore",
        help = "by default 80(50 flavor + 30 perk), but you can lower it for cheaper recipes"
    ).int().restrictTo(0..80, clamp = true).default(80)

    val leaderboardSizeOpt: Int by option(
        "-b", "--leaderboardSize",
        help = "Print when first n best recipes updates. Default: 10"
    ).int().restrictTo(min = 1, clamp = true).default(10)

    val cookingTimeStopOpt: Boolean by option(
        "-st", "--stopByCookingTime",
        help = "Stop search if all recipes with shortest cooking time has found. Default: true. Ignored if cookingTimeFactor > 0"
    ).flag(default = true)

    val costStopOpt: Float by option(
        "-sc", "--stopByCost",
        help = "Stop search if most of rest recipes are n times more costly then current best recipe. Default 1.8"
    ).float().restrictTo(min = 1.0F, clamp = true).default(1.8F)

    val memoryFullThresholdOpt: Pair<Float, Float> by option(
        "-m", "--memoryThreshold",
        help = """drop n([0.1, 1.0] * 100%) recipes from open set if memory is m([0.1, 1.0] * 100%) full. 
                  In practice not very useful for finding better recipes, but...you never know :P. default 0.9/0.8""".trimMargin()
    ).float().restrictTo(min = 0.1F, max = 1F, clamp = true).pair().default(0.9F to 0.8F)

    override fun run() {
        // read config from options
        val config = Config(
            mustHave = mustHaveOpt.associate { column ->
                val names = column.withIndex()
                    .takeWhile { (index, name) -> index + 2 < column.count() || name.toIntOrNull() == null }
                    .map { it.value }.toSet()
                echo(names)
                when (column.count() - names.count()) {
                    1 -> names to (column.last().toInt().coerceAtLeast(1) to 0)
                    2 -> names to (column[names.count()].toInt().coerceAtLeast(1) to
                            column[names.count() + 1].toInt().coerceAtLeast(0))
                    else -> names to (1 to 0)
                }
            },

            perks = perksOpt.associate { column ->
                when (column.count()) {
                    1 -> column[0] to (1 to 0)
                    2 -> column[0] to ((column[1].toIntOrNull() ?: 1) to 0)
                    3 -> column[0] to ((column[1].toIntOrNull()?.coerceAtLeast(1) ?: 1) to
                            (column[2].toIntOrNull()?.coerceAtLeast(0) ?: 0))
                    else -> error("invalid perk option ${column.joinToString()}")
                }
            },

            requiredTags = tagsOpt.toSet(),
            avoid = avoidOpt.toSet(),
            boughtIngredients = ownIngredientsOpt,
            ingredientPoints = ingredientPointsOpt,
            cookingTimeModifier = cookingTimeOpt,
            maxScore = maxScoreOpt,
            leaderboardSize = leaderboardSizeOpt,
            stopByCookingTime = cookingTimeStopOpt,
            stopByCostMultiple = costStopOpt,
            memoryFullThreshold = memoryFullThresholdOpt.second,
            openSetDropRate = memoryFullThresholdOpt.first
        )
        echo("Here is your config:")
        echo("$config")
        TermUi.confirm("Continue with this config?", default = true, abort = true)

        // 讀取並解析食材資料
        val data: Raw = Gson().fromJson(raw, Raw::class.java)
        val available: HashSet<String> = Gson().fromJson(rawAvailable, mutableListOf<String>().javaClass).toHashSet()
        available += config.boughtIngredients

        // 為了用 bits 表示食譜，將食材對應至 bit
        val requiredBits = data.indi.count()
        val ingredBitsMap =
            data.indi.mapIndexed { i, ingredient -> ingredient.Name to BitSet(requiredBits).apply { set(i) } }.toMap()

        val known = HashSet<BitSet>()
        // todo runtime change priority, migrate to newly configured priority queue, continue search
        val priority = compareBy<Recipe> { it.toBuyIngredientCount.coerceAtLeast(config.ingredientPoints) }
            .thenByDescending { it.mustHaveCompleteRate }
            .thenByDescending { it.requiredTagsCount }
            .thenByDescending { it.mustNotHaveCompleteRate }
            .thenBy { it.uselessIngredientCount }
            .thenBy { it.cookingTime * config.cookingTimeModifier }
            .thenByDescending { it.score.coerceAtMost(config.maxScore) }
            .thenBy { it.realCost }
        var open = PriorityQueue<Recipe>(priority)
        Recipe.candidates = data.indi
        Recipe.available = available
        Recipe.ingredBitsMap = ingredBitsMap
        Recipe.match = data.match
        Recipe.config = config

        // # 初始化搜尋起始狀態
        val beginRecipe = Recipe(emptyList(), 0, 0F)
        open.add(beginRecipe)
        known += beginRecipe.bits

        // todo save/load solutions
        // 目標：風味滿50分的前提下，煮最快且成本最低
        val closed = PriorityQueue<Recipe>(priority)

        // 排行榜－顯示目前前 n 個最佳解
        val leaderboard = PriorityQueue<Recipe>(priority.reversed())

        // 開始搜尋
        val stopCondition = Function<Recipe, Boolean> {
            when {
                config.cookingTimeModifier > 0 && config.stopByCookingTime -> it.cookingTime > closed.peek().cookingTime
                else -> it.realCost / closed.peek().realCost > config.stopByCostMultiple
            }
        }
        val allMem = Runtime.getRuntime().maxMemory()
        val initMemUsed = Runtime.getRuntime().totalMemory()
        val initMemUsage = initMemUsed.toFloat() / allMem
        echo("start! memory usage %.2f%%".format(initMemUsage * 100))
        while (open.any()) {
            val curr = open.poll()!!
            closed += curr

            if (stopCondition.apply(curr)) {
                echo("\nstop condition meets")
                break
            }

            if (closed.count() % 100 == 0) {
                val memUsed = Runtime.getRuntime().totalMemory()
                val memUsage = memUsed.toFloat() / allMem
                echo(
                    "\r[${closed.count()} closed, ${open.count()} open, cost multiple %.2f%%, memory usage %.2f%%]"
                        .format(curr.realCost / closed.peek().realCost * 100, memUsage * 100),
                    trailingNewline = false
                )

                if (memUsage > config.memoryFullThreshold) {
                    echo(
                        "\nmemory usage over %.2f%%, releasing %.2f%% open set..."
                            .format(config.memoryFullThreshold, config.openSetDropRate)
                    )
                    val retain = PriorityQueue(priority)
                    while (retain.count() < open.count() * (1 - config.openSetDropRate)) {
                        retain += open.poll()
                    }
                    known -= open.map { it.bits }
                    echo("${open.count()} dropped")
                    open = retain
                    System.gc()
                }
            }

            // 搜尋相鄰的 Recipes 意味著：新增或移除一種食材進 Recipes
            for ((index, _) in data.indi.withIndex()) {
                val adjacentBits = BitSet.valueOf(curr.bits.toLongArray()).apply { flip(index) }
                // 不重複搜尋
                if (known.contains(adjacentBits)) {
                    continue
                }
                known += adjacentBits

                val recipe = curr.evolveNew(index)
                open.add(recipe)

                // 刷新排行榜
                leaderboard += recipe
                if (leaderboard.count() <= config.leaderboardSize || leaderboard.poll() != recipe) {
                    echo("\n$divider [after ${known.count()} searched, best ${config.leaderboardSize} below] $divider")
                    leaderboard.asSequence().sortedWith(priority).forEachIndexed { rank, leadingRecipe ->
                        echo("#$rank: ${leadingRecipe.toShortString()}")
                    }
                }
            }
        }
        echo("end!") // probably never run to here
    }
}

data class Config(
    val mustHave: Map<Set<String>, Pair<Int, Int>>,
    val perks: Map<String, Pair<Int, Int>>,
    val requiredTags: Set<String>,
    val avoid: Set<String>,
    val boughtIngredients: List<String>,
    val ingredientPoints: Int,
    val cookingTimeModifier: Float,
    val maxScore: Int,
    val leaderboardSize: Int,
    val stopByCookingTime: Boolean,
    val stopByCostMultiple: Float,
    val memoryFullThreshold: Float,
    val openSetDropRate: Float
)

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
        lateinit var config: Config
    }

    val score by lazy { flavor.coerceIn(0..50) + 10 * perkCompleteRate }

    val uselessIngredientCount by lazy {
        ingredients.count {
            it.AromaNeutral && !(config.perks.contains(it.Name) || it.Tags.any { tag -> config.perks.contains(tag) }) &&
                    !config.mustHave.keys.any { key -> key.contains(it.Name) || it.Tags.any { tag -> key.contains(tag) } }
        }
    }

    val toBuyIngredientCount by lazy { ingredients.count { !available.contains(it.Name) } }

    // 0 ~ 720
    val cookingTime by lazy { ingredients.maxBy { it.CookingTime }?.CookingTime ?: 0 }

    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val mustHaveCompleteRate: Int by lazy {
        if (config.mustHave.isEmpty()) 0
        else config.mustHave.map { (condition, numberAndPortion) ->
            val (number, portion) = numberAndPortion
            ingredients.filter {
                it.MaxPortion >= portion && (condition.contains(it.Name) || it.Tags.any { tag -> condition.contains(tag) })
            }.take(number).count()
        }.sum()
    }
    val mustNotHaveCompleteRate: Int by lazy {
        if (config.avoid.isEmpty()) 0
        else config.avoid.count { condition ->
            ingredients.any {
                condition.contains(it.Name) || it.Tags.any { tag -> condition.contains(tag) }
            }
        } * -1
    }

    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val perkCompleteRate: Int by lazy {
        if (config.perks.isEmpty()) 0
        else config.perks.count { (condition, numberAndPortion) ->
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

    // todo 實際上不一定能夠達到此 tag (e.g. max portion 太低而無法滿足必要比例)
    val requiredTagsCount: Int by lazy { config.requiredTags.count { req -> ingredients.any { i -> i.Tags.any { it == req } } } }

    // todo extract logic, these knowledge is not Recipe should know
    // all config considered: mustHave, perks, requiredTags
    val extraPortion: Map<String, Int> by lazy {
        val newPortion = mutableMapOf<String, Int>()
        // 對每個分量要求，找到 Unit Cost 最低的選項
        config.mustHave.entries.filter { (_, numberAndPortion) -> (numberAndPortion.second > 0) }
            .forEach { (conditions, numberAndPortion) ->
                val (number, portion) = numberAndPortion
                ingredients.asSequence()
                    .filter { conditions.contains(it.Name) || it.Tags.any { tag -> conditions.contains(tag) } }
                    .filter { it.MaxPortion >= portion }
                    .sortedBy { it.UnitCost }.take(number)
                    .forEach { newPortion[it.Name] = max((newPortion[it.Name] ?: 0), portion) }
            }
        config.perks.entries.filter { it.key.isNotBlank() && it.value.second > 0 }
            .forEach { (condition, numberAndPortion) ->
                val (number, portion) = numberAndPortion
                ingredients.asSequence()
                    .filter { it.Name == condition || it.Tags.contains(condition) }
                    .filter { it.MaxPortion >= portion }
                    .sortedBy { it.UnitCost }.take(number)
                    .forEach { newPortion[it.Name] = max((newPortion[it.Name] ?: 0), portion) }
            }
        if (config.requiredTags.any()) {
            // 調成指定的比例以滿足 requiredTags
            // calc 1% = ?g by portion_of_other / remain_%
            val requiredPercentage = config.requiredTags.sumBy { tagPercentage[it] ?: 25 }.coerceIn(0..99) // 0~99
            val others = ingredients.filter { it.Tags.all { tag -> !config.requiredTags.contains(tag) } }
            if (others.count() < ingredients.count()) {
                val gPerPercent =
                    others.sumBy { newPortion[it.Name] ?: it.MinPortion }.toFloat() / (100 - requiredPercentage)
                val requiredPortion = config.requiredTags
                    .associateWith { (gPerPercent * (tagPercentage[it] ?: 25)).toInt() + 1 }.toMutableMap()
                ingredients.forEach { i ->
                    i.Tags.filter { requiredPortion.contains(it) }.forEach { tag ->
                        requiredPortion[tag] =
                            (requiredPortion[tag]!! - (newPortion[i.Name] ?: i.MinPortion)).coerceAtLeast(0)
                    }
                }
                ingredients.asSequence()
                    .filter { it.Tags.any { tag -> requiredPortion.contains(tag) } }
                    .sortedBy { it.UnitCost }
                    .forEach { i ->
                        val tag = i.Tags.filter { requiredPortion.contains(it) }.maxBy { requiredPortion[it]!! }!!
                        val required =
                            requiredPortion[tag]!! // todo fix: 這裡會多算，其他也有此 tag 的 portion 也應該要消耗 required portion
                        val curr = newPortion[i.Name] ?: i.MinPortion
                        val room = i.MaxPortion - curr
                        val extra = required.coerceAtMost(room)
                        if (extra > 0) {
                            newPortion[i.Name] = curr + extra
                            i.Tags.filter { requiredPortion.contains(it) }.forEach {
                                requiredPortion[it] = (requiredPortion[it]!! - extra).coerceAtLeast(0)
                            }
                        }
                    }
            }
        }
        newPortion
    }

    // todo extract logic to be with mustHave/perks, these knowledge is not Recipe should know
    val realCost: Float by lazy {
        // 將原本的 cost 加上額外增加的成本 (unit*portion - minCost)
        this.cost + ingredients.filter { extraPortion.contains(it.Name) }
            .sumByDouble { (it.UnitCost * (extraPortion[it.Name] ?: it.MinPortion) - it.MinCost).toDouble() }
            .toFloat()
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
                        ingredient.AromaNeutral -> newFlavor
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
                        ingredient.AromaNeutral -> newFlavor
                        changedIngredient.AromaNeutral -> newFlavor
                        else -> newFlavor + (match[ingredient.Name]?.byName?.get(changedIngredient.Name) ?: -1) * 2
                    }
                },
                cost = this.cost + changedIngredient.MinCost
            )
        }
    }

    fun toShortString(): String {
        return "${toBuyIngredientCount}/${mustHaveCompleteRate}/${requiredTagsCount}/${cookingTime}/${realCost}/${score}/${ingredients.joinToString { i ->
            val isNew = !available.contains(i.Name)
            val extra = extraPortion.contains(i.Name)
            (if (isNew) "*" else "") + i.Name + (if (extra) "-${extraPortion[i.Name]!!}" else "")
        }}"
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
