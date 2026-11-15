package com.reel.editor.model

/**
 * Ready-made text designs. A template is one or more text parts plus optional shapes (bars,
 * badges, frames), each with its own place, style and animation, and a small delay so the
 * parts arrive one after another. Applying a template adds ordinary layers, so everything
 * stays editable afterwards.
 *
 * Positions are canvas fractions (x across, y down). Shape scale is a fraction of the canvas
 * height, and a shape's aspect is its width over its height; a 9:16 canvas is 0.5625 heights wide.
 */
object TextTemplates {
    data class Part(
        val text: String,
        val style: TextStyle,
        val x: Float = 0.5f,
        val y: Float = 0.5f,
        val scale: Float = 1f,
        val rotation: Float = 0f,
        val animIn: TextAnim? = TextAnim("pop", 400_000),
        val animOut: TextAnim? = TextAnim("fade", 300_000),
        val loop: TextAnim? = null,
        val delayUs: Long = 0,
    )

    data class Box(
        val shape: String = "roundrect",
        val fill: Long? = 0xFFFFFFFF,
        val stroke: Long = 0xFFFFFFFF,
        val strokeWidth: Float = 0f,
        val corner: Float = 0.18f,
        val aspect: Float = 1f,
        val x: Float = 0.5f,
        val y: Float = 0.5f,
        val scale: Float = 0.1f,
        val rotation: Float = 0f,
        val opacity: Float = 1f,
        val animIn: TextAnim? = TextAnim("fade", 300_000),
        val animOut: TextAnim? = TextAnim("fade", 300_000),
        val delayUs: Long = 0,
    )

    data class Template(
        val id: String,
        val name: String,
        val category: String,
        val parts: List<Part>,
        val boxes: List<Box> = emptyList(),
        val durationUs: Long = 3_000_000,
        /** Backdrop colour for the picker tile. */
        val tile: Long = 0xFF1B1D24,
    )

    private fun a(id: String, ms: Long = 450) = TextAnim(id, ms * 1000)

    private const val WHITE = 0xFFFFFFFF
    private const val BLACK = 0xFF000000

