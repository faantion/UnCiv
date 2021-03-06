package com.unciv.logic.automation

import com.unciv.UnCivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitActions

class UnitAutomation{

    fun healUnit(unit:MapUnit) {
        // If we're low on health then heal
        // todo: go to a more defensible place if there is one
        val tilesInDistance = unit.getDistanceToTiles().keys
        val unitTile = unit.getTile()

        // Go to friendly tile if within distance - better healing!
        val friendlyTile = tilesInDistance.firstOrNull { it.getOwner()?.civName == unit.owner && unit.canMoveTo(it) }
        if (unitTile.getOwner()?.civName != unit.owner && friendlyTile != null) {
            unit.moveToTile(friendlyTile)
            return
        }

        // Or at least get out of enemy territory yaknow
        val neutralTile = tilesInDistance.firstOrNull { it.getOwner() == null && unit.canMoveTo(it)}
        if (unitTile.getOwner()?.civName != unit.owner && unitTile.getOwner() != null && neutralTile != null) {
            unit.moveToTile(neutralTile)
            return
        }
    }

    fun containsAttackableEnemy(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        val tileCombatant = Battle().getMapCombatantOfTile(tile)
        if(tileCombatant==null) return false
        return tileCombatant.getCivilization()!=civInfo
    }

    fun getAttackableEnemies(unit: MapUnit): List<TileInfo> {
        val attackableTiles = unit.civInfo.getViewableTiles()
                .filter { containsAttackableEnemy(it,unit.civInfo) }

        if(MapUnitCombatant(unit).isMelee()) {
            val distanceToTiles = unit.getDistanceToTiles()
            // If we're conducting a melee attack,
            // then there needs to be a tile adjacent to the enemy that we can get to,
            // AND STILL HAVE MOVEMENT POINTS REMAINING,
            return attackableTiles.filter {
                it.neighbors.any {
                    unit.getTile()==it || // We're already right nearby
                        unit.canMoveTo(it)
                            && distanceToTiles.containsKey(it)
                            && distanceToTiles[it]!! < unit.currentMovement  // We can get there
                }
            }
        }

        else { // Range attack, so enemy needs to be in range
            return attackableTiles.filter { unit.getTile().getTilesInDistance(2).contains(it) }
        }

    }

    fun automateUnitMoves(unit: MapUnit) {

        if (unit.name == "Settler") {
            automateSettlerActions(unit)
            return
        }

        if (unit.name == "Worker") {
            WorkerAutomation().automateWorkerAction(unit)
            return
        }

        if(unit.name.startsWith("Great")) return // DON'T MOVE A MUSCLE

        if (unit.health < 50) {
            healUnit(unit)
            return
        } // do nothing but heal

        // if there is an attackable unit in the vicinity, attack!
        val enemyTileToAttack = getAttackableEnemies(unit).firstOrNull()

        if (enemyTileToAttack != null) {
            val setupAction = UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen!!).firstOrNull{ it.name == "Set up" }
            if(setupAction!=null) setupAction.action()

            val enemy = Battle().getMapCombatantOfTile(enemyTileToAttack)!!
            val damageToAttacker = BattleDamage().calculateDamageToAttacker(MapUnitCombatant(unit), enemy)

            if (damageToAttacker < unit.health) { // don't attack if we'll die from the attack
                if(MapUnitCombatant(unit).isMelee())
                    unit.movementAlgs().headTowards(enemyTileToAttack)
                Battle(unit.civInfo.gameInfo).attack(MapUnitCombatant(unit), enemy)
                return
            }
        }

        if(unit.getTile().isCityCenter()) return // It's always good to have a unit in the city center, so if you havn't found annyonw aroud to attack, forget it.

        val reachableCitiesWithoutUnits = unit.civInfo.cities.filter {
            unit.canMoveTo(it.getCenterTile())
                && unit.movementAlgs().canReach(it.getCenterTile())  }
        if(reachableCitiesWithoutUnits.isNotEmpty()){
            val closestCityWithoutUnit = reachableCitiesWithoutUnits
                    .minBy { unit.movementAlgs().getShortestPath(it.getCenterTile()).size }!!
            unit.movementAlgs().headTowards(closestCityWithoutUnit.getCenterTile())
            return
        }

        if (unit.health < 80) {
            healUnit(unit)
            return
        } // do nothing but heal until 80 health


        // else, if there is a reachable spot from which we can attack this turn
        // (say we're an archer and there's a unit 3 tiles away), go there and attack
        // todo

        // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
        val closestEnemy = unit.getTile().getTilesInDistance(5)
                .firstOrNull { containsAttackableEnemy(it,unit.civInfo) && unit.movementAlgs().canReach(it) }

        if (closestEnemy != null) {
            unit.movementAlgs().headTowards(closestEnemy)
            return
        }

        if (unit.health < 100) {
            healUnit(unit)
            return
        }

        // else, go to a random space
        randomWalk(unit)
        // if both failed, then... there aren't any reachable tiles. Which is possible.
    }

    private fun randomWalk(unit: MapUnit) {
        val reachableTiles = unit.getDistanceToTiles()
                .filter { unit.canMoveTo(it.key) }
        val reachableTilesMaxWalkingDistance = reachableTiles.filter { it.value == unit.currentMovement }
        if (reachableTilesMaxWalkingDistance.any()) unit.moveToTile(reachableTilesMaxWalkingDistance.toList().getRandom().first)
        else if (reachableTiles.any()) unit.moveToTile(reachableTiles.toList().getRandom().first)
    }

    fun rankTileAsCityCenter(tileInfo: TileInfo, nearbyTileRankings: Map<TileInfo, Float>): Float {
        val bestTilesFromOuterLayer = tileInfo.tileMap.getTilesAtDistance(tileInfo.position,2)
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
        val top5Tiles = tileInfo.neighbors.union(bestTilesFromOuterLayer)
                .sortedByDescending { nearbyTileRankings[it] }
                .take(5)
        return top5Tiles.map { nearbyTileRankings[it]!! }.sum()
    }

    private fun automateSettlerActions(unit: MapUnit) {
        // find best city location within 5 tiles
        val tilesNearCities = unit.civInfo.gameInfo.civilizations.flatMap { it.cities }
                .flatMap { it.getCenterTile().getTilesInDistance(2) }

        // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
        val nearbyTileRankings = unit.getTile().getTilesInDistance(7)
                .associateBy ( {it},{ Automation().rankTile(it,unit.civInfo) })

        val possibleTiles =  unit.getTile().getTilesInDistance(5)
                .minus(tilesNearCities)

        val bestCityLocation: TileInfo? = possibleTiles
                .sortedByDescending { rankTileAsCityCenter(it, nearbyTileRankings) }
                .firstOrNull { unit.movementAlgs().canReach(it) }

        if(bestCityLocation==null) // We got a badass over here, all tiles within 5 are taken? Screw it, random walk.
        {
            randomWalk(unit)
            return
        }

        if (unit.getTile() == bestCityLocation) // already there!
            UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen).first { it.name == "Found city" }.action()
        else {
            unit.movementAlgs().headTowards(bestCityLocation)
            if (unit.currentMovement > 0 && unit.getTile() == bestCityLocation)
                UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen).first { it.name == "Found city" }.action()
        }
    }

}