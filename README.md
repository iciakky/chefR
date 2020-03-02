# chefR

[![InstallDist by gradle](https://github.com/iciakky/chefR/workflows/InstallDist%20by%20gradle/badge.svg)](https://github.com/iciakky/chefR/actions)

**ChefR** is a brute-force Recipe search tool written in kotlin.  
It finds you good-enough recipes within few seconds, with memory intensive Dijkstra-like search algorithm.  
*Good* means: 
1. have enough ingredient points to purchase
1. meets template requirements
1. contain no ingredients/tags of your choice 
1. meets perk conditions
1. less cooking time
1. more flavor score
1. less cost

## Getting started

To begin, simply run chefR will show its usage.  
But usually examples helps more.

### Spaghetti Example

To search some recipes for your, said, Spaghetti template. The only requirement is:  
*Use one of the following ingredients in High Quantity (100g or more): Pasta, Fresh Pasta, Noodles.*  

then you can run
```bash
❯ ./chefR --mustHave Pasta,Fresh_Pasta,Noodles,1,100
```
This means *1* of [*Pasta*, *Fresh_Pasta*, *Noodles*] in *100*g  
and show you the config for your confirmation. 
```bash
Config(mustHave={[Pasta, Fresh_Pasta, Noodles]=(1, 100)}, perks={}, avoid=[], boughtIngredients=[], ingredientPoints=0, cookingTimeModifier=1.0, leaderboardSize=10, stopByCookingTime=true, stopByCostMultiple=1.8, memoryFullThreshold=0.8, openSetDropRate=0.9)
Continue with this config? [Y/n]:
```
Just press [Enter], and you'll see:
```bash
---------------------------------------- [after 6306 searched, best 10 below] ----------------------------------------
#0: 0/1/0/240/0.8810001/54/Fresh_Pasta, Butter, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Blonde_Beer
#1: 0/1/0/240/0.88600016/52/Fresh_Pasta, Butter, Honey, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil
#2: 0/1/0/240/0.90100014/52/Fresh_Pasta, Butter, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Blonde_Beer, Ketchup
#3: 0/1/0/240/0.90600014/56/Fresh_Pasta, Butter, Honey, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Blonde_Beer
#4: 0/1/0/240/0.90600014/50/Fresh_Pasta, Butter, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Blonde_Beer, Apple
#5: 0/1/0/240/0.90600014/50/Fresh_Pasta, Butter, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Blonde_Beer, Olive_Oil
#6: 0/1/0/240/0.90600014/50/Fresh_Pasta, Butter, Honey, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Ketchup
#7: 0/1/0/240/0.91100013/52/Fresh_Pasta, Butter, Honey, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Apple
#8: 0/1/0/240/0.9210001/52/Fresh_Pasta, Butter, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Olive_Oil, Garlic, Lettuce
#9: 0/1/0/240/0.9260001/52/Fresh_Pasta, Butter, Honey, Banana, Mint, WhiteChocolate, DarkChocolate, Chocolate, Basil, Blonde_Beer, Ketchup
[4900 closed, 910192 open, cost multiple 144.15%, memory usage 36.87%]
```
This is leaderboard, showing leading 10 recipes among other searched (4900+910192 in this case)
* `#0` means best recipe so far
* `0/1/0/240/0.8810001/54/` means 
  1. no ingredient points is required
  1. 1 requirement meets
  1. no ingredient/tag limitation is violated
  1. 240 cooking time
  1. 0.88 cost
  1. 54 flavor
* followed by ingredients of this recipe   

If these are good enough for you, ctrl+c whenever you want to stop the search.  

### Pie Example
Pie have 3 requirements, they are:
* Fat 20g
* Flour 100g
* Sugar 30g

Since Pie is a Dessert template, it has some negative perk like **Wait, What?**:  
*People are not exactly eager to try strange desserts. If you want to avoid this penalty do not use Vegetables, Meat, or Seafood Tagged Ingredients in your Desserts.*

To do this, we can:
```bash
❯ ./chefR --mustHave Fat,1,20 --mustHave Flour,1,100 --mustHave Sugar,1,30 --avoid Vegetables,Meat,Seafood
```
We can even search for Vegan only recipes by avoid Dairy (and make this command shorter by the way):
```bash
❯ ./chefR -mh Fat,1,20 -mh Flour,1,100 -mh Sugar,1,30 -a Vegetables,Meat,Seafood,Dairy
```
Unfortunately, vegan pie seems too hard to cook with only default ingredients available. Non of leading recipes reaches 50 flavor:   
```bash
---------------------------------------- [after 5877 searched, best 10 below] ----------------------------------------
#0: 0/3/0/300/0.3355/38/Sugar, Olive_Oil, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Blonde_Beer
#1: 0/3/0/300/0.56049997/38/Sugar, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Blonde_Beer, Margarine, Honey
#2: 0/3/0/300/0.31550002/36/Sugar, Olive_Oil, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate
#3: 0/3/0/300/0.3355/36/Sugar, Olive_Oil, Flour, Basil, Ketchup, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate
#4: 0/3/0/300/0.3405/36/Sugar, Olive_Oil, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Honey
#5: 0/3/0/300/0.35549998/36/Sugar, Olive_Oil, Flour, Basil, Ketchup, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Blonde_Beer
#6: 0/3/0/300/0.3605/36/Sugar, Olive_Oil, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Blonde_Beer, Honey
#7: 0/3/0/300/0.5355/36/Sugar, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Blonde_Beer, Margarine
#8: 0/3/0/300/0.5855/36/Sugar, Flour, Basil, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Blonde_Beer, Margarine, Honey, Apple
#9: 0/3/0/300/0.3605/34/Sugar, Olive_Oil, Flour, Basil, Ketchup, Banana, Chocolate, Mint, WhiteChocolate, DarkChocolate, Honey
[1500 closed, 281880 open, cost multiple 127.27%, memory usage 8.70%]
```

Thing may be different if you have some free ingredient points:

```bash
❯ ./chefR --ingredientPoints 3 -mh Fat,1,20 -mh Flour,1,100 -mh Sugar,1,30 -a Vegetables,Meat,Seafood,Dairy
```
This time you got all recipes flavor > 50
```bash
---------------------------------------- [after 611182 searched, best 10 below] ----------------------------------------
#0: 3/3/0/300/0.27449995/50/Seeds_Oil, Sugar, Flour, Chocolate, Banana, WhiteChocolate, DarkChocolate, Cinnamon, Coffee
#1: 3/3/0/300/0.27899998/50/Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Coffee, Chestnuts, Seeds_Oil
#2: 3/3/0/300/0.28149998/54/Seeds_Oil, Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Lemon, Coffee
#3: 3/3/0/300/0.28249997/56/Seeds_Oil, Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Tea_Leaves, Coffee
#4: 3/3/0/300/0.28449997/52/Seeds_Oil, Sugar, Flour, Chocolate, Banana, WhiteChocolate, DarkChocolate, Orange, Coffee
#5: 3/3/0/300/0.2855/50/Seeds_Oil, Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Lemon, Soy
#6: 3/3/0/300/0.28649998/64/Seeds_Oil, Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Cinnamon, Coffee
#7: 3/3/0/300/0.28649998/52/Seeds_Oil, Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Tea_Leaves, Soy
#8: 3/3/0/300/0.28649998/50/Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Coffee, Lime, Seeds_Oil
#9: 3/3/0/300/0.28749996/50/Seeds_Oil, Sugar, Flour, Mint, Chocolate, Banana, WhiteChocolate, DarkChocolate, Lemon, Blueberries
[9200 closed, 1709904 open, cost multiple 128.60%, memory usage 49.04%]
```
It is worth mentioning that you don't have to use all your free ingredient points to search. In this case, actually 1 free point can reach you 50-flavor recipes like this one:
```bash
#0: 1/3/0/300/0.33049998/54/Sugar, Flour, Banana, Mint, Chocolate, Basil, Lemon, Olive_Oil, WhiteChocolate, DarkChocolate
```
After you purchase **Lemon** for this recipe, **remember to add it in your following searches** by
 ```bash
 ❯ ./chefR --ingredients Lemon
 ```

### Lv3 Soup Example
Assume you're an expert of soup, following perks may be key to satisfy your Gourmet customers:
* 3 Vegetables
* 4 Spice
* 10 Ingredients

Combine with soup requirement: 
* 2 Vegetables 50g

We can search with perks option:
```bash
❯ ./chefR -mh Vegetables,2,50 --perks Vegetables,3 --perks Spice,4 --perks ,10
```
This produce following config:
```bash
Config(mustHave={[Vegetables]=(2, 50)}, perks={Vegetables=(3, 0), Spice=(4, 0), =(10, 0)}, avoid=[], boughtIngredients=[], ingredientPoints=0, cookingTimeModifier=1.0, leaderboardSize=10, stopByCookingTime=true, stopByCostMultiple=1.8, memoryFullThreshold=0.8, open
SetDropRate=0.9)
```
As you can see, `Vegetables,3` means `Vegetables=(3, 0)`, 0 is the default portion if omitted.
You can even use `Vegetables` for `Vegetables=(1, 0)`

You may notices `--perks ,10` means `=(10, 0)`. This is because empty string will match any ingredients.

Above command gives you:
```bash
---------------------------------------- [after 22592 searched, best 10 below] ----------------------------------------
#0: 0/2/2/400/0.28050008/54/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, Chocolate, Banana, DarkChocolate
#1: 0/2/2/400/0.28050008/54/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, Chocolate, WhiteChocolate, Banana
#2: 0/2/2/400/0.28050008/54/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, WhiteChocolate, Banana, DarkChocolate
#3: 0/2/2/400/0.28600007/50/Lettuce, Carrot, Garlic, Basil, Mint, Butter, Chocolate, WhiteChocolate, Banana, DarkChocolate, Honey
#4: 0/2/2/400/0.28600007/60/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, Chocolate, WhiteChocolate, Banana, DarkChocolate
#5: 0/2/2/400/0.29100007/50/Lettuce, Carrot, Garlic, Basil, Mint, Butter, Chocolate, WhiteChocolate, Banana, DarkChocolate, Black_Pepper
#6: 0/2/2/400/0.2935001/52/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, Chocolate, Apple, DarkChocolate
#7: 0/2/2/400/0.2935001/52/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, WhiteChocolate, Apple, DarkChocolate
#8: 0/2/2/400/0.2935001/52/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, Chocolate, WhiteChocolate, Apple
#9: 0/2/2/400/0.29900008/50/Lettuce, Carrot, Garlic, Olive_Oil, Basil, Mint, Butter, Chocolate, WhiteChocolate, Apple, DarkChocolate
[5200 closed, 970854 open, cost multiple 157.22%, memory usage 36.97%]
```

In addition, for perks like **Balanced Recipe** (use 10 or less ingredients), you can use `--perks ,-10`