    val all: List<Template> = listOf(
        // ------------------------------------------------------------------ titles
        Template("t_bold", "Bold title", "Titles", listOf(
            Part("BIG NEWS", TextStyle(font = "anton", sizeSp = 120f, color = WHITE, shadow = true), y = 0.45f, animIn = a("zoom")),
            Part("watch till the end", TextStyle(font = "montserrat", sizeSp = 40f, color = 0xFFFFD36B, shadow = true), y = 0.53f, animIn = a("slideup"), delayUs = 250_000),
        )),
        Template("t_cinema", "Cinema", "Titles", listOf(
            Part("THE JOURNEY", TextStyle(font = "cinzel", sizeSp = 72f, color = 0xFFF2E6C9, letterSpacing = 0.25f, shadow = true), y = 0.48f, animIn = a("fade", 900), animOut = a("fade", 700)),
            Part("A SHORT FILM", TextStyle(font = "raleway", sizeSp = 28f, color = 0xCCF2E6C9, letterSpacing = 0.5f, shadow = false), y = 0.54f, animIn = a("fade", 900), delayUs = 600_000),
        ), boxes = listOf(
            Box("rect", 0xFF000000, aspect = 10f, x = 0.5f, y = 0.06f, scale = 0.12f, animIn = a("slidedown", 600)),
            Box("rect", 0xFF000000, aspect = 10f, x = 0.5f, y = 0.94f, scale = 0.12f, animIn = a("slideup", 600)),
        ), durationUs = 4_000_000, tile = 0xFF101010),
        Template("t_gradient", "Sunset", "Titles", listOf(
            Part("Summer Vibes", TextStyle(font = "pacifico", sizeSp = 96f, color = 0xFFFFC371, gradient = 0xFFFF5F6D, shadow = true), y = 0.45f, animIn = a("elastic", 700), loop = a("float", 2000)),
        ), tile = 0xFF2A1630),
        Template("t_arc", "Arc", "Titles", listOf(
            Part("ADVENTURE AWAITS", TextStyle(font = "bungee", sizeSp = 58f, color = WHITE, curve = -0.55f, shadow = true), y = 0.42f, animIn = a("spread", 600)),
            Part("2026", TextStyle(font = "bebasneue", sizeSp = 70f, color = 0xFF6BE3FF, shadow = true), y = 0.5f, animIn = a("pop"), delayUs = 300_000),
        )),
        Template("t_stack", "Stacked", "Titles", listOf(
            Part("MAKE", TextStyle(font = "archivoblack", sizeSp = 96f, color = WHITE, shadow = false), y = 0.38f, animIn = a("slideleft")),
            Part("IT", TextStyle(font = "archivoblack", sizeSp = 96f, color = 0xFFFF3B6B, shadow = false), y = 0.46f, animIn = a("slideright"), delayUs = 150_000),
            Part("COUNT", TextStyle(font = "archivoblack", sizeSp = 96f, color = WHITE, shadow = false), y = 0.54f, animIn = a("slideleft"), delayUs = 300_000),
        ), tile = 0xFF16161C),
        Template("t_outline", "Outline", "Titles", listOf(
            Part("NO LIMITS", TextStyle(font = "russoone", sizeSp = 100f, color = 0x00FFFFFF, strokeColor = WHITE, strokeWidth = 3f, shadow = false), y = 0.46f, animIn = a("wipe", 600)),
            Part("NO LIMITS", TextStyle(font = "russoone", sizeSp = 100f, color = WHITE, shadow = true), y = 0.53f, animIn = a("wipe", 600), delayUs = 300_000),
        )),
        Template("t_serif", "Elegant", "Titles", listOf(
            Part("Once upon a time", TextStyle(font = "playfairdisplay", sizeSp = 74f, color = WHITE, italic = true, shadow = true), y = 0.47f, animIn = a("rise", 800)),
        ), boxes = listOf(Box("rect", WHITE, aspect = 30f, y = 0.53f, scale = 0.004f, animIn = a("stretch", 700))), tile = 0xFF22201C),

        // ------------------------------------------------------------------ lower thirds
        Template("l_name", "Name tag", "Lower thirds", listOf(
            Part("Alex Morgan", TextStyle(font = "poppins", sizeSp = 46f, color = BLACK, shadow = false, align = TextAlignment.START), x = 0.3f, y = 0.78f, animIn = a("slideright"), delayUs = 150_000),
            Part("Founder, Reel Studio", TextStyle(font = "inter", sizeSp = 26f, color = WHITE, shadow = false), x = 0.3f, y = 0.825f, animIn = a("slideright"), delayUs = 300_000),
        ), boxes = listOf(
            Box("rect", WHITE, aspect = 6.2f, x = 0.3f, y = 0.78f, scale = 0.055f, animIn = a("wipe", 400)),
            Box("rect", 0xFF4C8DFF, aspect = 7.5f, x = 0.3f, y = 0.825f, scale = 0.035f, animIn = a("wipe", 400), delayUs = 150_000),
        ), durationUs = 4_000_000),
        Template("l_pill", "Pill", "Lower thirds", listOf(
            Part("@yourname", TextStyle(font = "montserrat", sizeSp = 38f, color = WHITE, shadow = false), y = 0.82f, animIn = a("pop")),
        ), boxes = listOf(Box("roundrect", 0xE6FF3B6B, corner = 0.5f, aspect = 5f, y = 0.82f, scale = 0.06f, animIn = a("pop"))), tile = 0xFF1A1A22),
        Template("l_news", "Breaking", "Lower thirds", listOf(
            Part("BREAKING", TextStyle(font = "oswald", sizeSp = 34f, color = WHITE, shadow = false), x = 0.2f, y = 0.8f, animIn = a("fade"), loop = a("blink", 1200)),
            Part("Something big just happened", TextStyle(font = "inter", sizeSp = 30f, color = BLACK, shadow = false), x = 0.62f, y = 0.8f, animIn = a("slideleft"), delayUs = 200_000),
        ), boxes = listOf(
            Box("rect", 0xFFE5262E, aspect = 3.2f, x = 0.2f, y = 0.8f, scale = 0.05f, animIn = a("wipe", 300)),
            Box("rect", WHITE, aspect = 7.4f, x = 0.62f, y = 0.8f, scale = 0.05f, animIn = a("wipe", 400), delayUs = 100_000),
        ), durationUs = 4_000_000),
        Template("l_minimal", "Minimal", "Lower thirds", listOf(
            Part("Sam Lee", TextStyle(font = "dmsans", sizeSp = 44f, color = WHITE, shadow = true), x = 0.32f, y = 0.8f, animIn = a("slideup")),
            Part("Photographer", TextStyle(font = "dmsans", sizeSp = 26f, color = 0xB3FFFFFF, shadow = true), x = 0.32f, y = 0.84f, animIn = a("slideup"), delayUs = 200_000),
        ), boxes = listOf(Box("rect", 0xFFFFD36B, aspect = 0.06f, x = 0.08f, y = 0.82f, scale = 0.075f, animIn = a("stretch", 400)))),
        Template("l_location", "Location", "Lower thirds", listOf(
            Part("Lisbon, Portugal", TextStyle(font = "montserrat", sizeSp = 36f, color = WHITE, shadow = true, letterSpacing = 0.05f), y = 0.86f, animIn = a("typewriter", 900)),
        ), boxes = listOf(Box("circle", 0xFFFF5A5F, aspect = 1f, y = 0.815f, scale = 0.025f, animIn = a("drop"))), durationUs = 3_500_000),

        // ------------------------------------------------------------------ social
        Template("s_subscribe", "Subscribe", "Social", listOf(
            Part("SUBSCRIBE", TextStyle(font = "montserrat", sizeSp = 44f, color = WHITE, shadow = false), y = 0.8f, animIn = a("pop"), loop = a("pulse", 1200)),
        ), boxes = listOf(Box("roundrect", 0xFFE5262E, corner = 0.25f, aspect = 4.2f, y = 0.8f, scale = 0.07f, animIn = a("pop")))),
        Template("s_follow", "Follow", "Social", listOf(
            Part("Follow for more", TextStyle(font = "poppins", sizeSp = 40f, color = BLACK, shadow = false), y = 0.8f, animIn = a("bounce", 600), loop = a("bob", 1600)),
        ), boxes = listOf(Box("roundrect", WHITE, corner = 0.5f, aspect = 5.5f, y = 0.8f, scale = 0.065f, animIn = a("bounce", 600)))),
        Template("s_like", "Like", "Social", listOf(
            Part("Double tap", TextStyle(font = "fredoka", sizeSp = 48f, color = WHITE, shadow = true), y = 0.6f, animIn = a("pop"), delayUs = 200_000),
        ), boxes = listOf(Box("heart", 0xFFFF4D6D, aspect = 1f, y = 0.5f, scale = 0.12f, animIn = a("pop"))), tile = 0xFF25141B),
        Template("s_pov", "POV", "Social", listOf(
            Part("POV: you finally did it", TextStyle(font = "sans-bold", sizeSp = 46f, color = BLACK, labelColor = WHITE, shadow = false), y = 0.25f, animIn = a("fade")),
        )),
        Template("s_hook", "Hook", "Social", listOf(
            Part("WAIT FOR IT", TextStyle(font = "luckiestguy", sizeSp = 84f, color = 0xFFFFE14D, strokeColor = BLACK, strokeWidth = 4f, shadow = false), y = 0.3f, animIn = a("pop"), loop = a("shake", 900)),
        )),
        Template("s_part", "Part 2", "Social", listOf(
            Part("PART 2", TextStyle(font = "bebasneue", sizeSp = 90f, color = WHITE, labelColor = 0xFF111111, shadow = false), y = 0.2f, animIn = a("drop")),
        )),
        Template("s_link", "Link in bio", "Social", listOf(
            Part("link in bio", TextStyle(font = "spacegrotesk", sizeSp = 40f, color = WHITE, shadow = false), y = 0.86f, animIn = a("slideup"), loop = a("bob", 1400)),
        ), boxes = listOf(Box("roundrect", 0x00000000, stroke = WHITE, strokeWidth = 2f, corner = 0.5f, aspect = 4.8f, y = 0.86f, scale = 0.06f, animIn = a("fade")))),

        // ------------------------------------------------------------------ quotes
        Template("q_classic", "Quote", "Quotes", listOf(
            Part("“", TextStyle(font = "playfairdisplay", sizeSp = 160f, color = 0xFFFFD36B, shadow = false), y = 0.36f, animIn = a("fade")),
            Part("Do what you love,\nlove what you do.", TextStyle(font = "lora", sizeSp = 52f, color = WHITE, italic = true, shadow = true, lineSpacing = 1.15f), y = 0.47f, animIn = a("letters", 1200), delayUs = 200_000),
            Part("RAY BRADBURY", TextStyle(font = "raleway", sizeSp = 24f, color = 0xB3FFFFFF, letterSpacing = 0.35f, shadow = false), y = 0.58f, animIn = a("fade", 700), delayUs = 1_200_000),
        ), durationUs = 5_000_000, tile = 0xFF1C1A16),
        Template("q_note", "Handwritten", "Quotes", listOf(
            Part("today was a good day", TextStyle(font = "caveat", sizeSp = 80f, color = WHITE, shadow = true), y = 0.5f, rotation = -4f, animIn = a("typewriter", 1300)),
        ), durationUs = 4_000_000),
        Template("q_card", "Card", "Quotes", listOf(
            Part("Small steps\nevery day", TextStyle(font = "dmserifdisplay", sizeSp = 60f, color = 0xFF1E1E1E, shadow = false, lineSpacing = 1.1f), y = 0.5f, animIn = a("rise", 700), delayUs = 200_000),
        ), boxes = listOf(Box("roundrect", 0xFFF5EFE6, corner = 0.08f, aspect = 1.25f, y = 0.5f, scale = 0.3f, animIn = a("pop", 500)))),

        // ------------------------------------------------------------------ neon and retro
        Template("n_neon", "Neon", "Neon", listOf(
            Part("OPEN LATE", TextStyle(font = "monoton", sizeSp = 80f, color = 0xFFFF4FD8, shadow = true), y = 0.45f, animIn = a("glitch", 600), loop = a("flicker", 1800)),
            Part("since 1999", TextStyle(font = "sacramento", sizeSp = 60f, color = 0xFF55F2FF, shadow = true), y = 0.53f, animIn = a("fade", 700), delayUs = 400_000),
        ), tile = 0xFF0B0716),
        Template("n_cyber", "Cyber", "Neon", listOf(
            Part("SYSTEM ONLINE", TextStyle(font = "orbitron", sizeSp = 56f, color = 0xFF39FF14, shadow = true, letterSpacing = 0.12f), y = 0.48f, animIn = a("typewriter", 900), loop = a("flicker", 2400)),
        ), boxes = listOf(Box("rect", 0x00000000, stroke = 0xFF39FF14, strokeWidth = 2f, aspect = 6f, y = 0.48f, scale = 0.08f, animIn = a("wipe", 500))), tile = 0xFF050A05),
        Template("r_arcade", "Arcade", "Retro", listOf(
            Part("PLAYER 1", TextStyle(font = "pressstart2p", sizeSp = 40f, color = 0xFFFFE14D, shadow = false), y = 0.42f, animIn = a("letterpop", 800)),
            Part("READY", TextStyle(font = "pressstart2p", sizeSp = 28f, color = WHITE, shadow = false), y = 0.5f, animIn = a("fade"), loop = a("blink", 900), delayUs = 600_000),
        ), tile = 0xFF0A0A2A),
        Template("r_vhs", "VHS", "Retro", listOf(
            Part("PLAY ▶", TextStyle(font = "vt323", sizeSp = 70f, color = WHITE, shadow = true), x = 0.25f, y = 0.1f, animIn = null, animOut = null, loop = a("flicker", 2000)),
            Part("JAN 01 1998", TextStyle(font = "vt323", sizeSp = 46f, color = WHITE, shadow = true), x = 0.72f, y = 0.92f, animIn = null, animOut = null),
        ), durationUs = 5_000_000, tile = 0xFF1A1A2E),
        Template("r_seventies", "70s", "Retro", listOf(
            Part("groovy", TextStyle(font = "lobster", sizeSp = 110f, color = 0xFFFFB347, strokeColor = 0xFF8B2E16, strokeWidth = 3f, shadow = false, curve = 0.35f), y = 0.47f, animIn = a("swirl", 700), loop = a("wave", 2000)),
        ), tile = 0xFF3A2414),
        Template("r_comic", "Comic", "Retro", listOf(
            Part("POW!", TextStyle(font = "bangers", sizeSp = 130f, color = 0xFFFFE14D, strokeColor = BLACK, strokeWidth = 5f, shadow = false), y = 0.45f, rotation = -8f, animIn = a("pop", 300), loop = a("jelly", 1000)),
        ), boxes = listOf(Box("star", 0xFFE5262E, aspect = 1f, y = 0.45f, scale = 0.32f, rotation = 10f, animIn = a("pop", 300))), tile = 0xFF1E2A5A),

        // ------------------------------------------------------------------ moments
        Template("m_birthday", "Birthday", "Moments", listOf(
            Part("Happy Birthday", TextStyle(font = "greatvibes", sizeSp = 96f, color = 0xFFFFD36B, shadow = true), y = 0.45f, animIn = a("rise", 900), loop = a("float", 2200)),
            Part("MIA", TextStyle(font = "titanone", sizeSp = 70f, color = WHITE, shadow = true), y = 0.54f, animIn = a("pop"), loop = a("heartbeat", 1300), delayUs = 500_000),
        ), boxes = listOf(
            Box("star", 0xFFFFD36B, aspect = 1f, x = 0.2f, y = 0.38f, scale = 0.05f, animIn = a("pop"), delayUs = 300_000),
            Box("star", 0xFFFFD36B, aspect = 1f, x = 0.82f, y = 0.6f, scale = 0.04f, animIn = a("pop"), delayUs = 600_000),
        ), durationUs = 4_000_000, tile = 0xFF2A1A2E),
        Template("m_travel", "Travel", "Moments", listOf(
            Part("TOKYO", TextStyle(font = "staatliches", sizeSp = 150f, color = WHITE, letterSpacing = 0.1f, shadow = true), y = 0.47f, animIn = a("spread", 800)),
            Part("day 03", TextStyle(font = "yellowtail", sizeSp = 54f, color = 0xFFFF5A5F, shadow = true), x = 0.64f, y = 0.53f, rotation = -6f, animIn = a("pop"), delayUs = 500_000),
        ), durationUs = 4_000_000),
        Template("m_recipe", "Recipe", "Moments", listOf(
            Part("STEP 1", TextStyle(font = "fredoka", sizeSp = 34f, color = WHITE, shadow = false), x = 0.5f, y = 0.16f, animIn = a("pop")),
            Part("Chop the onions", TextStyle(font = "nunito", sizeSp = 46f, color = WHITE, shadow = true), y = 0.22f, animIn = a("slideup"), delayUs = 200_000),
        ), boxes = listOf(Box("roundrect", 0xFF2BB673, corner = 0.5f, aspect = 3f, y = 0.16f, scale = 0.05f, animIn = a("pop")))),
        Template("m_countdown", "Countdown", "Moments", listOf(
            Part("3", TextStyle(font = "anton", sizeSp = 200f, color = WHITE, shadow = true), y = 0.47f, animIn = a("zoom", 250), animOut = a("fade", 200)),
        ), boxes = listOf(Box("ring", WHITE, aspect = 1f, y = 0.47f, scale = 0.26f, animIn = a("zoom", 250), animOut = a("fade", 200))), durationUs = 1_000_000),
        Template("m_wedding", "Wedding", "Moments", listOf(
            Part("Anna & Leo", TextStyle(font = "greatvibes", sizeSp = 100f, color = WHITE, shadow = true), y = 0.47f, animIn = a("fade", 1200)),
            Part("06 . 21 . 2026", TextStyle(font = "cinzel", sizeSp = 30f, color = 0xE6FFFFFF, letterSpacing = 0.3f, shadow = true), y = 0.55f, animIn = a("fade", 1000), delayUs = 800_000),
        ), durationUs = 5_000_000, tile = 0xFF2B2622),
        Template("m_gaming", "Gaming", "Moments", listOf(
            Part("VICTORY", TextStyle(font = "blackopsone", sizeSp = 110f, color = 0xFFFFD36B, gradient = 0xFFFF7A00, strokeColor = BLACK, strokeWidth = 3f, shadow = true), y = 0.45f, animIn = a("zoom", 350), loop = a("pulse", 1000)),
            Part("+250 XP", TextStyle(font = "russoone", sizeSp = 46f, color = 0xFF6BE3FF, shadow = true), y = 0.53f, animIn = a("slideup"), delayUs = 400_000),
        ), tile = 0xFF14101E),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    /** Adds the template's layers at [startUs], fitted to [maxUs] of timeline. Returns the project and the id of the main text. */
    fun apply(project: Project, t: Template, startUs: Long, maxUs: Long): Pair<Project, String> {
        val dur = t.durationUs.coerceAtMost(maxUs.coerceAtLeast(1_000_000))
        val texts = t.parts.map { p ->
            val d = p.delayUs.coerceAtMost(dur / 2)
            TextLayer(
                text = p.text, startUs = startUs + d, durationUs = dur - d, style = p.style,
                transform = Transform(x = p.x, y = p.y, scale = p.scale, rotation = p.rotation),
                animIn = p.animIn, animOut = p.animOut, loop = p.loop,
            )
        }
        val shapes = t.boxes.map { b ->
            val d = b.delayUs.coerceAtMost(dur / 2)
            ShapeLayer(
                shape = b.shape, fill = b.fill, stroke = b.stroke, strokeWidth = b.strokeWidth, corner = b.corner, aspect = b.aspect,
                startUs = startUs + d, durationUs = dur - d,
                transform = Transform(x = b.x, y = b.y, scale = b.scale, rotation = b.rotation, opacity = b.opacity),
                animIn = b.animIn, animOut = b.animOut,
            )
        }
        return project.copy(texts = project.texts + texts, shapes = project.shapes + shapes) to texts.first().id
    }

    /** One-tap looks for an existing text: only the style and motion change, the words stay. */
    val styles: List<Pair<String, TextStyle>> = all.map { it.name to it.parts.first().style.copy(curve = 0f) }.distinctBy { it.second }
}
