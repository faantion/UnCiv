package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Array
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import java.util.*

class CivilopediaScreen : CameraStageBaseScreen() {
    init {
        Gdx.input.inputProcessor = stage
        val buttonTable = Table()
        buttonTable.pad(15f)
        val entryTable = Table()
        val splitPane = SplitPane(buttonTable, entryTable, true, CameraStageBaseScreen.skin)
        splitPane.setSplitAmount(0.2f)
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        val label = Label("", CameraStageBaseScreen.skin)
        label.setWrap(true)

        val goToGameButton = TextButton("Return \r\nto game", CameraStageBaseScreen.skin)
        goToGameButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.setWorldScreen()
                dispose()
            }
        })
        buttonTable.add(goToGameButton)

        val map = LinkedHashMap<String, Collection<ICivilopedia>>()

        map["Basics"] = GameBasics.Helps.linqValues().`as`(ICivilopedia::class.java)
        map["Buildings"] = GameBasics.Buildings.linqValues().`as`(ICivilopedia::class.java)
        map["Resources"] = GameBasics.TileResources.linqValues().`as`(ICivilopedia::class.java)
        map["Terrains"] = GameBasics.Terrains.linqValues().`as`(ICivilopedia::class.java)
        map["Tile Improvements"] = GameBasics.TileImprovements.linqValues().`as`(ICivilopedia::class.java)

        val nameList = List<ICivilopedia>(CameraStageBaseScreen.skin)

        val nameListClickListener = {
            if(nameList.selected!=null) {
                val building = nameList.selected
                label.setText(building.description)
            }
        }
        nameList.addClickListener (nameListClickListener)

        nameList.style.fontColorSelected = Color.BLACK
        nameList.style.font.data.setScale(1.5f)

        val buttons = ArrayList<Button>()
        var first = true
        for (str in map.keys) {
            val button = TextButton(str, CameraStageBaseScreen.skin)
            button.style.checkedFontColor = Color.BLACK
            buttons.add(button)
            val buttonClicked = {
                val newArray = Array<ICivilopedia>()
                for (civ in map[str]!!) newArray.add(civ)
                nameList.setItems(newArray)
                nameList.selected = nameList.items.get(0)

                for (btn in buttons) btn.isChecked = false
                button.isChecked = true
            }
            button.addClickListener(buttonClicked)
            if (first) {// Fake-click the first button so that the user sees results immediately
                first = false
                buttonClicked()
            }
            button.label.setFontScale(0.7f)
            buttonTable.add(button).width(button.width * 0.7f)
        }

        val sp = ScrollPane(nameList)
        sp.setupOverscroll(5f, 1f, 200f)
        entryTable.add(sp).width(Value.percentWidth(0.25f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        entryTable.add(label).colspan(4).width(Value.percentWidth(0.65f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))

        buttonTable.width = stage.width
    }

}
