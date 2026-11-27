package com.zero.todo.feature.execute

/**
 * Minimalist Swiss-architectural Google Maps styles paired with ZERO's Obsidian Dark and Cool Paper Light palettes.
 * Commercial POIs and visual clutter are suppressed so 3D buildings and quest beacons dominate cleanly.
 */
object AtlasMapStyles {

    val OBSIDIAN_DARK_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#0B0C10"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#71717A"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#09090B"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#27272A"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#14161D"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#27272F"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#0E1412"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#1F232C"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#09090B"}]},
          {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#63666F"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#272B36"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#323846"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#181B24"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#1C2029"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#8E95A5"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#071019"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#3E4C5E"}]}
        ]
    """.trimIndent()

    val COOL_PAPER_LIGHT_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#F3F4F7"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#6B7280"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#D4D4D8"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#E6E8EE"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#CFD3DC"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#E4ECE8"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#E2E4EA"}]},
          {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#71717A"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#FAFAFC"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#E5E8F0"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#CBD0DC"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#EAECEF"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#52525B"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#DDE6EF"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#64748B"}]}
        ]
    """.trimIndent()
}
