package com.revline.idle.ui

/**
 * Hand-drawn side silhouettes for the strip, one per chassis. Nose is +x, the road is y = 0, and the units
 * are the same as [Wireframe] so the wheels land on the same axles. Everything here is plain numbers; the
 * paths are built from them on first draw, so tests can read the data without a canvas.
 */
internal class DragLook(
    /** Closed outline, tail first and clockwise: x, y, rounding (0 sharp .. 0.5 round) per vertex. */
    val body: FloatArray,
    /** Window polygons, same triples. */
    val glass: List<FloatArray> = emptyList(),
    /** Panel lines: x, y pairs, drawn as open polylines. */
    val cuts: List<FloatArray> = emptyList(),
    /** Body-colored add-ons drawn behind the car (fins, wing struts): closed polygons, x, y pairs. */
    val fins: List<FloatArray> = emptyList(),
    val bumpers: Boolean = false,
    /** Spoiler: x, deck y, stand height, chord. Height 0 is a lip. */
    val wing: FloatArray? = null,
    /** Hood or roof scoop: x, y, width, height. */
    val scoop: FloatArray? = null,
    /** Engine-deck slats: x1, x2, y, count. */
    val louvers: FloatArray? = null,
    /** Side exhaust: x1, x2, y. */
    val pipes: FloatArray? = null,
    /** Beltline stripe: y low, y high. */
    val stripe: FloatArray? = null,
    /** Driver's head for the open cars: x, y. */
    val helmet: FloatArray? = null,
    /** Roll hoop: x, base y, top y. */
    val hoop: FloatArray? = null,
    /** Headlamp: x, y, rx, ry. */
    val lamp: FloatArray,
    /** Taillamp: y center, height. */
    val tail: FloatArray,
    /** Cycle fenders over the wheels (the Seven, the Cobra). */
    val flares: Boolean = false,
    /** Extra wheel-well size for skirted arches. */
    val skirt: Float = 1f,
)

internal object DragLooks {
    fun of(id: String, mid: Boolean = false): DragLook = when {
        id == "r5" && mid -> table.getValue("r5mid")
        else -> table[id] ?: table.getValue("mule")
    }

    val ids: Set<String> get() = table.keys

    private fun f(vararg v: Float) = v

    /** A small DSL: `tri(x, y, k, ...)` is just the floats, kept as a function so the tables read like drawings. */
    private fun tri(vararg v: Float) = v

