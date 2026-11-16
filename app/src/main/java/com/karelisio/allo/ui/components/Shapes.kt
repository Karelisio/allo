package com.karelisio.allo.ui.components

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** Forme Compose à partir d'un polygone MaterialShapes (normalisé dans le carré unité). */
class PolygonShape(polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    private val morph = Morph(polygon, polygon)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(scaled(morph.toPath(0f, Path()), size, rotation))
}

/** Forme qui interpole entre deux polygones (cercle → fleur → cercle sur les touches). */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(scaled(morph.toPath(progress, Path()), size, rotation))
}

internal fun scaled(path: Path, size: Size, rotation: Float): Path {
    val m = Matrix()
    m.scale(size.width, size.height)
    if (rotation != 0f) {
        m.translate(0.5f, 0.5f)
        m.rotateZ(rotation)
        m.translate(-0.5f, -0.5f)
    }
    path.transform(m)
    return path
}

object AlloShapes {
    /** Formes des avatars, choisies de façon stable à partir du nom. */
    val avatarPolygons: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny, MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.Puffy,
            MaterialShapes.Cookie7Sided, MaterialShapes.SoftBurst, MaterialShapes.Arch, MaterialShapes.Circle,
            MaterialShapes.Clover8Leaf,
        )
    }
    val avatarShapes: List<Shape> by lazy { avatarPolygons.map { PolygonShape(it) } }

    fun forKey(key: String): Shape = avatarShapes[(key.hashCode() and 0x7fffffff) % avatarShapes.size]
    fun polygonForKey(key: String): RoundedPolygon = avatarPolygons[(key.hashCode() and 0x7fffffff) % avatarPolygons.size]

    val keyMorph: Morph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Flower) }
    val circle: Shape by lazy { PolygonShape(MaterialShapes.Circle) }
    val cookie: Shape by lazy { PolygonShape(MaterialShapes.Cookie9Sided) }
    val sunny: Shape by lazy { PolygonShape(MaterialShapes.Sunny) }
    val pill: Shape by lazy { PolygonShape(MaterialShapes.Pill) }
}