    private val table: Map<String, DragLook> = mapOf(
        "mule" to DragLook(
            body = tri(-1.05f, .16f, .1f, -1.05f, .52f, .06f, -.58f, .52f, 0f, -.55f, .96f, .18f, .02f, 1.0f, .22f, .2f, .64f, .15f, .22f, .52f, .1f, 1.0f, .5f, .12f, 1.05f, .4f, .2f, 1.05f, .16f, .1f),
            glass = listOf(tri(-.44f, .6f, .1f, -.42f, .9f, .2f, -.02f, .93f, .2f, .12f, .62f, .1f)),
            cuts = listOf(f(-.2f, .5f, -.2f, .86f), f(.22f, .52f, .3f, .44f), f(-.58f, .52f, -1.05f, .52f)),
            bumpers = true, stripe = f(.46f, .52f),
            lamp = f(.97f, .44f, .045f, .05f), tail = f(.34f, .12f),
        ),
        "p50" to DragLook(
            body = tri(-.5f, .16f, .15f, -.52f, .62f, .3f, -.42f, .98f, .3f, -.1f, 1.04f, .3f, .22f, .95f, .2f, .42f, .5f, .2f, .5f, .36f, .2f, .5f, .16f, .15f),
            glass = listOf(tri(-.3f, .62f, .1f, -.3f, .92f, .3f, -.08f, .97f, .3f, .18f, .88f, .2f, .3f, .62f, .1f)),
            cuts = listOf(f(-.05f, .3f, -.04f, .88f)), bumpers = true,
            lamp = f(.44f, .42f, .04f, .05f), tail = f(.4f, .12f),
        ),
        "isetta" to DragLook(
            body = tri(-.72f, .16f, .2f, -.78f, .5f, .35f, -.62f, .9f, .4f, -.3f, 1.12f, .5f, .2f, 1.1f, .45f, .62f, .7f, .4f, .78f, .45f, .3f, .76f, .16f, .2f),
            glass = listOf(tri(-.55f, .55f, .1f, -.55f, .88f, .4f, -.25f, 1.04f, .45f, .18f, 1.02f, .4f, .45f, .7f, .3f, .5f, .55f, .1f)),
            cuts = listOf(f(.56f, .2f, .56f, .62f)), bumpers = true,
            lamp = f(.7f, .44f, .04f, .06f), tail = f(.38f, .12f),
        ),
        "cv2" to DragLook(
            body = tri(-1.05f, .18f, .12f, -1.05f, .6f, .2f, -.95f, .8f, .3f, -.62f, 1.22f, .3f, -.1f, 1.28f, .3f, .5f, 1.24f, .25f, .78f, .86f, .3f, .95f, .7f, .2f, 1.0f, .55f, .15f, .98f, .18f, .12f),
            glass = listOf(tri(-.74f, .8f, .1f, -.5f, 1.14f, .3f, .4f, 1.16f, .3f, .64f, .84f, .1f)),
            cuts = listOf(f(-.12f, .56f, -.12f, 1.12f), f(.24f, .56f, .24f, 1.1f)), bumpers = true, skirt = 1.35f,
            lamp = f(.92f, .66f, .05f, .055f), tail = f(.42f, .12f),
        ),
        "kr200" to DragLook(
            body = tri(-1.15f, .17f, .1f, -1.1f, .34f, .3f, -.5f, .5f, .4f, -.3f, .82f, .45f, .05f, .84f, .45f, .4f, .5f, .3f, .95f, .34f, .3f, .95f, .17f, .1f),
            glass = listOf(tri(-.36f, .54f, .1f, -.3f, .76f, .4f, 0f, .8f, .4f, .32f, .54f, .2f)),
            cuts = listOf(f(-.02f, .26f, -.02f, .54f)),
            lamp = f(.88f, .36f, .04f, .045f), tail = f(.3f, .1f),
        ),
        "fiat500" to DragLook(
            body = tri(-.7f, .16f, .15f, -.72f, .5f, .35f, -.62f, .8f, .35f, -.35f, 1.04f, .4f, .05f, 1.05f, .4f, .34f, .92f, .3f, .55f, .62f, .3f, .7f, .48f, .25f, .7f, .16f, .1f),
            glass = listOf(tri(-.52f, .62f, .1f, -.45f, .86f, .3f, -.3f, .98f, .4f, .02f, .98f, .4f, .28f, .86f, .3f, .4f, .62f, .1f)),
            cuts = listOf(f(-.1f, .32f, -.1f, .96f)), bumpers = true,
            lamp = f(.65f, .52f, .04f, .05f), tail = f(.4f, .12f),
        ),
        "saab96" to DragLook(
            body = tri(-.95f, .18f, .12f, -.97f, .55f, .3f, -.85f, .7f, .3f, -.5f, .98f, .5f, -.1f, .99f, .4f, .2f, .94f, .3f, .48f, .68f, .25f, 1.0f, .56f, .2f, 1.05f, .4f, .2f, 1.04f, .18f, .1f),
            glass = listOf(tri(-.72f, .7f, .1f, -.45f, .9f, .5f, -.12f, .9f, .3f, .14f, .86f, .2f, .34f, .68f, .1f)),
            cuts = listOf(f(-.1f, .4f, -.1f, .9f)), bumpers = true,
            lamp = f(.98f, .5f, .045f, .05f), tail = f(.4f, .12f),
        ),
        "sprite" to DragLook(
            body = tri(-.9f, .17f, .1f, -.9f, .5f, .4f, -.6f, .6f, .4f, 0f, .56f, .3f, .15f, .62f, .3f, .55f, .66f, .4f, .85f, .62f, .4f, .95f, .45f, .3f, .95f, .17f, .1f),
            glass = listOf(tri(.02f, .58f, 0f, .12f, .8f, 0f, .19f, .8f, 0f, .15f, .58f, 0f)),
            cuts = listOf(f(-.15f, .22f, -.15f, .56f)), bumpers = true, helmet = f(-.32f, .76f), stripe = f(.34f, .38f),
            lamp = f(.62f, .7f, .07f, .07f), tail = f(.42f, .1f),
        ),
        "mini" to DragLook(
            body = tri(-.85f, .17f, .1f, -.86f, .58f, .15f, -.66f, .64f, .15f, -.58f, .92f, .35f, -.5f, .98f, .3f, .28f, .99f, .25f, .5f, .66f, .3f, .85f, .6f, .15f, .86f, .17f, .1f),
            glass = listOf(tri(-.52f, .68f, .1f, -.46f, .9f, .3f, .22f, .92f, .3f, .4f, .68f, .1f)),
            cuts = listOf(f(0f, .3f, 0f, .94f)), bumpers = true, stripe = f(.46f, .5f),
            lamp = f(.8f, .5f, .045f, .05f), tail = f(.42f, .11f),
        ),
        "amazon" to DragLook(
            body = tri(-1.15f, .18f, .1f, -1.15f, .62f, .15f, -.72f, .66f, .15f, -.55f, .96f, .3f, -.35f, .98f, .3f, .2f, .97f, .3f, .5f, .66f, .2f, 1.1f, .64f, .15f, 1.15f, .5f, .2f, 1.15f, .18f, .1f),
            glass = listOf(tri(-.6f, .7f, .1f, -.46f, .92f, .3f, .14f, .92f, .3f, .36f, .7f, .1f)),
            cuts = listOf(f(-.04f, .3f, -.04f, .92f), f(.24f, .3f, .24f, .62f)), bumpers = true,
            lamp = f(1.07f, .55f, .045f, .045f), tail = f(.4f, .13f),
        ),
        "elan" to DragLook(
            body = tri(-.85f, .16f, .1f, -.85f, .44f, .3f, -.55f, .5f, .3f, -.35f, .66f, .3f, -.1f, .7f, .4f, .12f, .68f, .4f, .3f, .5f, .2f, .8f, .44f, .3f, 1.15f, .34f, .3f, 1.15f, .16f, .1f),
            glass = listOf(tri(-.45f, .52f, .1f, -.3f, .64f, .3f, -.1f, .66f, .4f, .06f, .64f, .3f, .18f, .52f, .1f)),
            cuts = listOf(f(-.05f, .22f, -.05f, .52f)), stripe = f(.3f, .34f),
            lamp = f(.92f, .46f, .07f, .035f), tail = f(.32f, .1f),
        ),
        "gta" to DragLook(
            body = tri(-.85f, .17f, .1f, -.85f, .48f, .15f, -.75f, .55f, .2f, -.45f, .82f, .3f, -.05f, .84f, .3f, .15f, .8f, .3f, .4f, .54f, .2f, 1.0f, .48f, .2f, 1.05f, .38f, .2f, 1.05f, .17f, .1f),
            glass = listOf(tri(-.52f, .58f, .1f, -.38f, .76f, .3f, -.02f, .78f, .3f, .12f, .74f, .3f, .28f, .56f, .1f)),
            cuts = listOf(f(-.05f, .26f, -.05f, .76f)), stripe = f(.36f, .42f), wing = f(-.84f, .5f, 0f, .24f),
            lamp = f(.96f, .44f, .05f, .04f), tail = f(.34f, .11f),
        ),
        "b2002" to DragLook(
            body = tri(-1.05f, .18f, .1f, -1.05f, .52f, .15f, -.7f, .56f, .2f, -.5f, .92f, .3f, .35f, .94f, .3f, .55f, .58f, .2f, 1.08f, .54f, .15f, 1.1f, .4f, .2f, 1.1f, .18f, .1f),
            glass = listOf(tri(-.58f, .62f, .1f, -.44f, .85f, .3f, .3f, .86f, .3f, .46f, .62f, .1f)),
            cuts = listOf(f(-.06f, .28f, -.06f, .86f)), stripe = f(.36f, .42f), wing = f(-1.03f, .54f, 0f, .26f), bumpers = true,
            lamp = f(1.04f, .46f, .05f, .05f), tail = f(.36f, .12f),
        ),
        "seven" to DragLook(
            body = tri(-1.0f, .2f, .1f, -1.0f, .42f, .3f, -.5f, .46f, .3f, -.2f, .5f, .3f, .2f, .52f, .3f, .7f, .42f, .4f, 1.05f, .3f, .4f, 1.05f, .2f, .1f),
            glass = listOf(tri(.12f, .52f, 0f, .2f, .74f, 0f, .26f, .74f, 0f, .2f, .52f, 0f)),
            helmet = f(-.14f, .68f), hoop = f(-.4f, .48f, .8f), flares = true, stripe = f(.36f, .4f),
            lamp = f(.9f, .46f, .06f, .06f), tail = f(.34f, .1f),
        ),
        "r5" to DragLook(
            body = tri(-.8f, .17f, .1f, -.82f, .5f, .2f, -.74f, .7f, .3f, -.5f, .92f, .35f, .1f, .96f, .3f, .3f, .9f, .3f, .55f, .7f, .2f, .88f, .58f, .2f, .9f, .4f, .2f, .9f, .17f, .1f),
            glass = listOf(tri(-.62f, .72f, .1f, -.42f, .88f, .4f, .05f, .9f, .3f, .24f, .86f, .2f, .4f, .72f, .1f)),
            cuts = listOf(f(-.1f, .34f, -.1f, .9f)), bumpers = true,
            lamp = f(.86f, .5f, .045f, .05f), tail = f(.42f, .12f),
        ),
        "r5mid" to DragLook(
            body = tri(-.8f, .17f, .1f, -.82f, .5f, .2f, -.74f, .7f, .3f, -.5f, .92f, .35f, .1f, .96f, .3f, .3f, .9f, .3f, .55f, .7f, .2f, .88f, .58f, .2f, .9f, .4f, .2f, .9f, .17f, .1f),
            glass = listOf(tri(-.3f, .72f, .1f, -.2f, .88f, .4f, .05f, .9f, .3f, .24f, .86f, .2f, .4f, .72f, .1f)),
            cuts = listOf(f(-.4f, .5f, -.4f, .86f)), scoop = f(-.36f, .66f, .22f, .1f), wing = f(-.78f, .72f, .1f, .34f), stripe = f(.38f, .44f),
            lamp = f(.86f, .5f, .045f, .05f), tail = f(.42f, .12f), skirt = 1.2f,
        ),
        "etype" to DragLook(
            body = tri(-.75f, .16f, .1f, -.78f, .4f, .4f, -.55f, .62f, .4f, -.3f, .72f, .4f, .05f, .74f, .45f, .3f, .55f, .3f, .55f, .46f, .3f, 1.35f, .34f, .4f, 1.35f, .16f, .1f),
            glass = listOf(tri(-.45f, .58f, .1f, -.25f, .7f, .4f, 0f, .7f, .4f, .18f, .58f, .1f)),
            cuts = listOf(f(-.05f, .22f, -.05f, .58f), f(.4f, .5f, 1.2f, .42f)), bumpers = true,
            lamp = f(1.22f, .38f, .07f, .045f), tail = f(.32f, .1f),
        ),
        "stratos" to DragLook(
            body = tri(-.95f, .2f, .1f, -.95f, .42f, .3f, -.7f, .62f, .3f, -.2f, .76f, .35f, .1f, .78f, .4f, .55f, .46f, .15f, .88f, .32f, .2f, .9f, .18f, .1f),
            glass = listOf(tri(-.1f, .52f, .1f, -.05f, .72f, .3f, .1f, .74f, .3f, .46f, .48f, .1f)),
            cuts = listOf(f(-.2f, .26f, -.18f, .6f)), wing = f(-.9f, .5f, .14f, .34f), louvers = f(-.78f, -.4f, .64f, 5f), scoop = f(-.3f, .5f, .16f, .1f), stripe = f(.34f, .4f),
            lamp = f(.78f, .38f, .06f, .045f), tail = f(.36f, .1f), skirt = 1.2f,
        ),
        "p911" to DragLook(
            body = tri(-1.05f, .2f, .1f, -1.04f, .4f, .4f, -.85f, .55f, .4f, -.45f, .75f, .5f, -.05f, .78f, .5f, .25f, .6f, .4f, .5f, .5f, .4f, .9f, .42f, .4f, .95f, .3f, .3f, .95f, .2f, .1f),
            glass = listOf(tri(-.55f, .58f, .1f, -.4f, .7f, .4f, -.05f, .72f, .4f, .2f, .58f, .2f)),
            cuts = listOf(f(-.08f, .28f, -.08f, .66f)), wing = f(-1.02f, .45f, 0f, .3f), louvers = f(-1.0f, -.7f, .52f, 4f), stripe = f(.34f, .4f),
            lamp = f(.84f, .46f, .055f, .06f), tail = f(.34f, .1f),
        ),
        "vette" to DragLook(
            body = tri(-.9f, .18f, .1f, -.9f, .42f, .3f, -.7f, .54f, .3f, -.35f, .7f, .4f, -.05f, .72f, .4f, .25f, .55f, .2f, .5f, .52f, .3f, 1.15f, .5f, .3f, 1.2f, .34f, .3f, 1.2f, .18f, .1f),
            glass = listOf(tri(-.4f, .58f, .1f, -.28f, .68f, .4f, -.05f, .7f, .4f, .2f, .58f, .2f)),
            cuts = listOf(f(.7f, .4f, .2f, .36f, -.2f, .4f), f(-.05f, .26f, -.05f, .6f)), scoop = f(.72f, .51f, .22f, .06f), stripe = f(.4f, .44f), pipes = f(-.1f, .26f, .2f),
            lamp = f(1.1f, .44f, .06f, .04f), tail = f(.36f, .1f),
        ),
        "gt40" to DragLook(
            body = tri(-1.15f, .18f, .1f, -1.15f, .34f, .3f, -.8f, .46f, .3f, -.5f, .52f, .3f, -.2f, .55f, .4f, .05f, .53f, .4f, .3f, .4f, .2f, .85f, .3f, .3f, .85f, .2f, .1f),
            glass = listOf(tri(-.35f, .44f, .1f, -.2f, .52f, .4f, 0f, .52f, .4f, .2f, .42f, .1f)),
            cuts = listOf(f(-.1f, .3f, -.1f, .5f)), louvers = f(-1.1f, -.6f, .44f, 5f), scoop = f(-.55f, .5f, .2f, .06f), stripe = f(.28f, .32f), pipes = f(-.2f, .3f, .22f),
            lamp = f(.74f, .34f, .05f, .035f), tail = f(.28f, .08f), skirt = 1.15f,
        ),
        "cobra" to DragLook(
            body = tri(-.85f, .22f, .1f, -.85f, .46f, .3f, -.6f, .54f, .3f, -.2f, .52f, .3f, .15f, .54f, .3f, .55f, .54f, .3f, .95f, .46f, .3f, .95f, .22f, .1f),
            glass = listOf(tri(.1f, .54f, 0f, .18f, .76f, 0f, .24f, .76f, 0f, .2f, .54f, 0f)),
            helmet = f(-.2f, .7f), hoop = f(-.4f, .5f, .82f), scoop = f(.6f, .56f, .26f, .09f), stripe = f(.44f, .48f), pipes = f(-.1f, .3f, .24f), flares = true,
            lamp = f(.88f, .46f, .055f, .06f), tail = f(.38f, .1f),
        ),
        "sel63" to DragLook(
            body = tri(-1.35f, .2f, .1f, -1.35f, .62f, .12f, -.8f, .66f, .15f, -.58f, 1.04f, .25f, .58f, 1.04f, .25f, .8f, .66f, .2f, 1.3f, .64f, .12f, 1.35f, .48f, .15f, 1.35f, .2f, .1f),
            glass = listOf(tri(-.62f, .72f, .1f, -.46f, .98f, .25f, .5f, .98f, .25f, .68f, .72f, .1f)),
            cuts = listOf(f(-.1f, .3f, -.1f, .98f), f(.34f, .3f, .34f, .98f)), bumpers = true,
            lamp = f(1.27f, .56f, .05f, .05f), tail = f(.44f, .12f),
        ),
        "p917" to DragLook(
            body = tri(-1.25f, .17f, .1f, -1.25f, .3f, .3f, -1.0f, .42f, .3f, -.4f, .46f, .4f, -.1f, .5f, .4f, .1f, .52f, .4f, .4f, .34f, .2f, .7f, .26f, .3f, .7f, .17f, .1f),
            glass = listOf(tri(-.1f, .38f, .1f, -.02f, .5f, .4f, .12f, .5f, .4f, .35f, .36f, .1f)),
            fins = listOf(f(-1.24f, .34f, -1.02f, .34f, -.95f, .56f, -1.22f, .58f)),
            cuts = listOf(f(-.3f, .3f, -.3f, .46f)), stripe = f(.28f, .34f), louvers = f(-.9f, -.5f, .46f, 4f),
            lamp = f(.58f, .3f, .06f, .035f), tail = f(.3f, .08f), skirt = 1.2f,
        ),
        "daytona" to DragLook(
            body = tri(-.8f, .18f, .1f, -.82f, .42f, .3f, -.55f, .55f, .3f, -.2f, .74f, .4f, .1f, .76f, .4f, .4f, .5f, .2f, .7f, .48f, .3f, 1.3f, .38f, .3f, 1.3f, .18f, .1f),
            glass = listOf(tri(-.4f, .58f, .1f, -.2f, .7f, .4f, .06f, .7f, .4f, .28f, .52f, .1f)),
            cuts = listOf(f(-.05f, .26f, -.05f, .64f)), stripe = f(.38f, .44f),
            lamp = f(1.2f, .42f, .06f, .045f), tail = f(.34f, .1f),
        ),
        "jensen" to DragLook(
            body = tri(-1.05f, .2f, .1f, -1.05f, .46f, .25f, -.95f, .5f, .3f, -.6f, .64f, .35f, -.35f, .82f, .35f, .12f, .84f, .3f, .4f, .54f, .2f, 1.1f, .5f, .2f, 1.15f, .38f, .2f, 1.15f, .2f, .1f),
            glass = listOf(tri(-.78f, .56f, .1f, -.52f, .74f, .4f, -.3f, .78f, .4f, .08f, .78f, .3f, .3f, .56f, .1f)),
            cuts = listOf(f(-.2f, .3f, -.2f, .78f), f(.4f, .54f, .5f, .4f)), bumpers = true, stripe = f(.4f, .44f),
            lamp = f(1.06f, .42f, .05f, .05f), tail = f(.34f, .11f),
        ),
    )
}
